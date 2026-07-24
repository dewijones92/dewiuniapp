---
title: Auto-play next — guaranteed, toggleable, works in fullscreen
kind: todo
status: refining
area: playback
priority: medium
requested: 2026-07-24
updated: 2026-07-24
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

## Open decisions

- Toggle placement: in the full player next to Skip silences (discoverable, matches
  the other playback toggles) or in Settings (tidier)? Proposal: the player.
- When the queue is **empty** at the end: stop (proposal), or YouTube-style
  continue-with-a-related-video? You didn't ask for the latter; it would be a
  separate feature and only exists on one pillar, so it would need a podcast
  answer too (next episode in the feed) to stay unified.

**Done when:** the setting exists (default on), auto-advance is proven on-device in
both windowed and fullscreen playback, and turning it off stops at the end.
