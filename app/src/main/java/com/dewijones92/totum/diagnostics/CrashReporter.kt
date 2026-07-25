package com.dewijones92.totum.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.getSystemService
import com.dewijones92.totum.BuildConfig
import com.dewijones92.totum.common.Breadcrumbs
import com.dewijones92.totum.common.Diag
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Catches crashes, writes a verbose report to disk, and lets [DiagnosticsUploader] send
 * it. Disk first, always: a crash is often the last thing the process does, so a report
 * that only existed in memory (or was mid-upload) would be lost. Anything left on disk
 * is sent on the next launch.
 *
 * **Collection policy:** verbose by explicit instruction (Dewi, 2026-07-25 — "forget
 * about PII or data sensitivity … prioritise collecting data"). Titles, URLs, ids,
 * queue contents and settings all go in. The single exception is credentials: the
 * YouTube OAuth tokens are never read here, because a token in a transmitted log is an
 * account-takeover risk rather than a disclosure of viewing habits.
 */
public class CrashReporter(
    private val context: Context,
    private val stateProviders: () -> Map<String, String> = { emptyMap() },
) {
    /** Installs the handler, chaining to whatever was there so the app still dies properly. */
    public fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeReport(kind = "crash", error = error, thread = thread.name) }
            previous?.uncaughtException(thread, error)
        }
        Diag.log("diagnostics", "crash reporter installed")
    }

    /** Writes a report for something that was handled but shouldn't have happened. */
    public fun reportNonFatal(error: Throwable, note: String? = null) {
        runCatching { writeReport(kind = "non-fatal", error = error, note = note) }
    }

    /** Writes a report with no error at all — "something felt wrong", from Settings. */
    public fun reportDiagnostics(note: String? = null): File? =
        runCatching { writeReport(kind = "diagnostics", error = null, note = note) }.getOrNull()

    private fun writeReport(
        kind: String,
        error: Throwable?,
        thread: String? = null,
        note: String? = null,
    ): File {
        val report = JSONObject().apply {
            put("kind", kind)
            put("reportedAt", Instant.now().toString())
            put("appVersion", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("gitCommit", BuildConfig.GIT_SHA)
            put("buildType", BuildConfig.BUILD_TYPE)
            put("installId", InstallId.get(context))
            note?.let { put("note", it) }
            thread?.let { put("thread", it) }

            error?.let {
                put("exception", it.javaClass.name)
                put("message", it.message ?: "")
                put("stackTrace", it.stackTraceText())
                it.cause?.let { cause ->
                    put("causeException", cause.javaClass.name)
                    put("causeMessage", cause.message ?: "")
                }
            }

            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            put("memory", memoryInfo())
            put("storageFreeMb", freeStorageMb())

            // Whatever the app can tell us about itself right now — playback, queue,
            // settings. Supplied by the caller so this class needs no app dependencies.
            put("state", JSONObject(stateProviders().toMap()))

            put("events", breadcrumbsJson())
            put("logcat", logcatTail())
        }
        return DiagnosticsStore.write(context, report)
    }

    private fun Throwable.stackTraceText(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun breadcrumbsJson(): JSONArray {
        val array = JSONArray()
        Breadcrumbs.snapshot().forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("at", Breadcrumbs.formatTime(entry.atEpochMs))
                    put("tag", entry.tag)
                    put("message", entry.message)
                },
            )
        }
        return array
    }

    /**
     * Our own logcat, which on modern Android is all an app can read — and all we want.
     * This is where the Media3 / MediaCodec / ExoPlayer lines live, and those were what
     * actually diagnosed this project's playback bugs.
     */
    private fun logcatTail(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", LOGCAT_LINES.toString())
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }.takeLast(MAX_LOGCAT_CHARS)
    }.getOrElse { "logcat unavailable: ${it.javaClass.simpleName}: ${it.message}" }

    private fun memoryInfo(): String {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService<ActivityManager>()?.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / MB
        return "heapUsed=${usedMb}MB heapMax=${runtime.maxMemory() / MB}MB " +
            "systemAvail=${info.availMem / MB}MB lowMemory=${info.lowMemory}"
    }

    private fun freeStorageMb(): Long = runCatching {
        StatFs(Environment.getDataDirectory().path).availableBytes / MB
    }.getOrDefault(-1)

    private companion object {
        const val MB = 1024L * 1024L
        const val LOGCAT_LINES = 1500
        const val MAX_LOGCAT_CHARS = 400_000
    }
}
