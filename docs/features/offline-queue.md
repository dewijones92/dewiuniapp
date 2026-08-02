---
title: Offline queue
kind: feature
status: shipped
area: downloads
updated: 2026-08-02
---

# Offline queue

Everything in the queue is fetched as audio automatically, so the queue plays with no signal —
and the app says plainly how far along that is.

Dewi, 2026-08-02: *"I expect everything in the queue to have an auto download for audio and for
it to work offline … I expect the gui / labels etc to be very very clear"*.

## What happens

`QueueAutoDownloader` watches the queue and fetches the audio of every entry. Playback prefers
the local file (`PlaybackQueue` → `playLocal`), so a downloaded item never touches the network.
Nothing is deleted automatically: leaving the queue keeps the file, and it is removed from
Library like any other download.

**Audio only, video on request.** Small, quick, and it matches how a queue is used. A full video
stays a deliberate per-item choice from the row menu.

## Three things were wrong, and only one was the machinery

**It only ran on Wi-Fi.** The default was Wi-Fi-only, so the queue was ready offline exactly when
you were somewhere you did not need it, and the pause was completely silent. It now downloads on
any network; the setting remains for whoever wants it, and the queue states when it is what is
holding things up.

**It was never sequential**, despite its own comment saying so. `DownloadManager.download`
launches into its own scope and returns at once, so the loop fired the whole queue together —
report 0.1.313 caught **nine at once**, each crawling, competing with playback for the
connection. It now waits for each to settle, bounded by `SETTLE_TIMEOUT_MS` (10 minutes) so one
download whose flow never terminates costs minutes rather than the session. Manual downloads are
untouched: a tap still starts immediately rather than queueing behind seventy background fetches.

**Nothing showed readiness.** A per-row headphones glyph was the only signal, so "is my queue
ready?" could only be answered by scrolling 77 rows and counting.

## What you see now

| Where | What it says |
|---|---|
| Queue banner | `All 77 ready to play offline` · `73 ready offline · 4 can't be downloaded` · `60 of 77 ready offline · 17 still to fetch` · `Waiting for Wi-Fi to download 9 items` · `Automatic downloads are off · 5 items not saved offline` |
| Queue row, fetching | `Downloading 42%`, or `Downloading…` when the server sends no length |
| Queue row, impossible | `Online only`, in words |
| Library | An in-progress section at the top, with a bar **and** a percentage |

`OfflineReadiness` (`:core:domain`) does the counting, so the screens only render. A **retryable**
failure counts as *waiting*, not as a problem — the app is still trying, and asking for a decision
that is not the person's to make would read as a broken queue on a flaky connection.

Items that can never download — members-only, removed, region-blocked — are **kept and marked**,
not removed. They still play with signal, and deleting somebody's queue items to make a number
tidy is not the app's call.

## Tests

- `OfflineReadinessTest` — the counting, including the case that bit: downloaded items plus
  permanent failures must read as *settled*, not as forever in progress.
- `OfflineSummaryTest` (instrumented, runs on CI's emulator) — the wording itself, because that
  is what a person actually reads.
- `QueueAutoDownloaderTest` — one download at a time, and that a never-settling download does not
  wedge the queue. Deleting the sequencing turns the first red.
