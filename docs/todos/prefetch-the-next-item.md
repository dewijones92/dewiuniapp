---
title: Preload the next item's first 30 seconds
kind: todo
area: playback
priority: medium
status: requested — readiness half DONE; byte preload not started
updated: 2026-08-02
---

# Preload the next item's first 30 seconds

Dewi, 2026-08-02: *"So our app has like a 30 sec buffer right???? If we are coming to the end of
a episode (t-29) seconds does it start buffering the next in the queue??"* — and, on scope,
*"just 30 seconds of future to be loaded right??"*.

His premise was right: `PlaybackService.MIN_BUFFER_MS` is 30s. That is the FLOOR for the item
playing (`MAX_BUFFER_MS` is four minutes); 30s is the budget for the *next* item.

"Getting the next item ready" is two layers, and only one of them is shared across the pillars.

## Layer 1 — readiness. Done, and it costs no mobile data

`NextUpPrefetcher` already did this for YouTube at a 45-second lead. It skipped everything else on
the reasoning that *"only a video costs anything to resolve"* — true when written, and false once a
torrent gained an audio-only URL with ~25s of ffmpeg behind it. That guard is now
`worthPreparing()`, and `AppContainer`'s `prefetchOne` is an exhaustive `when`, so a fourth kind of
playable cannot silently get no preparation.

| Handle | Getting it ready |
|---|---|
| `Video` | resolve, warming the resolver's short-lived cache |
| `Podcast` with an `audioUrl` (a torrent) | ask the home server to start remuxing — the ~25s case |
| `Podcast` without one | nothing; an enclosure URL is already playable |
| `LocalVideo` | nothing; it is already on the device |

The 45s lead stays. It was chosen against a ~7s extraction and is a better fit for a 25s remux
than 30 would be — and unlike Layer 2 it pulls no media, so a longer lead costs nothing.

**A separate class was written for this and deleted before it shipped.** It duplicated
`NextUpPrefetcher` almost exactly — same lead, same `peekNext`, same once-per-item guard. Worth
recording: the seam already existed and only its routing was out of date.

## Layer 2 — the bytes. Not started, and the expensive half

`ExoPlayer.PreloadConfiguration` looks like the answer and is not: it preloads the next item **in
the player's playlist**, and `Media3PlaybackController` calls `setMediaItem` — one item at a time,
because `PlaybackQueue` owns advancing (skip segments, history, just-in-time resolution).

The mechanism that fits is `DefaultPreloadManager` (present in Media3 1.10.1, checked), which
preloads media sources independently of the playlist. It has to live in `PlaybackService`: only
the service side owns `MediaSource`s, so a `MediaController` cannot be handed one. That means a
custom session command to nominate the next item, and it is a real piece of work rather than a
setting.

### What 30 seconds actually costs

| | 30s of "future" |
|---|---|
| Podcast enclosure | ~0.5 MB |
| Torrent, Listen mode | ~1 MB |
| Torrent, watching | ~7.6 MB |
| YouTube 1080p | ~5–10 MB |

Flat in time, eight times apart in bytes. In Listen mode it is cheap; watching video it is ~8 MB
per track change — bounded and predictable, but worth a setting rather than a silent default.

## Related

- `docs/todos/listen-mode-saves-data.md` — the audio-only stream this readies.
- `AutoAdvancer` — the sibling that watches for the END rather than the position.
