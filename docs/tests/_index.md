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
