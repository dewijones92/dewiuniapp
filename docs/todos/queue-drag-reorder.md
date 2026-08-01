---
title: Queue drag-and-drop, AntennaPod-grade
kind: todo
area: queue
priority: high
status: shipped (auto-scroll); pickup + resilience open
updated: 2026-08-01
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
