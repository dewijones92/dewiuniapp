---
title: What Totum is, and what must never break
kind: spec
status: living
updated: 2026-08-01
---

# What Totum is, and what must never break

Written 2026-08-01, at Dewi's request: requirements arrived in dribs and drabs over several
weeks, and while every one of them is recorded (see [`features/`](features/_index.md) and
[`todos/`](todos/_index.md)), nothing said what the app **is** or which of its behaviours are
allowed to be wrong. This is that statement.

It is deliberately not a feature list — that exists and is current. This is the shorter, harder
document: the handful of things that must be true every time, why each one is here, and whether
we can actually prove it today.

## What it is

Totum is **one person's media app**. Dewi's. It replaces PipePipe and AntennaPod on a Pixel 7,
installed through Obtainium from this repo's releases. There is no second user, no store
listing, no support burden.

That fact is the spec's most useful constraint, and it cuts both ways:

- **It lowers the bar on breadth.** Features exist because Dewi asked for them, not because a
  general audience might want them. An unbuilt feature nobody has asked for is not a gap.
- **It raises the bar on the core loop.** With one user there is no second opinion, no crash
  aggregation across a userbase, and no A/B. If putting the phone in a pocket stops the queue,
  the only evidence that ever exists is one person noticing and sending a report. So the loop
  has to work, and when it does not, the app has to say why in its own logs.

Dewi's answer when asked directly (2026-08-01): *"me, I want the app ux etc to all work great"*.
Not more features — the ones that exist, working.

## The core loop

Everything below is one behaviour: **put something on, and it keeps playing the right thing
until you stop it.** It is what the app is for, it is where every recent bug has been, and it is
the thing to protect ahead of any new capability.

Each invariant states what must be true, why it earned a place here, and what proves it today.
"Proven" means something fails when the behaviour breaks — not that it was seen working once.

### I1 — When an item finishes, the next one starts

Regardless of: the screen being off, the app being backgrounded, how long the session has run,
or whether that item has been played before.

**Why it is here:** it has broken three times in a week, and all three shipped.

| Break | Cause | Fixed |
|---|---|---|
| Nothing advanced with the phone in a pocket | The advance lived in a composable effect fed by `collectAsStateWithLifecycle`, which stops when the activity stops | `2026-07-30` |
| A queue stopped silently after a stall | The watchdog *collected* a `StateFlow` — but a stall is a sequence of identical states, and equal values are conflated, so the emission never came | `2026-07-31` |
| An item played a second time never advanced | `AutoAdvancer.handled` held one id forever, so any item the queue had already passed was refused — citing an end three hours old | `2026-08-01` |

A fourth, the same shape, was found by inspection rather than by report: `StallWatchdog` kept
its own never-cleared `handled`, so an item rescued from one stall could never be rescued again.

**The cause behind all three was addressed on 2026-08-01**, rather than the three bugs
individually. `PlaybackState` is a level signal — it re-emits on every position tick and, being a
`StateFlow`, drops values equal to the last — so anything needing to know about a *change* had to
reconstruct the edge itself and remember what it had already acted on. `PlaybackEvent.Ended` is
now derived once, in `Media3PlaybackController`, from the player callback that already IS the
edge. `AutoAdvancer` consumes it and has no fields at all: no `handled`, no baseline-on-connect
branch, no transition dedupe. There is nothing left to keep past its meaning.

**Proven by:** unit tests per watcher, including the exact reported sequences, **and since
2026-08-01 end to end on a device** — `AutoAdvanceLoopTest` plays real media to its real end
through the real graph and asserts the next item starts. It fails on the code as it was before
each of the fixes above, which is what makes it a guard rather than a description. See
[The weakness](#the-weakness-the-loop-is-tested-in-pieces).

### I2 — An item that "finishes" has actually finished

The queue advancing off a stalled stream is worse than it stopping, because it looks like
correct behaviour.

**Why it is here:** three separate things currently produce "ended" and the advancer cannot tell
them apart — Media3's `STATE_ENDED`, the stall watchdog's *"frozen at its own end, treat it as
ended"*, and a SABR stream giving up at 16% of the file. The last one is indistinguishable from
a short video without extra information.

**Proven by:** `PlaybackDiagnostics.reportEnd()` reports every finish against the duration and
warns `ENDED EARLY at Xms of Yms — Nms short`; `SabrStream` warns `PREMATURE END … served X of
Y (N%)`. Detection, not prevention — the video still ends early, it just no longer does so
silently.

### I3 — Losing the network is a pause, not a death

Coming out of a tunnel resumes; it does not sit dead requiring a tap.

**Proven by:** `StreamRecovery` waits for a *validated* network and retries with backoff;
verified on the emulator with `svc wifi disable`, which is the only way to reproduce it (dropping
packets with iptables leaves Android reporting the network as fine, so the recovery path never
runs).

### I4 — Where you stopped is where you resume

**Proven by:** `RoomPlaybackProgressStore` with instrumented tests. One consequence is written
down because it is unobvious and was found the hard way: **resuming is a seek**, so a
part-watched video cannot use the SABR path and is extracted instead — at a cost of 14–25s
rather than 200ms. Correctness beats speed here without argument.

### I5 — Silence is never allowed to be the explanation

Any decision a user would notice — advance or not, skip or not, retry or give up, this stream or
that one — is logged with **its reason**, because this app is debugged almost entirely from
reports sent off a phone that is not in front of whoever is reading them.

**Why it is here:** it has repeatedly been the difference between a fix and a guess. Yesterday's
`not advancing past 40pRi5wMBwA: already handled this item's end` named a three-hour-old bug in
one line. Conversely, a 23-second stall was once invisible because nothing recorded stalls at
all.

**Proven by:** the practice is in [`../CLAUDE.md`](../CLAUDE.md) and holding. The one real
constraint is the bounded report buffer — anything firing many times a second is counted and
logged periodically, never per event and never dropped.

### I6 — The app says when it is busy, and how long things took

**Proven by:** the global busy bar ([`features/loading-feedback.md`](features/loading-feedback.md)),
and as of 2026-08-01 the handover between items is timed in wall clock —
`3436ms of silence since the last item ended (SLOW handover)` — because "ended" and "playing"
both carry *media* positions, so a 3-second gap and a 40-second one used to read identically.

## The weakness: the loop is tested in pieces

There are 377 unit tests and they are good ones. **Every single autoplay bug above passed
them.** Each component was correct against its fake; the composition was not:

- an advancer that worked, hosted by a lifecycle that stopped
- a watchdog that worked, fed by a flow that conflates equal values
- a guard that worked for one end, kept across a whole session

The instrumented tests covered Room stores, migrations, the Python engine and the shell — **not
the loop**. There was no test in which a real `ExoPlayer` finishes a real item and the next one
starts, which was the single best explanation for why I1 kept breaking.

**Closed 2026-08-01** by `AutoAdvanceLoopTest`, which plays a generated one-second WAV through
the app's real container and asserts the next item starts on its own. It fails on the pre-fix
code and passes on the fix, so it holds I1 rather than merely describing it. Three things had to
be true before it measured anything at all, each of which cost a run:

- **Drive the real graph, not a private one.** `TotumApplication.onCreate` already starts the
  advancer, watchdog and prefetcher, and an instrumented test shares that process — so a
  second controller was a second client of one `MediaSession`, every callback fired three
  times, and the item sat READY and never played.
- **The app must be in the foreground.** Android 16 denies audio focus to a background app
  (`AS.HardeningEnforcer: Focus request DENIED … procState:4`), so the player reaches READY and
  stops there. An `ActivityScenarioRule` fixes it; the screen must also be on.
- **Assert PLAYING, not merely current.** Being the selected item is not playing, and
  conflating them turned a suppressed player into a timeout twenty seconds later that read
  like a broken advance.

## Parked, deliberately

- **SABR** — the protocol works and resolves in ~200ms against 14–25s, but YouTube stops serving
  after roughly a minute of media and asks for a proof-of-origin token this app cannot mint.
  Kept behind an off-by-default switch, honestly labelled, no further effort. Dewi's call,
  2026-08-01. Detail: [`todos/sabr-streaming.md`](todos/sabr-streaming.md).
- **Casting** — the button no longer crashes, but real casting is unverified for want of
  hardware. Best-effort, not an invariant.
- **A second user** — no multi-user, sync, or account-portability work is in scope.

## Open questions for Dewi

Nothing here blocks work; these are the places where a decision would change what gets built.

1. **Should an early end stop the queue rather than advance?** Today I2 detects and reports it,
   then advances anyway. Stopping would be more honest but would interrupt a session on a
   false positive.
2. **How much is a resume worth waiting for?** I4 currently pays 14–25s of extraction to resume
   a part-watched video correctly. The alternative is starting from the beginning instantly.
3. **Is the podcast pillar getting equal attention?** The last several weeks have been almost
   entirely video and YouTube. Asking the question found one real gap, now closed: refreshing
   swallowed every failure in three bare `return`s, so a feed that moved or began serving
   malformed XML looked exactly like one with no new episodes, indefinitely — I5 violated for a
   whole pillar. It now reports per feed, on screen and in the log.

   Repeating the exercise for downloads and notifications found the answer was not "podcasts
   lag video" at all. **Downloads are fine** — `DefaultDownloadManager` logs start, failure with
   its reason, and completion in one place, which is the correct DRY shape (the strategies emit
   state, the manager logs it). **The notification path was silent for BOTH pillars**:
   `ContentRefresher` swallowed a throwing source into an empty list, and `NewContentWorker`
   swallowed any exception into a bare `Result.retry()`. A background job retrying every six
   hours for weeks with no trace of why is the hardest thing here to diagnose, and it applied to
   video and podcasts equally. Both now say what happened and carry the throwable, and **Settings → Check for new content
   now** runs that same path by hand — the job was previously unobservable without waiting six
   hours per attempt. Worker and button share one `NewContentCheck`, because a button running
   merely similar code would prove the wrong thing.

## What this document is not

It does not repeat the architecture, the twin laws (Unified and DRY), the quality bar, or the
build commands — those live in [`../CLAUDE.md`](../CLAUDE.md) and
[`architecture.md`](architecture.md), and duplicating them here would break the second law on
the way to describing it.
