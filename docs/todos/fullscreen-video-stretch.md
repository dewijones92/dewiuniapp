---
title: Fix video stretched in fullscreen
kind: todo
status: shipped
area: playback
priority: high
requested: 2026-07-24
updated: 2026-07-24
---

# Video stretched in fullscreen

**Ask:** video appears stretched in fullscreen.

Likely the fullscreen surface uses a fill/zoom resize mode (or forces the
aspect) instead of fit/letterbox. Check `FullscreenVideo` / `VideoStage` and the
`PlayerSurface` resize mode — it should be `RESIZE_MODE_FIT` and honour
`videoAspectRatio`.

**Done when:** a non-16:9 video letterboxes correctly (not stretched) in
landscape fullscreen, verified on-device.

## Fullscreen dropped on auto-advance (fixed 2026-07-27)

Reported by Dewi: fullscreen should survive the queue auto-playing the next item.

`FullPlayerOverlay` exited fullscreen the instant `state.hasVideo` went false. That is
also what the player reports for the moment *between* items, before the next one's
tracks arrive — confirmed in the trail (`transition -> X`, then `hasVideo=false`, then
`hasVideo=true`). So every auto-advance silently dropped you back to portrait and
nothing put you back.

The exit is now delayed past that gap; the effect is cancelled and restarted the
instant video reappears, so a normal advance never reaches it, while a genuinely
audio item still leaves fullscreen. The video surface is also bound to the player
rather than to `hasVideo`, so it is not torn down and rebuilt across the change.

**Not yet verified on a device** — reaching fullscreen and then triggering a real
auto-advance on the emulator did not come off; the logic and the gate are green but
the on-screen behaviour is unconfirmed.
