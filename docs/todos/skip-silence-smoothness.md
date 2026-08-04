---
title: Skip-silence as smooth as AntennaPod
kind: todo
area: playback
priority: high
status: shipped — sample removal for audio, speed-up kept for video
updated: 2026-08-04
---

# Skip-silence as smooth as AntennaPod

Dewi, 2026-08-04: *"make sure the skip silences thing is as smooth as other apps e.g. antennapod"*.

## Why ours was worse, and it was not a tuning problem

AntennaPod is smooth because it **removes the silent samples**. The audio simply gets shorter:
no rate change, nothing to hear at either edge of a gap.

Totum could not do that, because it plays video too. Removing samples shortens the audio stream but
not the video clock, so the picture falls behind — measured at ~6s over a 20s clip. Speeding through
the gap instead retimes audio *and* video together, so it cannot desync. That was the right call.

The mistake was applying the video-safe mechanism to **everything**. Speeding up is audibly worse: a
step from 1x to 4x is heard at both edges of every gap, and each change reconfigures the audio sink,
which is heard as a stutter. Podcasts — the overwhelming majority of what skip-silence is used on —
were paying that cost for a desync they could never have had.

## What it does now

`SilenceStrategy` picks by whether a **video track is selected**, not by pillar:

| | |
|---|---|
| Audio only | `REMOVE_SAMPLES` — Media3's `SilenceSkippingAudioProcessor`, the AntennaPod mechanism |
| Video | `SPEED_UP` — the existing rate change, because nothing else keeps the picture in sync |
| Switched off | `OFF` |

Both processors sit in the sink chain; exactly one is ever active. It re-evaluates on
`onTracksChanged`, because a queue mixes both — the same switch has to mean sample removal for a
podcast and a rate change for the video after it.

Deliberately NOT chosen by pillar: a video played in Listen mode still carries a video track, and a
podcast with cover art does not. What matters is whether something is being kept in sync with the
audio clock.

## The device test earned its place immediately

The first run picked `REMOVE_SAMPLES` **for a video** — the one combination that desyncs the
picture. Cause: the strategy read `player.videoSize`, which is only populated once the decoder has
reported a size, so asking early says "no video" for a video. It now reads the selected tracks,
which is what `hasVideo` has always meant elsewhere.

A unit test could not have caught it: the decision was correct, and the input was wrong.

## Still to do

- **Listen to it.** The mechanism is now the same one AntennaPod uses, so it should sound the same,
  but nobody has actually heard it on a real podcast with real gaps. That is the remaining check.
- The `SPEED_UP` path keeps its 4x step and 20-buffer (~500ms) entry threshold. Both were tuned
  against the flapping problem rather than for smoothness, and video is now the only thing using
  them — worth revisiting if it still sounds abrupt.
