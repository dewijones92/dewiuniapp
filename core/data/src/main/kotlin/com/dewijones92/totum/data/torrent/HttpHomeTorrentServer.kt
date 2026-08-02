package com.dewijones92.totum.data.torrent

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.HttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * [HomeTorrentServer] over Prowlarr (search) and TorrServer (streaming) on Dewi's Pi.
 *
 * Both sit behind the same nginx + oauth2-proxy gate, restricted to one Google address, so every
 * request here needs the session cookie the app obtained at sign-in — supplied by whatever
 * `CookieJar` the [client] carries, which is why authentication does not appear in this class at
 * all.
 *
 * Endpoints verified live 2026-08-01 against the real services rather than read from docs.
 */
public class HttpHomeTorrentServer(
    private val client: OkHttpClient,
    /** One host, e.g. `https://totum.example.com` — Prowlarr under `/prowlarr/`, TorrServer `/ts/`. */
    private val base: String,
    /** Prowlarr requires its own key behind the proxy; the gate protects, it does not identify. */
    private val prowlarrApiKey: String,
    /**
     * The token obtained by signing in with Google, replayed on every request.
     *
     * Read per call rather than captured, so a fresh sign-in takes effect immediately instead of
     * after a restart — and so a blank one produces an honest 401 rather than a silent failure.
     */
    private val token: () -> String,
) : HomeTorrentServer {

    private val prowlarrBase get() = "$base/prowlarr"
    private val torrServerBase get() = "$base/ts"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): TorrentSearchResult = withContext(Dispatchers.IO) {
        // Asked BEFORE the request, because signing in is the one cause the person can fix and
        // the reply it produces is indistinguishable from every other refusal: nginx answers a
        // missing token and a wrong one with the same bare 401. Report 0.1.308 said only
        // "search failed: HTTP 401", which named the symptom and hid the entire cause.
        if (token().isBlank()) {
            Diag.log("torrent", "not searching for \"$query\": not signed in to the home server")
            return@withContext TorrentSearchResult.Failure("sign in to the home server first")
        }
        val encoded = query.replace(" ", "+")
        val request = Request.Builder()
            .url("$prowlarrBase/api/v1/search?query=$encoded&type=search")
            .header("X-Api-Key", prowlarrApiKey)
            .header(TOKEN_HEADER, token())
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Each code says something different to the person holding the phone, and
                    // "HTTP 401" says nothing at all. A rejected token means the sign-in has to
                    // be done again; a gateway timeout means the search itself was slow and
                    // retrying may work; anything else is genuinely the server.
                    val detail = when (response.code) {
                        HTTP_UNAUTHORIZED -> "the home server rejected the sign-in — sign in again"
                        HTTP_GATEWAY_TIMEOUT -> "the search took too long — try a narrower one"
                        else -> "the home server answered HTTP ${response.code}"
                    }
                    Diag.warn("torrent", "search for \"$query\" failed: HTTP ${response.code} — $detail")
                    return@withContext TorrentSearchResult.Failure(detail)
                }
                val body = response.body.string()
                val results = parseProwlarr(body) ?: run {
                    // A 200 that is not a search response means the request reached the wrong
                    // thing — both services answer an unknown path with their own web UI rather
                    // than a 404. Named precisely, because "0 results" and "you are talking to a
                    // login page" look identical from the outside and have nothing in common.
                    Diag.warn(
                        "torrent",
                        "search for \"$query\" got HTTP 200 but not a search response " +
                            "(${response.header("Content-Type")}, ${body.length} chars) — misrouted?",
                    )
                    return@withContext TorrentSearchResult.Failure("the home server returned an unreadable reply")
                }
                Diag.log("torrent", "search \"$query\" -> ${results.size} result(s)")
                TorrentSearchResult.Success(results)
            }
        } catch (e: IOException) {
            // The Pi is only reachable at home or over wg-home, so this is the ordinary case of
            // being elsewhere rather than a fault. Said plainly so the UI can say it plainly.
            Diag.warn("torrent", "search for \"$query\" could not reach the home server", e)
            TorrentSearchResult.Failure(e.message ?: "could not reach the home server")
        }
    }

    override suspend fun prepare(magnet: String): PreparedTorrent? = withContext(Dispatchers.IO) {
        // save_to_db false: the server keeps a RAM cache and nothing is written to disk, which is
        // what makes this sustainable on a Pi that is 88% full.
        val added = post(
            "$torrServerBase/torrents",
            """{"action":"add","link":${magnet.quoted()},"save_to_db":false}""",
        ) ?: return@withContext null
        val hash = added["hash"]?.jsonPrimitive?.contentOrNull ?: run {
            Diag.warn("torrent", "server accepted the magnet but returned no hash")
            return@withContext null
        }
        val name = added["name"]?.jsonPrimitive?.contentOrNull ?: "torrent"
        // Metadata arrives from the swarm a moment after the add, so the file list is asked for
        // separately rather than assumed to be in the add response.
        val files = filesFor(hash)
        Diag.log("torrent", "prepared ${hash.take(HASH_CHARS)} \"$name\" with ${files.size} file(s)")
        PreparedTorrent(hash, name, files)
    }

    private fun filesFor(hash: String): List<TorrentFile> {
        val listed = post("$torrServerBase/torrents", """{"action":"get","hash":${hash.quoted()}}""")
        val stats = listed?.get("file_stats") as? JsonArray ?: return emptyList()
        return stats.mapIndexedNotNull { position, element ->
            val file = element.jsonObject
            TorrentFile(
                index = file["id"]?.jsonPrimitive?.intOrNull ?: (position + 1),
                path = file["path"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null,
                sizeBytes = file["length"]?.jsonPrimitive?.longOrNull ?: 0,
            )
        }
    }

    /**
     * The token rides in the QUERY here, not a header, and that is deliberate.
     *
     * This URL is handed to ExoPlayer, which fetches it with its own HTTP stack and knows
     * nothing about the app's headers. A header-only scheme would authenticate every call the
     * app makes and then fail on the one that actually plays the video.
     */
    override fun stream(torrent: PreparedTorrent, file: TorrentFile): HttpUrl = HttpUrl.of(
        "$torrServerBase/stream/${file.name.urlPath()}" +
            "?link=${torrent.hash}&index=${file.index}&play&totumToken=${token()}",
    )

    private fun post(url: String, body: String): JsonObject? = try {
        val request = Request.Builder()
            .url(url)
            .header(TOKEN_HEADER, token())
            .post(body.toRequestBody(JSON_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            when {
                !response.isSuccessful -> {
                    Diag.warn("torrent", "POST $url failed: HTTP ${response.code}")
                    null
                }
                text.isBlank() -> null
                else -> json.parseToJsonElement(text) as? JsonObject
            }
        }
    } catch (e: IOException) {
        Diag.warn("torrent", "POST $url could not reach the home server", e)
        null
    }

    /** Minimal JSON string quoting — magnets carry `&`, `=` and quotes that would break a body. */
    private fun String.quoted(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** Only what a path segment cannot contain; the server treats this purely as a label. */
    private fun String.urlPath(): String = replace(" ", "%20").replace("?", "").replace("#", "")

    private companion object {
        val JSON_TYPE = "application/json".toMediaType()

        /** Checked by nginx before anything is proxied; a wrong or missing one is a 401. */
        const val TOKEN_HEADER = "X-Totum-Token"

        const val HASH_CHARS = 12

        /** Both mean "sign in again" / "that was slow" rather than "the server is broken". */
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_GATEWAY_TIMEOUT = 504
    }
}
