---
title: Auto-download audio for everything in the queue
kind: todo
status: in-progress
area: downloads
priority: high
requested: 2026-07-24
updated: 2026-07-25
---

# Auto-download the queue's audio, and show it

**Ask:** everything in the master queue should have its **audio downloaded by
default**, and that should be **indicated in the GUI**.

Depends on [queue-first-playback](queue-first-playback.md) — the "master queue" is
the spine that lands there.

## What exists today

- `DownloadManager` port + `RoutedDownloadStrategy`: `HttpDownloadStrategy` for
  podcast enclosures, `EngineDownloadStrategy` for videos (yt-dlp fetches best
  video **+** audio and merges them via bundled ffmpeg, then cuts SponsorBlock).
- `DownloadState` (NotDownloaded / Downloading(fraction) / Downloaded / Failed) is
  already rendered by `MediaItemRow`'s `DownloadControl` on every feed — so GUI
  indication mostly comes free once auto-download writes the same rows.
- Playback already prefers a local file when one exists (`play(..., localPath=)`).

## Proposed shape

1. **Audio-only download mode.** A video's auto-download fetches `bestaudio` with
   no merge — fast, small, and exactly what "audio downloaded" means. The existing
   full video+audio merge stays as the explicit user-initiated download.
   One port, one more strategy behind `RoutedDownloadStrategy`.
2. **Queue membership triggers it.** Entering the queue schedules the download;
   the existing `DownloadState` flows light up the row automatically (spinner →
   check) in the feed, the Queue tab, and Library.
3. **No cleanup, no auto/pinned flag** (decided): an auto-download is a normal
   download and stays until deleted from Library. Keeps the download row schema
   untouched — no migration, no reference-counting against queue membership.
4. **Settings** (`AppPreferences`): "Auto-download queued items" (default **on**)
   and "Wi-Fi only" (default **on** — `NetworkStatus` already exists and already
   drives per-network quality).

## Decided (Dewi, 2026-07-25)

- **Audio-only** for videos (`bestaudio`, no ffmpeg merge); podcasts their enclosure.
  Watching a queued video still streams the picture; the local audio is what listen
  mode and offline playback use.
- **Nothing is auto-deleted.** Leaving the queue keeps the file; you remove it from
  Library. So no auto/pinned distinction in the GUI either — one Downloaded state.
- Wi-Fi-only by default (toggleable), auto-download on by default.
- **The whole queue is downloaded**, not a rolling window (Dewi, 2026-07-25) —
  sequentially so it doesn't hammer the network. Library should surface total size
  used so a long queue never surprises.

## Watch out

Audio-only files and full merged downloads both land in the same download row keyed
by `MediaItemId`, so "queued → audio-only downloaded" then "user downloads the full
video" must not read as already-downloaded. Needs deciding at build time: either
record which variant a row holds, or let an explicit full download supersede the
audio-only file.

**Done when:** an item entering the queue downloads its audio by itself (honouring
the settings), its row shows progress then downloaded, and playback uses the local
file offline.

## Shipped 2026-07-25

- **Audio-only download variant.** `DownloadStrategy.download(item, target, audioOnly)`;
  the engine strategy selects `ba/b` (bestaudio, no ffmpeg merge) instead of
  `bv*+ba/b`. `HttpDownloadStrategy` ignores the flag — a podcast enclosure *is* the
  audio, which also makes its recorded variant correctly "full".
- **The variant is persisted** (`downloads.audioOnly`, DB v11) and
  `DefaultDownloadManager` treats an audio-only file as **not** satisfying a request
  for the full media. Two tests lock both directions.
- **`QueueAutoDownloader`** observes the queue and fetches each item's audio
  sequentially (a long queue must not saturate the connection or starve playback),
  skipping already-downloaded/downloading items, local files, and items with no
  fetchable URL yet. Started from `UniAppApplication`.
- **Settings** (Settings → Downloads): "Download queued audio" (default on) and
  "Wi-Fi only" (default on, disabled when auto-download is off).

### Verified on-device

- v10 → v11 migrated cleanly (`audioOnly` column present, no crash).
- Queuing a podcast episode auto-downloaded it (117MB enclosure) → `downloaded`,
  variant recorded as full (correct — an enclosure is the whole thing).
- Queuing a **video** auto-downloaded its audio → `downloaded` with **audioOnly=1**.
  (Caught while testing: the engine strategy initially failed to stamp the variant on
  its finished state, which would have re-opened the exact trap the flag exists to
  prevent. Fixed and covered by a test.)

## Open UX question this exposes — how do I get the full video offline?

You asked for **no GUI distinction** between an automatic and a manual download. The
consequence: once the queue has grabbed a video's audio, its row shows the
"downloaded" tick, and tapping that tick **deletes** — so there is no longer any way
from the row to ask for the full video offline. The manager would honour such a
request; the UI just can't express it.

Three honest options:

1. **Getting the video is the audio↔video switch's job**, not the Download button
   (see [audio-video-switching](audio-video-switching.md)): downloads exist for
   listening, watching offline isn't promised. Coherent, and needs no UI change.
2. **Show audio-only downloads as not-downloaded** in the row, so Download still
   offers the full video — but then the tick lies about what's offline.
3. **A small distinction after all** (e.g. a headphones-tinted tick), reversing the
   "one Downloaded state" decision.

My preference is (1), with (3) as the fallback if you want full-video offline to be a
first-class action. Not guessed — flagged.

### Resolved 2026-07-25 — a contextual action, no new visual state

Dewi: "go with what you think is sensible". Neither of my three options was quite it.
Shipped instead: the row keeps **one** Downloaded state (no new tint, no lie), and the
long-press sheet gains **"Download video"** — offered *only* when the local copy is
audio-only. The capability comes back without cluttering the row, and the sheet is
where per-item actions already live.
