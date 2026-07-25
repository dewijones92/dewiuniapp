---
title: Easy audio ↔ video switching for a queue item
kind: todo
status: refining
area: playback
priority: high
requested: 2026-07-25
updated: 2026-07-25
---

# Switch between audio and video, carrying the position

**Ask:** the queue auto-downloads audio for everything — but when I tap a queue item
(playing or not), ask me whether I want to **switch to video**; if yes, carry on from
the same point. Warn in the same dialog if I'm on mobile data. Also put a **toggle**
in the item while I'm actually in it.

Depends on [queue-first-playback](queue-first-playback.md) and
[auto-download-queue](auto-download-queue.md).

## Most of this already exists

`VideoPlaybackLauncher` already switches a *resolved* video between audio-only and
video and resumes from the same point, and the full player already shows the toggle:

- `listen()` — replays the same item with the audio-only stream (no video track, so
  the player shows artwork; far less data).
- `watch()` — returns to the video ladder.
- Position is preserved because `Media3PlaybackController.play` restores from
  `PlaybackProgressStore` on every play, so a switch resumes where you were.
- `ListenWatchToggle` in the full player shows when `canListen && (hasVideo ||
  listening)` — added when the "listen-mode trap" was fixed.

So the **toggle while you're in an item is largely done**; what's missing is the rest.

## What's actually new

1. **The prompt on tapping a queue item.** A dialog: "Playing audio — switch to
   video?" with Yes / Keep audio, plus a **mobile-data warning line** when
   `NetworkStatus` says we're not on Wi-Fi (`NetworkStatus` and per-network quality
   prefs already exist). Needs a "don't ask again" so it doesn't nag.
2. **Switching to video from a *downloaded audio* item.** Today's toggle only works
   for a video resolved this session (the launcher holds `current`). A queue item
   played from a local audio file has no resolved video, so "switch to video" must
   resolve the watch URL fresh and seek to the saved position. That's the real work
   — and it's also the piece that makes the toggle meaningful for offline items.
3. **Best-of-both playback (worth considering).** If the audio is already downloaded,
   switching to video should stream **video-only** and keep using the local audio
   file — the player already merges a separate audio track with a video-only stream
   (`EXTRA_AUDIO_URL` in `Media3PlaybackController`, used for the high-quality
   ladder). Extending that to accept a local audio *path* would make "switch to
   video" cost only the video bytes. Nice fit; needs verifying the service accepts a
   file URI as the merged audio source.

## Decided (Dewi, 2026-07-25)

- **No prompt on every tap.** Tapping plays audio; a clearly visible toggle does the
  switch; the mobile-data warning appears only when you actually switch to video on
  mobile data. (Dewi: "lets do it yourway".)
- **The toggle lives in both places** — the row's long-press sheet (so it lands on
  every feed, both pillars) *and* the Queue tab, alongside the existing one in the
  full player.
- **Stickiness is a global mode, not per item.** Dewi's instinct, and it beats the
  per-item idea: wanting audio is *situational* ("I'm washing up"), not a property of
  a particular video. Playback speed is per-source because a slow talker is a
  property of the source; audio-vs-video isn't.

## The mode (proposal)

`PlaybackMode` in `AppPreferences`, persisted across restarts, three states:

| Mode | Behaviour |
|---|---|
| **Auto** (default) | Video on Wi-Fi, audio on mobile data — the "smart" default, which also makes the data warning nearly redundant |
| **Audio** | Everything plays audio-only, preferring the downloaded audio |
| **Video** | Videos play with picture |

Consulted in exactly one place — `VideoPlaybackLauncher`, when it decides between the
video ladder and the audio-only stream — so it covers every screen and is a no-op for
podcasts (no video track to choose).

**Honesty about the global effect:** a row-sheet action that silently changes global
state reads oddly ("why did this row's menu change everything?"). So the sheet action
plays *that* item the chosen way **and** sets the mode, and says so — a snackbar
("Video mode on"). One concept, no hidden per-item state, and no settings-screen hunt.

## Still open

- Does **Auto** as the default feel right, or a plain Audio/Video default?
- **Shorts and an explicit fullscreen tap** are unambiguous "I want to watch" signals
  — force video for that item without changing the mode? (Instinct: yes.)
- **Cast**: a TV receiver is a video device, so casting should probably ignore Audio
  mode. (Instinct: yes.)
- When audio is downloaded but you choose video on mobile data: stream video-only and
  reuse the local audio (point 3 above), or just stream the normal muxed video?

**Done when:** tapping a queue item and switching to video (and back) continues from
the same position, the data cost is made clear before it's spent, and the switch is
reachable both in the player and from the queue.
