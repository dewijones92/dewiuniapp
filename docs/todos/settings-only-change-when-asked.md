---
title: Settings change only when you change them
kind: todo
area: settings
priority: high
status: speed and volume boost done; brightness is a decision for Dewi
updated: 2026-08-05
---

# Settings change only when you change them

Dewi, 2026-08-05: *"things like playback speed, brightness, volume booster settings should not
change until I deliberately change them in the GUI — no exceptions"*.

Asked whether speed should be per-podcast or global: *"global please"*. Asked whether the metered
audio switch counts: *"meter thing is an exception"*.

## Audited

| Setting | Was | Now |
|---|---|---|
| Playback speed | stored per `SourceId` | one global value |
| Volume boost | stored per `SourceId` | one global value |
| Brightness | window override, released when video ends | **unchanged — see below** |

Both stores were keyed by source on the same reasoning: a podcast you listen to at 1.5×, a quiet
recording you boost. Defensible as a feature, and precisely the reported problem — the value moves
on its own every time the queue reaches a different feed, which from the outside is the app altering
a setting nobody touched. Old per-source entries are left behind rather than migrated: picking one
to become the global value would itself change the setting unprompted.

**A false lead worth recording.** The first suspicion was that skip-silence leaked into the stored
speed — it changes the playback rate up to 1600 times a session. It does not: `save()` is called
only from `setSpeed()`, the control itself, and the service separately guards `userSpeed` with
`if (!inSilence)`. The real cause was one line away.

## Brightness — a decision, not an oversight

`VideoGestures.release()` drops the window brightness override when the video goes away, because a
window left dimmed would darken the queue, the settings screen and everything else. Every video
player does this.

But it is called whenever video goes away — so on a queue of videos, a brightness you set by gesture
is likely lost at each track change. That IS the app changing it.

Three options, and it is Dewi's call:

1. **Leave it.** Brightness is a transient window override, not a stored setting. Simplest, and it
   is what other players do — but it does violate "no exceptions" as stated.
2. **Remember it for the session** and re-apply when a video appears, still releasing the override
   outside video. Satisfies both halves: nothing else darkens, and the choice survives track changes.
3. **Persist it** like speed and boost. Most literal reading of the rule; also the most surprising,
   since a brightness set once weeks ago would apply to a video today.

Recommended: **(2)**. It fixes the case that actually looks like a bug without making brightness a
sticky global that outlives the sitting.

## Tests

- `GlobalPlaybackSpeedTest` (4) and `GlobalVolumeBoostTest` (4) — one value, applies to everything,
  replaced only by an explicit save, and the no-op stores never remember.
- Not instrumented: both are pure decisions with no device-shaped failure mode. The device-shaped
  question — does the level survive a real track change — is worth an instrumented test if
  brightness option (2) is taken, since all three then share the behaviour.
