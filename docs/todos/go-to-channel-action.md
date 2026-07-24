---
title: "Go to channel" action in the long-press sheet
kind: todo
status: open
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

Remaining for this item: the channel handle for account-feed videos, and the
`goToSource` action itself. yt-dlp's `extract` already returns the full info dict
(which carries `channel_id` / `channel_url`), so the handle can be resolved on tap
rather than plumbed through every parser — `MediaMetadata` just needs the field.
