---
title: Drop to audio when Wi-Fi drops mid-video
kind: todo
area: playback
priority: high
status: shipped — decision + hysteresis unit-tested; on-device toggle not yet exercised
updated: 2026-08-04
---

# Drop to audio when Wi-Fi drops mid-video

Dewi, 2026-08-04: *"if the phone is playing video but then there is suddenly no wifi, a notification
appears saying 'hey we have switched to listening only mode'"*.

Confirmed as the **metered** reading: the phone falls off Wi-Fi onto mobile data. (The other
reading — no network at all — is a different feature: audio needs the network too, so the honest
response there is "playing your downloads", which is the offline-skip path in
`offline-queue-e2e.md`.)

The saving is why it is worth doing at all: **15.2 MB/min** for a whole stream against **2.1** for
the audio alone, measured on the Pi. Walking out of the house with a video playing otherwise spends
mobile data at eight times the rate, silently.

## It needed almost no new machinery

Every part already existed — listen mode, an audio-only route per pillar (a YouTube audio stream, an
HLS playlist the home server remuxes), `replayCurrent` to switch without losing your place, and
`NetworkStatus.isMetered()`. `MeteredAudioSwitch` decides *when*; it knows nothing about pillars,
which is the unified seam paying off rather than three implementations.

It flips the real playback mode, so the player's own toggle is the undo and the choice carries to
the next item as anyone would expect.

## Hysteresis is the whole risk

A connection bouncing between Wi-Fi and mobile — a lift, a train, the end of the drive — would
otherwise re-prepare the player every few seconds and stutter playback continuously. Mobile has to
hold for **15 seconds** before anything happens, which turns flapping into a no-op. That case has
its own test, because it is the one that decides whether the feature is tolerable.

Sampled on a clock rather than collected, for the same reason `StallWatchdog` is: connectivity is a
level, and the interesting case is a state that persists.

## Decisions taken

- **Coming back to Wi-Fi does NOT restore video.** Dewi's call: a screen lighting up with video
  nobody asked for is worse than staying put, and it is one tap away.
- **Once per item, re-armed by returning to Wi-Fi**, so a second trip out switches again. A flag
  that survives for the life of the process is the exact defect that broke autoplay and then the
  stall rescue; this would have been the third time.
- **A failed switch is never announced.** Video is still running, and saying otherwise would be a
  lie in a notification.
- **Its own notification channel**, so it can be silenced alone. A notification you cannot turn off
  separately is one people turn off entirely, taking the useful ones with it.

## Still to do

- **Exercise it on a device.** The decision is unit-tested (10 cases) but the actual switch —
  re-prepare, notification, and what the player looks like afterwards — has not been run with the
  radios toggled. `svc data enable` / `svc wifi disable` is the proven technique here (see
  `offline-queue-e2e.md`).
- A **"keep video"** action on the notification itself. `MeteredAudioSwitch.keepVideo` exists and is
  tested; nothing calls it yet, because switching back through the player's toggle already prevents
  a re-downgrade (the item is marked switched until Wi-Fi returns).
