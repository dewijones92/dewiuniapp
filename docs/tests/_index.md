---
title: Testing
kind: reference
updated: 2026-07-24
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
| InnerTube parsers (feeds/related/comments/…) | JVM unit | `:lib:innertube`, against captured fixtures |
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
