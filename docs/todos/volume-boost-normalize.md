---
title: Volume boost / loudness normalization for quiet audio
kind: todo
status: refining
area: playback
priority: medium
requested: 2026-07-24
updated: 2026-07-24
---

# Make quiet talkers audible

**Ask:** an option to increase the volume, or equalize volume, or something like
that — in case someone speaks a bit quieter.

Two different features hide in that sentence; worth naming them separately:

- **Boost** — make everything louder than the system max allows.
- **Normalize / equalize** — even out loud vs quiet passages (and loud vs quiet
  *items*), so you don't ride the volume key.

## Options considered

| Approach | What it does | Cost / risk |
|---|---|---|
| **`LoudnessEnhancer`** (platform `AudioEffect`, API 19+) | Straight gain in mB on the player's audio session — the standard Android answer for quiet speech | Small: attach to `ExoPlayer.audioSessionId`, a few preset levels. Local playback only (not Cast) |
| **`DynamicsProcessing`** (platform `AudioEffect`, API 28+) | Real multiband compressor + limiter → true "equalize", quiet passages lifted, peaks held | Medium: more parameters to get right; still no custom DSP to maintain |
| Custom Media3 `BaseAudioProcessor` (AGC/compressor) | Full control | Highest: hand-rolled DSP. **Note:** gain is sample-count-preserving, so unlike the silence-skipping trap it cannot desync A/V |
| `LoudnessCodecController` (Media3, API 30+) | Uses CTA-2075 loudness metadata | Only helps content that carries the metadata — most won't |

## Proposed shape

Start with **`LoudnessEnhancer`** (Off / Low / Medium / High), applied in exactly
one place — the `PlaybackController` — so it covers both pillars and every screen,
alongside the existing `setSpeed` / `setSkipSilence` controls. Expose it in the
full player next to Skip silences.

**Per-source memory comes free in shape:** playback speed is already remembered per
source (`SharedPrefsPlaybackSpeedStore`), so a boost store mirrors it exactly —
one quiet podcast stays boosted without affecting everything else.

Then, if that isn't enough, add `DynamicsProcessing` as the "Normalize" level (real
compression) behind the same control.

## Open decisions

- **Which first**: simple boost (small, ships now) vs going straight to
  compression/normalize (better result, more tuning)?
- **Global or per-source** (proposal: global default with per-source memory,
  mirroring speed)?
- Cast: the effect can't apply to a remote receiver — fine to be local-only?

**Done when:** a quiet item can be made comfortably audible from the player, the
choice persists, and it applies to both pillars.
