---
title: Testing
kind: reference
updated: 2026-08-01
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
| ViewModels, queue | JVM unit | `:app` |

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
