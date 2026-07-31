package com.dewijones92.totum.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the app is waiting on, so one indicator can say "something is happening" everywhere.
 *
 * Dewi's ask (2026-07-31): *"we need GLOBALLY middleware maybe??? a spinner gui when ANYTHING
 * is loading"*. The failure it exists to kill is tapping something and being left to guess —
 * "go to channel" took **12.5 seconds** with no feedback at all, which is indistinguishable
 * from the tap not having registered.
 *
 * An object, like [Diag], and for the same reason: this is a cross-cutting concern reported
 * from the lowest layers (HTTP, the extraction engine) and read by the top one. Threading it
 * through every constructor between them would be a lot of plumbing for one boolean, and the
 * layers that most need to report are the ones furthest from the UI.
 *
 * Registered work is **named**, not merely counted, so a report can say *what* the app was
 * waiting on rather than just that it was. A count alone would have left "12.5 seconds of
 * something" as the diagnosis.
 */
public object Busy {

    private val active = MutableStateFlow<List<String>>(emptyList())

    /** What is in flight right now, most recently started last. Empty when idle. */
    public val work: StateFlow<List<String>> get() = active

    /**
     * Marks [label] as in flight until the returned handle is closed. Paired rather than
     * scoped because the callers that matter most — an OkHttp interceptor, a Media3
     * listener — are not suspending functions and cannot take a lambda.
     *
     * Always close it in a `finally`, or the app is busy forever.
     */
    public fun begin(label: String): Handle {
        active.update { it + label }
        return Handle(label)
    }

    /** Runs [block], reporting it as in flight for its duration. */
    public suspend fun <T> during(label: String, block: suspend () -> T): T {
        val handle = begin(label)
        try {
            return block()
        } finally {
            handle.close()
        }
    }

    /** One piece of in-flight work. Closing twice is harmless; not closing is not. */
    public class Handle internal constructor(private val label: String) {
        private var closed = false

        public fun close() {
            if (closed) return
            closed = true
            // Removes ONE matching label rather than all of them: two concurrent requests to
            // the same endpoint share a label, and dropping both when the first finishes
            // would report idle while work was still running.
            active.update { current ->
                val at = current.indexOf(label)
                if (at < 0) current else current.subList(0, at) + current.subList(at + 1, current.size)
            }
        }
    }

    /** Forgets all in-flight work. For tests, so one case cannot leak into the next. */
    public fun reset() {
        active.value = emptyList()
    }
}

/**
 * `MutableStateFlow.update` for a plain read-modify-write, which is atomic (a compare-and-set
 * retry loop) and so needs no lock of its own.
 */
private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    while (true) {
        val current = value
        if (compareAndSet(current, transform(current))) return
    }
}
