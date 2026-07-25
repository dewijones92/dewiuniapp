---
title: Above 1080p didn't play
kind: todo
status: shipped
area: video
priority: high
requested: 2026-07-25
updated: 2026-07-25
---

# "Selecting anything above 1080p doesn't play" (Pixel 7)

**Ask:** above 1080p doesn't play — reproduce/fix.

## Root cause

Two facts combine:

1. Above 1080p, YouTube publishes **video-only** streams in VP9 **and** AV1 (its AVC
   ceiling is 1080p). So every high quality goes down the merge path.
2. The bridge read `vcodec` **only** to decide whether a format had video, and then
   **threw the codec string away**. `videoQualities()` therefore picked
   `atHeight.first()` — an arbitrary codec — and the app happily offered a quality the
   device might have no decoder for. Selecting it just stopped playback.

Confirmed on-device, and the numbers are unambiguous:

```
canDecode av01.0.08M.08 1920x1080 -> size=true   anySize=true
canDecode av01.0.12M.08 2560x1440 -> size=false  anySize=true
canDecode av01.0.12M.08 3840x2160 -> size=false  anySize=true
canDecode vp9           2560x1440 -> size=true   anySize=true
canDecode vp9           3840x2160 -> size=true   anySize=true
```

An AV1 decoder **exists** but does not reach 1440p/2160p — so a codec-only check
would still have offered the broken stream. The check has to be **size-aware**.

## Fix

- `MediaFormat` now carries `videoCodec` / `audioCodec` (surfaced from yt-dlp).
- `VideoCodecSupport` — a small seam answering "can this device decode this codec at
  this size?", implemented by `PlatformVideoCodecSupport` via
  `MediaCodecList.findDecoderForFormat` **with the width/height keys set**, which is
  what makes it size-aware. Unknown codecs pass (refusing what we can't identify
  would hide playable streams).
- `videoQualities(support)` filters undecodable streams and, where several codecs
  decode at one height, prefers the most likely to be hardware-accelerated
  (AVC → VP9 → HEVC → AV1).

## Verified on-device

Before: ladder offered AV1 at 2160p → selecting it failed.
After: `ladder=[2160p/vp9, 1440p/vp9, 1080p/avc1.640028, …]` — the AV1 variants at
those heights are withheld and VP9 takes their place. Selecting **2160p** played:
`state=PLAYING`, `c2.goldfish.vp9.decoder` created, zero errors.

## Also found while testing (and fixed)

Tapping a video whose audio the queue had auto-downloaded played **the audio file**,
giving sound with a blank picture (only an opus decoder was created,
`hasVideo=false`). A download only stands in for a video if it is the full thing:
`DownloadState.videoFileOrNull()` now encodes that, with tests.

## Note for the Pixel 7

The emulator's decoder table isn't the phone's. The fix is device-driven (it asks
*this* device), so a Pixel 7 that can't do AV1 at 1440p+ will now be offered VP9
instead. If a high quality is ever missing, `dewidebug` logs the withheld stream:
`no decoder for <codec> at <w>x<h> — quality withheld`.
