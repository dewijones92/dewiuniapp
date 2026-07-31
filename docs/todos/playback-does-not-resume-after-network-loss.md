---
title: Playback never resumes after the network comes back
kind: todo
area: playback
priority: high
status: done
updated: 2026-07-31
---

# The third way the queue silently stops

Found on the emulator 2026-07-31 while testing the stall watchdog, by black-holing HTTPS
mid-playback (`iptables -A OUTPUT -p tcp --dport 443 -j DROP`) and then removing the rule.

**The player never comes back.** It sat at exactly 517805ms, `(stopped)`, for over three
minutes with full connectivity restored, and would have sat there forever. Resuming needs the
user to notice and press play.

## Why nothing recovers it

The load fails rather than hangs, so the player goes to **IDLE**, not BUFFERING:

| State the player lands in | Who reacts | Covered? |
|---|---|---|
| ENDED | `AutoAdvancer` | yes |
| error that `looksExpired()` (403/410) | `ExpiredStreamRecovery` | yes |
| BUFFERING, position frozen | `StallWatchdog` | yes (2026-07-31) |
| **IDLE after a connection failure** | **`StreamRecovery`** | **yes (2026-07-31)** |

`Media3PlaybackController.onPlayerError` gates the `StreamFailure` emission on
`looksExpired()`. A `SocketException` / "Failed to connect" is not an expired lease, so no
signal is raised and nothing retries. `StallWatchdog` deliberately requires `isBuffering`, so
it does not fire either — correctly, since the player is not buffering.

## Why this matters more than it sounds

Stopping when the network dies is right. **Staying stopped when it returns is not.** This is
the tunnel case, and Dewi listens with the screen off in exactly that situation — which is the
same complaint ("the queue just stopped") that produced the stall watchdog, arriving by a
different route.

## The fix

`ExpiredStreamRecovery` became `StreamRecovery` — the old name was a lie once it handled more
than expiry — and `StreamFailure` now carries a `Reason`:

- **`Expired`** (403/410) — retry at once with a fresh URL. The network is fine.
- **`Unreachable`** (any other `IOException` in the cause chain) — make *no request at all*
  until `NetworkStatus.awaitOnline()` says there is a validated connection, then replay from
  the saved position.

The two need opposite responses, which is why it is a named reason and not a boolean.
Retrying an `Unreachable` immediately just spends the retry budget on connections that never
had a chance — which is exactly what the app did.

`awaitOnline()` waits on a `NetworkCallback` rather than polling, so playback resumes the
moment signal returns; coming out of a tunnel, that difference is the whole experience. It
requires `NET_CAPABILITY_VALIDATED`, not merely `AVAILABLE`, because a captive portal or a
still-associating Wi-Fi is "connected" while unable to carry a byte.

### Retries are spaced out now, too

Found while testing this, with packets dropped by `iptables` while Android still reported a
validated network: the whole three-attempt budget was spent in **56 milliseconds** and the
item skipped, because each replay failed the instant it was tried. A retry with no gap is not
a retry. Attempts 2 and 3 now wait 2s and 4s, so the guard against a genuinely dead item
stops skipping live ones on a weak signal or a captive portal.

## Verified on-device

Emulator, radios turned off mid-playback and back on:

```
[playback] stream failed at 400491ms — Unreachable
[playback] stream unreachable at 400491ms — waiting for a network
[playback] waiting for a validated network
… radios re-enabled …
[playback] network is back — resuming from 400491ms
[playback] playing at 397974ms
```

Note `iptables`-level packet dropping does NOT reproduce this: Android still reports a
validated network, so `awaitOnline()` returns at once and the retries (now spaced) run
anyway. Turning the radios off is what makes the OS agree it is offline.
