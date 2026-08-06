---
title: Do torrents go through the unified play route too?
kind: todo
area: torrent
priority: medium
status: partly answered — routing yes and unit-tested; downloading a torrent for offline is UNVERIFIED
updated: 2026-08-06
---

# Do torrents go through the unified play route too?

Dewi, 2026-08-06, on the back of the offline-video fix: *"later … check that torrents work this
way too????? unified approach????"*.

## Already true, and tested

A torrent is a `PlayHandle.Podcast(localPath, audioUrl)` — the home server serves plain HTTP with
range support — so it goes through **the same `routeNow`** as everything else, with no torrent
knowledge anywhere above it. Three unit tests in `PlayRouteTest` pin the torrent-specific
behaviour of that one decision:

- listening, it takes the remuxed **audio-only** URL (2.1 MB/min against 15.2);
- watching, it streams the file with its picture;
- a copy on disk beats **both** — including the cheap audio-only stream.

So the routing question is answered: yes, unified, and a regression fails a test.

## What is NOT verified — the actual gap

**Whether a torrent can be downloaded for offline at all.** Nothing has ever tested it end to end.
On paper it should work: `PlayableItem.fetchUrl` gives the TorrServer stream URL,
`RoutedDownloadStrategy` routes by `PlayHandle.pillar` → `Podcast` → `HttpDownloadStrategy` → a
plain ranged GET. Three specific doubts, each of which would make it fail in a different way:

1. **TorrServer serves as fast as pieces arrive**, so a straight GET of a 1.7GB film is bounded by
   the swarm, not the link. `SETTLE_TIMEOUT_MS` is 10 minutes; a slow torrent would be abandoned
   half-fetched and the queue would move on.
2. **The auto-downloader asks for `audioOnly = true`**, which `HttpDownloadStrategy` ignores
   ("a podcast enclosure is the audio") — so a queued torrent would fetch the WHOLE FILE, video and
   all, over the home upstream. That is the opposite of what Listen mode measured as the win, and
   is the most likely real defect here.
3. **The `audioUrl` is a remux behind ~25s of ffmpeg** on the Pi, and it is an **HLS** playlist —
   which a single GET cannot store as one playable file at all.

## What to do

- Check (2) first: it is the one that would quietly burn data and disk. If confirmed, the fix is
  for `routeNow`'s sibling — the download side — to know that an audio-only request for a torrent
  means "fetch the remuxed audio", not "fetch everything".
- Then an instrumented test in the shape of `OfflineQueuePlaybackTest`, against a local server
  standing in for TorrServer, so the answer stops being on paper.
- The Pi side is up and proven (`public-domain-film-tv.md`), so this is testable today.
