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
| Python runtime | Chaquopy 17 (revised from "official CPython" once that proved tier-3/no artifact) | Mature drop-in embed, MIT, pip support |
| ffmpeg | **Bundled** — minimal static build in jniLibs | Merges best-quality DASH streams and removes SponsorBlock from downloads |
| CI/CD | GitHub Actions; signed APKs on GitHub Releases | No Play Store (yt-dlp app) |
| UI bar | Genuinely nice, modern | Material 3 expressive, dynamic colour, dark/light, edge-to-edge, considered motion — never template-default |

## Quality bar (from the brief, non-negotiable)

- **Unified, always** — this and DRY are the project's twin laws. The app has two
  pillars but every capability gets ONE seam that serves both: one domain model
  (`MediaItem` is a podcast episode *and* a video), one playback path
  (`PlaybackController` + one mini player), one search port (`SearchSource` →
  sealed `SearchHit`), one HTTP text port, one URL type. Before building any
  feature, ask "what is the pillar-agnostic seam?" and build that; pillar
  specifics live in small adapters behind it. A feature implemented twice —
  once per pillar — is a design failure even if neither copy shares a line.
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

## Performance (measured 2026-07-12, API-35 emulator)

- **Cold start** ~1.5s warm / ~3.3s first-ever. The embedded Python engine is
  lazy (constructed on first Videos/Search use, never at launch) — confirmed:
  startup pays nothing for it. Keep it that way.
- **Memory**: ~115MB idle; ~196MB once the Python interpreter is resident.
  Reasonable for an embedded CPython; watch it if it climbs.
- **APK**: release is **arm64-v8a only + R8 (minify + resource shrink)** →
  **~33MB**, down from 94MB. Debug stays multi-ABL/unminified for the emulator.
  Build a release for the emulator with `-PemulatorAbis` (adds x86_64).
- R8 verified end-to-end on-device (podcasts, Room, Media3, Chaquopy/Python
  search all survive minification). App keep-rules: `app/proguard-rules.pro`
  (kept minimal — library consumer rules handle Room/Media3/Chaquopy/Compose).

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
  `PodcastRepository` with its `HttpTextFetcher`/`PodcastStore` ports, OkHttp
  fetcher, and the unified search seam: `SearchSource` port returning sealed
  `SearchHit`s (Podcast | Video), implemented by `ItunesPodcastSearchSource`
  (iTunes directory API) and `YtDlpVideoSearchSource` (engine `ytsearch`).
  Also `SkipSegmentSource` (SponsorBlock-backed, fail-open: lookup failure =
  no segments). Business logic lives here, testably, off Android.
- Skip segments (`SkipSegment` + `skipTargetFor` in `:core:domain`) are
  enforced in exactly one place — `Media3PlaybackController`'s position
  ticker — so any pillar's playback skips them.
- **Downloads** (`DownloadManager` port in `:core:data`, `RoomDownloadStore`
  in `:core:database`): one seam, one port. The manager takes a single
  `DownloadStrategy`; `RoutedDownloadStrategy` (wired in `AppContainer`, the
  only place pillar routing lives) picks per item — `EngineDownloadStrategy`
  for video pages (yt-dlp fetches best video+audio and merges via the bundled
  ffmpeg, then cuts SponsorBlock segments), `HttpDownloadStrategy` for podcast
  enclosures (a plain HTTP GET). `DownloadState` in `:core:domain`; playback
  prefers the local file via `play(..., localPath=)` (a downloaded video
  needs no re-resolution); the Library tab lists downloads (shared
  `MediaItemRow`). Interrupted downloads (a `Downloading` row at startup)
  reset to NotDownloaded. Strategy IO runs off the main thread
  (`flowOn(Dispatchers.IO)`). Verified offline in airplane mode; video merge
  verified on-device (AV1 4K + Opus → one Matroska, played locally).
- **ffmpeg IS bundled** as a minimal static binary (`libffmpeg.so` in
  `app/src/main/jniLibs/<abi>`, ~7MB; built from FFmpeg 7.1.1 by
  `tools/ffmpeg/build-ffmpeg-android.sh`, remux-only — no decoders/encoders/
  ffprobe). PyPI has no `aarch64-linux-android` ffmpeg wheel, so it can't be
  pip'd; a shipped binary is the only way. Under Android 14 W^X the only
  app-private executable location is `nativeLibraryDir`, so the `.so` is
  extracted there (`packaging { jniLibs { useLegacyPackaging = true } }`) and
  `FfmpegBinary` symlinks it to the name yt-dlp expects; the engine passes
  `ffmpeg_location`. This unblocks merged best-quality video and download-side
  SponsorBlock removal. The interpreter and ffmpeg can't self-update (W^X);
  yt-dlp itself (pure Python) still can.
- `:core:database` — Android library (Room via KSP): entities, DAO, and
  `RoomPodcastStore` implementing `:core:data`'s `PodcastStore` port. The only
  place entities meet domain types. Verified by instrumented tests; exempt from
  the Kover JVM gate.
- `:core:playback` — the unified playback seam: `PlaybackController` port +
  `PlaybackState`, implemented by `Media3PlaybackController` connected to a
  `MediaSessionService` (`PlaybackService`, foreground, Media3-managed
  notification with title/artist/artwork, seek back 10s / forward 30s, audio
  focus + becoming-noisy handling). Both pillars play through it — anything
  with a `MediaItem.mediaUrl` is playable, and every system surface
  (notification, lock screen, Bluetooth/headset media keys, Assistant)
  controls that one session. `POST_NOTIFICATIONS` is requested at first play
  (`RequestNotificationPermissionOnFirstPlay`) — required on API 33+ or the
  notification never shows. `fake.FakePlaybackController` for tests/previews.
  Kover-exempt adapter (Media3 glue; instrumented-verified).
- Manual DI: `AppContainer` (in `:app`) wires the graph; construction is code,
  errors are compile-time. No Hilt/Koin.
- **Cleartext HTTP is deliberately permitted** (network_security_config):
  podcast enclosures in the wild are frequently plain http (BBC media hosts
  included); refusing them breaks playback of legitimate feeds. Same policy
  as AntennaPod.
- `:lib:ytdlp` — from-scratch yt-dlp library (replaces the youtubedl-android
  fork). **Pure JVM on purpose** — it is the platform-neutral API (types,
  port, fake); only the real engine module needs Android. Public API:
  `YtDlpEngine` (suspend `extract`, `searchVideos`, cold-`Flow` `download`,
  sealed results), `bestPlayableFormat()` selection. Deliberately independent
  of `:core:domain` (standalone, reusable).
- `:lib:ytdlp-chaquopy` — the real engine: yt-dlp on embedded CPython 3.12
  via Chaquopy 17 (MIT). `uniapp_ytdlp.py` is a thin JSON-in/JSON-out bridge;
  `BridgeJson.kt` parses it (JVM unit-tested); `ChaquopyYtDlpEngine`
  implements the API. Chaquopy constraints: exactly ONE module per app may
  apply the plugin; build-host Python minor version must match the target
  (3.12 here); NOT configuration-cache compatible (config cache disabled in
  gradle.properties because of this). ABIs: arm64-v8a + x86_64. Adds ~80MB
  to the APK (Python runtime per ABI). yt-dlp itself (pure Python) can be
  self-updated at runtime; the interpreter and ffmpeg cannot (Android W^X).
  ffmpeg is bundled separately in `:app` jniLibs (see Downloads above).
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
- Remote: `github.com/dewijones92/dewiuniapp` (public). Default branch is
  `main`; pushing to it is fine and CI (GitHub Actions) must stay green.
- Every push to main publishes a signed APK to the rolling `latest`
  prerelease (consumed by Dewi's Obtainium). Release signing key lives in
  three places, never this repo: locally at `/home/dewi/code/dewiuniapp-signing/`,
  in this repo's Actions secrets (CI signing; write-only), and backed up in
  the PRIVATE repo `dewijones92/uniapp-signing-backup` (survives laptop
  loss). versionCode is `100 + run number`, so it only ever increases.
- Temporary debug logging must be prefixed `dewidebug` and never committed.
