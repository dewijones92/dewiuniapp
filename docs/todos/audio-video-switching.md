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

## Open questions for Dewi

- **Prompt or just a toggle?** A dialog on every queue tap could get annoying fast;
  the alternatives are (a) prompt once with "don't ask again", (b) no prompt — tap
  plays audio and the toggle in the player is the switch, (c) prompt only on Wi-Fi→
  mobile-data transitions. My instinct is (b) plus a clearly visible toggle, with the
  data warning shown only when you actually switch on mobile data.
- Where should the toggle live for a **queue row** (not the player)? In the row's
  long-press sheet ("Watch video" / "Listen only"), which would put it on every feed
  for free, or only in the Queue tab?
- Should switching to video **stick** for that item (remembered like playback speed),
  or reset to audio next time?
- When audio is downloaded but you choose video on mobile data: stream video-only and
  reuse the local audio (point 3), or just stream the normal muxed video?

**Done when:** tapping a queue item and switching to video (and back) continues from
the same position, the data cost is made clear before it's spent, and the switch is
reachable both in the player and from the queue.
