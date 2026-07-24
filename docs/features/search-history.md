---
title: Search history
kind: feature
status: shipped
area: search
updated: 2026-07-24
---

# Search history

Recent search queries offered again in the search screen's idle state.

## Seam

`SearchHistoryStore` (port, `:core:data` `data/search/`) — one store for the
single unified search box (both pillars), most-recent-first, de-duplicated
case-insensitively, capped at 10.

- Fake: `InMemorySearchHistoryStore` (`:core:data`, tests/previews).
- Impl: `SharedPrefsSearchHistoryStore` (`:app` `search/`), newline-separated
  (queries are single-line), held in a `StateFlow` so the UI observes changes.
- Wired in `AppContainer.searchHistoryStore`.

## Behaviour

- `SearchViewModel` records a query on **explicit submit** (search button / IME
  action / tapping a history item) — not on every keystroke.
- Idle state shows "Recent searches" (tap to re-run, X to forget one, Clear all)
  instead of the empty prompt once there's history.

## Files

- `core/data/.../data/search/SearchHistoryStore.kt`, `fake/InMemorySearchHistoryStore.kt`
- `app/.../search/SharedPrefsSearchHistoryStore.kt`
- `app/.../ui/search/SearchViewModel.kt`, `SearchScreen.kt`

## Tests

`SearchHistoryStoreTest` (order / dedup / cap / remove / clear). Verified
on-device: submit → clear box → query appears under Recent searches.
