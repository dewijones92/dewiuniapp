---
title: Explore channel content (InnerTube tabs)
kind: feature
status: planned
area: channel
updated: 2026-07-24
---

# Explore channel content

Rebuild the channel page with tabs — **Videos / Shorts / Playlists** — fetched
via InnerTube. Videos gain "x days ago" upload dates automatically (fixing part
of [upload-dates.md](upload-dates.md)). Chosen over the yt-dlp path because
InnerTube returns dates for free and a richer tabbed structure.

Today the channel page (`ChannelScreen`/`ChannelViewModel`) shows a single flat
`fetchChannelVideos` (yt-dlp) list with **no dates** and no Shorts/Playlists.

## InnerTube shape (reverse-engineered live, 2026-07-24)

Public channel content — use the **WEB client, unauthenticated** (works signed
out; channel content is public). Endpoint: `POST youtubei/v1/browse` with
`{"context":{"client":{"clientName":"WEB","clientVersion":"…"}},"browseId":"<UCID>","params":"<tab>"}`.

`browseId` is the channel's `UC…` id. The tab `params` are **stable across all
channels**:

| Tab | `params` | Item renderer |
|---|---|---|
| Videos | `EgZ2aWRlb3PyBgQKAjoA` | `lockupViewModel` (`LOCKUP_CONTENT_TYPE_VIDEO`) |
| Shorts | `EgZzaG9ydHPyBgUKA5oBAA==` | `shortsLockupViewModel` |
| Playlists | `EglwbGF5bGlzdHPyBgQKAkIA` | `lockupViewModel` (`LOCKUP_CONTENT_TYPE_PLAYLIST`) |

Path to items: `contents.twoColumnBrowseResultsRenderer.tabs[selected].tabRenderer.content`
→ Videos/Shorts: `richGridRenderer.contents[].richItemRenderer.content.<renderer>`;
Playlists: `sectionListRenderer.contents[].itemSectionRenderer.contents[].gridRenderer.items[].lockupViewModel`.

### Videos — reuse `RelatedVideosParser`

Same `lockupViewModel` shape that `RelatedVideosParser` already parses:
`contentId` = videoId, `metadata.lockupMetadataViewModel.title.content`,
`metadataRows` → `["3.3M views", "2 days ago"]` (last part = **publishedText**),
duration from the thumbnail badge overlay. → `FeedVideo` (has `publishedText`).
**Extract the shared lockup→`FeedVideo` collector so both use one copy (DRY).**

### Shorts — new parser for `shortsLockupViewModel`

- videoId: `onTap.innertubeCommand.reelWatchEndpoint.videoId`
- title: `overlayMetadata.primaryText.content`
- views: `overlayMetadata.secondaryText.content` (no date on shorts)
- → `FeedVideo` with `Kind.SHORT`.

### Playlists — new parser + reuse `Playlist` model

`lockupViewModel` `LOCKUP_CONTENT_TYPE_PLAYLIST`: `contentId` = playlistId,
title from `lockupMetadataViewModel`. Reuse `:lib:innertube` `playlists/Playlist.kt`
+ the existing `PlaylistScreen`.

## Plan

1. `:lib:innertube` `YouTubeChannel` port + `HttpYouTubeChannel` (browse each tab).
   Add `InnerTubeClient.browseWeb(browseId, params)` (WEB, no auth).
2. Parsers: shared lockup collector (Videos), `ChannelShortsParser`, `ChannelPlaylistsParser`. Fakes.
3. Resolve the channel to a `UC…` id (subscribed channels carry it in `channelUrl`;
   pasted `@handle` URLs need resolving — keep yt-dlp `fetchChannel` for that, or
   an InnerTube `navigation/resolve_url`). **Decide + note here.**
4. `ChannelViewModel` loads the three tabs (map `FeedVideo` → `MediaItem`);
   `ChannelScreen` gets a `TabRow`.
5. Live/on-device verify each tab; tests for each parser against a captured fixture.

## Risks

- InnerTube shapes drift; parsers must be null-tolerant (kotlinx JSON present-null
  gotcha — use `as? JsonArray`, never `?.jsonArray`).
- `@handle` → `UC…` resolution path.
- Shorts thumbnails came back empty in one probe — confirm the thumbnail source.
