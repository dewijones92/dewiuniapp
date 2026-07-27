package com.dewijones92.totum.common

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Running counts and latest values worth knowing when something goes wrong — how many
 * times playback stalled, how long it spent buffering, what the last player error was.
 *
 * The counterpart to [Breadcrumbs]: that answers "what happened just now", this answers
 * "how has this session been going". A stall that scrolled off the end of a 400-entry
 * trail still shows up here as a number, which is what makes an intermittent problem
 * (buffering especially) visible at all.
 *
 * Lives beside [Diag] in the platform-neutral module for the same reason: the values
 * worth having come from the lower layers, and a registry only the UI could write to
 * would miss them.
 */
public object Vitals {

    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val values = ConcurrentHashMap<String, String>()

    /** Adds to a running total, e.g. `Vitals.add("playback.stalls")`. */
    public fun add(name: String, amount: Long = 1) {
        counters.computeIfAbsent(name) { AtomicLong() }.addAndGet(amount)
    }

    /** Records the latest value of something, replacing any previous one. */
    public fun set(name: String, value: String) {
        values[name] = value
    }

    /** Everything recorded, sorted so two reports can be read side by side. */
    public fun snapshot(): Map<String, String> =
        (counters.mapValues { (_, count) -> count.get().toString() } + values).toSortedMap()

    public fun clear() {
        counters.clear()
        values.clear()
    }
}
