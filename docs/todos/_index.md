---
title: Backlog
kind: index
updated: 2026-08-06
---

# Backlog

Requested items not yet built (or in flight). One file per item. Move a `status`
to `shipped` and migrate it to `../features/` once it's a real feature on `main`.

| Item | Area | Priority | Status |
|---|---|---|---|
| [Explore channel content](../features/channel-browse.md) | channel | high | shipped |
| [Upload dates everywhere](../features/upload-dates.md) | video/search | high | planned |
| [background-audio-listen-mode](background-audio-listen-mode.md) | playback | high | shipped |
| [fullscreen-video-stretch](fullscreen-video-stretch.md) | playback | high | shipped |
| [long-press-context-menu](long-press-context-menu.md) | ui | medium | shipped (all feeds; go-to-channel split out) |
| [go-to-channel-action](go-to-channel-action.md) | ui | medium | shipped |
| [url-share-target](url-share-target.md) | integration | medium | shipped |
| [play-history-screen](play-history-screen.md) | library | medium | shipped |
| [local-cross-pillar-playlists](local-cross-pillar-playlists.md) | library | high | shipped |
| [skip-silence-on-video](skip-silence-on-video.md) | playback | medium | shipped |
| [queue-first-playback](queue-first-playback.md) | playback | high | shipped (incl. drag-reorder) |
| [auto-download-queue](auto-download-queue.md) | downloads | high | shipped |
| [autoplay-next-guaranteed](autoplay-next-guaranteed.md) | playback | medium | shipped |
| [volume-boost-normalize](volume-boost-normalize.md) | playback | medium | shipped |
| [subtitles-captions](subtitles-captions.md) | playback | medium | shipped (video; podcast transcripts open) |
| [youtube-progress-two-way-sync](youtube-progress-two-way-sync.md) | video | medium | refining |
| [ui-polish](ui-polish.md) | ui | medium | quality/speed overlay shipped; wider sweep open |
| [rebrand](rebrand.md) | branding | medium | shipped as **Totum** |
| [crash-reporting](crash-reporting.md) | infrastructure | high | shipped (verbose reports live on the Pi) |
| [row-status-indicators](row-status-indicators.md) | ui | high | shipped (real PlayState behind it) |
| [high-quality-playback-fix](high-quality-playback-fix.md) | video | high | shipped |
| [feature-gap-review](feature-gap-review.md) | planning | — | triage of the AI review |
| [channel-groups](channel-groups.md) | video | high | shipped |
| [watch-history-not-recorded](watch-history-not-recorded.md) | video | high | done — fixed by an authenticated player call carrying a current signatureTimestamp |
| [sabr-streaming](sabr-streaming.md) | video | medium | fallback shipped; QuickJS runtime open |
| [feed-pagination](feed-pagination.md) | video | high | feeds + channel tabs shipped; search pending |
| [queue-drag-reorder](queue-drag-reorder.md) | queue | high | shipped (auto-scroll); pickup + resilience open |
| [public-domain-film-tv](public-domain-film-tv.md) | search | medium | Pi side built and proven; app side not started |
| [age-restricted-videos](age-restricted-videos.md) | video | high | shipped — age-restricted videos play on-device |
| [torrent-zero-config](torrent-zero-config.md) | torrent | high | requested |
| [testing-depth](testing-depth.md) | tests | medium | refining |
| [audio-video-switching](audio-video-switching.md) | playback | high | shipped (local-audio merge outstanding) |
| [notification-opens-app](notification-opens-app.md) | playback | high | shipped |
| [listen-mode-exit-ux](listen-mode-exit-ux.md) | playback | high | shipped |
| [library-downloads-podcast-only](library-downloads-podcast-only.md) | downloads | medium | done |
| [playback-does-not-resume-after-network-loss](playback-does-not-resume-after-network-loss.md) | playback | high | done — StreamRecovery waits for a validated network, then resumes |
| [download-races-playback](download-races-playback.md) | playback | high | done — downloads now yield to playback; the duplicate extraction itself remains |
| [listen-mode-saves-data](listen-mode-saves-data.md) | playback | medium | true for YouTube; torrents measured feasible (8x saving), seeking is the blocker |
| [downloaded-video-not-played-offline](downloaded-video-not-played-offline.md) | playback | high | fixed — one routing decision for both pillars |
| [torrents-through-the-unified-route](torrents-through-the-unified-route.md) | torrent | medium | routing verified; torrent DOWNLOAD for offline unverified |
| [metered-audio-switch](metered-audio-switch.md) | playback | high | shipped — proven with the radios toggled |
| [prefetch-the-next-item](prefetch-the-next-item.md) | playback | medium | done — readiness and byte preload, Wi-Fi only |
| [skip-silence-smoothness](skip-silence-smoothness.md) | playback | high | shipped — sample removal for audio, speed-up for video |
| [offline-queue-e2e](offline-queue-e2e.md) | tests | high | done — in CI, and it found a real bug |
| [buffering-defects-0.1.332](buffering-defects-0.1.332.md) | playback | high | all four fixed with tests |
| [settings-only-change-when-asked](settings-only-change-when-asked.md) | settings | high | done — speed, boost and brightness hold |
| [buffer-ahead-gauge](buffer-ahead-gauge.md) | playback | medium | shipped — seconds-ahead gauge on the scrub bar |

All backlog items are Dewi requests. `refining` = spec written, decisions still open;
`ready` = decisions made, implementation waits for Dewi's explicit go (his standing
instruction on the queue/download/autoplay/volume group: refine first, implement on
command).
