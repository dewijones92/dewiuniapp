---
title: Offline playback, proven with the radios off
kind: todo
area: testing
priority: high
status: done — test in CI, and it found a real bug
updated: 2026-08-03
---

# Offline playback, proven with the radios off

Dewi, 2026-08-03: *"put e2e tests that test that offline stuff (i.e. stuff in the queue) can be
played successfully offline … this means putting emulator offline"*.

`OfflineQueuePlaybackTest`, in the CI emulator job. Two phases in one test, because the order is
the point: download while online through the **real** download path, then take the device genuinely
offline and play what was downloaded. Split across two tests, the offline half would pass on a
device that had never downloaded anything.

## It found a real bug on its first run

**Nothing told the queue about a completed download.** `PlaybackQueue` read `handle.localPath` — a
snapshot taken when the item was *queued* — and nothing updated it when the file arrived. So the
auto-downloader would fetch all 84 items in the queue and every one of them would still try to
stream, because their handles predate their files.

That is the whole offline feature failing silently, and it is invisible anywhere with a connection:
the stream works, so nothing looks wrong until you are on a plane. No report would have shown it.

Fixed by asking at play time — `downloadedPath(item.id)` against the download store — rather than
trusting the handle. One seam, so it holds for anything in the queue however it got there.

## Two ways this test could have passed while proving nothing

Both are guarded, and the first one actually fired:

- **It streamed instead.** The first run played from `http://127.0.0.1:…` and passed every
  liveness check — the local test server is on loopback, which survives the radios going off. So
  the test asserts the player was handed the **local file**, not merely that audio came out.
- **The network was never off.** If `svc` silently failed this would be an online test wearing an
  offline name, so being offline is asserted through `ConnectivityManager` — the same source the
  app consults — before anything is played.

## Radios off, not a packet filter

`iptables -j DROP` leaves Android reporting the network as VALIDATED, so every connectivity-aware
path carries on believing it is connected. That cost a day on this app on 31 July: the filtered run
looked like a successful reproduction while leaving the code under test untouched. `svc wifi
disable` + `svc data disable` is what makes the OS agree.

The radios are restored in `@After` unconditionally. Test-class order is not guaranteed, so a
leaked offline device would fail every later test in the run for a reason nowhere near the code
that appears broken.

## Still worth adding

- The **negative** case: a queued item with no download should be visibly "waiting" offline rather
  than spinning. `OfflineReadiness` already models it and is unit-tested; the UI assertion is not.
- The **reported-online-but-broken** case (the iptables row above), which is the shape of report
  0.1.332 — see `docs/todos/buffering-defects-0.1.332.md`.
