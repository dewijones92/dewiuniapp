---
title: Listen mode should only stream audio
kind: todo
area: playback
priority: medium
status: done — YouTube always did; torrents now stream audio-only via the Pi (8x saving), proven on device
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

## Torrents: not possible in the APP, very possible on the Pi

A torrent is ONE file carrying both tracks. There is no audio-only URL for the app to switch to,
which is why `listen()` now declines rather than pointlessly restarting it (see
`ListenModeSingleStreamTest`). Listening to a torrent therefore still pulls the video bytes today.

That is a statement about the app, not about what is achievable — as the measurements below
show. An earlier version of this file said "not currently possible" full stop, which was a guess
dressed as a finding.

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

1. ~~Say so in the UI.~~ **Already handled** — `QualityControl` returns early when
   `canListen` is false, so the toggle is not shown for a torrent at all. Checked rather than
   assumed, 2026-08-02. What Dewi actually hit was the pointless restart, fixed in
   `ListenModeSingleStreamTest`. Nothing to build here.
2. **An `?audio` variant of the stream endpoint**, remuxing with the ffmpeg already there.
   The seek question is now answered — see the design below.
3. ~~TorrServer's own transcode support.~~ Checked 2026-08-02: it has none, so this is a build
   rather than a setting.

## The design, now that the seek question is measured

**HLS, not a pipe.** A piped remux cannot seek; an HLS playlist of audio-only segments can,
natively, because ExoPlayer already speaks it. That removes the only real objection.

Measured on the Pi against a real episode:

| | |
|---|---|
| First playable segment | **25s** |
| Generation rate after that | 48s of audio per 20s wall — **2.4× realtime** |
| Size | **2.1 MB/min** against 15.2 for the video |

So it keeps ahead comfortably once started, and the 25s is a STARTUP cost, not a throughput one.

**Start it at prepare time, not play time.** The app already calls `HomeTorrentServer.prepare()`
when a search result is opened — which is exactly when the torrent is registered and metadata
fetched. Kicking the remux off there absorbs the 25s while the person is still looking at the
file list, so pressing play meets a playlist that is already growing. Without that it is 25
seconds of spinner and the feature is not worth having.

### What it needs building

- A small service on the Pi that owns ffmpeg jobs keyed by `hash:index`, writes segments to a
  temp dir, and reaps them when nobody is listening. The estate already has this shape in
  `totum-crashlog`, so it is a sibling rather than a new pattern.
- An nginx location (`/ts/audio/`) behind the same token guard as `/ts/`.
- App side: `HomeTorrentServer` gains an audio URL, and the torrent item carries it as
  `audioOnlyUrl` — at which point `listen()` works unchanged, `canListen` turns true, and the
  Listen toggle appears on its own. **No playback code changes at all**, which is the point of
  having had one seam for this.

### Honest cost

A long-running ffmpeg per listening stream on a Pi that is 88% full. Segments are temporary and
small (2.1 MB/min, reaped after), but the job lifecycle is the part that will bite: an orphaned
ffmpeg per abandoned tap would be a slow leak, so reaping has to be right before this ships.

## Proven on the device, 2026-08-02

Built and verified end to end on the emulator against a real cached episode:

```
[playback] torrent:bb58…:8 playing as audio only
[format]   audio mp4a.40.2          ← audio only, no video track at all
[playback] ready after 1601ms at 2ms
```

The video path on the SAME episode reports `video hvc1… + audio`. No video track means the
15.2 → 2.1 MB/min saving is real on the phone, not just on the Pi.

**Two bugs only the device could find**, both now fixed:

- **The audio URL did not survive a restart.** `PlayHandle.Podcast` persisted only its local
  path, so a reloaded queue silently fell back to video — the feature worked once and never
  again. Both fields are now encoded into the one column, with legacy bare paths still read
  correctly, so no migration.
- **Every segment 401'd.** Relative URLs in an HLS playlist do **not** inherit the playlist's
  query string, so the token vanished on each segment fetch. The service now stamps it onto
  each segment line. A code comment had confidently claimed the opposite.

### The playing entry kept a stale route — fixed

Its neighbours in the queue had the audio URL and it did not, so the one item being listened to
was the one still pulling video. Cause: every other entry is removed and re-added on a re-queue
and so takes the fresh handle, but the playing entry is deliberately exempt (moving it would
interrupt playback) — leaving it stuck with whatever route it was created with.

Two changes, both in the queue:

- **The playing entry adopts routes it lacks** (`PlayHandle.mergedWith`). Merged, never replaced:
  "newest wins" would drop a `localPath` when a fresh handle arrives without one, and the app
  would then stream a file already on the disk — no error, just data spent on nothing.
- **It is no longer re-inserted alongside itself.** Re-queueing the playing item left the queue
  holding *two* copies and moved the cursor onto the new one, so any in-place refresh landed on
  the entry being abandoned. A pre-existing bug, surfaced by fixing the first.

Verified on the emulator: `play-all(89)` leaves 89 entries (not 178), the cursor stays on the
entry already playing, and it continues from 34s as audio-only rather than restarting.

**Not yet proven on device:** the adoption branch itself. That run rebuilt the whole queue, so
every entry got a fresh handle and adoption was never reached — it is covered by unit tests
(`PlayHandleMergeTest`, `PlayingEntryAdoptsRoutesTest`) and by the `[queue] playing entry adopted
a fresher route` line, which has not yet been seen in a real report.

## Related

- `docs/features/offline-queue.md` — the downloads path, which already fetches audio only.
- Torrent items are `PlayHandle.Podcast` by design (one URL, played directly), which is why they
  have no quality ladder and no audio sibling.
