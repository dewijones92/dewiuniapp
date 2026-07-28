---
title: UI polish — transient overlay controls, and other candidates
kind: todo
status: refining
area: ui
priority: medium
requested: 2026-07-25
updated: 2026-07-28
---

# Make the UI nicer

**Ask:** the quality selector should be a **transient overlay dropdown on the video**
(PipePipe-style) rather than chips selectable beneath the video. "Maybe there are
other areas we could improve the UI?"

The brief's bar is "genuinely nice, modern — Material 3 expressive, considered
motion, never template-default". The app is currently *tidy* M3 but plain, and the
full player in particular has drifted into a settings list.

## 1. The concrete ask: quality (and speed) as overlay controls

Today the full player stacks, vertically, beneath the video: speed chips
(0.8x…2.0x), Sleep timer, Skip silences, Auto-play next, quality chips
(1080p…144p), Listen, then Like/Dislike/Watch Later. That's a lot of chrome and it
pushes the description and comments far down.

**Proposal:** put transient controls **on** the video, PipePipe/YouTube-style — a
small overlay row (⚙ / quality / speed) that appears with the playback controls and
fades with them, opening a compact dropdown or bottom sheet. The persistent stack
beneath the video then shrinks to what belongs there.

Both pillars: a podcast has no video surface, so its controls stay in the sheet/stack
— the overlay is a video affordance, and the *content* of the menu is shared.

## 2. Other candidates (my observations from driving the app this session)

| # | Observation | Suggested fix |
|---|---|---|
| a | **Player control stack reads as a settings list** — 7 rows of controls under the video | Collapse the rarely-changed ones (sleep timer, toggles, quality, speed) behind one ⚙ "playback settings" sheet; keep only Listen/Watch + actions inline |
| b | **Feed rows are tall** — a 3-line wrapping title plus subtitle, ⋮ and download button makes each row ~200dp | Either cap the title to 2 lines, or move to a YouTube-style card (wider thumbnail, metadata below) for the video pillar while keeping the compact row for podcasts |
| c | **Queue rows carry three icon buttons** (↑ ↓ ✕) | Drag-to-reorder (already noted as not-done) and demote ✕ to a swipe |
| d | **"Go to channel" uses an AccountCircle icon** | A channel/subscription icon reads better; ideally the channel's avatar |
| e | **Now-playing in the Queue tab is text-only** | Artwork + a slim progress line, so the tab feels like a player surface |
| f | **Mini player has no next button** | Now that the queue is the spine, a skip-next belongs there |
| g | **Tab transitions are a plain AnimatedContent** | Considered motion: shared-axis transition between tabs, container-transform when opening a source page |
| h | **Empty states are generic** | They're fine, but the Queue's could invite the first action ("tap anything to queue it") — it already does; others could match |
| i | **No collapsing header on feeds** | A large-title header that collapses on scroll is the M3-expressive default and would give the feeds identity |

## Open questions for Dewi

- Start with **(1) + (a)** — i.e. move quality/speed onto the video and collapse the
  rest behind one settings sheet? That's the biggest single improvement and directly
  answers the ask.
- Of the rest, which bother you? My ranking by payoff: **(b) feed row height**, then
  **(f) mini-player next**, then **(g) motion**, then **(e)**.
- Any appetite for the bigger swing of **(i) collapsing headers** across the feeds —
  more visual identity, but it touches every feed screen.

**Done when:** quality/speed are transient overlay controls on the video, the
persistent stack beneath it is short, and whichever of the other items you pick are
done.

---

## Shipped 2026-07-25 — the quality/speed overlay

The headline item is done: quality and speed are transient menus **on** the video, the
PipePipe pattern Dewi asked for, and the button row beneath the player is gone. Speed keeps
its inline row for audio, which has no overlay to hang it on. Detail in
[`docs/features/video-settings-overlay.md`](../features/video-settings-overlay.md).

Also folded in while there: media rows cap titles at two lines (they were running to five),
and the playback-rate list stopped existing twice.

Still open in this doc: the remaining sweep for rough edges elsewhere in the app.

## Shipped 2026-07-28 — mini player, motion, queue marker

Dewi: "jazz up the UI a bit, get creative". Picked by what you touch most often rather
than what photographs best.

**(f) Mini player.** Artwork with the pillar glyph on its corner, title + channel, and a
skip-next. The progress line became a 2dp hairline: the default indicator is thick enough
to read as a draggable control, and this is a status line.

**(g) Motion.** Shared-axis slide between tabs, keyed off the tab ordinal so direction
matches the row. Deliberately short travel — a third of the width would imply the tabs can
be swiped between, which they cannot. Lives in `ui/motion/SharedAxis.kt` as shared design
vocabulary.

**(e) Queue now-playing.** Brand-tinted bar plus the item's progress, still in place in the
list rather than promoted to a card — the playing item being a queue member is a deliberate
decision and worth keeping.

Still open from the candidate list: **(c)** demote queue ✕ to a swipe, **(d)** channel avatar
instead of the AccountCircle glyph, **(i)** collapsing large-title headers across the feeds.
(i) remains the biggest identity win and the biggest job — it touches every feed screen.
