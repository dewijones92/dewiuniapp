package com.dewijones92.uniapp.domain

/**
 * Which pillar a playing item belongs to. Playback, queueing and downloads are
 * pillar-agnostic; this exists only so the UI can label what's playing (a
 * YouTube video vs a podcast) — the one place the distinction is surfaced.
 */
public enum class MediaKind { VIDEO, PODCAST }
