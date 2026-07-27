---
title: Row status — pillar, played, offline
kind: feature
status: shipped
area: ui
updated: 2026-07-27
---

# Every row says what it is

**Ask (Dewi, 2026-07-25):** items in *any* list should clearly state whether they're
played, whether they're downloaded offline, and whether they're YouTube or podcast.

One `MediaItemRow` serves every list, so this landed everywhere at once — feeds, search,
queue, history, playlists, Library, channel tabs.

## What a row shows

| Signal | Treatment | Why |
|---|---|---|
| **Pillar** | The antenna or video glyph — the same pair the bottom bar uses | Learnable at a glance; no legend needed |
| **In progress** | A thin sliver under the thumbnail | Says *how far*, which a label can't |
| **Played** | A bare check, plus a dimmed title | The row recedes without disappearing |
| **Offline** | A circled down-arrow in the status line | Separate from the trailing button, which is the *action* |

Three deliberate choices:

- **The glyphs differ in kind, not just shape.** Offline was first a circled check, which
  sat next to the played check and read as one signal. A down-arrow can't be confused
  with a tick.
- **Quiet by default.** An unplayed, streaming item shows only its pillar. Status that
  shouts on every row stops carrying information.
- **Titles cap at two lines.** Long podcast titles were running to five, which made every
  row a paragraph. Found by looking at a screenshot, not by reading the code.

## The gap this exposed: "played" wasn't representable

Progress rows for finished items were **deleted** — so a finished item was
indistinguishable from one never started. There was no concept of *played*, only
*part-way*. Fixed at the root rather than papered over:

```
PlayState = Unplayed | InProgress(positionMs, durationMs?) | Played
```

- `playback_progress` gains `completedAtEpochMs` (migration **v12 → v13**). The row
  survives; restarting from the beginning becomes a property of *playback*
  (`resumePositionMs` reports nothing for a completed item) rather than of storage.
- Marked automatically at end-of-item, and by hand from the row's action sheet
  ("Mark as played" / "Mark as unplayed") — AntennaPod's most-used action.
- **Replaying clears it** once real progress accrues, but a trivial position does not:
  otherwise every replay would silently mark an item unplayed on its first tick.

## Why no screen had to change

Play state is provided **once**, around the whole shell, and read as `MediaItemRow`'s
default (`LocalPlayStates` / `LocalSetPlayed`). Ten screens and their view models needed
no plumbing; passing it explicitly still works, which is what previews and tests do.

The pillar is **required** rather than defaulted: mixed lists get it from the item's
`PlayHandle` (which knows exactly — `PlayHandle.pillar`), single-pillar screens state it
outright. Nothing sniffs a URL, and a new list can't forget to say which pillar it shows.

## Tests

- `core/domain/.../PlayStateTest.kt` — fraction maths (including unknown duration and
  positions past the end), `isPlayed`, negative positions rejected, `PlayHandle.pillar`.
- `core/database/.../RoomPlaybackProgressStoreTest.kt` — finishing marks played rather
  than forgetting; part-way reports its progress; marking by hand needs no prior
  playback; marking unplayed clears everything; replaying keeps the mark until real
  progress. 14 instrumented tests green on emulator-5554.

Verified on-device: marked a queue row played → `completedAtEpochMs` set in the database
→ the row gained its check and dimmed title.

## Follow-on this unlocks

Cheap now that play state exists: a **"hide played"** filter on the feeds, auto-advance
skipping played items, and an honest Library count ("3 played, 12 unplayed").

## Offline state is provided, not plumbed (2026-07-27)

`LocalDownloadStates` joins `LocalPlayStates` at the app root, and `MediaItemRow`
defaults to it. Every screen used to pass `downloadState` itself and two did not:
search results and new-item notifications passed a hardcoded `NotDownloaded`, so a
downloaded video showed as not downloaded on exactly the screens you would find it
from. Nobody chose that — a required parameter with a plausible value to hand is easy
to satisfy wrongly.

## Played was wrong twice (audited 2026-07-27)

Asked to check items are actually labelled played. The display was fine; what set the
state was not.

**Nothing marked an item played when playback reached the end.** The only route was the
progress store's heuristic — position within 15s of the end — saved by a ticker that runs
*only while playing*. It usually got there, because the last save lands within 5s of the
end, but it could never fire for an item with no known duration (a live stream), and it
was inference where a fact was available. `STATE_ENDED` now marks it directly.

**A flat 15s tail is wrong for short items.** It marked a 30-second Short played at the
**halfway point**, and a 60-second one at 75%. The tail is capped at 10% of the item, so
"nearly finished" means the same at any length: 30s → 27s (90%), 3h → unchanged at 15s.

Verified on device: playing to the end logs `ended`, writes `completedAtEpochMs`, and the
row renders `content-desc="Played"`.
