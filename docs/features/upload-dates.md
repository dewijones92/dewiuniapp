---
title: Upload dates everywhere
kind: feature
status: shipped
area: video/search
updated: 2026-07-25
---

# Upload dates everywhere

Show the video upload date consistently across every list.

## Where it stands today

`mediaItemSubtitle` renders `author · date · duration`, using `publishedText`
(YouTube's relative "2 days ago") or a formatted `publishedAt` (podcasts).

| Surface | Has date? | Source |
|---|---|---|
| Podcast episodes | ✅ | `RssParser` `publishedAt` |
| Subscriptions / feeds / Watch Later / History | ✅ | `VideoTileParser.publishedLine()` |
| Related videos | ✅ | `RelatedVideosParser` |
| **Channel videos** | ❌ | `DefaultChannelRepository` maps `publishedAt = null` (yt-dlp `VideoSearchEntry` has no date field) |
| **Search results** | ❌ | `YtDlpVideoSearchSource`; `VideoSearchEntry` has no date field |

## Plan

- **Channel dates**: come free once channel browse moves to InnerTube — see
  [channel-browse.md](channel-browse.md). Videos-tab `lockupViewModel` carries
  `publishedText`.
- **Search dates (best-effort)**: add a date field to `:lib:ytdlp`
  `VideoSearchEntry` and extract `upload_date`/relative text in the Chaquopy
  bridge where yt-dlp provides it; surface in `SearchHit.Video`'s subtitle.
  yt-dlp flat `ytsearch` results often omit a cheap date, so this is best-effort
  and may be blank — do NOT pay for per-result full extraction.

## Note

The unified subtitle (`mediaItemSubtitle` / search subtitle) is already the one
place dates render — this is about feeding it data at the two sources that lack
it, not a second rendering path.

## Status 2026-07-24

Channel dates: **shipped** (channel browse now uses InnerTube — see channel-browse.md).
Search-result dates: **deferred**. The yt-dlp search path uses `extract_flat`, whose
entries don't reliably carry an upload date, and per-result full extraction is
explicitly out of scope (too slow). Every other surface (feeds, subscriptions,
related, channel, podcasts) shows dates; search is the one gap, limited by yt-dlp.
Revisit only if a cheap date source appears in flat search results.

## Shipped 2026-07-25 — search dates too, via a source we already owned

The deferral assumed yt-dlp was the only video-search backend. It isn't: the app
owns an InnerTube client, and a live probe of `youtubei/v1/search` (WEB client, no
auth) showed **every** result carrying `publishedTimeText`, plus duration, views,
channel and thumbnail. So the limitation was the backend, not the data.

- `:lib:innertube` `search/`: `YouTubeSearch` port, `SearchedVideo`,
  `HttpYouTubeSearch`, `SearchResultsParser` (search still answers with the classic
  `videoRenderer` shape, not `lockupViewModel`, so it's its own parser — reusing
  `parseClockToSeconds` and `FeedVideo.watchUrlFor`), fake, and a unit test against
  the captured `search_web_sample.json`.
- `:core:data`: `InnerTubeVideoSearchSource` → `SearchHit.Video.publishedText`, with
  `FallbackSearchSource` keeping yt-dlp's `ytsearch` as the fallback if YouTube's
  shape changes (tested: primary wins, failure and empty both fall back).
- Bonus DRY: the search row had its own subtitle assembly. Both it and
  `mediaItemSubtitle` now go through one `mediaSubtitle(author, dateText,
  durationMinutes)`, so search rows read like every other list.

Verified on-device: "Fireship · 4 years ago · 2 min".

**Every surface now shows dates** — the table above has no ❌ rows left.
