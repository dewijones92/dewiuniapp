---
title: Queue drag-and-drop, AntennaPod-grade
kind: todo
area: queue
priority: high
status: shipped (auto-scroll, long-distance accuracy, surviving its own swaps); pickup open
updated: 2026-08-06
---

# Queue drag-and-drop, AntennaPod-grade

Dewi, 2026-08-01: *"make sure that the item dragger in the queue works properly???? similar to
in antennapod??? i.e. able to drag and move while scrolling?? move big distances etc"*

Reordering exists (shipped with `queue-first-playback`), but the ask is about the *feel* of it,
which is a different thing from the operation working. What a 74-item queue needs:

- **Auto-scroll while dragging.** Holding an item near the top or bottom edge should scroll the
  list under it, and keep scrolling, so an item can be moved somewhere off-screen. Without this
  a move is capped at one screenful, which on Dewi's queue is a handful of positions.
- **Big distances in one gesture** — the consequence of the above, and the actual complaint.
- **A drag that survives the list changing underneath it**: playback advancing, a download
  finishing, or the queue being persisted must not drop the item being held.
- **Sensible pickup**: long-press or a handle, with the same target size AntennaPod uses; a drag
  that starts by accident while scrolling is worse than no drag.

Verify on a queue of realistic size (his was 74 items), not three test rows — the failure mode
here is entirely about distance and duration, and a short list cannot show it.

## Shipped 2026-08-01: auto-scroll at the edges

`ReorderState` now scrolls the list while a row is held near the top or bottom, so a drag is no
longer capped by the viewport. The mechanism is worth remembering: scrolling by N pixels moves
the content under a stationary finger by exactly N pixels, so auto-scroll feeds those pixels
into the **same** accumulator a real drag uses. Swapping continues while scrolling through one
code path, instead of a second rule for "moved because the list moved underneath".

Proven rather than asserted. `ReorderAutoScrollTest` holds a row at the bottom edge and lets time
pass with the finger stationary — which emits no pointer events at all, so anything that moves
moved because the list scrolled itself. With auto-scroll disabled the same gesture produces
**exactly 13 moves and stops**: one screenful, which is precisely the limitation being fixed.

The swap arithmetic is covered separately on the JVM (`ReorderStateTest`, 7 cases) — it was
already correct, and stayed correct; what was missing was anything calling it while the finger
sat still. Testing the arithmetic alone would have proved nothing about this.

## Proven 2026-08-06: ten places in one motion

Dewi, 2026-08-06: *"make sure the items in the queue dragging drag successfully????? in one motion 10
places?????"*.

It already worked; what was missing was anything holding it to that. The three-row case could not,
and the difference is not pedantry — **the swap arithmetic is an accumulator**, so a per-swap error
(a stale row height, a remainder dropped each step, an off-by-one in where the held row is considered
to be) is invisible over three rows and glaring over ten. The bug this area actually had was exactly
that shape: measuring the row from the 24dp grip rather than the 64dp row made every gesture roughly
four times too fast, which reads as *aim for ten and land on thirty*.

Proven on the JVM (`ReorderStateTest`), and that placement is a deliberate retreat. It was written as
a gesture first and failed on CI **twice**, both times for reasons that had nothing to do with the
code: ten rows at 64dp is more travel than CI's emulator is tall, so the finger was clamped into the
auto-scroll edge zone and the test measured the clock (exactly 30 moves — reading as the very bug it
exists to catch); shortening the rows to fit then made the gesture too small for the injector's frame
timing there (1 move). Both passed on the emulator here, both times. A test that reports a swap-rate
bug because of the screen it ran on is worse than no test.

What needed proving is the **accumulator over distance**, and that is arithmetic:

- ten rows of stepped travel produce exactly ten moves, each by one place, finishing ten places down;
- the same distance arriving as one event behaves identically;
- half-row steps accumulate rather than being dropped or rounded — ten of them is five places.

The gesture-to-swap wiring stays instrumented (`draggingThreeRowsMovesExactlyThreePlaces`), as does
travelling further than the viewport (`draggingToTheBottomEdgeKeepsMovingPastTheVisibleRows`). Two
traps found while trying and recorded in that file: asserting "the list did not scroll" does not work
(`LazyColumn` re-anchors on the dragged item's key), and splitting a gesture to release the finger
costs a second lookup of a grip that has moved, which makes Compose scroll to it.

## Fixed 2026-08-07: it only ever moved ONE place

Dewi, on 0.1.359: *"i am only able to drag the items in the queue by 1 position :("*. He was right,
and every test here said otherwise.

The grip's gesture was `pointerInput(index, itemCount)`. **Every swap changes the dragged row's
index**, so the key changed, so Compose tore down the pointer input and cancelled the gesture in
flight — exactly one swap per touch, forever. `itemCount` was a key for the same reason and just as
wrong: a download finishing mid-drag would have dropped the item. Keyed on `Unit` now, with the
current index read through `rememberUpdatedState` only when a drag begins, and `handleTop`
remembered rather than a per-composition local (the gesture lambda is created once, so a plain
`var` would have left auto-scroll aiming at where the grip used to be).

### Why nothing caught it, which is the part worth keeping

Every other test in `ReorderAutoScrollTest` sets `mainClock.autoAdvance = false` so it can hold a
finger still and watch auto-scroll. **A frozen clock means no recomposition happens during the
gesture**, so the index never changes as far as the composition is concerned and the pointer input
is never restarted. The tests were structurally incapable of seeing this. Even running the clock is
not enough: inside a single `performTouchInput` block every event is delivered before Compose
recomposes. The reproduction needs the composition to *settle between* pointer events, which is the
ordinary case on a device — the queue alone recomposes on a 500ms position ticker.

**CI told me and I did not believe it.** A run reported exactly 1 move where the local emulator
reported 10, and it was written off as frame timing. It was the bug, on the one configuration that
happened to let a frame through. When an environment disagrees about a *count*, that is evidence,
not noise.

`aDragSurvivesTheRowChangingIndexUnderIt` now holds it: composition settles between each injected
move, and it was watched failing (1 move) with the old key restored. It asserts SURVIVAL and
single-step continuity, not an exact count — touch slop absorbs the opening movement, and how far a
given travel is worth is `ReorderStateTest`'s job on the JVM.

### Still open

- **Pickup ergonomics** — long-press on the grip is unchanged; AntennaPod's target size and
  feel have not been compared side by side.
- **A drag surviving the list changing underneath it** (playback advancing, a download
  finishing, the queue being persisted mid-gesture). Untested, and the failure would be a
  dropped item rather than anything visible in a log.
- **Hand-verification on a long real queue.** The UI test uses 40 synthetic rows; driving a
  genuine long-press drag through `adb input motionevent` proved unreliable (each event is a
  separate process, so the gesture timing breaks), and my attempts at it cleared the queue
  twice. Worth Dewi trying it on his 74-item queue and saying whether the speed feels right —
  `SCROLL_STEP_PX` and `EDGE_ZONE_PX` are the two numbers to tune.
