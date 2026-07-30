package com.dewijones92.totum.ytdlp.chaquopy

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Points a chosen filename at a bundled native binary.
 *
 * Under Android 14's W^X the only app-private place a binary stays executable is
 * `nativeLibraryDir`, where the installer extracts native libs — but the tools we hand to
 * yt-dlp must be found under their real names (`ffmpeg`, `ffprobe`, `qjs`), not
 * `libffmpeg.so`. A symlink resolves both: exec follows it to the executable inode, and no
 * binary is ever written to app storage.
 *
 * Shared by [FfmpegBinary] and [QuickJsBinary] — the second one is what turned this from a
 * private helper into a seam.
 */
internal object NativeBinaryLink {

    /** Points [link] at [target], replacing any stale link. False when the target is absent. */
    fun point(link: File, target: File): Boolean {
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
