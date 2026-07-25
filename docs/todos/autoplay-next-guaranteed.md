---
title: Auto-play next — guaranteed, toggleable, works in fullscreen
kind: todo
status: shipped
area: playback
priority: medium
requested: 2026-07-24
updated: 2026-07-25
---

# Auto-play next always works

**Ask:** make sure auto-play to the next item always works — default on,
toggleable — and make sure it works in fullscreen mode.

## What exists today

Auto-advance was originally inside `FullPlayerHost`, so the queue was inert
whenever the full player wasn't composed (fixed in the hardening pass by hoisting
an always-composed `AutoAdvance` into `AppShell`, keyed on the end-transition with
a seeded `handledEndFor` so a mid-item open doesn't double-fire). There is **no
setting** for it, and fullscreen has not been explicitly verified.

## Proposed shape

1. **Setting:** `autoPlayNext` in `AppPreferences` (default **true**) + a toggle in
   the player's controls (next to Skip silences / Sleep timer) or Settings.
   `AutoAdvance` reads it; off means playback simply stops at the end.
2. **Fullscreen:** verify on-device that the end-of-item transition still advances
   while fullscreen is active (fullscreen re-composes the player subtree, but
   `AutoAdvance` lives above it in `AppShell`, so it should hold — needs proving,
   not assuming), and that the next item's video surface attaches without leaving
   fullscreen.
3. **Both pillars, one path:** advance is `PlaybackQueue.playNextInQueue()`, which
   already handles Video (re-resolves the watch URL just-in-time), LocalVideo and
   Podcast — nothing pillar-specific to add.

## Decided

- Toggle lives in the full player next to Skip silences (matches the other playback
  toggles), default on.
- Empty queue at the end → stop. Continue-with-a-related-video is a separate,
  not-yet-requested feature and would need a podcast answer to stay unified.

**Done when:** the setting exists (default on), auto-advance is proven on-device in
both windowed and fullscreen playback, and turning it off stops at the end.

## Shipped 2026-07-25

- `autoPlayNext` in `AppPreferences` (default **on**, SharedPreferences-backed);
  `AutoAdvance` in `AppShell` returns early when it's off.
- Toggle in the full player next to Skip silences. Both are now drawn by one shared
  `PlayerToggle`, with a `PlaybackToggles` bundle threading them through the player —
  room for the volume boost to join the row.
- **Gating bug caught while building:** the new toggle first landed inside the
  `if (!state.hasVideo)` block that (correctly) hides *skip silence* for video, which
  would have hidden auto-play from the video pillar entirely. Auto-play applies to
  both pillars, so it now sits outside that gate.

### Verified on-device

- Toggle visible for a **video** (with Skip silences correctly absent) and for a
  **podcast** (both shown).
- **Fullscreen advance proven:** a video played to its end while fullscreen was
  active; the queued podcast took over, the queue emptied, and the player dropped
  out of fullscreen to portrait artwork for the audio item, resuming its saved
  position. This was the specific worry — `AutoAdvance` lives above the player in
  `AppShell`, and fullscreen recomposition doesn't disturb it.
- Turning it off writes `auto_play_next=false` to SharedPreferences (persisted
  across restarts).

Test-driving note worth keeping: `uiautomator dump` and `adb shell input` both use
the **native portrait** coordinate frame even while the display is rotated, so
landscape taps must use the dumped bounds, not screenshot coordinates.
