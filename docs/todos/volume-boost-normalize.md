---
title: Volume boost / loudness normalization for quiet audio
kind: todo
status: shipped
area: playback
priority: medium
requested: 2026-07-24
updated: 2026-07-25
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

## Decided (Dewi, 2026-07-25)

- **Boost first**: `LoudnessEnhancer` with Off / Low / Medium / High, wired once in
  `PlaybackController` so both pillars and every screen get it, surfaced in the full
  player next to Skip silences.
- Global default with **per-source memory**, mirroring the existing per-source
  playback speed.
- Local playback only (a platform effect can't reach a Cast receiver) — accepted.
- `DynamicsProcessing` "Normalize" can be added later behind the same control.

**Done when:** a quiet item can be made comfortably audible from the player, the
choice persists, and it applies to both pillars.

## Shipped 2026-07-25

- `VolumeBoost` (Off / Low +3dB / Med +7dB / High +12dB, in millibels — the unit
  `LoudnessEnhancer` takes) plus a `VolumeBoostStore` port, mirroring
  `PlaybackSpeedStore` exactly: keyed by `SourceId`, so a quietly recorded podcast
  stays boosted without shouting everywhere else.
- Applied in the service by attaching the platform's `LoudnessEnhancer` to the player's
  audio session, recreated on change (a stale effect bound to an old session would
  silently do nothing). Wrapped in `runCatching` — some devices refuse the effect, and
  that should degrade to "no boost", not a crash.
- Restored per source on play, alongside the remembered speed.
- Levels selector in the full player under the toggles.

While in here, `FullPlayer.kt` had outgrown its function limit, so the playback
preferences (rate, silence, auto-advance, boost) moved into `PlaybackPreferences.kt` —
which is also where they need to be for the UI-polish plan to collapse them behind one
settings affordance on the video.

### Verified on-device

Off / Low / Med / High render in the player; tapping **Med** persisted
`https://feed.syntax.fm/rss → MEDIUM` (per-source, as intended) and the enhancer was
created with **no** "unavailable" failure. Honest caveat: the *audible* effect isn't
verified by ear from here — what's verified is that the platform effect instantiates,
takes the gain, and the choice sticks to the right source.
