package com.dewijones92.uniapp.playback

import com.dewijones92.uniapp.domain.SourceId

/** Normal playback speed; what a source plays at until the user picks otherwise. */
public const val DEFAULT_PLAYBACK_SPEED: Float = 1.0f

/**
 * Remembers the playback speed chosen per source, so a podcast you listen to at
 * 1.5× or a channel you watch at 1× resumes at that speed next time — one seam
 * for both pillars. The speed is keyed by [SourceId], not per item, so it
 * carries across every episode or upload from that source.
 */
public interface PlaybackSpeedStore {

    /** The remembered speed for [source], or [DEFAULT_PLAYBACK_SPEED] if none. */
    public suspend fun speedFor(source: SourceId): Float

    /** Records [speed] as the preferred speed for [source]. */
    public suspend fun save(source: SourceId, speed: Float)
}

/** Default store that remembers nothing — everything plays at [DEFAULT_PLAYBACK_SPEED]. */
public object NoOpPlaybackSpeedStore : PlaybackSpeedStore {
    override suspend fun speedFor(source: SourceId): Float = DEFAULT_PLAYBACK_SPEED
    override suspend fun save(source: SourceId, speed: Float): Unit = Unit
}
