---
title: Auto-download audio for everything in the queue
kind: todo
status: refining
area: downloads
priority: high
requested: 2026-07-24
updated: 2026-07-24
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
3. **Auto vs pinned.** Mark auto-downloads with a flag on the download row
   (DB migration) so leaving the queue can clean them up, while a download the
   user asked for is never auto-deleted.
4. **Settings** (`AppPreferences`): "Auto-download queued items" (default **on**)
   and "Wi-Fi only" (default **on** — `NetworkStatus` already exists and already
   drives per-network quality).

## Open decisions

- **Videos: audio-only confirmed?** If so, watching a queued video still streams
  the picture (with the local audio used in listen mode). The alternative —
  auto-downloading full video — is much heavier and probably not what you meant.
- **Cleanup on leaving the queue**: delete the auto-download (queue = a cache), or
  keep it until storage pressure?
- Should the GUI distinguish "downloaded automatically" from "you downloaded this"
  (e.g. a different icon tint), or is one Downloaded state enough?

**Done when:** an item entering the queue downloads its audio by itself (honouring
the settings), its row shows progress then downloaded, and playback uses the local
file offline.
