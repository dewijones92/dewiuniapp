---
title: Every row states its pillar, played state and offline status
kind: todo
status: shipped
area: ui
priority: high
requested: 2026-07-25
updated: 2026-07-25
---

# Rows should say what they are

**Ask:** items in **any** list should clearly state whether they're played, whether
they're downloaded offline, and whether they're YouTube or podcast.

One shared `MediaItemRow` serves every list, so this is one change that lands
everywhere — feeds, search, queue, history, playlists, Library, channel tabs.

## The gap this exposes: we don't actually know what's "played"

The other two are easy. **Played is not**, and it's worth being precise about why:

`playback_progress` stores a position per item, but finished items are **deleted** —
the entity comment says so outright ("finished items are deleted so they restart"). So
a finished item is indistinguishable from one never started. We currently have no
concept of *played*, only *part-way*.

Fixing that properly means a real play state, which also delivers the AntennaPod
parity item the AI review flagged (played/unplayed + "hide played"):

```
PlayState = Unplayed | InProgress(fraction) | Played
```

- Add a `completedAt` (nullable) to the progress row instead of deleting on finish —
  the row already exists, so no new table, and "restart from the beginning" becomes a
  property of *playback* (start at 0 when completed) rather than of *storage*.
- Marked automatically when an item ends (the queue's end-transition already fires
  there), and manually via a row action ("Mark as played" / "Mark as unplayed") —
  AntennaPod's most-used action.
- Unified by construction: a video and an episode both get it.

## What each row shows

A single status line, one component, so all three read consistently:

| Signal | Treatment |
|---|---|
| **Pillar** | A small icon — the video glyph vs the podcast antenna (the same pair the bottom bar already uses, so it's learnable at a glance) |
| **Played** | Unplayed: nothing (the default state shouldn't shout). In progress: a thin progress sliver under the thumbnail — far more informative than a label. Played: a check + dimmed title |
| **Offline** | A download glyph in the status line, distinct from the trailing button (which is the *action*). Today the tick doubles as "tap to delete", which conflates state and action |

**Where the pillar comes from:** mixed lists (queue, history, playlists) already store a
`PlayHandle`, which says exactly which pillar an item is — no guessing. Single-pillar
screens pass it directly. So no URL-sniffing and no new field on `MediaItem`.

## Follow-on this unlocks (cheap once play state exists)

- **"Hide played"** filter on the podcast feed and video feeds.
- Auto-advance skipping already-played items (optional).
- A more honest Library: "3 played, 12 unplayed" rather than a flat list.

**Shipped 2026-07-25** — with the real `PlayState` behind it (migration v12 -> v13), one
status component, and play state provided once at the shell so no screen needed plumbing.
Details in [`docs/features/row-status.md`](../features/row-status.md).

The "hide played" filter and played-skipping auto-advance listed below are now cheap and
remain open.
