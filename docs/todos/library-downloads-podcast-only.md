---
title: Library downloads list only shows podcasts
kind: todo
status: done
area: downloads
priority: medium
requested: 2026-07-25
updated: 2026-07-27
---

# The Library's downloads list is podcast-only

Found while wiring row status (2026-07-25), not reported.

`LibraryViewModel.downloaded` combines `repository.observeEpisodes()` with the download
states — so it can only ever surface **podcast episodes**. A downloaded *video* has a
`Downloaded` row in the download store and a file on disk, but never appears in the
Library. Its own comment admits the assumption ("The Library lists downloaded podcast
episodes"), while the screen and the architecture doc both describe the Library as
"downloads across both pillars".

This is a **unified-law violation**, not a missing feature: the download seam is already
one port for both pillars (`RoutedDownloadStrategy`), and only this read path narrows it.

## Why it happens

The download store keys by `MediaItemId` and holds the local path, but not enough of the
item to render a row — so the list is reconstructed by joining against a *source* of
items, and the only source joined is the podcast repository. Videos have no equivalent
"all videos I know about" table to join against.

## The fix, in the shape the rest of the app already uses

Store the row with the download, exactly as the queue, history and playlists do:
denormalized item columns plus a `PlayHandle` (the `PlaylistItemColumns` interface is
already shared by three tables and would make four). Then the Library reads the download
store alone, needs no join, and gets both pillars by construction — a downloaded video
row would carry `PlayHandle.LocalVideo`, which is also what playback wants.

Migration adds columns to `downloads`; existing podcast rows can be backfilled from the
episodes table, and anything unbackfillable simply doesn't list until re-downloaded.

**Done when:** a downloaded video appears in Library beside downloaded episodes, plays
from its local file, and the pillar glyph distinguishes them — with no per-pillar branch
in the read path.

## Shipped 2026-07-27

Done as described, plus two things the write-up above did not anticipate.

`DownloadEntity` became the fourth `PlaylistItemColumns` table; `DownloadStore` gained
`observeDownloaded()` returning `DownloadedMedia` (item + path + variant), and
`LibraryViewModel` reads that alone. `DownloadedMedia.offline` swaps in the local handle,
so an audio-only video plays as audio and a full one as `LocalVideo`.

**The root cause was wider than the read path.** Two copies of "is this a video?" existed
and disagreed — `toPlayableOrNull` matched only `youtube.com/watch`, while the download
router matched any YouTube host, so a Shorts URL downloaded through the engine but queued
as a podcast enclosure. Both now come from one `MediaItem.pillar`, and
`RoutedDownloadStrategy` routes on `PlayHandle.pillar` via an exhaustive `when` rather
than URL predicates — a third pillar cannot be added without it failing to compile.
`PlayableItem.fetchUrl` is likewise the single answer to "where do the bytes come from",
which deleted the copy of that rule `QueueAutoDownloader` was carrying.

**Existing downloads are backfilled, not dropped.** The migration joins `downloads`
against queue / history / playlist / episode rows, so files already on disk keep their
titles; anything matching nowhere keeps its id as a title rather than being dropped,
because dropping the row would strand the bytes with nothing able to play or delete them.
Verified on the emulator's real database: five pre-existing downloads came through named
and typed as videos.
