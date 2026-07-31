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
2. ~~**Select formats**~~ — done, and `xtags` turned out to be the crux. See below.
3. ~~**State across requests**~~ — `ClientAbrState.player_time_ms` does it. See below.
4. ~~**A Media3 `DataSource`**~~ — done, and **a real video plays through it on Android**.
5. **PO token**: not needed. Never sent one, and full-quality media came back every time.

## It fetches real, decodable media. Verified 2026-07-31.

Everything the protocol needs is now proven, and the last three unknowns fell to probing:

**`xtags` is mandatory, not optional.** A real response carried **22 entries for each audio
itag** — one per dubbed language track. Selecting itag 251 by itag and `lastModified` alone
matched an arbitrary one of the 22 and the server answered
`RELOAD_PLAYER_RESPONSE: sabr.no_audio_selected`. With `xtags` (`acont=original`, `lang=en-US`)
in the `FormatId` it served exactly the requested track.

**`preferred_audio_format_ids` (16) and `preferred_video_format_ids` (17) are honoured;
`selected_format_ids` (2) is ignored.** Asking for itag 251 + itag 137 returned precisely
those two, 1.44MB in one request.

**`player_time_ms` must be inside `ClientAbrState` (field 28); the top-level field 4 is
ignored.** Four requests differing only in the top-level field returned byte-identical
responses. Moved inside, 0ms reached video byte 1271335 and 30000ms reached 8761825 — the same
request in every other respect.

**`enabled_track_types_bitfield` (40) = 1 gives audio ALONE** — 167876 bytes, one itag. Values
0, 2, 3, 6 and 7 all returned audio and video together, and no value was found that gives video
without audio. That is fine: playing a video needs both, so one request carrying both is
efficient rather than wasteful, and the two are separated by their `MediaHeader` itag.

### The proof

A request built by **our Kotlin encoder** (9715 bytes) was POSTed to the live endpoint:

```
itag 137: 1389065 bytes, magic 0000001c66747970  (ftypdash — fMP4 init)
itag 251:   34893 bytes, magic 1a45dfa39f428681  (WebM/EBML)
```

and the audio bytes handed to ffprobe:

```
codec_name=opus   codec_type=audio   sample_rate=48000   channels=2
format_name=matroska,webm            duration=1087.701
```

then decoded to PCM: **2.13s of 48kHz stereo, mean volume -14.7 dB, max -0.0 dB.** Real audio,
not silence. ("File ended prematurely" is expected — that was one segment.)

So the protocol layer works. What is left is plumbing it into Media3, which is engineering
against a known quantity rather than a research problem.

The prize remains what it was: a ~150ms resolve instead of 2-4s, and no JS runtime on the
playback path at all.

## It plays. On the device. 2026-07-31.

```
[sabr] opened at 0 of -1 bytes
[sabr] PLAYED 1187ms of itag 140 over SABR
```

`SabrPlaybackTest` (instrumented, `:app`) does the whole thing with no fakes: a live `/player`
call, the real `SabrStream`, the real `SabrDataSource`, a real `ExoPlayer`. The only assertion
that matters is the one it makes — **the playback position moved**. It reached 1187ms of itag
140 on "Me at the zoo", chosen because it is short and unlikely ever to be taken down.

So the chain is complete: `/player` in ~150ms → SABR request → UMP → media bytes → ExoPlayer →
audio out.

### What the failure taught, before it passed

The first run failed with `Source error` and nothing to explain it. The fix was instrumentation,
not guesswork: `SabrStream` now says what a response *did* contain when it contains nothing
useful — part names, itags and any refusal — because an empty result has three very different
causes (a refusal, media for a format we did not ask for, or a genuine end) and they are
indistinguishable otherwise. That line is what turned "Source error" into "itag 140 got no bytes
from 1562B, reasons=[…]".

Also learned: **itag 139 is refused outright** (`sabr.no_audio_selected`) while 140, 249, 251,
599 and 600 all serve, so a format chooser cannot assume every listed audio format is
obtainable.

## SHIPPED, behind a switch, for audio. 2026-07-31.

- **Seeking.** `SabrDataSource` is not seekable to an arbitrary byte: SABR is asked for a media
  TIME, not an offset. A reader opening at a position we have not reached gets nothing. Playing
  from the start works; scrubbing does not.
- **Video as well as audio.** The video path is written and the request is honoured, but only
  audio has been played end to end.
- **Adaptive switching**, which is the entire point of the "ABR" in SABR and currently unused —
  one format is picked and kept.
- **Then, and only then**, wiring it in front of yt-dlp for the ~150ms resolve.

## What actually shipped

**Settings → "Fast start (beta, no seeking)", off by default.** With it on, a YouTube video
resolves over `/player` + SABR instead of an extraction:

```
[sabr] prepared dc84PmnKlyo — audio itag 251, video itag none
[resolve] dc84PmnKlyo in 1839ms for describe OVER SABR
[sabr] serving dc84PmnKlyo itag 251 as AUDIO
[playback] playing at 1ms
[snapshot] playing "..." at 53845ms (running)
```

**1839ms against ~10s on the emulator**, and it sustains — 53 seconds in and still running.

With the switch off, nothing changes: verified on-device, `playing at 1ms` through the ordinary
extraction path and not a single `[sabr]` line.

### Audio only, and why

Video is written and served but **not shipped**: itag 137 arrives and ExoPlayer rejects it with
`Invalid NAL length` and `contentIsMalformed` — it reads valid mp4 and then meets a gap, so the
runs SABR returns for video are not byte-contiguous in the order they arrive and `SabrStream`
needs to hold them until they are. Shipping a video path that decodes to corruption would be
worse than shipping none.

### Two format rules, both measured rather than assumed

Probing every format of a real video:

| | Result |
|---|---|
| **video/webm (VP9)** — 313, 271, 248, 247, 244, 243, 242, 278, 598 | **every one refused** (`sabr.no_video_selected`) |
| video/mp4 (H.264, AV1) — 137, 400, 399, 398, 397, 396, 136, 135, 134, 133, 160, 394 | served |
| audio itag 139 | refused (`sabr.no_audio_selected`) |
| audio 140, 249, 251, 599, 600 | served |

So a listed format is not an obtainable one, and the chooser excludes VP9 for video and 139 for
audio. Note the asymmetry: `audio/webm` opus serves perfectly — the webm refusal is video-only.

### No custom URL scheme

`sabr://` would have read better, but `HttpUrl` is deliberately http(s)-only so every URL in the
app is known-good, and widening that invariant for one feature is a bad trade. The real SABR
endpoint is already https, so the session and track are marked on it as query parameters — and
the URL ends up honest about where the bytes come from.

## Still to do

- **Contiguous video assembly**, then video over SABR.
- **Seeking.** SABR is asked for a media time, not a byte offset, so scrubbing does not work —
  which is exactly why the switch says "no seeking" and defaults to off.
- **Adaptive switching**, the "ABR" half, still unimplemented; the quality menu is deliberately
  empty on this path rather than offering switches that would not work.
- Watch for whether a 403 seen once from the yt-dlp DOWNLOAD path during a SABR session is
  related; downloads use the watch URL and should be untouched, so it is more likely transient.
