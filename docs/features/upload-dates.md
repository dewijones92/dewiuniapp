---
title: Upload dates everywhere
kind: feature
status: in-progress
area: video/search
updated: 2026-07-24
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
