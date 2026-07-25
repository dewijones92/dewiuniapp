package com.dewijones92.uniapp.diagnostics

import android.content.Context
import com.dewijones92.uniapp.common.Diag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Sends pending reports to the sink and deletes them once accepted.
 *
 * Runs at launch rather than at crash time: at crash time the process is dying and a
 * network call would likely be killed mid-flight, so the crash handler only writes to
 * disk and the next launch does the sending.
 */
public class DiagnosticsUploader(
    private val context: Context,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val endpoint: String = ENDPOINT,
) {
    public fun uploadPending() {
        scope.launch(Dispatchers.IO) {
            val pending = DiagnosticsStore.pending(context)
            if (pending.isEmpty()) return@launch
            Diag.log("diagnostics", "uploading ${pending.size} pending report(s)")
            pending.forEach { file ->
                val sent = runCatching { post(file.readText()) }.getOrElse { error ->
                    Diag.warn("diagnostics", "upload failed, keeping ${file.name}", error)
                    false
                }
                // Kept on failure so it retries next launch; deleted only once accepted.
                if (sent) file.delete()
            }
        }
    }

    private fun post(body: String): Boolean {
        val request = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                Diag.log("diagnostics", "report upload -> HTTP ${response.code}")
                response.isSuccessful
            }
        } catch (e: IOException) {
            Diag.warn("diagnostics", "report upload failed", e)
            false
        }
    }

    private companion object {
        /** The sink on Dewi's Pi; /ingest is the only unauthenticated path there. */
        const val ENDPOINT = "https://crashlog.333133333.xyz/ingest"
        val JSON = "application/json".toMediaType()
    }
}
