---
title: Architecture — unified seams & modules
kind: reference
updated: 2026-07-24
---

# Architecture

The twin laws (from `CLAUDE.md`): **Unified** — one seam per capability serving
both pillars — and **strictly DRY**. Every feature must be unified across
YouTube/video and podcasts by default; a pillar-specific split is allowed only
for a genuinely strong technical reason, which must be surfaced to Dewi.

## Modules (deps point inward)

| Module | Kind | Holds |
|---|---|---|
| `:core:domain` | pure JVM | `MediaItem`, `MediaSource` (VideoChannel \| PodcastFeed), `SourceId`, `Chapter`, `SkipSegment`, `DownloadState`. `explicitApi()`. No Android. |
| `:core:data` | JVM | Business logic: `RssParser`, `PodcastRepository`, search seam (`SearchSource` → `SearchHit`), `SearchHistoryStore`, downloads (`DownloadManager` + strategies), `SponsorBlockSegmentSource`, `ContentRefresher`/`SeenItemsTracker`, import/export. Ports here, impls in Android modules. |
| `:core:database` | Android/Room | Entities, DAOs, `Room*Store` impls. The only place entities meet domain types. Kover-exempt. |
| `:core:playback` | Android/Media3 | `PlaybackController` port + `Media3PlaybackController`, `PlaybackService`, `SleepTimer`, `PlaybackProgressStore`, `PlaybackSpeedStore`. Kover-exempt adapter. |
| `:lib:ytdlp` | pure JVM | yt-dlp API: `YtDlpEngine` (extract/search/download), result types. Platform-neutral. |
| `:lib:ytdlp-chaquopy` | Android | Real engine on embedded CPython; `BridgeJson` (JSON in/out), wheel self-update. |
| `:lib:innertube` | pure JVM | YouTube InnerTube seams: auth (TV device-code OAuth), feeds, comments, related, actions, playlists, subscriptions, history. `InnerTubeClient`. |
| `:lib:common` | pure JVM | `HttpUrl` — the one validated URL type. |
| `:app` | Android/Compose | UI (`AppShell`, screens), ViewModels, manual DI (`AppContainer`), SharedPrefs impls, video resolve/launch, queue, notifications. |

## The one-seam-per-capability map

| Capability | Seam | Both pillars via |
|---|---|---|
| Media item | `MediaItem` | a podcast episode *is* a video — same type |
| Playback | `PlaybackController` + one `MediaSession` + one mini/full player | anything with `mediaUrl` plays |
| Search | `SearchSource` → sealed `SearchHit` (Podcast \| Video) | iTunes + `ytsearch` adapters |
| Subscriptions freshness | `ContentRefresher` + `SeenItemsTracker` | podcast RSS + YouTube subs adapters |
| Downloads | `DownloadManager` + `RoutedDownloadStrategy` | `EngineDownloadStrategy` (video) / `HttpDownloadStrategy` (enclosure) |
| Skip segments | `SkipSegment` + `skipTargetFor`, enforced in the position ticker | any pillar's playback skips |
| HTTP text | `HttpTextFetcher` port | OkHttp impl |
| URL | `HttpUrl` value type | everywhere |

## DI

Manual: `AppContainer` interface + `DefaultAppContainer` (real) + `FakeAppContainer`
(previews/tests). Construction is code; errors are compile-time. Pillar routing
lives in exactly one place (`AppContainer`, e.g. `RoutedDownloadStrategy`).
