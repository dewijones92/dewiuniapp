package com.dewijones92.uniapp.common

/**
 * An opaque "where to continue from" marker. Never parsed or constructed by the app —
 * a source hands one out and takes it back, which is what lets one type serve YouTube's
 * continuation tokens, an offset, or a cursor without callers caring which it is.
 */
@JvmInline
public value class PageToken(public val value: String) {
    init {
        require(value.isNotBlank()) { "PageToken must not be blank" }
    }

    /** Redacted: tokens are long, opaque and pure noise in a log. */
    override fun toString(): String = "PageToken(…)"
}

/**
 * One page of results, plus how to get the next one.
 *
 * The single shape for every paged source in the app — account feeds, channel tabs,
 * search, comments — so infinite scroll is written once rather than per feed. A source
 * with nothing more to give (an RSS document holds the whole feed) returns [last], and
 * that is a complete answer rather than a special case: pagination unifies across the
 * pillars because "no more pages" is an ordinary page.
 */
public data class Page<out T>(
    public val items: List<T>,
    public val next: PageToken? = null,
) {
    public val hasMore: Boolean get() = next != null

    public fun <R> map(transform: (T) -> R): Page<R> = Page(items.map(transform), next)

    public companion object {
        /** A page with nothing after it. */
        public fun <T> last(items: List<T>): Page<T> = Page(items, next = null)

        public fun <T> empty(): Page<T> = Page(emptyList(), next = null)
    }
}

/**
 * Appends [next] to this page, keeping the newer continuation. Deduplicates by [key]
 * so a repeated or overlapping page — which YouTube does return — cannot double rows.
 */
public fun <T, K> Page<T>.append(next: Page<T>, key: (T) -> K): Page<T> {
    val seen = LinkedHashMap<K, T>(items.size + next.items.size)
    (items + next.items).forEach { seen.putIfAbsent(key(it), it) }
    return Page(seen.values.toList(), next.next)
}
