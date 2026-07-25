---
title: Auto-download audio for everything in the queue
kind: todo
status: ready
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

## Watch out

Audio-only files and full merged downloads both land in the same download row keyed
by `MediaItemId`, so "queued → audio-only downloaded" then "user downloads the full
video" must not read as already-downloaded. Needs deciding at build time: either
record which variant a row holds, or let an explicit full download supersede the
audio-only file.

**Done when:** an item entering the queue downloads its audio by itself (honouring
the settings), its row shows progress then downloaded, and playback uses the local
file offline.
