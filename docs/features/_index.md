---
title: Features
kind: index
updated: 2026-07-24
---

# Features

Status of every feature. `shipped` = on `main` and verified. Detail docs exist
for the larger / in-flight ones; small shipped features are tracked by this row
alone until they need more.

| Feature | Area | Status | Detail |
|---|---|---|---|
| Unified media model + playback (one controller, mini/full player) | playback | shipped | — |
| Podcasts: subscribe, RSS parse, episodes, refresh | podcasts | shipped | — |
| Videos: signed-in feeds (Home/Subscriptions/Watch Later/History) | video | shipped | — |
| YouTube TV device-code OAuth | auth | shipped | — |
| Unified search (iTunes + ytsearch → `SearchHit`) | search | shipped | — |
| Search history (recent queries, idle-state chips) | search | shipped | [search-history.md](search-history.md) |
| Downloads (video merge + SponsorBlock cut / podcast enclosure) | downloads | shipped | — |
| Comments, related, like/dislike, Watch Later, subscribe | video | shipped | — |
| Playlists (account) | video | shipped | — |
| Channel page (subscribe + uploads) | channel | shipped | — |
| Chapters (yt-dlp + Podcasting 2.0 `psc`/remote) + seek-bar markers | playback | shipped | — |
| Playback queue (unified up-next) | playback | shipped | — |
| Shorts reel (full-screen vertical pager) | video | shipped | — |
| Skip-silence (audio-only, A/V-safe) | playback | shipped | — |
| Sleep timer | playback | shipped | — |
| Per-source playback-speed memory | playback | shipped | — |
| New-content notifications (background refresh, both pillars) | notifications | shipped | — |
| Import / export subscriptions (OPML / NewPipe / Takeout) | subscriptions | shipped | — |
| Cast to TV (Chromecast) — best-effort | cast | shipped* | podcast/local only works; video casting fragile |
| **Explore channel content (InnerTube tabs: Videos/Shorts/Playlists)** | channel | **planned** | [channel-browse.md](channel-browse.md) |
| **Upload dates everywhere** | video/search | **planned** | [upload-dates.md](upload-dates.md) |

\* Cast: crash + disconnect-loses-playback fixed; real casting unverified (no hardware).

See [`../todos/`](../todos/_index.md) for smaller requested items not yet features.
