---
title: Play history screen
kind: todo
status: open
area: library
priority: medium
requested: 2026-07-24
updated: 2026-07-24
---

# Play / watch history screen

**Ask:** a place to see play history.

The app has `playback_progress` (per-item position) in the DB and
`WatchHistorySync` (mirrors video progress to YouTube). Neither surfaces a local
"recently played" list across both pillars.

**Approach (unified):** record a last-played timestamp per `MediaItemId` (extend
the progress store, or a small `PlayHistoryStore`/DAO), and show a "Recently
played" list — both podcasts and videos — likely as a Library tab section or its
own screen. One history seam, both pillars; rows reuse `MediaItemRow`.

**Done when:** recently-played items (both pillars) show in a history list, most
recent first, tap to resume.
