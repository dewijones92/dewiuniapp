---
title: Listen mode should only stream audio
kind: todo
area: playback
priority: medium
status: requested — true for YouTube; MEASURED as feasible for torrents (8x saving, stream copy), blocked on seeking
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

### Measured on the Pi, 2026-08-02 — it is cheaper than it sounds

Dewi's instinct was that a server-side audio-only feed could not be done. It can, and the cost
is not where you would look for it. Against a real cached episode:

| | |
|---|---|
| Whole stream | **15.2 MB/min** |
| Audio only, remuxed | **1.9 MB/min** |
| Remuxing 120s of audio | **31s wall**, i.e. 2.4× faster than realtime |

`ffmpeg -vn -sn -c:a copy` is a **stream copy, not a transcode** — no decoding, no encoding, so
the Pi's CPU is never the constraint. ffmpeg 4.3.9 is already installed on the host.

**Eight times less mobile data**, which is exactly what was asked for.

### The real obstacle is SEEKING, not CPU

A piped remux is not seekable. Scrubbing would mean restarting ffmpeg at an offset (`-ss`) for
every seek, which is how streaming proxies generally do it — workable, but each seek costs a
process restart and a re-fetch, and the torrent logs show seeking is used heavily.

Two smaller things worth stating plainly:

- **The Pi still downloads the video.** Audio and video are interleaved in the container, so
  extracting the audio means reading the whole file. That is fine — it saves the PHONE's data,
  which is the goal, and the Pi is on home broadband — but it does not reduce what the swarm
  sends.
- **2.4× realtime is on a warm file.** On a cold one, competing with the torrent fetch, it may
  not keep ahead.

### Order of work

1. **Say so in the UI.** The Listen control is currently silent about why it cannot help on a
   torrent. Cheapest, removes the confusion immediately, and worth doing whatever else happens.
2. **An `?audio` variant of the stream endpoint**, remuxing with the ffmpeg already there.
   Prove the seek story before building it — a listen mode that cannot scrub is a different
   feature, and possibly still the right one for a podcast-shaped listen.
3. TorrServer's own transcode support, only if it turns out to solve seeking for free.

## Related

- `docs/features/offline-queue.md` — the downloads path, which already fetches audio only.
- Torrent items are `PlayHandle.Podcast` by design (one URL, played directly), which is why they
  have no quality ladder and no audio sibling.
