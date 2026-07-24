---
title: Long-press context menu on items
kind: todo
status: open
area: ui
priority: medium
requested: 2026-07-24
updated: 2026-07-24
---

# Long-press context menu

**Ask:** long-press an item to bring up a menu (browse channel, etc.). "What do
apps typically use?"

**Answer / approach:** the standard modern Android pattern is a Material 3
`ModalBottomSheet` of actions (what YouTube uses), triggered by both long-press
(`Modifier.combinedClickable(onLongClick = …)`) and the existing overflow (⋮).
The app already has a small `QueueMenu` `DropdownMenu` on `MediaItemRow`
(Play next / Add to queue) — expand to a bottom sheet with more actions:

- Play next / Add to queue (exist)
- Go to channel (needs the item's channel — ties into channel browse)
- Download / remove download
- Share

Unified: one sheet component for `MediaItemRow`, used everywhere (both pillars);
actions that don't apply to a pillar are simply absent.

**Done when:** long-press any media row opens the sheet; actions work.
