---
title: Skip silence on videos too
kind: todo
status: open
area: playback
priority: medium
requested: 2026-07-24
updated: 2026-07-24
---

# Skip silence on videos (not just audio)

**Ask:** skip-silence should work on videos as well, not only audio.

## Why it's currently audio-only (deliberate)

Skip-silence today uses Media3's `SilenceSkippingAudioProcessor`, which shortens
the **audio** stream but not the video/media clock — on a video that desyncs the
audio ahead of the picture (measured: audio ran ~6s ahead over a 20s clip). So it
was **forced off when a video track is present** and the UI hides the toggle for
video. See the `media3-silence-skip-desyncs-video` memory.

## What making it work on video needs (harder)

The audio-processor approach can't work for video. To skip silence on video you
must move the **whole timeline** past silent gaps, keeping A/V together:

1. **Detect** silent regions (timestamps). Options: a lightweight audio analysis
   pass, or reuse the processor purely as a silence *detector* (not a shortener)
   to find gap boundaries live.
2. **Seek** the player past each detected gap (both audio + video jump together,
   so they stay in sync) — enforced in one place, like the SponsorBlock
   `skipTargetFor` position ticker, so it's unified across pillars.

Risks: real-time detection latency/accuracy; seek jank; battery. May be better as
a "detect then seek" analog of the skip-segment mechanism than the audio
processor. Research spike first.

**Done when:** enabling skip-silence on a video skips silent gaps with audio and
video staying in sync (no desync), verified on-device.
