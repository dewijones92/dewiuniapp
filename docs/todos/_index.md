---
title: Backlog
kind: index
updated: 2026-07-24
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
| [queue-first-playback](queue-first-playback.md) | playback | high | shipped (drag-reorder outstanding) |
| [auto-download-queue](auto-download-queue.md) | downloads | high | shipped |
| [autoplay-next-guaranteed](autoplay-next-guaranteed.md) | playback | medium | shipped |
| [volume-boost-normalize](volume-boost-normalize.md) | playback | medium | shipped |
| [subtitles-captions](subtitles-captions.md) | playback | medium | refining |
| [youtube-progress-two-way-sync](youtube-progress-two-way-sync.md) | video | medium | refining |
| [ui-polish](ui-polish.md) | ui | medium | refining |
| [rebrand](rebrand.md) | branding | medium | refining (needs a name + applicationId call) |
| [crash-reporting](crash-reporting.md) | infrastructure | high | ready (collect-everything; Pi endpoint to confirm) |
| [row-status-indicators](row-status-indicators.md) | ui | high | refining |
| [high-quality-playback-fix](high-quality-playback-fix.md) | video | high | shipped |
| [feature-gap-review](feature-gap-review.md) | planning | — | triage of the AI review |
| [feed-pagination](feed-pagination.md) | video | high | refining |
| [testing-depth](testing-depth.md) | tests | medium | refining |
| [audio-video-switching](audio-video-switching.md) | playback | high | shipped (local-audio merge outstanding) |
| [notification-opens-app](notification-opens-app.md) | playback | high | shipped |
| [listen-mode-exit-ux](listen-mode-exit-ux.md) | playback | high | shipped |

All backlog items are Dewi requests. `refining` = spec written, decisions still open;
`ready` = decisions made, implementation waits for Dewi's explicit go (his standing
instruction on the queue/download/autoplay/volume group: refine first, implement on
command).
