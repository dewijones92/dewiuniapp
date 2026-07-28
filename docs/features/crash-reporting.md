---
title: Crash and diagnostics reporting
kind: feature
status: shipped
area: infrastructure
updated: 2026-07-28
---

# Crash reporting

A crash on Dewi's phone used to be invisible — logcat only reaches the emulator attached
to this laptop. Now every crash, and every "that behaved wrongly", arrives on his Pi with
enough context to diagnose it without asking him anything.

## The seam

One rule shapes the design: **a crash is often the last thing the process does**, so
nothing is trusted to survive it. The report is written to disk inside the crash handler
and uploaded on the *next* launch — never at crash time. A failed upload keeps the file
and retries, so a crash on the Tube still arrives later. Verified: a genuine crash whose
upload hit a DNS failure was re-sent successfully on the following launch.

| Piece | Where | Job |
|---|---|---|
| `Breadcrumbs` / `Diag` | `:lib:common` | The rolling event trail, and the one call that logs *and* remembers |
| `installAndroidLogSink()` | `:app` | Routes `Diag` to logcat — the only `android.util.Log` use left |
| `CrashReporter` | `:app` | Uncaught handler; builds the report |
| `DiagnosticsStore` | `:app` | Pending reports on disk, capped at 50 |
| `DiagnosticsUploader` | `:app` | Sends pending reports at launch; deletes only on success |
| `tools/crashlog-server` | the Pi | FastAPI sink → files + SQLite index, behind Google auth |

### Why the trail lives in `:lib:common`

It started in `:app` and that was wrong: the most valuable breadcrumbs come from the
lower layers — playback transitions, codec rejections, download failures. A trail only
the UI could write would miss exactly the lines that diagnose a bug. `:lib:common` is
pure JVM and api-exposed through `:core:domain`, so every module can feed it; a pluggable
`Diag.Sink` keeps Android out of it (silent by default, so tests and pure-JVM callers
need no setup).

## What a report contains

Verbose by explicit instruction (Dewi, 2026-07-25 — *"forget about PII or data
sensitivity … prioritise collecting data"*):

- **Identity:** app version, versionCode, **git commit** (so a report maps to code),
  build type, a stable install id.
- **The failure:** exception class, message, full stack trace, cause, thread.
- **The story:** the last 400 `Diag` events, timestamped and tagged — queue mutations,
  playback transitions, codec rejections, download start/done/failed, sync results.
- **The state at the moment it broke:** what was playing and its position, the whole
  queue, every setting, whether the network was metered.
- **The device:** model, Android version, ABIs, heap and system memory, free storage.
- **~150KB of logcat** — where the Media3 / MediaCodec lines live, which is what
  actually diagnosed this project's playback bugs.

**One security exception, which is not a privacy preference:** the YouTube OAuth tokens
are never read into a report. A token in a transmitted log is an account-takeover risk
rather than a disclosure of viewing habits. Trivial to include if ever wanted.

## Reading them

```bash
curl -s https://crashlog.333133333.xyz/latest              # newest report, pretty-printed
ssh pi@333133333.xyz 'cat /home/pi/crashlog-data/reports/*/*.json'   # works even if the service is down
```

The web index (`/`) filters by commit and exception and groups by exception; `/api/reports`
is the machine-readable list. Reports are stored as plain files first and indexed second,
so an unparseable payload is still kept and still visible.

## Not just crashes

Most of this project's bugs weren't crashes — they were wrong behaviour. **Settings →
Diagnostics → "Send diagnostics"** sends the same report with no crash involved, which is
how a "this played the audio-only file as a video" report gets diagnosed.

## A note on log volume

The silence detector enters silence every few seconds during speech. Logging each one
flooded the trail and evicted the useful lines, so it logs the first plus every 50th with
a running count — enough to prove detection works, quiet enough to leave room for signal.

The skip-silence speed change had the same problem and was worse: **239 lines, 59% of one
400-entry report**, leaving sixteen minutes of history in a buffer meant to hold hours —
precisely when a stall needed explaining. Same treatment: counted, every 50th logged.

That is the one real constraint on the "log generously" rule in `CLAUDE.md`. Anything firing
many times a second gets counted and logged periodically. Counted, never silent.

## Log the decision, not just the outcome (2026-07-28)

Dewi asked whether the playback position clears when switching bottom tabs. The honest
answer was that the diagnostics *could not say* — nothing recorded that a tab had been
switched — so the question was unanswerable rather than answered. His standing instruction
came from that and now governs this repo: err on the side of far more logging, because an
unlogged decision is an unanswerable question.

Four trails were added, each closing a specific blind spot:

| Trail | Answers |
|---|---|
| `nav` | which tab, when — the correlation everything else needs |
| `place` | where a screen was on entry and exit; if they differ, state was lost |
| `vm` | whether a view model was recreated (it is not — ruled out a whole class of cause) |
| `advance` | which of four reasons stopped an auto-advance |
| `queue` | *which operation* mutated the queue, not just the resulting snapshot |

The queue one is the subtle one. A cursor of `-1` is what **both** a deliberate "peek" and a
hydration-with-nothing-playing look like, and telling those apart decides whether an
auto-advance failure is a bug or by design. It was impossible to tell from a report; every
mutation now carries one word of intent.

`place` has a trap worth knowing: `entered` appears *before* the previous screen's `left`,
because `AnimatedContent` composes the incoming destination before disposing the outgoing
one. Not a bug, but it reads like one.

**This paid for itself the same day.** One `nav` line found a root cause on the very next
report: an `extract` completed, the user switched tab 1.7s later, and nothing ever played —
because row actions ran playback on `rememberCoroutineScope()`, which dies with the
composition. A tap that takes a second to become audible was a race against the user's next
gesture.

## Tests

`lib/common/src/test/kotlin/.../DiagTest.kt` — breadcrumb recording, sink routing, warn
formatting, oldest-first ordering, and buffer eviction at the cap.

Verified on-device end to end: forced crash → 154KB report on disk → DNS failure kept it
→ next launch uploaded it → indexed on the Pi with a 9-event trail, stack, and state.

## From a trail to a timeline (2026-07-27)

Dewi's ask: press a button and send "the last 30 mins of logs/analytics", including what
is downloading or streaming.

**The trail is time-bounded now, not count-bounded.** Nothing younger than 30 minutes is
dropped; at least 400 entries are kept regardless of age, so a quiet session still has
context; and 5,000 is a hard ceiling on memory. An entry is evicted only when it is
*both* past the floor and past the window — so age protects a line the count would have
evicted, and vice versa. A chatty minute of ticks can no longer push out the thing that
broke twenty minutes ago.

**`ActivitySnapshotter` writes a line every 30s** naming what is playing and where, how
long the queue is, and every download in flight with its progress. Transitions alone
answer "what happened" but never "what was it in the middle of" — a download stuck at 40%
for ten minutes produces no events at all, which is exactly when it matters. Silent when
nothing is happening, so an idle app spends none of the window.

**More paths instrumented:** subscribe / unsubscribe (with failures and unparseable
feeds), and every settings change, routed through one logged path so a report can say
*when* a setting changed rather than only its current value.

Verified on device: `[snapshot] playing "…" at 29346ms (running); queue=5; downloading=0`.
