---
title: Quality and speed as an on-video overlay
kind: feature
status: shipped
area: ui
updated: 2026-07-25
---

# Quality and speed live on the video

**Ask (Dewi):** "the quality selectors in pipepipe is a dropdown on the video as a
transient overlay thing — I prefer that to what they are currently in our app ...
selectable beneath the video".

Quality and speed are now buttons in the video's auto-hiding control overlay, top-right,
each opening a compact menu **over the picture**. The row of quality buttons that used to
sit below the player is gone.

## Why it's the better place, beyond taste

**In fullscreen there is no "beneath the player".** A control that lives below the video is
a control you lose exactly when you most want it — mid-video, full screen. Putting these in
the overlay means one implementation serves inline *and* fullscreen, because the overlay is
the same composable in both.

## What moved where

| Control | Before | Now |
|---|---|---|
| Quality | Scrolling button row beneath the video | Menu on the video (video only — audio has no resolutions) |
| Speed | Button row beneath, for everything | Menu on the video for video; **the row stays beneath for audio** |
| Sleep timer, skip-silence, auto-play, volume boost, Listen⇄Watch | Beneath | Unchanged |

Speed keeps its inline row for audio deliberately: a podcast has no video overlay to hang
it on, and speed matters more for podcasts than anything else in the app.

## One duplication caught on the way

Writing the menu, I gave it its own list of playback rates — a second copy of something
`PlaybackPreferences.kt` already had, with different values *and* a different label format.
That is exactly the drift the project's DRY law exists to stop, so the rates and their
label now live in one place (`PlaybackSpeeds`, `speedLabel`) and both surfaces read it. The
merged list reaches 3× because a podcast at 3× is a real use where a video rarely is.

The selected item is marked with a **dot, not a tick**: a tick beside a row's label reads
as a checkbox list, i.e. as though several could be on at once.

## Verified on-device

Played a YouTube video, opened the overlay, and drove it through `uiautomator`:

- The overlay composes `Close`, `Quality`, `1x`, transport, and `Fullscreen`.
- The quality menu opens over the video with the full ladder — 2160p, 1440p, 1080p, 720p,
  480p, 360p, 240p, 144p — the current one dotted.
- Selecting **720p** moved the dot and playback continued (`state=PLAYING`), rather than
  restarting or stalling.

Worth knowing for future device checks: the overlay auto-hides a few seconds into playback,
and each tap on the video *toggles* it, so a `screencap` taken after a `sleep` usually
catches an empty frame. Dump immediately after the tap, with no sleep, or pause first.
