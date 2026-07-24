---
title: Local cross-pillar playlists
kind: todo
status: open
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
