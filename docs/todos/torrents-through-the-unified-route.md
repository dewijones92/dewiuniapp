---
title: Do torrents go through the unified play route too?
kind: todo
area: torrent
priority: medium
status: answered and fixed — routing + offline proven in CI; the audioOnly gap closed by not auto-fetching films
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

## Now proven in CI (2026-08-06)

`TorrentQueuePlaybackTest` (instrumented, every commit) drives the real `HttpHomeTorrentServer`,
`TorrentSearchSource`, queue and player against a stand-in that speaks Prowlarr's and TorrServer's
protocols — copyright-free media, public-domain titles, no swarm:

- **search → prepare → queue → play** streams from the home server through the one unified route:
  `route torrent:… -> streaming it [handle=Podcast copy=none offline=false listen=false]`.
- **downloaded → radios off → plays from the file**: it works, which was doubt (1) below and the
  thing nobody had ever checked. `route torrent:… -> the downloaded audio at /data/… [copy=full
  offline=true]`.

## The gap that was real, found by that test, and now fixed

**Doubt (2) was CONFIRMED.** The download was requested with `audioOnly = true` and the trail
recorded `copy=full`: `HttpDownloadStrategy` ignores the flag ("a podcast enclosure is the audio"),
which is true of a podcast and false of a torrent. So a queued torrent fetched the **whole file,
video included** — the opposite of the 8x saving Listen mode measured, and a fast way to fill a
phone with films nobody asked to store.

**Fixed by not pretending.** The automatic path exists to make the queue *listenable*, so it now
fetches only what has an audio-only form: `PlayableItem.hasAudioOnlyFetch` (one definition, in
`:core:data` beside `TorrentPlayables`) is false for a torrent, the downloader skips it with a logged
reason, and a deliberate tap on Download still fetches the whole thing.

The alternative — teaching the download side to store the server's HLS audio — is a real option and
a bigger one: it means fetching segments and remuxing them locally (the bundled ffmpeg cannot fetch,
by design). Worth doing if Dewi wants films listenable offline without storing the video; not worth
guessing at.

**And the banner does not lie about it.** `OfflineReadiness` counts these apart as `notAutomatic`,
because they are neither *waiting* (nothing is coming) nor *unavailable* (a tap works). The queue
says `3 ready offline · 2 to download by hand`.

## Still unverified

- **Listen mode's remuxed audio over the wire** (`/ts/audio/…/index.m3u8`). It is real HLS, and a
  stand-in cannot serve a valid playlist plus segments without shipping media this repo will not
  carry, so the CI tests watch rather than listen. URL construction and warm-up are unit-tested;
  the stream itself is proven only against the real Pi.
- The three original doubts about the real server, which a stand-in cannot answer:

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

- **Fix the confirmed audioOnly gap** — the download side of the seam should route an audio-only
  request for a torrent to the server's remuxed audio. Dewi's call on priority: it costs data and
  disk today but breaks nothing.
- A real-server check of the three doubts above, once `torrent-zero-config` makes an unattended
  sign-in possible; that is also what would let a live CI test reach the Pi at all.

## The CI-only flake, still open (2026-08-07)

`TorrentQueuePlaybackTest > a torrent found by search plays through the same queue as everything
else` has now failed **three CI runs in five**, always as *"the torrent never started playing"*, and
always on commits that cannot have affected it (test files, docs, an unrelated database column). It
has never failed locally, including five consecutive runs on the emulator here.

**Not diagnosed, and deliberately not guessed at.** The 60-second timeout rules out simple slowness.
The plausible causes have opposite fixes — audio focus refused to a backgrounded instrumentation app,
the stand-in socket not accepting in time, a player error swallowed, or state left behind by whichever
test ran before it — and the assertion said nothing that could tell them apart.

So the first step taken was instrumentation, not a cure: the failure now reports the player's state,
`playWhenReady`, any error code, the current item, the source URL, the queue length and the last
twelve breadcrumbs. The next CI failure should say which of those it is.

Worth noting the pattern this sits in: two other CI-only failures this week were read as flakes and
were not — a drag test reporting "1 move" was a real one-place-only bug, and an intermittent metadata
test was a real race in how the view count crossed the session. **The prior on "CI-only" here is
currently a real defect, not noise.**
