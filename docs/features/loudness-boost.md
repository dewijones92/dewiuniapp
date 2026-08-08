---
title: Quiet podcasts made audible, automatically
kind: feature
area: playback
status: shipped
updated: 2026-08-08
---

# Quiet podcasts made audible, automatically

**Ask (Dewi, 2026-08-07):** *"make the volume booster thing in the app better???? so I can actually
hear quiet podcasts e.g."* — *"there is already some sort of volume booster in the app but it isnt
strong enough"*.

**And then (2026-08-08), on the version that answered it:** *"the volume booster setting thing in the
app causes distortion … i dont want distortion, i want it to allow me to hear things like quiet
podcasts well please … compression?? or sumin????"* — noticeable from **Medium upwards**, on
earphones, on 0.1.371.

This doc covers all three versions, because the mistakes are the interesting part and each one was
caused by fixing the previous one too literally.

## The three versions, and why each was wrong

| | What it did | Why it failed |
|---|---|---|
| **1. `LoudnessEnhancer`** (to 2026-08-07) | Android's platform effect, one flat gain, capped at +12 dB | Too quiet. A flat gain **clips**, so past the point the loudest peak reaches full scale more gain buys distortion, not volume — the +12 cap was roughly the ceiling for a flat gain, not timidity |
| **2. Fixed levels + limiter** (0.1.371) | Our own compressor/limiter, Off/+6/+12/+20/+30 dB | **Distorted.** The gain moved down as slowly as it moved up, so a loud moment after a quiet one was still being multiplied by the full boost for tens of ms while the gain wound down — every sample of it sliced flat |
| **3. Automatic** (current) | Measures the item and applies the difference, capped at +20 dB, gain falls instantly | — |

## "Won't a hard dB cap prevent distortion?"

Dewi's question, and the answer is the whole design. **A ceiling is not a wall the sound bounces off,
it is a knife.** Everything above it gets sliced flat, and those flat tops are frequencies that were
never in the recording. Hard-capping *is* distorting — they are one event described two ways.

What avoids it is turning the gain down **before** the loud part is multiplied, so nothing ever
reaches the ceiling and the waveform keeps its shape, just smaller. That is what a limiter is.

Version 2 did turn the gain down — over 30 ms, at the same rate it turned up. Measured, on a quiet
passage followed by full-level audio: **5,428 samples (123 ms) clipped at Max and 2,844 (64 ms) at
Medium**, per transient. Which is exactly where he said he heard it.

The fix is an asymmetry, and it is the entire trick of a limiter: the gain **falls instantly and
recovers slowly**. Because the fall is clamped per sample to `CEILING / |sample|`, the output is
bounded by construction — `|sample| × (CEILING / |sample|) = CEILING`. Not "rarely clips": **cannot**.
That is why `clippedSamples` is reported, and why a non-zero value in a report is a broken assumption
rather than loud audio.

The per-sample clamp does not itself modulate the waveform, which would be its own distortion: the
gain can only *rise* at the slow rate, so it settles just under what the recent peak requires and
sits there rather than following the wave up and down.

## Automatic, rather than a number you pick

Dewi chose this himself over keeping the fixed steps: *"Auto — make everything the same loudness"*.
He was right for a reason worth writing down — **the number was the problem**. Picking a level by ear
per item means routinely asking for more gain than the audio needs, and gain you did not need is
exactly what sounds squashed and over-driven. Measuring the item and applying the difference cannot
over-ask.

So there is nothing to tune. The control is **Off / Auto**.

- It measures a slow average of the item's level and applies exactly the gain that brings it to a
  comfortable target.
- **It never turns anything down.** Quieter-than-expected is a surprise nobody asked for.
- **It never applies more than +20 dB.** Dewi's call over keeping the +30 that version 2 offered:
  *"trade some maximum loudness for naturalness"*. Past roughly this point, even a clean limiter
  leaves everything the same loudness, which sounds processed rather than loud.

An old stored level (`LOW`…`MAX`) is migrated to `AUTO`, not to `OFF` — silently switching the boost
off during an upgrade would be the app changing a setting nobody touched, which is
[explicitly ruled out](../todos/settings-only-change-when-asked.md).

## The mistake that is easiest to make twice: an absolute noise floor

The level estimate has to ignore pauses, or silence between sentences drags the average down and
winds the gain up, so every gap ends in a blast. Versions 2 and 3 both did that with a **fixed**
threshold at about −45 dBFS, and it is wrong in a way that is invisible until you test it:

**a recording peaking at −46 dBFS sits entirely underneath the floor, is measured as *nothing*, and
receives no boost at all** — the quietest podcasts, which is the whole point of the feature. Caught
by `the quieter the recording, the more gain it gets`, which produced `[1.0, 6.7, 1.9, 1.0]`: the
*quietest* input got the *least* gain.

Lower the floor to admit that speech and it admits tape hiss too. There is no absolute number that
separates them, because the difference between quiet speech and loud hiss is not a level — **it is a
level relative to the rest of the recording**. So the gate now follows the content: 20 dB below its
recent peak (which rises instantly and decays over 2 s), with a −66 dBFS absolute floor that only
rejects digital silence and dither.

The old **noise-floor taper** is gone with it. It existed to stop a fixed +30 dB turning hiss into a
roar, and it was a downward expander that ate the quiet ends of words. Automatic gain removes the
need: the lift is proportionate to the item, so its noise floor rises with its speech, exactly as it
would if you turned the volume up.

## The seam

Unified by construction — it is in the **sink's processing chain**, so every byte of audio the app
plays passes through it: both pillars, every screen, streamed or from disk.

| Piece | Where | Why there |
|---|---|---|
| `LoudnessBoost` | `:core:playback` | The arithmetic. Pure Kotlin on a `ShortArray` — no Android, no platform effect, so it behaves identically on every device **and the maths is provable on the JVM** |
| `BoostingAudioProcessor` | `:core:playback` | A Media3 `BaseAudioProcessor` wrapping it, plus the reporting. Only 16-bit PCM is touched; anything else passes through untouched rather than being reinterpreted as samples |
| Chain wiring | `PlaybackService` | The booster sits **after** the silence detector, so silence detection still judges the raw recording rather than a boosted one |
| `VolumeBoost` | `:core:playback` | `OFF` / `AUTO`, plus `fromStoredName` for the migration |
| `VolumeBoostStore` | `:core:playback` | One setting for the app, moved only by the control |

`LoudnessEnhancer` and its recreate-on-audio-session-change block were removed in version 2 and have
not come back. It was bound to a session id, and a stale one silently did nothing.

## Proving it in the wild

Dewi's standing rule is that a change is done when a report sent from his phone a week later can
settle whether it worked **there**. The question this feature has to answer is the one he raised by
ear, so the report carries the two numbers that decide it:

```
boost: auto gain 9.8dB (level 0.0321) clipped=0
```

`clipped=0` is the claim the design makes, so a non-zero value is the entire diagnosis in one word —
and it is logged as a **warning** rather than a note, because it would mean a broken assumption
rather than loud audio. There is also a warning for the otherwise-silent case where the stream is not
16-bit PCM and is therefore not being boosted at all.

Rate-limited by **change** rather than by clock: the gain settles within seconds and then sits still,
so a well-behaved hour costs a handful of lines. Logging every interval regardless would fill a
bounded report buffer with the news that nothing happened, which has destroyed real evidence in this
app before.

## Tests

| Level | Test | Claim |
|---|---|---|
| JVM | `LoudnessBoostTest` (19) | **Not distorting:** a sudden loud passage after a quiet one clips zero samples, so does six alternating bursts, `clippedSamples` stays 0, nothing wraps. **Making quiet things audible:** a very quiet recording is lifted >8×, an already-loud one is left within 5% of untouched, gain falls monotonically as the input gets louder, nothing is ever attenuated, the cap holds at +20 dB, and it settles within half a second rather than swelling. **Not sounding processed:** a steady tone stays steady within 5%, a pause between sentences does not move the gain, the gap is not lifted more than the speech, quiet speech is amplified rather than gated, silence stays silent, OFF is bit-exact, and an old stored level migrates to AUTO |
| Instrumented | `BoostingAudioProcessorTest` (6) | The plumbing Media3 actually drives: the whole input buffer is consumed, samples are read **little-endian** (proven by sign correlation — a byte-swapped read would sit near 50%), OFF passes bytes through, a non-16-bit format is left alone, an empty buffer is fine, and the setting can change mid-stream |

**Why version 2's tests did not catch its bug, which is the lesson worth keeping.** All eleven of
them used a **constant** tone, so the gain had always finished settling before anything was measured
— and the defect only exists during a *change* of level. The clipping test even permitted 5% of
samples pinned to the rail, and the real clipping came to 3%: it passed by squeaking under a bar that
should never have been above zero. Real speech is quiet and then somebody laughs. The tests now lead
with that, and assert exactly zero.

### Honest caveat

What is verified is the arithmetic and the plumbing, by measurement. What cannot be verified from
here is how it *sounds* to Dewi on his earphones — that needs ears. The numbers say a −40 dBFS
recording comes up about 10× with not one sample clipped, and that a properly-mastered one is left
alone. If it is still not loud enough in practice, the honest lever is the target level or the +20 dB
cap, and the limiter is what makes raising either safe to try.
