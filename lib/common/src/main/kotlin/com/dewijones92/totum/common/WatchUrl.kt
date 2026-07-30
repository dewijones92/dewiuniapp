package com.dewijones92.totum.common

/**
 * The one canonical form of a YouTube watch URL: `https://www.youtube.com/watch?v=<id>`.
 *
 * A video arrives under many URLs — `youtu.be/<id>?si=<tracking>` from the share sheet,
 * `m.youtube.com/watch?v=<id>` from mobile, `/shorts/<id>` from a reel — and the app uses
 * the URL as the video's IDENTITY: it is the `MediaItemId`, the resolve cache's key, and
 * what the queue dedupes on. So two spellings of one video are two different videos to
 * everything downstream.
 *
 * That is what a shared link hit (0.1.228): sharing a video already in the queue added a
 * second, unrelated copy rather than jumping to the one already there, and its resolve
 * could not hit a cache entry stored under the canonical spelling.
 *
 * Returns null for anything that is not a YouTube watch link, so a podcast enclosure or an
 * arbitrary URL passes through untouched.
 */
public fun HttpUrl.youTubeVideoId(): String? {
    val raw = value
    val id = when {
        "youtu.be/" in raw -> raw.substringAfter("youtu.be/")
        "/shorts/" in raw -> raw.substringAfter("/shorts/")
        "watch?v=" in raw -> raw.substringAfter("watch?v=")
        else -> return null
    }
    return id.takeWhile { it.isLetterOrDigit() || it == '_' || it == '-' }
        .takeIf { it.length == ID_LENGTH }
}

/**
 * [this] as the canonical watch URL, or unchanged when it is not a YouTube video link.
 *
 * Tracking parameters go, deliberately — `si` identifies the sharer, and carrying it into a
 * stored id would keep it in the queue and the play history forever. A `t=` start offset
 * goes too: the app does not honour it today, and a URL that means "this video at 2:30"
 * being a different video from "this video" is exactly the bug this exists to stop.
 */
public fun HttpUrl.canonicalWatchUrl(): HttpUrl =
    youTubeVideoId()?.let { HttpUrl.parse("https://www.youtube.com/watch?v=$it") } ?: this

/** YouTube video ids are always this long; anything else is a false match. */
private const val ID_LENGTH = 11
