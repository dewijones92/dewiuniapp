---
title: We only surface the FIRST page of every feed
kind: todo
status: in progress
area: video
priority: high
requested: 2026-07-25
updated: 2026-07-25
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

Still on page one, and now cheap to add on the same seam:

- **channel tabs** (Videos / Shorts / Playlists) — `browseWeb` already takes a
  `BrowseTarget`, so this is `LockupParser` + `ChannelViewModel` work only
- **search results**
- **podcast episode lists** — a no-op by nature (RSS returns the whole feed), but the
  seam should be threaded so every screen is uniform rather than some being special
