package com.dewijones92.totum.ytdlp.chaquopy

import android.content.Context
import java.io.File

/**
 * Exposes the bundled QuickJS interpreter (shipped as `libqjs.so`) to yt-dlp.
 *
 * yt-dlp has **deprecated YouTube extraction without a JavaScript runtime**, and without one
 * it silently drops formats rather than failing: a made-for-kids video YouTube serves at
 * 1080p came back as a single 360p stream on Dewi's phone, while the same yt-dlp with
 * `--js-runtimes node` on a laptop returned the full ladder. Chaquopy is CPython — no JS in
 * sight — so the runtime has to be shipped.
 *
 * QuickJS because it is the smallest of the four yt-dlp supports (deno, node, quickjs, bun):
 * about a megabyte per ABI, against ~100MB for a deno binary. yt-dlp looks for it under the
 * name `qjs`, so the same trick as ffmpeg applies — under Android 14's W^X the only
 * app-private place a binary stays executable is `nativeLibraryDir`, so we symlink a file
 * named `qjs` at the extracted `libqjs.so` and hand yt-dlp that path. No binary is ever
 * written to app storage.
 */
internal object QuickJsBinary {

    private const val LINK_DIR = "qjs-bin"

    /**
     * Path to hand yt-dlp as the quickjs runtime, or null when QuickJS was not bundled for
     * this ABI. Idempotent, and refreshed each call so an app update that moves the native
     * directory cannot leave a dangling link behind.
     */
    fun executablePath(context: Context): String? {
        val target = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")
        if (!target.exists()) return null
        val link = File(File(context.filesDir, LINK_DIR).apply { mkdirs() }, "qjs")
        return if (NativeBinaryLink.point(link, target)) link.absolutePath else null
    }
}
