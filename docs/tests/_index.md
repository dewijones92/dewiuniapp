---
title: Testing
kind: reference
updated: 2026-08-06
---

# Testing

Testing pyramid: many fast JVM unit tests, fewer integration, few
instrumented/UI. **New behaviour lands with tests.** ~50 unit-test files, ~5
instrumented.

## The gate (matches CI)

```bash
./gradlew detekt lint test koverVerify        # full local gate
./gradlew connectedDebugAndroidTest           # instrumented (device/emulator)
```

`koverVerify` covers `:core:*` / `:lib:*` **except** the kover-exempt adapters
`:core:database`, `:core:playback`, `:lib:ytdlp-chaquopy` (instrumented-verified
instead).

## Where coverage lives

| Area | Kind | Notes |
|---|---|---|
| RSS parse, chapters, import/export | JVM unit | `:core:data` — the untrusted-input hot spot |
| Search (sources, history), content refresher | JVM unit | `:core:data` |
| Local playlists, play history | JVM unit | `:core:data` (in-memory store contracts) |
| Downloads (routed/engine/http strategies) | JVM unit | `:core:data` |
| Pillar inference, `fetchUrl`, `DownloadedMedia.offline` | JVM unit | `:core:domain` — the rules that used to exist twice |
| Download-record migration (v13→v14 backfill + table shape) | instrumented | `:core:database` — real files must survive it |
| InnerTube parsers (feeds/related/comments/search/…) | JVM unit | `:lib:innertube`, against captured fixtures |
| yt-dlp `BridgeJson` | JVM unit | `:lib:ytdlp-chaquopy` |
| Room DAOs / stores | instrumented | `:core:database` |
| `Media3PlaybackController` / service | instrumented + on-device | `:core:playback` |
| Ranged fetch arithmetic + stopping rule (`ChunkedRead`) | JVM unit | `:core:playback` — 18 cases; the class every stream flows through, previously untested |
| `ChunkedDataSource` over a googlevideo-shaped stand-in | instrumented | `:core:playback` — no network; resumed reads, past-the-end ranges, truncated resources |
| An item resumed near its end reaches its end | instrumented | `:app` `StreamPlaysToItsEndTest` — real player over a localhost ranged server |
| The same against a real YouTube stream | instrumented, live | `:app` `LiveStreamPlaysToItsEndTest` — via `tools/ci/live-test-via-home.sh`, allowed to skip. **Neither of these reproduces the reported stall** — see below |
| ViewModels, queue | JVM unit | `:app` |
| The line under every video title (`author · views · date`) | JVM unit | `:app` `MediaItemSubtitleTest` — testable at all only because `@Composable` came off the formatter |
| What a resolution may change about an item | JVM unit | `:core:domain` `WithStreamFromTest` — the rule that stops views/dates being destroyed at play time |
| Views + dates on a **page-2** feed video | JVM unit | `:app` `VideosPagingTest` — where "scrolled down" can actually break |
| Views + dates crossing the media session | instrumented | `:app` `PlayerMetadataTest` — extras written but never read compile fine and deliver nothing |
| Views + dates on a row 60 deep, the last row, and one scrolled back into view | instrumented | `:app` `ScrolledRowMetadataTest` |
| A queue drag of ten places in one motion | instrumented | `:app` `ReorderAutoScrollTest` — the count AND the final position |

## Verification reflexes (learned the hard way)

- **Verify on a device, not just the JVM.** The podcast RSS bug (Android's Expat
  parser rejecting `DocumentBuilder` bean toggles) passed every JVM test and only
  surfaced on the emulator. Same for the Cast crash (only when the full player
  opened) and the queue being inert in the mini player.
- **Check the source of truth**, not the surface: read the DB / prefs / session
  state (`dumpsys media_session`, SharedPrefs) after driving the UI.
- **kotlinx JSON present-null gotcha:** `obj["k"]?.jsonArray` throws on a JSON
  `null`; always `(obj["k"] as? JsonArray)`. Cover parser paths with a
  null/missing-key fixture.

## Adversarial audit

The whole codebase can be swept with a fan-out audit workflow
(find → multi-lens verify → synthesize). The targeted version has repeatedly
found real HIGH bugs; keep it in the toolkit for pre-release hardening.

## The core loop, on a device (2026-08-01)

`app/src/androidTest/…/playback/AutoAdvanceLoopTest` is the only test that exercises invariant
I1 — *when an item finishes, the next one starts* — with a real `ExoPlayer` reaching the real
end of real media. It exists because all three autoplay bugs of the previous week passed the
JVM suite: each component was correct against its fake, and the composition was not.

It plays a one-second silent WAV, generated at run time rather than committed, through the app's
own container and the `PlayHandle.Podcast` route — a local file handed straight to the
controller, so a failure is a failure of the loop and not of YouTube. Two cases: an item
finishing starts the next, and an item played a SECOND time advances again (report 0.1.258).

Run it with:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.dewijones92.totum.playback.AutoAdvanceLoopTest
```

**The screen must be on and unlocked.** Android denies audio focus to a background app, and a
suppressed player never ends, so the test would be measuring nothing. It says so when it fails.

## Live-YouTube tests run through home broadband (2026-08-01)

`SabrPlaybackTest` talks to the live service, and a GitHub runner is a datacentre IP that gets
bot-checked — its first CI run came back `Unplayable`. It now runs through a WireGuard peer on
Dewi's Pi (dot-files `vpn-stack`, `wg-home`), so YouTube sees a residential IP, driven by
[`tools/ci/live-test-via-home.sh`](../../tools/ci/live-test-via-home.sh).

Three things about it are deliberate:

- **The peer can only reach the internet.** `wg-home` clients normally get full LAN access, and
  this key lives in a public repo's secrets. The Pi firewalls it
  (`vpn-stack/wg-home-init/10-ci-peer-lockdown.sh`), and the script **asserts** both that egress
  is the expected residential IP and that two LAN hosts are unreachable — failing the build if
  the lockdown ever stops holding, rather than proceeding quietly.
- **Neither the IP nor the key is ever printed.** This repo is public, so its logs are; the
  expected IP is itself a secret and only a verdict is logged.
- **It says whether it RAN or skipped.** "Finished 1 tests" and "BUILD SUCCESSFUL" look
  identical either way, so the one question the tunnel exists to answer was unanswerable from
  its own output until the script read the result XML and said which.

It runs inside the emulator action's `script`, because that action kills the emulator the moment
the script returns — and as a file, because it rewrites backslash line-continuations inside
`script:` (its own log shows `sh -c \yes | sdkmanager`), which turned a wrapped gradle command
into a task named backslash.

## Test the wiring, not just the part (2026-08-01)

The recovery fix shipped with two tests of `VideoResolver.forget` — and neither covered the bug.
Removing the one line in `PlaybackQueue.replayCurrent` that calls it leaves both green while the
defect returns in full. Demonstrated, not argued: with the fix reverted, 39 tests ran and exactly
one failed, and it was the new one.

This is the same shape that let three autoplay bugs ship in a week. A component tested against a
fake proves the component; it says nothing about whether anything calls it, and "nothing calls
it" is the more common defect. **When a fix is one line of wiring, the test belongs where the two
pieces meet** — here, `PlaybackQueueTest`, counting real extractions through a real resolver.

The habit that catches it: after writing a regression test, delete the fix and watch the test go
red. If it stays green it is testing something else, however true that something is.

## Every flow that matters has an e2e in CI (2026-08-06)

Dewi: *"make sure you have e2e of all these flows (copyright free stuff for torrents ofcourse) in
the ci/cd please"*. Not a nicety — this app is used on a phone that is not in front of anyone, so a
flow with no e2e is a flow whose next regression is found by Dewi on a plane.

| Flow | Test | Runs |
|---|---|---|
| Downloaded podcast plays offline | `OfflineQueuePlaybackTest` | every commit |
| Downloaded **video** plays offline | `OfflineQueuePlaybackTest` | every commit |
| Offline, the queue skips what it cannot play and reaches what it can | `OfflineQueuePlaybackTest` | every commit |
| Torrent: search → prepare → queue → stream from the home server | `TorrentQueuePlaybackTest` | every commit |
| Torrent: downloaded → radios off → plays from the file | `TorrentQueuePlaybackTest` | every commit |
| Preload nominated, and released once it plays | `PreloadCommandReachesServiceTest` | every commit |
| Real yt-dlp download → radios off → plays from that file | `LiveDownloadedVideoOfflineTest` | residential-egress tunnel |
| The app fetching a stream ITSELF (SABR) → radios off → plays from that file | `LiveSabrDownloadTest` | residential-egress tunnel |
| SABR playback against live YouTube | `SabrPlaybackTest` | residential-egress tunnel |
| Auto-advance, stall recovery, metered switch, silence strategy | `AutoAdvanceLoopTest`, `StalledStreamRecoveryTest`, `MeteredAudioSwitchDeviceTest`, `SilenceStrategyDeviceTest` | every commit |

**Nothing copyrighted is ever fetched.** The torrent tests use a stand-in that speaks Prowlarr's and
TorrServer's protocols, media generated by this repo (a silent WAV, or `clip.mp4` — 90s of black
H.264 made with ffmpeg), and titles naming genuinely public-domain films. No magnet is resolved, no
peer is contacted, no swarm exists.

**Two things are deliberately NOT covered, and are named rather than left looking covered:** Listen
mode's remuxed torrent audio (real HLS, which a stand-in cannot serve honestly), and anything about
the real Pi — CI's tunnel peer is firewalled to internet-only egress and the home services are behind
a Google login the app cannot complete unattended yet.

## A stand-in must BEHAVE, not merely claim (2026-08-06)

`TorrentQueuePlaybackTest`'s fake home server advertised `Accept-Ranges: bytes` and then ignored
every `Range` header, answering 200 with the whole file. It passed locally — the player opens at zero
and never notices — and **failed in CI**, where the slower emulator rebuffered, asked for a range,
got the whole file with a 200, and never started playing. A flake that passes here and fails there is
the worst kind, because the natural response is to re-run it.

The lesson is not "raise the timeout" (that was also needed: CI's emulator is far slower, so 60s):
it is that a stand-in claiming a capability has to implement it, or it tests the app against a server
that does not exist. It now serves real 206 responses with a `Content-Range`.

## A passing e2e is not the same as a reproduction (2026-08-06)

Reverting the fix and watching the test fail is the only thing that tells you what a test covers, and
on this fix it overturned the diagnosis.

`StreamPlaysToItsEndTest` (generated WAV over localhost) and `LiveStreamPlaysToItsEndTest` (a real
YouTube stream) both play an item resumed seconds from its end through the real queue, session,
service and player — the whole flow behind Dewi's *"buffers towards the end of the video"*. **Both
pass with the fixed defects deliberately reinstated.** The WAV result had an explanation that
predicted the live one would fail; it did not, so the explanation was wrong as well.

What *does* fail without the fix, at two levels each: `ChunkedReadTest` (6 of 18 cases) and
`ChunkedDataSourceTest` (2 of 6 on the arithmetic, plus the read-cap assertion tripping on the
infinite loop). Those are the tests that prove something.

The lesson is not "write more e2e". It is that **an e2e passing either side of a change tells you
nothing about the change**, and reporting it as coverage would have shipped a wrong root cause with a
green tick beside it. Full write-up: `../todos/stalls-near-the-end-of-an-item.md`.

Full write-up: `../todos/stalls-near-the-end-of-an-item.md`.

## A test whose name is broader than its coverage hides the gap (2026-08-06)

`OfflineSkipsUnavailableTest` had *"offline, a video is declined without resolving"* — true, and
the reason the missing case was never noticed: read as a rule, it says videos cannot play offline,
which is what the code did and precisely the bug. There was **no** test for a video that HAD been
downloaded, and the whole offline feature is about downloaded things.

So: **name a test for the case it actually covers**, and when a rule has an obvious other half,
write both halves or neither. The renamed one now says *"a video **with no copy**"*, and the four
tests beside it cover the copy. Nine tests across two tiers go red if the fix is reverted.

Where the tiers landed for that fix, as a worked example of the pyramid:

| Tier | Count | What only this tier can prove |
|---|---|---|
| Unit (`:core:domain` `PlayRouteTest`) | 17 | every combination of pillar × copy variant × offline × Listen, instantly |
| Integration (`app` `OfflineSkipsUnavailableTest`) | 11 | the queue really consults the store and really advances past what it cannot play |
| Instrumented (`OfflineQueuePlaybackTest`) | 3 | radios genuinely off, real Room, real Media3, on a device |
| Instrumented + live (`LiveDownloadedVideoOfflineTest`) | 1 | yt-dlp fetching a real YouTube video, then playing that file offline |

The live one runs only through the residential-egress tunnel, which is allowed to skip — so it
adds proof but can never be the only proof. Both, not either.
