---
title: Library downloads list only shows podcasts
kind: todo
status: open
area: downloads
priority: medium
requested: 2026-07-25
updated: 2026-07-25
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
