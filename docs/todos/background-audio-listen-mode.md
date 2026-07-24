---
title: Verify listen-mode audio with screen off
kind: todo
status: shipped
area: playback
priority: high
requested: 2026-07-24
updated: 2026-07-24
---

# Background audio in listen mode with screen off

**Ask:** when listen mode (audio-only) is on for a video and the phone screen is
off, audio must keep playing.

`PlaybackService` is a foreground `MediaSessionService`, so audio should continue
with the screen off — but the video-in-listen-mode path needs confirming (the
decoder/surface must not pause playback when the surface detaches).

**Done when:** verified on-device — play a video → enable listen mode → screen
off → audio continues. Fix if it pauses (e.g. ensure the player keeps decoding
audio with no surface; check `setVideoSurface(null)` / listen path).
