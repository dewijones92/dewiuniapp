---
title: Settings change only when you change them
kind: todo
area: settings
priority: high
status: done — speed, volume boost and brightness all hold until you change them
updated: 2026-08-08
---

> **This status was wrong for brightness for three days, and the gap is named at the bottom of this
> very page.** "Brightness holds" was true across a track change and false across a **fullscreen
> toggle**, which wiped it for the rest of the session (Dewi, 2026-08-08). The Tests section below
> called for an instrumented test on exactly this — *"the device-shaped question … is worth an
> instrumented test if brightness option (2) is taken"* — and it was not written, so nothing
> contradicted the claim. Fixed and covered; see **Fullscreen wiped it** below.

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
| Brightness | window override, released when video ends — and the choice reset with it | window released, choice kept for the session |

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

Three options were put to Dewi; he chose (2), 2026-08-05:

1. **Leave it.** Brightness is a transient window override, not a stored setting. Simplest, and it
   is what other players do — but it does violate "no exceptions" as stated.
2. **Remember it for the session** and re-apply when a video appears, still releasing the override
   outside video. Satisfies both halves: nothing else darkens, and the choice survives track changes.
3. **Persist it** like speed and boost. Most literal reading of the rule; also the most surprising,
   since a brightness set once weeks ago would apply to a video today.

**Chosen: (2).** `ChosenBrightness` holds the level for the sitting; `release()` now drops only the
WINDOW override, not the choice, and `rememberVideoGestures()` re-applies it when a video appears.
Not persisted, deliberately — a brightness set weeks ago applying to a video today would be the
same surprise in the other direction.

Note `isSet` is `value >= 0`, not `value > 0`: full dark is a real choice, and a `> 0` test would
silently ignore it. That edge has its own test.

## Fullscreen wiped it (2026-08-08)

Dewi: *"The brightness is turned up when I go into a video item, but then it's turned down when I go
into full screen video."* He confirmed the **backlight** itself changes, that he had set a level by
swipe earlier in the session, and that leaving fullscreen does not bring it back — it stays dim for
the rest of the sitting.

**The cause is an ordering, not a decision.** Every individual step above was right. But going
fullscreen swaps one subtree for another (`FullPlayer.kt:137` renders the stage directly; windowed it
sits deep inside `DraggablePlayerContent`), so the outgoing stage is disposed and a fresh one is
created. The incoming one re-applied the remembered brightness during **composition**; the outgoing
one released the override in its `onDispose`, which runs in the **effects** phase afterwards. Later
wins, so every transition ended on "follow the system".

That is why it never came back: each subsequent toggle repeated the same pair in the same order.

**The fix is to make the answer order-independent.** `ChosenBrightness` now counts how many video
stages are on screen, and the override is released by the **last** one to go rather than the first —
so during the swap, when both briefly exist, the window keeps the choice whichever order the two
lifecycle calls run in. A flag on either composable could not do this, because neither knows about
the other. `rememberVideoGestures()` owns both halves of the pair, so a caller cannot register one
without the other.

**Not quite PipePipe, deliberately.** As I understand it PipePipe applies its override only in
fullscreen and drops back to system brightness on leaving — Totum keeps your level on the windowed
video page too, and releases only when video leaves the screen entirely. Dewi chose that shape
(2026-08-08): *keep whatever brightness I had*. The practical difference is the windowed video page,
which stays at your level rather than reverting.

## Tests

- `ChosenBrightnessTest` (11, JVM) — the original five (remembered across a video ending, zero counts
  as a choice, later gestures replace earlier ones, out-of-range drags clamp) plus the counting rule:
  a stage swap holds the brightness **whichever way round** it happens, the last stage to go releases
  the window, an unbalanced release cannot strand the count below zero, and with no choice made
  nothing is overridden.
- `BrightnessSurvivesFullscreenTest` (5, instrumented) — the real `VideoStageWithControls` driven
  through the real subtree swap, asking the real window what brightness it shows: survives entering
  fullscreen, survives leaving it, survives six toggles, is still released when video goes away
  entirely, and is left alone when nothing was chosen.
- `GlobalPlaybackSpeedTest` (4) and `GlobalVolumeBoostTest` (4) — one value, applies to everything,
  replaced only by an explicit save, and the no-op stores never remember.

**This is why the instrumented test was needed and the unit tests were not enough**, which the
previous version of this section correctly predicted and then did not act on. The defect lives in
Compose's execution order, so it is invisible to any test of a single function: the JVM tests all
passed throughout. The instrumented one failed on the first run with `expected:<0.87> but
was:<-1.0>` before a line of the fix was written.
