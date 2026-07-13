package com.dewijones92.uniapp.ytdlp.chaquopy

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Exposes the bundled ffmpeg (shipped as `libffmpeg.so`) to yt-dlp.
 *
 * Under Android 14's W^X policy the only app-private place a binary stays
 * executable is `nativeLibraryDir`, where the installer extracts native libs —
 * but yt-dlp's `ffmpeg_location` needs a directory containing a file literally
 * named `ffmpeg`. So we symlink `<filesDir>/ffmpeg-bin/ffmpeg` at the extracted
 * `libffmpeg.so`: exec follows the link to the executable inode, and no binary
 * is ever written to app storage.
 */
internal object FfmpegBinary {

    private const val LINK_DIR = "ffmpeg-bin"

    /**
     * Directory to hand yt-dlp as `ffmpeg_location`, or null if ffmpeg was not
     * bundled for this ABI. Cheap and idempotent; the link is refreshed each
     * call so an app update that moves the native dir can't leave it dangling.
     */
    fun locationDir(context: Context): String? {
        val lib = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!lib.exists()) return null

        val dir = File(context.filesDir, LINK_DIR).apply { mkdirs() }
        val link = File(dir, "ffmpeg")
        try {
            if (link.exists() || isSymlink(link)) link.delete()
            Os.symlink(lib.absolutePath, link.absolutePath)
        } catch (e: ErrnoException) {
            if (e.errno != OsConstants.EEXIST) return null
        }
        return dir.absolutePath
    }

    private fun isSymlink(file: File): Boolean =
        try {
            OsConstants.S_ISLNK(Os.lstat(file.absolutePath).st_mode)
        } catch (_: ErrnoException) {
            false
        }
}
