---
title: Permanent vs transient failure, and playback that goes nowhere
kind: feature
status: shipped
area: playback
updated: 2026-07-31
---

# Knowing when to stop asking

The same mistake existed in two places: a failure that can never succeed was treated as
worth retrying, so the app either burned data forever or parked on something dead. Both
found by reading real diagnostics on 2026-07-28.

## Downloads

Two members-only videos sat in a 59-item queue and were re-attempted on **every queue
change, on every launch, for days** — present in every report sent that morning. The
auto-downloader matched `Downloaded` and `Downloading` and let everything else fall through
to "fetch it", so a failure was indistinguishable from never having tried.

`DownloadState.Failed.isPermanent` (`:core:domain`) classifies the extractor's own words,
which is all the failure carries: members-only, private, unavailable, removed, terminated,
age-gated. Age-gating counts because it needs a signed-in fetch the downloader cannot do,
so an unattended retry will not fix it either.

**Deliberately conservative** — anything unrecognised is treated as transient. Wrongly
giving up on a flaky connection is worse than one wasted request, and a 5xx or a timeout
says nothing about the content. Transient failures get a bounded three attempts per
session, not persisted: a fresh launch is a fair reason to try again, and a permanent
failure is refused on its reason regardless of any counter.

## Playback

The expired-stream recovery stopped after three re-resolves — right for the item, wrong for
the session. A real report had the player dead on one video with 58 more behind it, going
nowhere. It now moves to the next entry, and logs when there is nothing left to move to.

Skipping is gated on the retry budget being spent rather than the first failure: a single
403 is usually just an expired lease that re-resolving fixes, so skipping immediately would
throw away the item the recovery exists to save.

## Why both report their reason

"It stopped and I cannot tell you why" is the failure mode all of that day's logging work
existed to kill. So the skip decision says *why* — "asking again cannot help — ERROR: …
Join this channel", "gave up after 3 attempts". Ordinary skips (already downloaded, nothing
to fetch yet) stay silent, so the trail keeps only what would otherwise be mysterious.

## Files

- `core/domain/…/DownloadState.kt` — `isPermanent` and the marker list
- `app/…/queue/QueueAutoDownloader.kt` — `skipReason`, `failureSkip`, the attempt budget
- `app/…/playback/ExpiredStreamRecovery.kt` — `moveOn` when the budget is spent

## Tests

`DownloadFailureTest` (7 cases: each permanent marker, case-insensitivity, network failures
and unknown reasons staying retryable), `QueueAutoDownloaderTest` (+3: permanent never
retried, transient retried, transient bounded), `ExpiredStreamRecoveryTest` (+2: moves on
once spent, does *not* move on while attempts remain — the second matters more, since an
over-eager skip would look like the bug being fixed).

Found while writing those: `FakeDownloadManager.emit` fires an event but does **not** touch
the observable state map, so a test driving a consumer of `observeDownloads()` could not see
a failure at all — the first two retry tests passed against state that was never set.
`setFailed` mirrors the existing `setDownloading`.

## Postscript: logic that must outlive the UI (2026-07-28)

Two bugs in one day with the same shape, which makes it a pattern rather than two
accidents: **work the user expects to continue does not belong in a composable.**

1. Row actions started playback on `rememberCoroutineScope()`. Switching tabs cancelled the
   composition and killed an in-flight extraction — a tap became a race against the user's
   next gesture.
2. `AutoAdvance` read playback state through `collectAsStateWithLifecycle()`, which stops
   collecting when the activity stops. With the screen off the composition never saw
   `hasEnded`, so nothing advanced. Proven by a seven-minute gap between an item ending and
   the decision being reached, while 30-second snapshots kept arriving from a plain coroutine
   the whole time.

Both now run on the application scope. The test for whether something belongs there: *would
the user expect this to happen with the phone in their pocket?* If yes, a composable cannot
host it.

Worth noting what makes this hard to catch: neither failed loudly. There was no crash and no
error — just an absence, which is why both needed the diagnostics trail to find at all.

## The third way playback goes nowhere: a stall (2026-07-31)

Dewi, again with the screen off: *"I expected the next item to be played as it finished the
first item, but it didn't auto play."*

This time neither watcher was asleep — both were running and neither had anything to react
to. A 41-minute video reached 2506062ms, **seven seconds from its end**, went to BUFFERING at
07:55:48 and was still at exactly that position 46 seconds later, across two 30-second
snapshots, until he picked the next item by hand. Sixty-five items were queued behind it.

That is a third state, distinct from the two the app already handled:

| What happened | Signal | Who acts |
|---|---|---|
| Item finished | `hasEnded` | `AutoAdvancer` |
| Stream died | `StreamFailure` | `ExpiredStreamRecovery` |
| Item froze | **nothing at all** | `StallWatchdog` |

`StallWatchdog` treats a position that has not moved for 20s while buffering, within 15s of
the duration, as an end and advances. A stall earlier in an item is **logged only** — same
fault, just as fatal in a pocket, but re-resolving mid-item would restart the video every
time a train went through a tunnel, and there is not one observation of it yet to design
against. The log carries the position and the duration so the next report can settle it.

### The thing that nearly shipped doing nothing

The first version collected `PlaybackController.state`. Its tests failed, which is the only
reason this is worth writing down: **`state` is a `StateFlow`, and a `StateFlow` drops a value
equal to the one before it.** A stall is by definition a run of identical states — same item,
same position, same buffering flag — so a collector gets exactly one emission when the stall
starts and then silence. An emission-driven timer would have been read once, at zero elapsed,
and never fired.

Nothing about that failure is observable: no crash, no error, no log line, just a watchdog
that quietly never triggers. It would have looked shipped and fixed. The tests catch it
because they hold the state completely still and let *time* pass, which is what a stall
actually is — so the watchdog samples on a clock instead of collecting.

Generalising: **when the signal you need is "nothing has changed", a conflating flow cannot
carry it.** Sample, don't observe.

### Files

- `app/…/playback/StallWatchdog.kt` — the sampler and the end-of-item decision

### Tests

`StallWatchdogTest` (11 cases, built on the report's real numbers): the reported stall
advances; 19s does not; a long stall advances exactly once; a mid-item stall is left alone; a
paused player is not a stall; buffering that keeps progressing is not a stall; a recovered
stall does not bank its time towards a later one; auto-play-off reports but does not play;
each item gets its own stall; an unknown duration is never the end; no state is not a stall.
