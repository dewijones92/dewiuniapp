package com.dewijones92.uniapp.ytdlp.chaquopy

import java.io.File

/**
 * The on-disk store of runtime-downloaded yt-dlp wheels. Pure file logic (no
 * network, no Python): the [YtDlpUpdater] writes into it, [ChaquopyYtDlpEngine]
 * reads the active wheel from it. At most one wheel is kept.
 */
internal class YtDlpUpdateCache(private val dir: File) {

    /** Absolute path of the cached wheel to activate, or null if none. */
    fun activeWheelPath(): String? = wheel()?.absolutePath

    /** Version of the cached wheel, or null if none — used to skip re-downloads. */
    fun cachedVersion(): String? = wheel()?.let(::versionOf)

    /** Store [bytes] as the wheel for [version], atomically, replacing any older wheel. */
    fun install(version: String, bytes: ByteArray): File {
        dir.mkdirs()
        val dest = File(dir, "$PREFIX$version$SUFFIX")
        val tmp = File(dir, "$PREFIX$version$SUFFIX.part")
        tmp.writeBytes(bytes)
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        dir.listFiles { f -> f.name.endsWith(SUFFIX) && f != dest }?.forEach { it.delete() }
        return dest
    }

    fun delete(path: String) {
        File(path).delete()
    }

    private fun wheel(): File? =
        dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            ?.maxByOrNull { versionKey(versionOf(it)) }

    private companion object {
        const val PREFIX = "yt_dlp-"
        const val SUFFIX = ".whl"

        fun versionOf(f: File): String = f.name.removePrefix(PREFIX).removeSuffix(SUFFIX)

        /** Zero-pad each dotted segment so date-based versions sort correctly as strings. */
        fun versionKey(v: String): String =
            v.split(".").joinToString(".") { it.padStart(VERSION_PAD, '0') }

        const val VERSION_PAD = 4
    }
}
