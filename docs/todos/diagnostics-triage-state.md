---
title: Mark diagnostics reports as triaged/fixed on the server
kind: todo
status: open
area: diagnostics
priority: high
requested: 2026-07-28
updated: 2026-07-28
---

# A report should record whether it has been dealt with

**Ask (Dewi, 2026-07-28):** "maybe put a bool FIXED column on the diag db?"

The crash/diagnostics store on the Pi is append-only files under
`/data/reports/<date>/<timestamp>-<id>.json`, with no notion of whether anyone has
looked at a report, let alone acted on it. On 2026-07-28 that cost something real: 27
reports had accumulated and **26 had never been opened**, including the two most recent —
which turned out to hold a live crash (tapping Cast killed the app) and the actual cause
of the "buffering lots" complaint (an expired stream URL retried seventeen times). They
were found only because Dewi asked whether any dumps were unread.

An unread flag alone would have surfaced those. A triage state does more, because the
other half of the same session's lesson was that **two of the five findings were already
dead** — a PyException fixed 28 builds earlier, and a "crash" that was just `adb` killing
the app during testing. Knowing that a signature has been judged before, and what the
judgement was, is what stops it being re-investigated.

## Shape

Minimum: a mutable `fixed` boolean per report, settable without rewriting the file.

Better, and barely more work — a small sidecar (SQLite, or a `triage.json` beside the
report) keyed by report id, holding:

- `state` — `new` | `triaged` | `fixed` | `wontfix` | `noise`
- `fixedIn` — the version the fix landed in, so a recurrence *after* that version is
  obviously a regression rather than a duplicate
- `note` — one line of why, so the next session inherits the reasoning
- `signature` — exception + top app frame, so repeats group instead of listing

`noise` matters as much as `fixed`: the CrashedByAdb reports are self-inflicted and
should never cost anyone attention again.

## Why it earns its place

- A "N unread" count is the thing that would have made 26 unread reports visible.
- Grouping by signature turns "11 crashes" into "one bug, 11 times, on one build".
- `fixedIn` makes the version question — the one that killed two of five findings — a
  property of the data instead of an investigation every time.

## Related

- `docs/todos/crash-reporting.md` — the reporting pipeline this sits on top of.
- The global rule "Date every artefact" now requires the version pass; this is the
  server-side half that makes it cheap.
