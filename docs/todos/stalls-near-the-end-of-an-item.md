---
title: Buffers towards the end of the video
kind: todo
status: partly fixed — two proven defects closed, the causal link not yet proven
area: playback
updated: 2026-08-06
---

# "Buffers towards the end of the video??????"

Dewi, 2026-08-06, after an evening in his queue: *"rest is politics in my queue buffers towards the
end of the video?????? obvs what the problem/solution is???"*.

His observation was exact. Reports `20260806T184333` and `20260806T184426` (0.1.359, commit
`a5355c9`, Pixel 7, Android 16) cover 52 minutes and contain **four consecutive items that each
hard-stalled inside their last 45 seconds and never recovered**:

| Item | Stalled at | Left of the item | Buffered ahead | Outcome |
|---|---|---|---|---|
| `chxbS3N3Llc` | 3,386,518ms | 42.8s | 82ms | rescued, then ended at 444ms left |
| `Xr9VqRawjAU` | 1,013,053ms | 11.5s | 68ms | given up on, treated as ended |
| `Ui8jZQirfj0` | 3,692,530ms | 35.8s | 70ms | rescued, then ended at 120ms left |
| `Ui8jZQirfj0` | 3,728,246ms | 0.1s | 87ms | treated as ended |

`playback.abandonedBufferingMs = 208530` of `bufferingMs = 244115`: **85% of all buffering in the
session was time that never resolved.** Two stalls of 20–26 seconds per item, every item.

## What the report proves on its own

- **Loading stopped completely, rather than going slowly.** Between 19:41:29 (80,484ms buffered) and
  19:42:22 the buffer drained at exactly playback rate — 80 seconds of media in 53 seconds of wall
  clock at 1.5×. Zero bytes arrived in that window.
- **Loads are leaking, and it is not an accounting artefact.** `playback.loadsOutstanding` climbed
  35 → 37 across 53 seconds and only ever climbs; `oldestLoadStartedAt` was byte-identical in both
  reports, and the stall lines put the oldest outstanding load's age at **25,489,806ms — seven
  hours**. Only 17 loads completed in the whole session for 53MB, against 66 cancellations.
- **Nothing errored.** `playback.loadErrors` sat at 14 and `playback.errors` at 12 across both
  reports, unchanged through every stall. So these loads did not fail — they simply never ended.
- **The heap is a consequence, not a coincidence.** 102MB holding 234s of buffer at 18:51; 255MB of
  256MB holding 80s at 19:41. Same workload, 150MB more heap. logcat over the stall shows
  `Clamp target GC heap from 323MB to 256MB` and repeated `Waiting for a blocking GC Alloc`.

## The two defects found, both fixed

### 1. `ChunkedDataSource` over-declared how much was left

`open()` set "bytes remaining" from the URL's `clen` — the length of the **whole resource** — while
the caller's `DataSpec` could start partway through it. So a read resumed at byte P over-declared
itself by exactly P, and on reaching the true end still believed P bytes were owed, and asked for a
range past it.

This is not a resumed-playback edge case. **ExoPlayer restarts its loader at a non-zero byte offset
on every seek AND every time the load control pauses loading**, so nearly every read of a long item
began this way. It has been live since the ranged-fetch change (`dcb8dbc`).

Fixed by `remainingFrom` in `ChunkedRead.kt`, which keeps the three quantities apart: a length the
caller stated is already relative to their position, `clen` is not, and a probe answers from the
position already.

### 2. A range that produced nothing was asked for again, forever

The end-of-input branch re-opened the same range and **called `read()` recursively**, with the only
stopping condition being `remaining == 0` — which the defect above guaranteed was false. Asked for a
range at or past the end, googlevideo can answer with **no bytes rather than a refusal**; a refusal
would at least have surfaced as a load error. Nothing at all meant one `read()` spun indefinitely:
no bytes, no completion, no cancellation, no error, and every buffer it held retained.

That is exactly the shape the counters show. Fixed twice over: `ChunkedRead.endOfRange()` refuses to
continue past a range that produced nothing or stopped short, and `read()` is now a bounded loop
rather than recursive.

### 3. (Same reports) the preloader could never let go — see `offline-queue.md`

`releaseIfPlaying` compared the **held URL** with the **playing URL**. A stream URL is re-resolved
per play and comes back signed, expiring and often at a different itag, so those two are routinely
different — and the report has three `still holding … — what started is …` lines where both URLs are
the same video at itags 18 and 399. So it never released, on a heap already at 255MB of 256MB. Now
keyed on the item id. The nomination resolving a *different format* from the one that plays is a
separate waste, now logged and counted (`playback.preloadsWasted`) rather than silent.

## What is NOT proven, said plainly

**The causal link between defect 1 and Dewi's stalls is not established.** `StreamPlaysToItsEndTest`
drives the whole flow — real queue, session, service and ExoPlayer, over HTTP with a `clen`
parameter, resumed near the end — and **passes with both defects deliberately reinstated**. So it
covers the flow but does not reproduce the reported failure.

The likely reason is the media format. A WAV (and a plain MP4) carries explicit sample sizes, so the
extractor stops at the last sample and never reads to the data source's end-of-input — the point at
which defect 1 bites. The streams in the report carry `gir=yes`, which is the shape read box-by-box
until the source signals end-of-input. Reproducing it therefore needs media of that kind, which this
repository does not carry and deliberately does not ship.

So: two real defects are closed, with the arithmetic and the spin each proven to fail without their
fix at two levels. Whether they were *the* cause of the end-of-item stalls is open.

## What the next report will settle

Per the repo's rule that every change must be provable in the wild from its logs alone, the
instrumentation that was missing has been added:

- **`playback.loadsInFlight`** — the outstanding loads *named*: track type, start time and host, for
  the oldest few. "37 in flight, oldest seven hours" could not say whether the stuck stream was the
  video, the audio or a subtitle, and those have different fixes.
- **`stopped loading … with only Nms buffered ahead and Nms of the item never fetched — the tail is
  not coming`** (`playback.loadsStoppedShort`) — fires only when the player stops fetching with a
  nearly-empty buffer and content still to come, so it is the anomaly and not the load control's
  ordinary pauses, which are counted instead (`playback.loadPauses`).
- **`stream ended while it still owed bytes`** (`playback.streamsEndedEarly`) — the data source
  saying so itself, with its numbers.

If the stalls persist, the next report says which track is stuck and whether the fetch gave up
believing it was finished. If they stop, `streamsEndedEarly` says how often the tail was rescued.

## Related

- `../features/streaming-reliability.md` — the ranged fetch this corrects.
- `../features/offline-queue.md` — the preloader.
- `high-quality-playback-fix.md` — the format choice that makes `gir=yes` streams the norm.
