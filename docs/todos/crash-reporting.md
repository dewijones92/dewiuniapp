---
title: Crash reporting somewhere Claude can read
kind: todo
status: refining
area: infrastructure
priority: high
requested: 2026-07-25
updated: 2026-07-25
---

# Crash reports I can actually read

**Ask:** the app should report crashes somewhere I can read, with verbose detail.

The hard part isn't capturing the crash — it's **where it goes**, because I can't reach
your phone. Right now a crash on the Pixel 7 is invisible to me: I can only read logcat
from the emulator attached to this laptop.

## Where reports could go

| Destination | Can I read it? | Notes |
|---|---|---|
| **Your Pi** (`333133333.xyz`) — a tiny endpoint that appends reports to a file | **Yes**, over SSH — I already have access | Self-hosted, no third party sees your viewing data, and verbose costs nothing. **My recommendation.** |
| On-device log file, pulled over adb | Only for the emulator | Useless for the phone unless you plug it in |
| GitHub issue / gist from the app | Yes | Needs a token **inside the APK** — and this repo is public. Extractable. Bad idea |
| Sentry / Crashlytics | Via their API | Standard and good, but sends your data to a third party and adds a dependency |

## Recommended shape

**ACRA** (the long-standing Android crash-reporting library, built for exactly this)
reporting to a small endpoint on your Pi:

- **Captures** the stack trace plus device model, Android version, available memory,
  free storage, the foreground screen, and — importantly — the **app version and git
  commit**, which this build already carries (the Library footer shows
  `v0.1.0-dev · <sha>`), so a report maps straight to code.
- **Breadcrumbs:** the last N `dewidebug` lines. We log a lot of useful state
  (playback transitions, codec rejections, silence detection), and having those
  leading up to a crash is most of the diagnosis.
- **Non-fatals and ANRs too**, not just hard crashes — a swallowed exception in a
  coroutine is exactly the kind of thing that hides.
- **Offline-safe:** written to disk first and retried, so a crash on the Tube still
  reaches me later.

### Privacy — this needs deliberate care

A naive crash reporter would leak your viewing life. Non-negotiables:

- **Never** the OAuth token or any credential (the token value classes already redact
  themselves in `toString()`, which helps, but the reporter must not read the store).
- **No** video/podcast titles, channel names, feed URLs or watch URLs — a report should
  say "playing a video from source #3", not what it was.
- Report **ids and shapes, not content**: `MediaItemId` is fine, a title is not.
- Nothing goes anywhere until you've seen a sample report and agreed it's clean.

## Questions for you

1. **Pi endpoint** — happy for the app to POST to something on `333133333.xyz`? I'd add
   a small service beside the existing ones (behind the same nginx, its own path, no
   auth needed for POST but the *reading* side stays private).
2. Or would you rather I keep it **on-device only** for now (a report file you can share
   when something happens) and skip the network entirely?
3. **Opt-out switch** in Settings, or always on for your own app?

**Done when:** a crash on your phone produces a verbose, redacted report that I can read
without you doing anything, and it maps to a git commit.
