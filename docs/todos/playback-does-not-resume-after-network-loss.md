---
title: Playback never resumes after the network comes back
kind: todo
area: playback
priority: high
status: open
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
| **IDLE after a connection failure** | **nobody** | **no** |

`Media3PlaybackController.onPlayerError` gates the `StreamFailure` emission on
`looksExpired()`. A `SocketException` / "Failed to connect" is not an expired lease, so no
signal is raised and nothing retries. `StallWatchdog` deliberately requires `isBuffering`, so
it does not fire either — correctly, since the player is not buffering.

## Why this matters more than it sounds

Stopping when the network dies is right. **Staying stopped when it returns is not.** This is
the tunnel case, and Dewi listens with the screen off in exactly that situation — which is the
same complaint ("the queue just stopped") that produced the stall watchdog, arriving by a
different route.

## The shape of a fix

React to a non-expired player error by waiting for connectivity and replaying from the saved
position. `NetworkStatus` already exists in `:app`, and `ExpiredStreamRecovery` already owns
"replay the current item from a position" with an attempt budget — so this is most likely a
widening of that seam rather than a fourth watcher. Whether it should also require the user's
intent (they may have deliberately stopped) is the open question.

Not built: this is a behaviour change to shipped playback beyond what was reported, so it
wants a decision first.
