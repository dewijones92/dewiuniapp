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

### Collection policy (Dewi, 2026-07-25): collect everything

I raised privacy; Dewi's explicit instruction overrides it: *"Forget about PII or data
sensitivity until I say so, prioritise collecting data!!!"* It's his app, his data and
his server, so that's his call — **collect verbosely**: titles, channel names, feed and
watch URLs, item ids, queue contents, settings, device and network state.

**One security exception, which is not a privacy preference:** credentials stay out —
the YouTube OAuth access/refresh tokens. A token in a transmitted log is an
account-takeover risk rather than a disclosure of viewing habits, and the reports land
somewhere readable. Trivial to include later if you ever actually want it; say so and
it goes in.

## The rolling event log (Dewi's design)

> "store in the app a log of the previous 30 events … a rolling log … send it to the
> server on a crash so we have a lot of context"

- An in-memory ring buffer of the last **N events** (N configurable; 30 is a good
  start, but it's cheap to hold a few hundred — I'd default higher and cap by size).
- Every `dewidebug` line feeds it automatically, so all the instrumentation already in
  the app becomes crash context for free: playback transitions, codec rejections,
  silence detection, queue mutations, download progress, InnerTube failures.
- Each entry timestamped and tagged with the screen and the playing item, so the
  sequence reads as a story rather than a pile of lines.
- Mirrored to disk continuously (small append-only file, rotated), so a **hard kill**
  — a native crash or the system killing us — still leaves the trail behind for the
  next launch to upload.
- Uploaded with the crash, and also on demand ("send diagnostics" in Settings) so a
  *misbehaviour* that isn't a crash can be diagnosed too. That last bit matters: most
  of this session's bugs weren't crashes.

## Remaining question — just the destination

Dewi has said "the server", so remote it is. My recommendation: a small endpoint on
**your Pi** (`333133333.xyz`), beside the existing services — I already have SSH there,
so I can read reports directly, and nothing goes to a third party. I'd need to add that
service on the Pi, so I'll confirm before touching it.

**Done when:** a crash on your phone lands on the server with a full event trail and the
git commit, and I can read it without you doing anything.
