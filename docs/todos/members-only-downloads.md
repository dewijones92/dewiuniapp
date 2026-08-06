---
title: Download what yt-dlp is refused
kind: todo
area: downloads
priority: high
status: shipped — the path works and is proven on a device; the members-only case needs Dewi's phone
updated: 2026-08-06
---

# Download what yt-dlp is refused

The other half of Dewi's 2026-08-06 report. He put the phone in airplane mode, tried to play a
Novara episode, and nothing happened. The routing bug that caused it is fixed
([downloaded-video-not-played-offline](downloaded-video-not-played-offline.md)) — but three items in
that same queue can never be offline at all, and if the one he tapped was one of them, the fix does
not reach it.

## The evidence

From report 0.1.346, logged once each and never retried:

| Item | What YouTube said |
|---|---|
| `AD FREE \| Education, Education, (Technical) Education` | "This video is available to this channel's members" |
| `Britain's Ticking Time Bomb \| Episode 2` | same |
| `AD FREE \| Why Trump Could Be About To Invade Iran` | "Join this channel to get access to…" |

Novara's members-only uploads. The asymmetry is the whole story: **playback is authenticated,
downloading is not.** Playback goes through the app's own InnerTube client with the account's tokens
(the age-restricted work); downloading goes through yt-dlp, which has no account — it removed OAuth
login, Google blocks WebView logins, and handing it the app's TV-client tokens is not a thing it
supports. So YouTube serves the app and refuses the downloader, correctly.

## Why the obvious fix does not work

Not "give yt-dlp the cookies". The app has no cookies: it holds TV device-code OAuth tokens for
InnerTube, which is a different mechanism entirely.

And not "download the URLs the authenticated `/player` call returns" either. Measured 2026-07-31: a
plain ranged GET of an ANDROID-client stream URL serves its **first megabyte** and then 403s forever,
at every offset. Those URLs are excellent metadata and useless for bytes. Anything past the first
megabyte is behind SABR.

## The design, which the app already has most of

SABR is shipped and proven: `SabrResolve` registers a session from a `/player` response,
`SabrSessions` hands out `sabr://<itag>` URLs, `SabrDataSource` serves the bytes, and
`SabrPlaybackTest` plays a real YouTube video through it on a device via CI's residential egress.

A download is then a `DataSource` read into a file:

- **`SabrDownloadStrategy`** — opens the audio itag's `sabr://` stream and copies it to the target.
  Audio only to begin with, because that is what the queue's automatic fetch wants and it needs no
  muxing. A full video means two streams and an ffmpeg mux, which the bundled binary can do (remux
  only) and which can come second.
- **`FallbackDownloadStrategy(primary, secondary, shouldFallBack)`** — mirrors the existing
  `FallbackSearchSource`. yt-dlp stays the primary path, because it handles everything else and
  produces SponsorBlock-cut files; the SABR path is tried only when the failure is one an account
  would fix (`isPermanent` for a members-only or age reason). Nothing else changes behaviour.
- Wired in `AppContainer`, the only place pillar routing lives, reusing the same authenticated
  player call playback already uses.

## Shipped (2026-08-06)

- `FallbackDownloadStrategy` — yt-dlp first, and on a **permanent** refusal only, the app's own path.
- `PlayerBackedDownloadStrategy` — resolves through the same `VideoResolver` playback uses, then
  copies the audio: over SABR when the resolution produced a SABR stream, by plain GET otherwise.
- `sabrStreamFor` in `:core:playback` — one function turning a marked endpoint URL into a stream,
  shared by playback and downloads so they cannot disagree about what a URL means.

**Proven on the emulator, against live YouTube** (`LiveSabrDownloadTest`, and in CI through the
residential tunnel): `over SABR (309288 bytes expected)` → three fetches → `ended at 309288 —
309288B of 309288B (100%)` → radios off → played from the file.

### Two defects the test caught in the first attempt, both silent

**A SABR URL is not a scheme.** `SabrSessions.uriFor` returns the real endpoint with this app's
markers appended (`__totum_video=`, `__totum_itag=`), so a `startsWith("sabr://")` test says no to
every real one — the `sabr://itag` form only exists inside one older test. The check sent gated
videos down the plain-HTTP branch, which GET the SABR endpoint and wrote its 2KB refusal to disk.
The strategy now asks `sabrStreamFor`, the same call playback makes.

**A file that exists is not a file that plays.** The first version treated any non-empty result as a
download, so that 2KB refusal was recorded as `Downloaded` — an item that looked offline and played
nothing. A fetch that ends below 95% of the length the format itself declared is now a failure, and
gets retried like one.

## What can and cannot be proven

- **The mechanism** — SABR bytes to a file, then played offline — is testable in CI on a public
  video through `tools/ci/live-test-via-home.sh`, the same rig `SabrPlaybackTest` uses.
- **The members-only case itself cannot be**, by anyone but Dewi: it needs his account's membership.
  So the test proves the path works and his phone proves it reaches the videos he cares about. Say
  which is which rather than implying the second.
