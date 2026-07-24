---
title: Fix video stretched in fullscreen
kind: todo
status: open
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
