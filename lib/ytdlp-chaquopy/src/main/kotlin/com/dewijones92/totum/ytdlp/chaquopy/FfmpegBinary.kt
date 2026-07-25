package com.dewijones92.totum.ytdlp.chaquopy

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Exposes the bundled ffmpeg **and ffprobe** (shipped as `libffmpeg.so` / `libffprobe.so`)
 * to yt-dlp.
 *
 * Under Android 14's W^X policy the only app-private place a binary stays
 * executable is `nativeLibraryDir`, where the installer extracts native libs —
 * but yt-dlp's `ffmpeg_location` needs a directory containing a file literally
 * named `ffmpeg`. So we symlink `<filesDir>/ffmpeg-bin/ffmpeg` at the extracted
 * `libffmpeg.so`: exec follows the link to the executable inode, and no binary
 * is ever written to app storage.
 *
 * ffprobe is linked into the SAME directory, because that is where yt-dlp looks for it —
 * `ffmpeg_location` names a directory, not a file. Without it the SponsorBlock
 * `ModifyChapters` postprocessor cannot read a duration and every audio download of a video
 * fails with "ffprobe not found", which is exactly what happened on Dewi's phone.
 */
internal object FfmpegBinary {

    private const val LINK_DIR = "ffmpeg-bin"

    /**
     * Directory to hand yt-dlp as `ffmpeg_location`, or null if ffmpeg was not
     * bundled for this ABI. Cheap and idempotent; the link is refreshed each
     * call so an app update that moves the native dir can't leave it dangling.
     */
    fun locationDir(context: Context): String? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        // ffmpeg is required; ffprobe is linked when present so an older build that shipped
        // without it still merges streams rather than failing outright.
        if (!File(nativeDir, "libffmpeg.so").exists()) return null

        val dir = File(context.filesDir, LINK_DIR).apply { mkdirs() }
        if (!link(File(nativeDir, "libffmpeg.so"), File(dir, "ffmpeg"))) return null
        link(File(nativeDir, "libffprobe.so"), File(dir, "ffprobe"))
        return dir.absolutePath
    }

    /** Points [link] at [target], replacing any stale link. False when the target is absent. */
    private fun link(target: File, link: File): Boolean {
        if (!target.exists()) return false
        return try {
            if (link.exists() || isSymlink(link)) link.delete()
            Os.symlink(target.absolutePath, link.absolutePath)
            true
        } catch (e: ErrnoException) {
            e.errno == OsConstants.EEXIST
        }
    }

    private fun isSymlink(file: File): Boolean =
        try {
            OsConstants.S_ISLNK(Os.lstat(file.absolutePath).st_mode)
        } catch (_: ErrnoException) {
            false
        }
}
