---
title: Torrents (public-domain film & TV)
kind: feature
status: shipped
area: torrent
updated: 2026-08-06
---

# Torrents (public-domain film & TV)

Search for a film, tap it, watch it. The Pi torrents; the phone is a remote control and a screen.

Dewi asked for classic public-domain films and TV — *The Beverly Hillbillies*, *Dragnet* — findable
and watchable "just like YouTube" (2026-08-01), with **no configuration to type** (2026-08-01:
*"make sure that the torrent ux works out the box … so i dont have to insert any config etc etc"*).

## Why this doc exists

Because the backlog said the opposite. Two todos claimed "app side not started" and "sign-in may
already be shipped, not checked" — and on 2026-08-06 that stale pair sent this session's opening
recommendation to Dewi in the wrong direction: I proposed building what already existed. The code was
read afterwards and the whole path was there. **A status nobody re-checks is worse than no status.**

## What is shipped

| Piece | Where |
|---|---|
| Search across indexers | `TorrentSearchSource` → `HttpHomeTorrentServer.search` (Prowlarr), best-seeded first |
| Add a magnet, wait for metadata | `prepare` — polls for the file list, because asking once returns nothing |
| Season packs as episodes | `TorrentEpisodes` + `TorrentPlayables.queueItems` — one queue item per playable file |
| Playback | **no new playback code**: a TorrServer stream is an ordinary ranged HTTP URL, so `MediaItem.mediaUrl` and the one `PlaybackController` take it unchanged |
| Listen mode | the server's remuxed audio (HLS), 2.1 MB/min against 15.2 measured |
| Offline | the same `routeNow` decision as every other pillar; a downloaded copy wins over the stream |
| Zero config | the host is baked in at build time from `TOTUM_HOME_SERVER` (a CI secret, never committed); signing in is one tap, and the token and Prowlarr key arrive together on a `totum://auth` deep link |

Nothing above the `HomeTorrentServer` port knows a torrent is involved. That is the point: the
pillar reaches the UI as one more `SearchHit`, one more queue item, one more thing that plays.

## What it deliberately does NOT do

- **No torrent client on the phone.** Torrenting is mostly UDP (µTP, DHT, UDP trackers) and a
  SOCKS5 proxy handles UDP badly, so an in-app client behind a proxy would have had few peers and
  magnets that barely resolve. The Pi does it inside gluetun behind PureVPN's kill-switch instead,
  and the phone↔Pi channel is plain HTTP — which is exactly what the existing Google gate can
  protect. Full reasoning, including two reversed decisions, in
  [`../todos/public-domain-film-tv.md`](../todos/public-domain-film-tv.md).
- **No automatic download of films.** The queue's automatic fetch exists to make the queue
  listenable and a torrent has no audio-only form to fetch (the server's audio is a live HLS
  playlist), so an `audioOnly = true` request quietly fetched the whole film — proven on a device
  2026-08-06, `copy=full`. Films are left for a deliberate tap and the queue banner says
  `2 to download by hand` rather than promising a fetch that is never coming.
- **No transcoding.** The Pi uploads at 65 Mbps measured, six times the headroom a 1080p x264 rip
  needs, so search is biased toward phone-safe releases instead. The real gap is codecs, not
  bandwidth: DTS and TrueHD cannot be decoded by Android at all.

## Proven where

- **In CI, every commit** — `TorrentQueuePlaybackTest`: search → prepare → queue → stream, and
  downloaded → radios off → plays from the file. Against a stand-in speaking Prowlarr's and
  TorrServer's protocols, with media this repo generates and public-domain titles. No magnet is
  resolved and no peer is contacted.
- **On the real Pi, by hand** (2026-08-01) — a 206 range request halfway into a 1.74GB film served
  in 3.3s against 3.6s at the start; torrent traffic asserted to leave via PureVPN, not the home IP;
  the gate refusing unauthenticated requests by content, not status code.
- **Not covered:** Listen mode's HLS audio over the wire (a stand-in cannot serve it honestly), and
  a full phone-to-Pi run (install → sign in → search → tap → watch), which needs the real device and
  is still owed.
