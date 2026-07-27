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

### Verified, after the first attempt failed

The first fix used a 2s grace and **still lost fullscreen** — caught on device, not in
review. The gap contains a yt-dlp resolve, measured at 3-11s, so no short timeout can
cover it. The working test is whether playback has *settled* without video
(`!hasVideo && !isBuffering`): an item still loading is buffering, an audio item that has
actually started is not.

Staged with `am start -a VIEW -d <watch-url>` (the existing share-target handler, so no
debug-only code was needed), then an item change forced by a second such intent. Before:
`transition` then `[fullscreen] active=false`. After: the same gap passes with no
`active=false` and `rotation=1` — still landscape. Release build.
