---
title: Local cross-pillar playlists
kind: todo
status: in-progress
area: library
priority: high
requested: 2026-07-24
updated: 2026-07-24
---

# Local playlists that mix podcasts + YouTube

**Ask:** a way to enqueue / make playlists locally that contain podcasts AND
YouTube stuff together.

A persistent, user-curated, **cross-pillar** playlist — distinct from:
- the **up-next queue** (`PlaybackQueue`) — transient "what plays next";
- **YouTube account playlists** (`:lib:innertube` `playlists/`) — remote, video-only.

This is a first-class unified feature: one playlist holds `MediaItem`s of either
pillar (podcast episodes and videos), reorderable, and plays through the one
`PlaybackController` / queue.

## Approach (unified, DRY)

- **Domain/store:** a `LocalPlaylist` (id, name, ordered `MediaItemId`s or
  denormalized `MediaItem`s so items survive offline) + a Room-backed store
  (`:core:data` port + `:core:database` impl), mirroring the podcast/download
  stores. Videos store their watch URL (resolve on play, like the queue) so
  stream URLs never go stale.
- **UI:** create/rename/delete playlists; add-to-playlist from the item overflow /
  long-press sheet (ties into [long-press-context-menu](long-press-context-menu.md));
  a playlist screen reusing `MediaItemRow`; "Play all" → loads the queue.
- **Reuse:** the existing `PlaybackQueue` for playback; `MediaItemRow` for rows;
  the same add-to-queue plumbing.

One playlist model for both pillars — a playlist that couldn't hold both would be
a design failure (the Unified law).

**Done when:** a user can create a local playlist, add both a podcast episode and
a YouTube video to it, reorder, and play it through.

## Progress 2026-07-24

**Data foundation shipped** (commit 0a67872): `LocalPlaylist` domain, `LocalPlaylistStore`
port + `PlaylistItem`/`PlaylistPlayback` (mirrors the queue shapes), Room impl +
DB v7→8 migration, InMemory fake + contract tests, wired into `AppContainer`.

**Remaining (UI):** a `PlaybackQueue.playAll(items)`; QueuedItem↔PlaylistItem
mapping; a Playlists section/screen in Library (list + create + delete); a playlist
detail screen (items via MediaItemRow, Play all → queue, remove); and an
'Add to playlist' entry on the MediaItemRow overflow, wired per pillar (podcast
episode → Podcast handle, feed/channel video → Video watchUrl handle). Note: the
row overflow (`onAddToQueue`) is currently only wired in PodcastsScreen, so
add-to-playlist needs wiring across the item screens.
