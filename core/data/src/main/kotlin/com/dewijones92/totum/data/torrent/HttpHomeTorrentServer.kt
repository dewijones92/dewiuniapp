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
    private val prowlarrBase: String,
    private val torrServerBase: String,
    /** Prowlarr requires its own key even behind the gate; the gate protects, it does not identify. */
    private val prowlarrApiKey: String,
) : HomeTorrentServer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): TorrentSearchResult = withContext(Dispatchers.IO) {
        val encoded = query.replace(" ", "+")
        val request = Request.Builder()
            .url("$prowlarrBase/api/v1/search?query=$encoded&type=search")
            .header("X-Api-Key", prowlarrApiKey)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Diag.warn("torrent", "search for \"$query\" failed: HTTP ${response.code}")
                    return@withContext TorrentSearchResult.Failure("HTTP ${response.code}")
                }
                val results = parseProwlarr(response.body.string())
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

    override fun stream(torrent: PreparedTorrent, file: TorrentFile): HttpUrl =
        HttpUrl.of("$torrServerBase/stream/${file.name.urlPath()}?link=${torrent.hash}&index=${file.index}&play")

    private fun post(url: String, body: String): JsonObject? = try {
        val request = Request.Builder().url(url).post(body.toRequestBody(JSON_TYPE)).build()
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

        const val HASH_CHARS = 12
    }
}
