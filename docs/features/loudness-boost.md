---
title: Volume boost strong enough to rescue a quiet podcast
kind: feature
area: playback
status: shipped
updated: 2026-08-07
---

# Volume boost strong enough to rescue a quiet podcast

**Ask (Dewi, 2026-08-07):** *"make the volume booster thing in the app better???? so I can
actually hear quiet podcasts e.g."* — with the framing that *"there is already some sort of
volume booster in the app but it isnt strong enough"*.

He is right, and the interesting part is **why turning the old one up would not have worked.**

## Why the old boost could not simply be turned up

The previous boost (shipped 2026-07-25) was Android's platform `LoudnessEnhancer` attached to
the player's audio session, at Off / +3 / +7 / **+12 dB**. That is a **flat gain**, and a flat
gain has two problems that more of it makes worse:

- **It clips.** Past the point where the loudest peak reaches full scale, extra gain buys
  distortion rather than volume. A podcast at −3 dBFS peaks has ~3 dB of headroom; ask for 20
  and you get a square wave. So the +12 dB cap was not timidity, it was roughly the most a flat
  gain can safely offer across arbitrary material.
- **It cannot help the quiet passages *within* an item**, which is the audio you actually cannot
  hear. One number applied to the whole stream treats a shouted intro and a mumbled answer
  identically.

There was also a structural weakness: the effect was bound to an audio **session id**, so it had
to be torn down and rebuilt whenever that changed, and a stale one silently did nothing. And the
platform implementation is the device's, with its own cap — "+12 dB" was a request, not a promise.

## What makes speech audible is compression, not gain

So the boost is now **our own DSP in the audio chain**: an envelope follower, a large makeup
gain, and a feed-forward limiter that pulls the gain back only when a peak would otherwise clip.
Quiet passages get the full lift; loud ones stay where they are. The result is both louder *and*
more even — which is what every broadcaster does to speech, and what lets the makeup gain go to
**+30 dB** without the output being a fuzz.

Levels are now **Off / Low +6 / Medium +12 / High +20 / Max +30 dB**. Medium is where the old
ceiling was, so anything above it is new headroom that did not exist before.

Three details separate this from a naive limiter, and each one sounds bad if skipped:

- **The gain glides, it does not switch.** Recomputing the gain per sample and applying it
  immediately modulates the waveform at audio rate, which is heard as distortion rather than as
  level control. It moves on a 30 ms time constant.
- **Hiss is not amplified.** +30 dB on room noise between sentences is unbearable, so below a
  ~−45 dBFS floor the gain **tapers away, cubed**. This was a real bug, not a hypothetical: a
  proportional taper let noise through at a quarter of the makeup gain, which measured as an
  **elevenfold** lift of the noise floor. Cubing turns the sub-floor region into a gentle
  downward expander instead — noise comes out slightly quieter than it went in.
- **One envelope for all channels.** Per-channel gain would move a stereo image around as one
  side happened to peak.

Attack is 5 ms (catch a transient before it clips), release 300 ms (don't pump between
syllables, but do follow a change of speaker), ceiling 0.95 of full scale (so rounding cannot
take a limited peak over it).

## The seam

Unified by construction — it is in the **sink's processing chain**, so every byte of audio the
app plays passes through it, both pillars, every screen, streamed or from disk.

| Piece | Where | Why there |
|---|---|---|
| `LoudnessBoost` | `:core:playback` | The arithmetic. Pure Kotlin on a `ShortArray` — no Android, no platform effect, so it behaves identically on every device **and the maths is provable on the JVM**. |
| `BoostingAudioProcessor` | `:core:playback` | A Media3 `BaseAudioProcessor` wrapping it. Only 16-bit PCM is touched; anything else passes through untouched rather than being reinterpreted as samples. |
| Chain wiring | `PlaybackService` | `DefaultAudioProcessorChain(arrayOf(silenceDetector, booster), …)` — the booster sits **after** the silence detector so silence detection still judges the raw recording rather than a boosted one. |
| `VolumeBoost` | `:core:playback` | The level, now in **decibels** (it used to be millibels, the unit `LoudnessEnhancer` took). `makeupGain` converts. |
| `VolumeBoostStore` | `:core:playback` | Unchanged: per-`SourceId` memory, mirroring `PlaybackSpeedStore`. One quiet podcast stays boosted without shouting everywhere else. |
| Control | `PlaybackPreferences.kt` | The `BoostPicker` in the full player, now with five levels. |

The session command carries the level **by name** (`EXTRA_VOLUME_BOOST_LEVEL`) rather than a
millibel number, so the service and the controller cannot disagree about the scale.

`LoudnessEnhancer` and its whole recreate-on-session-change block are **gone**, not kept as a
fallback. Two mechanisms doing the same job at different strengths is exactly the shape that
makes "the boost stopped working" undiagnosable.

## Proving it in the wild

`BoostingAudioProcessor` logs every level change with its makeup gain
(`boost: level -> MAX (30dB makeup)`), and — the case that would otherwise be silent — warns when
the stream is not 16-bit PCM and is therefore *not* being boosted
(`boost: encoding 4 is not 16-bit PCM; not boosting`). Without that second line, "the booster
does nothing on this one podcast" would have no explanation in a report.

## Tests

| Level | Test | Claim |
|---|---|---|
| JVM | `LoudnessBoostTest` (11) | Quiet audio lifted >10×; HIGH more than 2× MEDIUM; levels monotonic; already-loud audio not pinned to the rail; no sample wraps; noise below the floor stays put; digital silence stays silent; a steady tone comes out steady within 5%; OFF is **bit-exact**; the smoothing coefficient scales with sample rate; a zero sample rate does not divide by zero. |
| Instrumented | `BoostingAudioProcessorTest` (6) | The plumbing Media3 actually drives: the whole input buffer is consumed, samples are read **little-endian** (proven by sign correlation — a byte-swapped read would sit near 50%), OFF passes bytes through, a non-16-bit format is left alone, an empty buffer is fine, and changing the level mid-stream takes effect without reconfiguring. |

The split is deliberate: a `ShortArray` unit test cannot catch a byte-order or buffer-lifecycle
mistake, and both of those would be catastrophic (full-scale noise, or silence) while compiling
perfectly. Neither test needs a device *behaviour*, so both run on every commit.

### Honest caveat

What is verified is the arithmetic and the plumbing — measured, not guessed. What is **not**
verified from here is how +30 dB *sounds* on Dewi's phone with a real quiet podcast, because
that needs ears. The numbers say a −40 dBFS recording comes up to comfortable speech level
without clipping; if Max still isn't enough in practice, the honest next lever is a higher
ceiling on the makeup gain, and the limiter is what makes that safe to try.
