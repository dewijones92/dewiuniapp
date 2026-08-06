---
title: A downloaded video did not play offline
kind: todo
area: playback
priority: high
status: fixed — one routing decision for both pillars, tested at all three tiers
updated: 2026-08-06
---

# A downloaded video did not play offline

Dewi, 2026-08-06: *"i set my phone on airplane mode, tried to play novara (it downloaded it
offline didnt it??????) but it didnt play"*.

It had. The file was on the disk and the app refused to look for it.

## Root cause

`PlaybackQueue.route` had one branch per pillar, and the branches disagreed about where a
playable copy might be:

- the **podcast** branch asked the download store (`handle.localPath ?: downloadedPath(id)`) —
  added 2026-08-03 precisely because a handle is a snapshot from when the item was queued and
  the auto-downloader finishes long afterwards;
- the **video** branch never asked. Offline it logged *"needs the network and there is none —
  skipping it"* and stopped.

Every YouTube queue entry is a `PlayHandle.Video`, and the queue's automatic downloads fetch
**audio only**, so the exact case the feature exists for — a queue of videos, downloaded for a
plane — was the one case that could not work. The unit tests even enshrined it: *"offline, a
video is declined without resolving"*, with no counterpart for a video that had been downloaded.

It cost data twice over, too: online, the same branch re-streamed a video whose audio was
already on the device.

## Fix: one decision, in `:core:domain`

`routeNow` (`PlayRoute.kt`) answers "how do I play this, right now" for both pillars, from three
inputs — any copy on disk, whether there is a network, whether Listen mode is on — and returns
one of four routes (`VideoFile` | `AudioFile` | `VideoStream` | `AudioStream`) or a `Refused`
carrying its reason. `PlaybackQueue.route` is its only caller and does nothing but carry it out,
so there is no longer a per-pillar branch to drift.

The handle-to-disk swap is shared with `DownloadedMedia.offline` via `playedFromDisk`, so the
Library and the queue cannot make different handles out of the same file.

### The one deliberate asymmetry (Dewi's call)

An **audio-only** copy of a video does not stand in while you are *watching* — that would
silently take the picture away — but it does the moment you are listening or offline. Asked
"prefer local always, or only when offline?", the answer was to honour both halves rather than
pick one.

## A second cause, still open

Three items in the same queue can never be downloaded at all, logged permanently:

| Item | What YouTube said |
|---|---|
| `AD FREE \| Education, Education, (Technical) Education` | "This video is available to this channel's members" |
| `Britain's Ticking Time Bomb \| Episode 2` | same |
| `AD FREE \| Why Trump Could Be About To Invade Iran` | "Join this channel to get access to…" |

Those are Novara's members-only uploads. **Playback** is authenticated (the age-restricted work);
**downloading** goes through yt-dlp, which is not, so YouTube refuses. If the episode Dewi tapped
was one of these, this fix does not help it — see [age-restricted-videos](age-restricted-videos.md)
for the authenticated-fetch machinery that would.

## Tests (all three tiers, and each verified to fail without the fix)

| Tier | Where | What it pins |
|---|---|---|
| Unit (17) | `core/domain/.../PlayRouteTest` | the whole decision table: pillar × copy × offline × listen |
| Integration (11) | `app/.../OfflineSkipsUnavailableTest` | the queue plays/refuses/advances correctly through the real `PlaybackQueue` |
| Instrumented | `app/.../OfflineQueuePlaybackTest` | a video-handled entry with a real file and real Room, radios off, on a device |
| Instrumented, live | `app/.../LiveDownloadedVideoOfflineTest` | yt-dlp fetches a real YouTube video's audio, radios off, it plays from that file |

Reverting the rule turns 9 of them red. The live test runs through
`tools/ci/live-test-via-home.sh` (residential egress), which is allowed to skip — which is why
the deterministic instrumented test exists as well rather than instead.

## Diagnostics this exposed

Report 0.1.346 carried the whole 97-item queue and every setting and **could not say whether the
item was downloaded**, so the diagnosis came from reading code rather than from the report. Now
every report carries `downloads.queueReady/Downloading/Waiting/UnavailableOffline`,
`downloads.onDisk`, and a per-item `downloads.queueStates`; and each routing decision logs its
inputs, not just its outcome:

```
route Ui8jZQirfj0 -> the downloaded audio at /data/…/1a2b.media [handle=Video copy=audio-only offline=true listen=true]
```

That distinction — refused with a copy versus refused without one — was the whole diagnosis, and
was previously the same line. See the logging law in `CLAUDE.md`, which Dewi restated on
2026-08-06 as a MUST.
