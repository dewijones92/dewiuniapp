---
title: Permanent vs transient failure
kind: feature
status: shipped
area: playback
updated: 2026-07-28
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
