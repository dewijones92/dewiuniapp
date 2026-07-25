package com.dewijones92.uniapp.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * A rolling in-memory trail of what the app just did, sent with a crash so the failure
 * arrives with the story that led to it.
 *
 * It lives here — in the platform-neutral module every other module already depends on —
 * rather than in `:app`, because the most valuable breadcrumbs come from the lower
 * layers: playback transitions, codec rejections, extraction failures. A trail that only
 * the UI could write would miss exactly the lines that diagnose a bug.
 *
 * Bounded by count so a long session can't grow without limit; oldest fall off first.
 */
public object Breadcrumbs {

    /**
     * Kept well above the "last 30 events" originally asked for: 30 can be a single
     * second of position ticks, and holding a few hundred short strings costs
     * practically nothing next to the value of seeing further back.
     */
    private const val MAX_ENTRIES = 400

    private val entries = ConcurrentLinkedDeque<Entry>()

    public data class Entry(val atEpochMs: Long, val tag: String, val message: String)

    public fun record(tag: String, message: String) {
        entries.addLast(Entry(System.currentTimeMillis(), tag, message))
        while (entries.size > MAX_ENTRIES) entries.pollFirst()
    }

    /** Oldest first, so a report reads top-to-bottom as a story. */
    public fun snapshot(): List<Entry> = entries.toList()

    public fun clear(): Unit = entries.clear()

    public fun formatTime(epochMs: Long): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
}

/**
 * The one call for "log this and remember it" — used from every module in place of
 * `android.util.Log`, so anything worth logging is also crash context.
 *
 * Printing is delegated to a [Sink] the platform installs, which is what lets this
 * object live in a pure-JVM module: `:app` installs an `android.util.Log` sink at
 * startup, tests leave the silent default in place.
 */
public object Diag {

    public enum class Level { INFO, WARN }

    public fun interface Sink {
        public fun write(level: Level, tag: String, message: String, error: Throwable?)
    }

    /** Silent by default so unit tests and pure-JVM callers need no setup. */
    public var sink: Sink = Sink { _, _, _, _ -> }

    public fun log(tag: String, message: String) {
        Breadcrumbs.record(tag, message)
        sink.write(Level.INFO, tag, message, null)
    }

    /** Records a throwable that was handled, so swallowed failures still leave a trail. */
    public fun warn(tag: String, message: String, error: Throwable? = null) {
        val text = if (error == null) message else "$message — ${error.javaClass.name}: ${error.message}"
        Breadcrumbs.record(tag, text)
        sink.write(Level.WARN, tag, text, error)
    }

    /** The single logcat tag everything goes out under, so `logcat -s dewidebug` sees it all. */
    public const val LOGCAT_TAG: String = "dewidebug"
}
