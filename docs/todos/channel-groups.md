---
title: Channel groups (groups of sources)
kind: todo
area: video
priority: high
status: in progress
updated: 2026-07-30
---

# Groups of sources

Dewi's ask (2026-07-29): *"special groups that are special channels… channel a
channel b channel c… I want the live stream, short, everything, so maybe like a
sub tab"*.

## Shape

Groups of **sources**, not of channels. A podcast feed and a video channel are
both `SourceId`s, so a group holds either or both and the merged feed merges what
it finds — the Unified law at no extra cost. See `SourceGroup` in `:core:domain`.

## Stages

1. **Domain + storage — shipped** (`4260907`). `SourceGroup` / `SourceGroupId`,
   the `SourceGroupStore` port, `RoomSourceGroupStore` and DB v15 (two additive
   tables). Membership is a toggle; the stored order is for editing, the feed
   sorts by date. Wired into `AppContainer`; instrumented tests cover the cascade
   and cross-group isolation.
2. **Merged feed fetch — next.** Design settled: resolve each member `SourceId`
   against the app's known sources so the fanout routes on the **sealed**
   `MediaSource` (VideoChannel | PodcastFeed) in an exhaustive `when`, the same
   way `RoutedDownloadStrategy` routes downloads — never by sniffing the URL,
   which is the mistake that once made a Shorts URL download as a video and queue
   as a podcast enclosure. Per-channel fetch rather than filtering the account
   subscriptions feed, because Dewi wants shorts and live too and the account
   feed carries neither reliably. Merge newest-first across members.
3. **Chips + manage screen — after that.** Groups appear as chips in the Videos
   feed selector beside RECOMMENDED / SUBSCRIPTIONS; membership is toggled from a
   channel page.

## Open

- How many members before the per-channel fanout is too slow to do on entry? Fetch
  concurrently and cap, or refresh in the background and show what is cached.
