---
title: The buffering defects from report 0.1.332
kind: todo
area: playback
priority: high
status: all four fixed with tests
updated: 2026-08-03
---

# The buffering defects from report 0.1.332

Dewi, 2026-08-03: *"dwarkesh video buffered????? hmmm we have lots of buffering issues dont we?????"*

## What actually happened

Report 0.1.332, commit `00dabd0`, Pixel 7, release build — the tip of `main` at the time, so none
of this was already fixed.

| Time | |
|---|---|
| 19:10:13 | playing normally at 649822ms, ~125 Mbps |
| 19:10:16 | froze at **652353ms**, 2 loads in flight, **48ms buffered** |
| 19:10:38 | watchdog saw the 20s stall and logged *"not at the end, so leaving it to the player"* |
| 19:10:43 → 19:12:13 | four snapshots, position identical every time |
| 19:12:25–32 | `[gesture] dismiss` → `[queue] play-at-1` — **Dewi gave up and re-played it** |
| 19:12:33 | fresh extract, new googlevideo URL, resumed at 650344ms and played on |

**2 minutes 16 seconds of spinner**, ended by hand, on a connection measuring 125 Mbps. Not a
bandwidth problem: 2 dropped frames all session, and the same AV1 1080p stream played fine after a
fresh URL.

## Fixed

1. **The watchdog abstained mid-item.** It only ever acted near the END of an item, so the failure
   it could fix was the one it declined to touch. It now **replays from where it stopped** — never
   advances, because skipping a video someone is watching is worse than the stall.
2. **`playback.bufferingMs` only counted stalls that ENDED.** Written on `STATE_READY`; every other
   exit from BUFFERING discarded it. The report says `1370` for a session containing a 136-second
   freeze. That is why "lots of buffering issues" never showed up in the numbers — the metric
   structurally excluded the worst cases. Now counted however it ends, with
   `playback.abandonedBufferingMs` naming the time nobody got back.
3. **STUCK vs STARVED split on `> 0`.** 48ms counted as "STUCK", which reads as "has data, not
   draining it" and points at the wrong fix. Floor is now 200ms.

## The reported-online-but-broken case — done

The network says VALIDATED, nothing arrives, and no number of fresh connections to the same dead
address changes that. `iptables -j DROP` is that shape, and so is the Dwarkesh report.

**A rescue that keeps failing used to have no bound.** It is now two rescues, then the app gives up
on the stream and moves on, keeping the queue alive. Budget refilled only by *genuine* forward
progress — past where the rescue resumed, not merely different from it, because the replay's own
seek would otherwise read as success and the budget would never deplete.

### The emulator found the flaw in my first attempt at this

The escalation was gated on the once-per-item guard, which re-arms when the position changes. But
**a replay seeks back to the stall position, so the position never changes** — two rescues fired at
4812ms and the give-up was permanently unreachable. Escalation now runs off elapsed stall time,
which needs no movement, because a frozen player cannot provide any by definition.

A second guard was needed for the same reason: giving up moves the position too, so without an
"already abandoned" flag the queue was advanced again on every later stall of the same item —
which matters if the advance fails and the dead item stays current.

## Newly found, and now fixed

**A replay only got a fresh URL for a video.** `PlaybackQueue.replayCurrent` invalidates the
resolver cache for `PlayHandle.Video` only, so replaying a podcast enclosure or a torrent stream
asks the same address again — which for a genuinely dead address changes nothing.

For Dewi's case that is fine (it was a video, and a fresh extraction is what fixed it by hand).
But the rescue is weaker than it looks for the other pillars, and this was found by *writing the
test*, not from a report.

## Tests

- `StallWatchdogTest` — the mid-item replay; that it rescues rather than skipping; that one
  continuous stall escalates to exactly two rescues and then gives up once; that genuine progress
  refills the budget; that auto-play off never skips; that the end-of-item case still advances.
- `PlaybackDiagnosticsTest` — abandoned buffering counted; recovered not counted as abandoned; a
  transition with nothing buffering invents nothing.
- `StalledStreamRecoveryTest` (instrumented, runs in the existing CI emulator job) — a socket that
  answers 200 and then goes quiet, with a real ExoPlayer. Two cases: the stall is rescued, and a
  stream that never recovers is given up on so the queue moves to something playable.
  **Verified both ways**: passes with the fix, fails without it.
- `OfflineQueuePlaybackTest` (instrumented) — see `docs/todos/offline-queue-e2e.md`.

### The trap this test walked into first

The first version served the media on a later request and asserted that playback recovered. It
passed with the fix **removed** — ExoPlayer retries a dead connection itself and the retry got
served. An "end-to-end" assertion that any competent player satisfies proves nothing about our
code. Two lessons, both cheap to forget:

- **Verify a regression test fails without the fix.** It cost two emulator runs and would
  otherwise have shipped as false assurance.
- **Reproduce the signature, not the symptom.** The phone showed loads accumulating while none
  completed, so the faithful fault hangs *every* request. It now runs 19 requests deep.

## The video-only rescue — fixed 2026-08-04

A replay now asks the item's SOURCE to produce the stream again, not just the network to try the
same address again. For a torrent that means telling the home server to restart the remux behind its
audio stream, which is the only second thing there is to try.

It reuses `AppContainer.readyAgain` — the same pillar routing the prefetcher uses — rather than a
second copy. A torrent gaining a new way to be warmed can no longer be picked up by one caller and
missed by the other, which is exactly how this gap opened: the prefetcher learned about audio
streams and the rescue did not.

Failure is swallowed by design. The home server being unreachable is precisely when a stall
happens, so a refresh that threw would turn a recoverable stream into a stopped queue. Pinned by
`ReplayRefreshesTheSourceTest`.
