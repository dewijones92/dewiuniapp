---
title: "Go to channel" action in the long-press sheet
kind: todo
status: shipped
area: ui
priority: medium
requested: 2026-07-24
updated: 2026-07-24
---

# "Go to channel" from a media row

**Ask (part of the long-press request):** hold an item → "browse channel".

The rest of [long-press-context-menu.md](long-press-context-menu.md) shipped; this
action didn't, because the data isn't there yet:

- `MediaItem.author` is only a display **name** ("The Set | Andy and Jamie Murray").
- Feed items carry `sourceId = ytfeed:SUBSCRIPTIONS` — the *feed*, not the channel.
- `ChannelScreen` needs a `MediaSource.VideoChannel` (a `UC…` id or channel URL);
  `ChannelViewModel` extracts the `UC…` from `channelUrl` and falls back to
  yt-dlp for handle-only channels.

**Approach (unified):** add an optional channel handle to `MediaItem` — the
pillar-agnostic name being "the source this item came from, addressable" (e.g.
`sourceHandle: HttpUrl?`). Populate it in the InnerTube parsers (`lockupViewModel`
carries the channel's browseId/canonical URL) and, for podcasts, from the feed URL
— so the same action means "go to the channel" for a video and "go to the feed"
for an episode. Then the sheet gains one more `SheetAction`, wired through
`MediaItemActions`, opening `ChannelScreen` (video) or the feed's episode list
(podcast).

**Done when:** long-press any row on either pillar → "Go to channel" (or "Go to
podcast") opens that source's page.

## Progress 2026-07-24 — destinations now exist on both pillars

The podcast pillar had no source page at all (the Podcasts subscription chips were
dead: `onClick = {}`), so there was nothing for a podcast row to navigate *to*.
Shipped: `PodcastFeedScreen` — one feed's episodes, reached by tapping its chip —
built as a filtered view of `PodcastsViewModel` (not a parallel view model), and
`ChannelScreen`'s header extracted to a shared `SourceHeader` (back / title /
subscribe toggle) now used by both pillars' source pages.

## Shipped 2026-07-25

`SourceLocator` (port, `:core:data` `data/source/`) + `DefaultSourceLocator`: one
seam that answers "what source is this row from?" for both pillars, **resolved from
data rather than by sniffing URLs a second time** —

- a **subscribed podcast feed** is a local lookup by `sourceId`;
- anything else is resolved through the engine, which now reports the uploader's own
  page (`MediaMetadata.uploaderUrl` ← yt-dlp's `channel_url` / `uploader_url`). So
  no channel handle had to be plumbed through every InnerTube parser, and the cost
  is one extract on tap instead of on every feed row.

Wired as `MediaItemActions.goToSource(item) { source -> … }`, surfaced as one more
`SheetAction` in `MediaItemRow` ("Go to channel" / "Go to podcast" — the host passes
the label since it knows its pillar), on both the Videos and Podcasts feeds.

**Load-bearing detail:** `DefaultSourceLocator` ids a channel by
`SourceId(channelUrl)`, which is exactly what `AccountSubscriptions` already does —
so a located channel matches the subscription list and the channel page's
subscribe/unsubscribe state is correct. Verified on-device: long-press an
account-feed video → Go to channel → the right channel's page with its tabs,
uploads, and **Unsubscribe** (not a false "Subscribe").
