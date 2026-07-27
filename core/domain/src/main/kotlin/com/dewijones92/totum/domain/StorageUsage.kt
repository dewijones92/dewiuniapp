package com.dewijones92.totum.domain

/**
 * How much room the downloads are taking, and how much is left.
 *
 * Needed rather than nice: the queue downloads everything in it automatically, so the app
 * fills a disk on its own, quietly, without anyone asking it to. A number that only
 * appears once the phone complains is a number that arrived too late.
 */
public data class StorageUsage(
    public val itemCount: Int,
    public val usedBytes: Long,
    /** Free space on the volume the downloads live on; null if it can't be read. */
    public val freeBytes: Long?,
) {
    public companion object {
        public val Empty: StorageUsage = StorageUsage(itemCount = 0, usedBytes = 0, freeBytes = null)
    }
}

/**
 * Bytes as a short human string — "1.2 GB". Rounded to one decimal above a megabyte,
 * whole numbers below, since "0.4 MB" reads worse than "412 KB".
 */
public fun formatBytes(bytes: Long): String = when {
    bytes < KB -> "$bytes B"
    bytes < KB * KB -> "${(bytes / KB).toInt()} KB"
    bytes < KB * KB * KB -> "${roundToOneDecimal(bytes / (KB * KB))} MB"
    else -> "${roundToOneDecimal(bytes / (KB * KB * KB))} GB"
}

private const val KB = 1024.0

/** One decimal, dropped when it says nothing — "5 MB" reads better than "5.0 MB". */
private fun roundToOneDecimal(value: Double): String {
    val scaled = Math.round(value * TENTHS) / TENTHS
    return if (scaled == scaled.toLong().toDouble()) scaled.toLong().toString() else scaled.toString()
}

private const val TENTHS = 10.0
