---
title: Queue-first playback (AntennaPod-style) + Queue tab
kind: todo
status: refining
area: playback
priority: high
requested: 2026-07-24
updated: 2026-07-24
---

# Queue-first playback + a Queue tab

**Ask:** AntennaPod has a Queue tab on the bottom bar. Everything you press in the
app is queued at the current position in the queue. Make that the **default**
behaviour here — but keep a "peek" option on long-press for items (video or
podcast).

This is the biggest structural change on the backlog: it makes the queue the
**spine** of playback rather than a side-car, which is squarely in line with the
Unified law (one playback entry point for both pillars).

## What exists today

- `PlaybackQueue` (`app/queue/`) holds only what plays *after* the current item,
  **in memory** (`MutableStateFlow`), lost on process death.
- Tapping a row calls each screen's own `play()` → `PlaybackController.play` (podcast)
  or `VideoPlaybackLauncher.play` (video). The queue is not involved, so tapping
  something **discards** the queue's relevance.
- Up-next is a section inside `FullPlayer`; there is no queue destination.

## Proposed shape

1. **One playback entry point.** `PlaybackQueue.playNow(item)` inserts the item at
   the current position and plays it, leaving the rest of the queue intact behind
   it. Every screen's `onPlay` routes through it — so "tap to play" and "queue"
   stop being two different code paths. (Both pillars, one seam.)
2. **The queue becomes persistent.** A Room-backed `QueueStore`, reusing the
   existing denormalized `PlaylistItemColumns` + `playlistItemFrom` mapper (same
   shape already shared by local playlists and play history — no third schema).
   A queue with its own tab must survive a restart.
3. **A Queue tab** in the bottom bar: the full queue (current item + up-next),
   drag-to-reorder, swipe/✕ to remove, tap to jump, clear all. `PlaybackQueue`
   already has `move`, `removeAt`, `clear`, `playFromQueue`.
4. **"Peek" on long-press** = play this **without touching the queue** (a one-off
   listen; today's behaviour), added as one more `SheetAction` in the existing
   `MediaItemRow` sheet, so it lands on every feed on both pillars at once.

## Open decisions

- **Bottom bar shape.** Five tabs today (Videos / Podcasts / Search / Library /
  Account). Adding Queue makes six, past Material 3's 3–5 guidance. Options: move
  Account off the bar (top-right avatar, or into Library) and keep five, or accept
  six.
- **Peek semantics** — confirm "play now, don't touch the queue" (vs a details
  preview).
- Does `playNow` on an item **already in** the queue move it, or duplicate it?
  (Proposal: move it to the current position — no duplicates, matching how play
  history de-dupes.)

**Done when:** tapping anything anywhere queues-at-current-position and plays;
the Queue tab shows and edits that one queue; it survives a restart; long-press →
Peek plays without disturbing it.
