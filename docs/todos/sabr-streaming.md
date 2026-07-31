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

## The protocol works. Proven 2026-07-31.

Started implementing it, and the unknown part — whether we can talk SABR at all — is now
answered. Three findings, in the order they arrived:

**1. yt-dlp cannot help.** The bundled 2026.07.04 has zero SABR support (no
`serverAbrStreamingUrl`, no UMP, nothing). The upstream PR
[#13515](https://github.com/yt-dlp/yt-dlp/pull/13515) is **still open** — ready for review 13
July 2026, no milestone — and it is an `fd/` *file downloader*. It would serve yt-dlp
downloads, not ExoPlayer playback, so it cannot fix video start even once merged. This has to
be a Media3 `DataSource`.

**2. The inputs are all there**, on the ANDROID client's player response:
`serverAbrStreamingUrl`, a 12820-char `videoPlaybackUstreamerConfig` (9613 bytes decoded),
`enableVideoPlaybackRequest`, and 151 formats carrying `initRange`, `indexRange`,
`lastModified` and `contentLength`. The WEB client returns UNPLAYABLE and none of it.

**3. A minimal request returns real media.** POST to `serverAbrStreamingUrl`:

| Body | Response |
|---|---|
| empty | 31 bytes: `RELOAD_PLAYER_RESPONSE` → `sabr.malformed_config` |
| `field 5 = videoPlaybackUstreamerConfig` | **212246 bytes, 26 UMP parts** |

And the media in it is genuine, identified by magic bytes:

| Part | Magic | What |
|---|---|---|
| header 0 | `1a45dfa3` | WebM/EBML header |
| header 1 | `ftypdash` | fMP4 init segment |
| header 2 | `1f43b675` | WebM Cluster |
| headers 3, 4 | `moof` | MP4 fragments |

Audio and video, initialisation and fragments, interleaved in one response — from a body
containing **one field**. No PO token, no `ClientAbrState`, no format selection needed to get
bytes flowing.

## What has landed

`:lib:sabr`, pure Kotlin, no Android:

- `UmpVarint` — UMP's width-prefixed little-endian integer, which is **not** protobuf's and
  sits inches from it in the same response. The five-byte case discards its first byte
  entirely, unlike every other width; that is the one that would silently corrupt offsets.
- `UmpReader` — the `[type][size][bytes]` framing, reporting bytes it could **not** consume so
  a part split across HTTP responses is carried forward rather than dropped. That boundary
  occurs on every response and is the hardest corruption to notice.
- `UmpPart` — the part-type names, so a log says `SABR_ERROR` rather than `42`.
- `Protobuf` + `VideoPlaybackAbrRequest` — enough to write the body that worked. Hand-rolled:
  the schema is Google's private one with no public `.proto`, and a generator plus runtime
  would be a build dependency and APK cost for a handful of length-delimited fields.

Tested against the real 26-part sequence (types and sizes genuine, payloads synthetic — the
real bytes are somebody's copyrighted video and prove nothing the framing does not).

## What is left

1. ~~**Decode `MEDIA_HEADER`**~~ — done. `MediaHeader` reads headerId, videoId, itag,
   lastModified, byte offset, the init-segment flag and content length, verified against a real
   52-byte header. The mapping is confirmed by container magic rather than by plausible
   numbers: field 3 said itag 396 and the bytes that followed began `ftypdash`, while itag 249
   was followed by `1a45dfa3`.
2. **Select formats** in the request (`selected_format_ids`, `preferred_*_format_ids`) instead
   of taking the server's default, which currently answers with an `av01` `SABR_ERROR`.
3. **A Media3 `DataSource`**, and this is the real design question: SABR interleaves audio and
   video in one response, while ExoPlayer wants a byte stream per track. So it needs a
   `MediaSource` that demuxes, or a pair of data sources sharing one request and buffering the
   other's bytes.
4. **State across requests** — `buffered_ranges` and `player_time_ms` — so seeking and
   continued playback ask for the right segments.
5. **PO token**, if it turns out to be needed for sustained playback. It was not needed for a
   first request.

The prize remains what it was: a ~150ms resolve instead of 2-4s, and no JS runtime on the
playback path at all.
