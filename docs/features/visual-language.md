---
title: One visual language across the app
kind: feature
status: shipped
area: ui
updated: 2026-08-07
---

# One visual language

Dewi, 2026-08-07: *"make whole app sexy please without losing funcitonality :) ... i trust you to
make it slick and sexy"*.

## The leverage: one row, ten screens

Videos, Search, Library, Queue, History, Playlists, Channel, Podcasts and Notifications all render
through `MediaItemRow`. Restyling it restyles the app — which is also why it is the riskiest thing in
here: one dropped affordance is nine screens losing it at once.

| Change | Why |
|---|---|
| Artwork **120×68** (was 96×54) | The artwork is the fastest thing to recognise in a list and it was the smallest thing in the row. Same 16:9, so nothing is cropped |
| Corners **12dp** (was 8dp) | At the larger size 8dp reads as an almost-square with the corners knocked off |
| Title at **titleSmall** (was bodyLarge) | It was set at the same weight as the subtitle under it, so a row had no hierarchy at all |
| Vertical padding **10dp** (was 16dp all round) | Every row was a third taller than its artwork needed; a screenful now holds seven items instead of five |

## The player

See [player-redesign.md](player-redesign.md) — the surface takes its colour from the artwork, a
left-aligned header, and one control strip where five stacked rows used to be.

## The frame

Bottom navigation animates the selected icon up 15%. The filled/outlined swap alone is a small
signal; scale makes which tab you are on readable at a glance rather than something you look for.

## Not losing anything, mechanically

Two guard tests, both written **against the design as it was** and kept passing through:

- `MediaItemRowKeepsActionsTest` — the row's title and channel, the LIVE and members-only badges, the
  duration, tap-to-play, and the long-press sheet with its actions.
- `PlayerKeepsEveryControlTest` — every control on the video player and on the audio player.

The badges have their own case for a reason: they are pills rather than subtitle text precisely
because the subtitle truncates, and *"you cannot actually play this"* must not be the part that gets
cut. A restyle that tidied them into the subtitle would look neater and be worse.

## Accessibility gaps found on the way

Three icons carried `contentDescription = null` beside otherwise-unlabelled controls:

- **Volume boost** — "Off / Low / Med / High" with nothing saying what they set
- **Playback speed** — "1× / 1.5× / 2×", likewise
- **Every bottom-navigation tab** — the label below is decoration a screen reader may not reach

All three found by writing inventories that could not name them either, which is a decent argument
for writing one.

## Deliberately not done

- **Item entrance animations in lists.** They look good in a demo and cost frames on a 400-item feed,
  which is what Dewi's actually is.
- **A shared card treatment for every list.** Cards inside a scrolling list add two nested surfaces
  per row for a visual gain that the larger artwork already delivers.
