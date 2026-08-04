---
title: Show how far ahead the file is buffered
kind: todo
area: playback
priority: medium
status: shipped — seconds-ahead gauge on the scrub bar
updated: 2026-08-04
---

# Show how far ahead the file is buffered

Dewi, 2026-08-02: *"lets make it clear in the gui how much of the 'future' of the file is
downloaded???? some sort of gauge in seconds or??"*.

## Why it matters more for torrents than anything else

A YouTube stream either keeps up or it does not, and the answer arrives within a second. A
torrent is different: it depends on seeders, on which pieces the swarm happens to hold, and on
whether you have just seeked into a region nobody has sent yet. Report 0.1.317 shows exactly
that shape — a stall of 20 seconds with **360ms buffered**, recovering, stalling again. Right
now the app shows a spinner for all of it, so "will this settle down or should I pick something
else?" is unanswerable from the screen.

Seconds, not a percentage. Ahead-of-playhead is what decides whether you can keep watching; a
percentage of a 1.7GB file says nothing about the next ten seconds.

## Where the number comes from

Two sources, and they answer different questions:

- **The player.** `Player.getBufferedPosition()` minus the current position is what ExoPlayer
  actually holds and can play without asking for more. This is the honest "will it keep going".
  Already available — `PlaybackAnalytics` sees the loads that fill it, and the stall watchdog
  already reads the buffered figure (`STUCK (360ms buffered)` in the report).
- **The server.** TorrServer reports `preloaded_bytes` and per-file piece state, so it can say
  what it holds beyond what the player has taken. Useful, but a second network call per tick and
  it describes the Pi rather than the phone.

Start with the player's own number. It needs no new I/O, it is the one that governs playback,
and if it turns out to be insufficient the server's view can be added behind the same UI.

## Shape

- A thin secondary track on the scrub bar showing buffered-ahead — the convention every player
  uses, so it needs no explanation.
- Plus **seconds in words** when it is low, because a bar an eighth full does not read as "three
  seconds left". Something like `12s buffered` under the controls, and a distinct state when it
  is falling rather than merely small.
- Say when the buffer is going BACKWARDS. That is the difference between "it will catch up" and
  "pick something else", and it is the whole question being asked.

## Careful of

The position ticker already drives skip-segment enforcement; adding a second timer for this
would be a second clock disagreeing with the first. Reuse the existing one.

And it must not become chatty in the diagnostics buffer — the log-volume rule applies, so any
trail from this is counted and periodic, never per tick.

## Status corrected, 2026-08-04

Said "requested" while `BufferAhead` (9 unit tests) had been shipped and wired into
`ui/player/SeekBar.kt`. Corrected against the code.
