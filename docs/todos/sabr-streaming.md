---
title: yt-dlp needs a JavaScript runtime (kids videos were stuck at 360p)
kind: todo
area: video
priority: medium
status: partly shipped
updated: 2026-07-30
---

# The 360p problem, and what it actually was

Made-for-kids videos (Ms Rachel) played at 360p while SmartTube played them properly.

**It was never SABR, and never a YouTube policy.** An earlier version of this document
recommended implementing YouTube's SABR/UMP protocol, a multi-week project. That was wrong.
Dewi asked the obvious question — "you sure the yt-dlp CLI can't play 1080p?" — and the CLI
answered it:

    WARNING: No supported JavaScript runtime could be found. Only deno is enabled by
    default ... YouTube extraction without a JS runtime has been deprecated, and some
    formats may be missing

With `--js-runtimes node`, the same yt-dlp returns the full ladder to 1080p (avc1, vp9 and
av01). Without one it returns a single 360p stream. **Chaquopy cannot provide a JS runtime,
so the app is permanently in the degraded case.**

## Shipped: a second opinion that needs no JS

`PlayerStreams` / `InnerTubePlayerStreams` asks YouTube's `/player` directly as the ANDROID
client, and `VideoResolver` uses it **only when yt-dlp comes back with a single quality at
360p**. Measured 2026-07-30:

| video | our `/player` call | app's yt-dlp (no JS) |
|---|---|---|
| kids (Ms Rachel) | 32/32 urls, 1080p | 1 format, 360p |
| normal (Fireship) | 32/32 urls, 1080p | 23 formats, 1080p |
| music video | 30/30 urls, 2160p | 27 formats, 2160p |

Those URLs carry no `n` parameter, so nothing needs deciphering and no runtime is implied —
which is exactly why this works on a phone where yt-dlp cannot. Verified end to end: a
ranged GET returns HTTP 206 at ~29 MB/s, and on-device the resolver logs
`direct ask gave 6 qualities to 1080p, up from 360p`.

Deliberately a fallback, not a replacement: yt-dlp handles age gates, region locks,
signature ciphers and non-YouTube sources that this does not.

## Open: ship QuickJS, so yt-dlp itself works properly

yt-dlp supports `deno`, `node`, `quickjs`, `bun`, and looks for quickjs as a binary named
`qjs` at a path we can supply. That is **the machinery we already have for ffmpeg**: build a
static binary, ship it in `jniLibs` as a `.so`, expose it under `nativeLibraryDir` (the only
executable location under Android 14 W^X) and pass the path.

QuickJS is around a megabyte, against ffmpeg's seven. It would fix yt-dlp broadly rather
than one symptom — including `n`-parameter deciphering, which otherwise throttles downloads
— and it is insurance for the next thing YouTube changes, since yt-dlp has now deprecated
running without it.

## Where exactly the wall is (measured 2026-07-31)

The fast path was enabled as the primary resolver and it failed the same way it always had —
`[resolve] … in 1435ms BY ASKING YOUTUBE` and then `Source error`. This time the URLs were
probed directly, so the reason is no longer a guess.

| Request against a fresh ANDROID-client URL | Result |
|---|---|
| bytes 0–256K | **206** |
| the same 0–256K request, three times | **206, 206, 206** |
| bytes 0–896K | **206** |
| bytes 0–1024K | 403 |
| **first** request = bytes 512K–1M | **403** |
| then bytes 0–512K on that same URL | **206** |

**Only the first megabyte of the stream is reachable.** It is not a rate limit (the same
request repeats fine), not the request count (an early failure happens on request one), not the
range size in itself (a 896K range from zero is fine), not the User-Agent (identical with
yt-dlp's, ExoPlayer's, and none), and not the length probe that `ChunkedDataSource` already
stopped making by reading `clen` from the URL.

151 of 152 formats answer 206 to a first small range, which is exactly why "resolve" looked
like success for so long, and why a ladder to 2160p means nothing here.

The rest of every stream is behind SABR — which is what `serverAbrStreamingUrl` is for, and
what SmartTube implements. So:

- `/player` is genuinely ~150ms and gives a full ladder, a title, a length, captions and the
  channel id. It is a fine METADATA source.
- It cannot serve **playback** at any speed until SABR is implemented. Re-enabling
  `playerStreams` as a resolver produces a video that resolves fast and plays for one megabyte.

yt-dlp's URLs are durable because it uses a client (`WEB_EMBEDDED_PLAYER`) with a deciphered
`n` parameter — which is what the JS runtime buys and why extraction costs 2-4s. **That cost
is the price of a stream that plays to the end**, and no amount of restructuring around
InnerTube avoids it.
