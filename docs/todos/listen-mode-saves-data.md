---
title: Listen mode should only stream audio
kind: todo
area: playback
priority: medium
status: requested — already true for YouTube, not possible for torrents without work on the Pi
updated: 2026-08-02
---

# Listen mode should only stream audio

Dewi, 2026-08-02: *"make sure when on 'listen only' mode that it only streams the audio to my
device to save data"*.

## YouTube: already true

`VideoPlaybackLauncher.listen()` plays `resolved.audioOnlyUrl`, a separate audio-only stream, so
no video bytes are fetched at all. That is the whole reason the mode exists and it needs nothing.

Worth a check rather than an assumption, though: the mode is chosen at PLAY time, so an item
already playing as video and then switched keeps whatever it was given until it is re-prepared.
That re-prepare does happen for YouTube (there is a separate URL to move to), so the saving is
real — but it is untested, and "we think it saves data" is exactly the sort of claim that turns
out to be false on a phone.

## Torrents: not currently possible

A torrent is ONE file carrying both tracks. There is no audio-only URL to switch to, which is
why `listen()` now declines rather than pointlessly restarting it (see
`ListenModeSingleStreamTest`). Listening to a torrent therefore still pulls the video bytes.

Three ways out, in increasing order of effort:

1. **Let TorrServer transcode.** It can remux/transcode on the fly in some builds; an
   audio-only output would be exactly this. Needs checking against the MatriX build on the Pi,
   and the Pi is a Raspberry Pi — transcoding cost is the question, not capability.
2. **Remux on the Pi with the ffmpeg already there**, exposing an `?audio` variant of the stream
   endpoint. More control, more moving parts, and a second thing to keep alive.
3. **Say so in the UI.** Cheapest and honest: when an item has no audio-only stream, the Listen
   control explains that this one cannot save data rather than appearing broken. Worth doing
   whatever else happens, because it is currently silent.

**(3) first**, since it removes the confusion immediately, then measure whether (1) is viable on
the hardware before building (2).

## Related

- `docs/features/offline-queue.md` — the downloads path, which already fetches audio only.
- Torrent items are `PlayHandle.Podcast` by design (one URL, played directly), which is why they
  have no quality ladder and no audio sibling.
