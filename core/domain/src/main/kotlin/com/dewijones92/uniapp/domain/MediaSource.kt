package com.dewijones92.uniapp.domain

/** Stable identity of a [MediaSource]; never blank. */
@JvmInline
public value class SourceId(public val value: String) {
    init {
        require(value.isNotBlank()) { "SourceId must not be blank" }
    }
}

/**
 * Something the user can subscribe to. The two pillars of the app are the
 * two variants: video channels and podcast feeds. Everything downstream
 * (subscriptions, queue, downloads, history) treats them uniformly.
 */
public sealed interface MediaSource {
    public val id: SourceId
    public val title: String

    public data class VideoChannel(
        override val id: SourceId,
        override val title: String,
        val channelUrl: WebUrl,
    ) : MediaSource

    public data class PodcastFeed(
        override val id: SourceId,
        override val title: String,
        val feedUrl: WebUrl,
        val websiteUrl: WebUrl? = null,
    ) : MediaSource
}
