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
| [skip-silence-on-video](skip-silence-on-video.md) | playback | medium | open |
| [queue-first-playback](queue-first-playback.md) | playback | high | ready (awaiting go) |
| [auto-download-queue](auto-download-queue.md) | downloads | high | ready (awaiting go) |
| [autoplay-next-guaranteed](autoplay-next-guaranteed.md) | playback | medium | ready (awaiting go) |
| [volume-boost-normalize](volume-boost-normalize.md) | playback | medium | ready (awaiting go) |
| [subtitles-captions](subtitles-captions.md) | playback | medium | refining |
| [audio-video-switching](audio-video-switching.md) | playback | high | ready (awaiting go) |
| [notification-opens-app](notification-opens-app.md) | playback | high | shipped |
| [listen-mode-exit-ux](listen-mode-exit-ux.md) | playback | high | shipped |

All backlog items are Dewi requests. `refining` = spec written, decisions still open;
`ready` = decisions made, implementation waits for Dewi's explicit go (his standing
instruction on the queue/download/autoplay/volume group: refine first, implement on
command).
