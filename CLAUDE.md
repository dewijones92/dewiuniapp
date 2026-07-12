# UniApp

One Android app that replaces two: **PipePipe** (YouTube-style streaming client) and
**AntennaPod** (podcast manager). Streaming and podcasts are both first-class pillars
sharing one unified domain model — subscriptions, playback queue, downloads, and history
behave identically whether the source is a video channel or an RSS feed.

The original brief lives in `init` at the repo root. Decisions below supersede it where
they conflict (notably: the `ytdlp-kt` fork dependency mentioned in `init` was dropped
in favour of a from-scratch library — see Decisions).

## Decisions (agreed with Dewi, July 2026)

| Decision | Choice | Why |
|---|---|---|
| Scope | Both pillars from day one | Unified data model proven early |
| Stack | Kotlin + Jetpack Compose, single Gradle project | Native fit, strongest type safety |
| minSdk | **34** (Android 14) | Personal modern devices; simplifies stack. Deliberate — drops the API-23 floor of the original apps |
| Extraction | **From-scratch library in this repo** (`:lib:ytdlp`), replacing dewijones92/youtubedl-android fork | Own a clean, tested Kotlin API around the real yt-dlp |
| Python runtime | Official CPython Android build (3.13+), embedded in-process via JNI | Upstream-maintained; no subprocess, no W^X issues |
| ffmpeg | Bundled from day one | Needed for merged best-quality streams and audio extraction |
| CI/CD | GitHub Actions; signed APKs on GitHub Releases | No Play Store (yt-dlp app) |
| UI bar | Genuinely nice, modern | Material 3 expressive, dynamic colour, dark/light, edge-to-edge, considered motion — never template-default |

## Quality bar (from the brief, non-negotiable)

- **Testing pyramid**: many fast unit tests; fewer integration tests; few instrumented/UI
  tests. New behaviour lands with tests.
- **Strictly DRY** — this matters a lot to Dewi. Knowledge lives in exactly one
  place: versions and SDK levels only in `gradle/libs.versions.toml`; Android
  build defaults (compileSdk/minSdk/Java level/lint policy) only in the root
  build's `androidDefaults`; shared code in a shared module (`:lib:common`),
  never copy-pasted. Before writing similar code twice, factor it — and if a
  duplication is ever deliberate, it must be recorded here with its reason.
- **SOLID**: small focused types, dependencies point inward.
- **Maximum compile-time safety** (the brief's "dependent types" translated to Kotlin):
  sealed hierarchies, value classes over primitives, exhaustive `when`, no platform types
  leaking, illegal states unrepresentable.
- CI must stay green; quality gates (lint, static analysis, tests) block merges.

## Build & test

```bash
./gradlew assembleDebug          # build debug APK
./gradlew detekt lint test koverVerify   # the full local gate (matches CI)
./gradlew connectedDebugAndroidTest  # instrumented tests (device/emulator needed)
```

On-device testing matters: the podcast RSS bug (Android's Expat parser rejecting
`DocumentBuilder` bean-property toggles) passed every JVM test and only surfaced
when driven on the emulator. Verify real flows on a device, not just via tests.

- JDK 21 lives at `/home/dewi/code/jdk/`; Android SDK at `/home/dewi/code/android-sdk`
  (see `local.properties`, not committed).
- The `android` CLI (`~/.local/bin/android`) is available for emulators, screenshots,
  layout inspection, and docs search.

## Architecture

- `:app` — Compose UI: `AppShell` bottom navigation across the pillars
  (Videos / Podcasts / Library), theme, screens.
- `:core:domain` — pure-Kotlin (JVM) unified media model: `MediaSource`
  (VideoChannel | PodcastFeed), `MediaItem`, `Subscription`, `SourceId`. No
  Android dependency — leakage is a compile error. `explicitApi()` is on.
- `:core:data` — pure-Kotlin (JVM): `RssParser` (hardened DOM), the
  `PodcastRepository` and its `FeedFetcher`/`PodcastStore` ports, OkHttp
  fetcher. Business logic lives here, testably, off Android.
- `:core:database` — Android library (Room via KSP): entities, DAO, and
  `RoomPodcastStore` implementing `:core:data`'s `PodcastStore` port. The only
  place entities meet domain types. Verified by instrumented tests; exempt from
  the Kover JVM gate.
- `:core:playback` — the unified playback seam: `PlaybackController` port +
  `PlaybackState`, implemented by `Media3PlaybackController` connected to a
  `MediaSessionService` (`PlaybackService`, foreground, Media3-managed
  notification). Both pillars play through it — anything with a
  `MediaItem.mediaUrl` is playable. `fake.FakePlaybackController` for
  tests/previews. Kover-exempt adapter (Media3 glue; instrumented-verified).
- Manual DI: `AppContainer` (in `:app`) wires the graph; construction is code,
  errors are compile-time. No Hilt/Koin.
- **Cleartext HTTP is deliberately permitted** (network_security_config):
  podcast enclosures in the wild are frequently plain http (BBC media hosts
  included); refusing them breaks playback of legitimate feeds. Same policy
  as AntennaPod.
- `:lib:ytdlp` — from-scratch yt-dlp Android library (replaces the
  youtubedl-android fork). Public API: `YtDlpEngine` (suspend `extract`,
  cold-`Flow` `download`, sealed `ExtractionResult`/`DownloadEvent`);
  `fake.FakeYtDlpEngine` implements it for tests/previews. Deliberately
  independent of `:core:domain` (standalone, reusable); the app maps between
  their types.
- `:lib:ytdlp-chaquopy` — the real engine: yt-dlp on embedded CPython 3.12
  via Chaquopy 17 (MIT). `uniapp_ytdlp.py` is a thin JSON-in/JSON-out bridge;
  `BridgeJson.kt` parses it (JVM unit-tested); `ChaquopyYtDlpEngine`
  implements the API. Chaquopy constraints: exactly ONE module per app may
  apply the plugin; build-host Python minor version must match the target
  (3.12 here); NOT configuration-cache compatible (config cache disabled in
  gradle.properties because of this). ABIs: arm64-v8a + x86_64. Adds ~80MB
  to the APK (Python runtime per ABI). yt-dlp itself (pure Python) can be
  self-updated at runtime; the interpreter and ffmpeg cannot (Android W^X).
  ffmpeg is not yet bundled — needed later for merged best-quality streams.
- `:lib:common` — pure-Kotlin utility module with no app dependencies, shared
  by app modules and standalone libraries alike (it would be published
  alongside `:lib:ytdlp`, like the old youtubedl-android's `common` module).
  Home of `HttpUrl`, the single validated URL type used everywhere.
- detekt, the Android lint policy, and Android build defaults
  (compileSdk/minSdk/Java level) apply to every module automatically from the
  root build — never configure them per module.
- Package root: `com.dewijones92.uniapp`.

## Working agreements

- Commit as you go — small, coherent commits at each green state.
- Remote: `github.com/dewijones92/dewiuniapp` (private). Push to master is fine;
  CI (GitHub Actions) must stay green.
- Temporary debug logging must be prefixed `dewidebug` and never committed.
