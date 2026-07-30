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
