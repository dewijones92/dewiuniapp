---
title: Bottom tabs should remember where you were
kind: todo
status: shipped
area: navigation
priority: high
requested: 2026-07-28
updated: 2026-07-28
---

# Switching tabs threw away where you were

**Ask (Dewi, 2026-07-28):** "you know the bottom buttons? 'videos'/'podcasts'/'queue' etc
— when I switch between them it resets where I am."

Each bottom-nav destination should keep its own state while you are away from it: how far
you had scrolled, and anything you had navigated *into* from that tab. Before this, leaving a tab
and coming back dropped you at the top of a freshly-built screen — so a scroll through a long
subscriptions feed was lost to a glance at the queue, which made the tabs feel unsafe to
use.

The expected behaviour is what every mainstream app does: tabs are parallel stacks, and
switching between them is suspending one and resuming another, not restarting it.

## Two distinct things to preserve

1. **Scroll position** within each tab's list — needs the `LazyListState` to survive, which
   it does not when the composable leaves composition and its ViewModel/state is rebuilt.
2. **Navigation depth** within a tab — if you were three screens deep in Podcasts (feed →
   episode → description), Videos and back should return you there, not to the root.

The second is the bigger design question and probably decides the first: it depends on
whether each tab owns a nav stack, or whether the shell keeps one stack and tabs are just
roots. Worth settling deliberately rather than patching scroll state onto the current
arrangement.

## Where it lives

`AppShell` — the one place bottom navigation is defined, so the fix is a single seam for
all pillars, not per-tab handling. Whatever mechanism preserves state must apply to every
destination automatically; a tab that has to opt in is a bug waiting to happen when a
fourth is added.

## Watch out for

- State kept alive for every tab forever is a memory question — bounded, but worth
  measuring against the ~115MB idle baseline.
- The queue tab arguably *should* jump to the current item rather than restore scroll;
  confirm with Dewi rather than assuming symmetry.

## Shipped (2026-07-28)

Both halves, in `8290161`.

`AppShell` swaps destinations through `rememberSaveableStateHolder` keyed per destination —
wrapped around the whole `when`, so every tab gets it and a new one cannot forget to opt in.
That restores scroll position and anything already held in `rememberSaveable`.

It was not enough on its own: sub-navigation was in plain `remember`, which saves nothing.
Library's playlists/history/account flags and its open playlist, Podcasts' open feed, and
Search's browsed channel are all saveable now. The last two needed a saver, since a domain
object cannot go in a Bundle — `ui/common/MediaSourceSaver.kt` serves both pillars from one
encoding, because a channel and a feed differ only in which URL they carry.

Verified on a release build: Library → Playlists → Videos → Library landed back **inside**
Playlists. Rotation and process death benefit for free.

The queue-tab question raised above was not resolved — it still restores like the others
rather than jumping to the current item. Left until it actually annoys someone.
