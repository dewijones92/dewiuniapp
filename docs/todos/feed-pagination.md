---
title: We only surface the FIRST page of every feed
kind: todo
status: shipped
area: video
priority: high
requested: 2026-07-25
updated: 2026-07-27
---

# Are we surfacing YouTube's recommendations — and missing any?

**Ask:** check that we surface YouTube recommendations, and that we aren't missing any.

## What we surface

| Surface | Status |
|---|---|
| **Home / recommended** (`FEwhat_to_watch`) | ✅ the "Home" chip on the Videos tab |
| **Subscriptions** (`FEsubscriptions`) | ✅ |
| Watch Later (`VLWL`), History (`FEhistory`) | ✅ |
| **Related / up-next** for the playing video | ✅ under the video in the full player |
| Channel tabs (Videos / Shorts / Playlists) | ✅ |
| Search | ✅ (now InnerTube-backed, with upload dates) |

So yes — recommendations *are* surfaced, in two places (Home, and per-video related).

## But we are missing a lot of them, for one reason

**Nothing follows continuations except comments.** Verified by grep: only
`HttpYouTubeComments` calls `nextContinuation`; feeds, channel tabs, search and
related each do a single `browse`/`search` and stop.

YouTube's Home is effectively endless — the first response carries perhaps 20–30
items plus a continuation token. We read that first page and throw the token away, so:

- **Home shows a fraction of your recommendations** and never grows on scroll.
- Same for Subscriptions, History, Watch Later, channel Videos/Shorts/Playlists, and
  search results: scrolling to the bottom is the end, when it shouldn't be.

That's the honest answer to "are we missing any": not a missing *surface*, a missing
*page-two*. It's also the single biggest content gap in the app right now.

## Fix (unified, DRY)

One pagination seam rather than per-feed loops:

- The parsers already walk the tree; each should also pick up the
  `continuationCommand.token` (the comments parser shows the shape).
- A shared `Paged<T>(items, continuation)` result plus a `loadMore(token)` on the
  ports, so every list — feeds, channel tabs, search, related — gains the same
  behaviour once.
- The UI side is one shared "load more when the last row appears" hook for
  `LazyColumn`, used by every feed.
- **Podcasts:** an RSS feed is a single document, so there's nothing to paginate —
  this is legitimately video-only, and the seam simply isn't used by that pillar
  (rather than each pillar growing its own list plumbing).

## Also worth adding while in here

- **Trending / Explore** (`FEtrending`, `FEexplore`) — a recommendation surface we
  don't offer at all. Cheap once the feed machinery is generic.
- YouTube's dedicated **Shorts feed**: our Shorts reel is filtered from whatever the
  current feed returned, not sourced from Shorts itself.

**Done when:** scrolling a feed loads more (Home, Subscriptions, channel tabs,
search), through one shared seam rather than per-screen paging code.

---

## Status 2026-07-25 — account feeds shipped

The seam (`Page`/`PageToken` in `:lib:common`, `Continuations`, `BrowseTarget`,
`LoadMoreOnScrollToEnd`) is in, and the four account feeds page end to end — verified on
device: 45 items became 149 across six continuations. See
[`docs/features/feed-pagination.md`](../features/feed-pagination.md).

**Channel tabs shipped too (same day).** Videos, Shorts and Playlists all page, and
`FeedResult` was retrofitted onto `Page<T>` so there is genuinely one paging shape.

Still on page one, and cheap on the same seam:

- **search results**
- a **playlist's** own screen and **related videos** — both currently drop their
  continuation explicitly (with a comment saying so), which is honest but incomplete
- **podcast episode lists** — a no-op by nature (RSS returns the whole feed)

## Search paginated too (2026-07-27)

The last page-one gap. `SearchSource` now takes an `after` token and answers with a
`Page<SearchHit>`, so the same `LoadMoreOnScrollToEnd` the account feeds and channel tabs
use drives search as well. iTunes and yt-dlp answer in one shot and say so by returning
`Page.last` — "no more pages" as an ordinary page is what lets one scroll serve both.

**A real bug fell out of it.** `Continuations.find` takes the *last* token in the
response, which is right for a channel grid but wrong for search: YouTube puts six filter
chips ("Shorts", "Live", "Recently uploaded") *after* the results, each holding a
continuation of its own. So "load more" followed a filter rather than page two, and the
filtered response had nothing this parser recognised — search paginated once, to nothing,
and stopped. Confirmed against the live endpoint: the chip token returns 0
`videoRenderer`s, the real one returns 21. Chip subtrees are now skipped, since a chip's
token replaces the results rather than extending them in any response.

Verified on device: `8 -> 107` results over 16 pages, with the dedupe absorbing YouTube's
overlapping pages (one page of 8 added only 7).

## Playlists paginated, and the rest resolved (2026-07-28)

`videosIn()` takes an `after` token and returns its `Page` instead of peeling `.items`
off it; `PlaylistViewModel` gained `loadMore()` on the same shape as the account feeds
and channel tabs, dedupe included. Six tests, the important one being that the second
call carries the first page's token — without it `loadMore` silently refetches page one
and appends nothing, which is indistinguishable from "there was no more".

The two remaining items are **closed as decisions, not gaps**:

**Podcast episode lists** — no. RSS has no continuation; a feed is one document and the
parser already returns all of it. Threading the seam for uniformity's sake would be
ceremony, and the earlier note above ("the seam should be threaded so every screen is
uniform") was wrong to ask for it — uniformity of *shape* is not worth a parameter that
can only ever be null.

**Related videos** — not now, and for a structural reason worth recording: the full
player is a `verticalScroll` Column, not a `LazyColumn`, so `LoadMoreOnScrollToEnd`
cannot drive it. Paging it needs a "show more" affordance, which is a UX decision on a
screen nobody has complained about, and the existing parser comment already treats the
up-next list as deliberately short. Revisit if the player ever becomes a LazyColumn.

So the paging work is **done** as far as it is worth doing.
