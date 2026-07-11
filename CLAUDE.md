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
- **SOLID + DRY**: small focused types, dependencies point inward, no copy-paste logic.
- **Maximum compile-time safety** (the brief's "dependent types" translated to Kotlin):
  sealed hierarchies, value classes over primitives, exhaustive `when`, no platform types
  leaking, illegal states unrepresentable.
- CI must stay green; quality gates (lint, static analysis, tests) block merges.

## Build & test

```bash
./gradlew assembleDebug          # build debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests (device/emulator needed)
```

- JDK 21 lives at `/home/dewi/code/jdk/`; Android SDK at `/home/dewi/code/android-sdk`
  (see `local.properties`, not committed).
- The `android` CLI (`~/.local/bin/android`) is available for emulators, screenshots,
  layout inspection, and docs search.

## Architecture

- `:app` — Compose UI, navigation (Navigation 3), ViewModels.
- `:lib:ytdlp` (planned) — from-scratch yt-dlp Android library: embedded CPython
  runtime, Kotlin coroutines/Flow API, strict public API boundary. Key constraint:
  Android W^X — executables/native code must ship in the APK's native-lib dir or run
  in-process; nothing downloaded can be executed. yt-dlp itself (pure Python) *can* be
  self-updated at runtime; the interpreter and ffmpeg cannot.
- Package root: `com.dewijones92.uniapp`.

## Working agreements

- Commit as you go — small, coherent commits at each green state.
- Local-only repo for now: no remote configured; do not create/push one unprompted.
- Temporary debug logging must be prefixed `dewidebug` and never committed.
