---
title: Search results arrive per section
kind: feature
status: shipped
area: search
updated: 2026-08-07
---

# Search results arrive per section

Dewi, 2026-08-07: *"the search in the app is quite slow as its blocked by the torrent search??????
come up with sensible ui/ux"*.

## What was wrong

Not concurrency — all three sources were already started together:

```kotlin
val podcasts = async { … }; val videos = async { … }; val torrents = async { … }
toLoaded(podcasts.await(), videos.await(), torrents.await())
```

That last line threw the concurrency away. Nothing reached the screen until the **slowest** source
finished, and the slowest is always the torrent search: it goes out to Prowlarr and through
FlareSolverr, which is seconds at best, and off the home network the request does not fail fast — it
hangs until it times out. So a YouTube search that had answered in a moment sat behind it with
nowhere to be shown, and the whole feature felt broken.

## The shape that fixes it

A section has a **lifecycle**, which the old model could not express. It was a list plus a `failed`
boolean per section, so "nothing yet" and "nothing found" were the same value — and if you cannot
say "still looking", you cannot show partial results without lying about the rest.

`SearchSection<T>` in `:core:data` names all four states, once, for every section:

| State | Means | Screen shows |
|---|---|---|
| `Searching` | still out | the heading, with a slim bar under it |
| `Found` | it answered (possibly with nothing) | its rows, or nothing at all when empty |
| `Failed` | it could not answer, and why | the heading and a reason |
| `Absent` | this section does not exist here | nothing — no home server, no heading |

`Absent` earns its place: someone who has never set up a home server would otherwise see a torrent
section permanently reporting a problem they do not have.

`SearchViewModel.searchStream` emits once with everything `Searching` and again each time a source
answers, updating only its own slot. `channelFlow` carries the emissions from three sibling
coroutines and closes when they finish; `transformLatest` upstream cancels the lot when the query
changes, so a search nobody is waiting for stops making requests.

## UI

One renderer, `hitSection`, draws all three — the same reason there is one `SearchSource` and one
`SearchHit`. A fourth section would render correctly without anything new being written.

- **A waiting section keeps its heading.** Seeing "From your home server" with a quiet bar under it
  says more is coming; an absent heading would say that is everything.
- **The indicator is deliberately slim**, not a spinner or a skeleton. Results above and below it are
  already usable, so it must read as "more may arrive", not "the screen is busy".
- **An empty answer shows nothing at all.** With three sections landing separately, empty headings
  would be on screen for most of a search rather than none of it.
- **The home server gets its own failure wording** — it is only reachable at home or on the VPN, so
  the usual cause is where you are, not a fault to go looking for.

Each section is also bounded at 20 seconds. Generous, because a slow answer is still worth having
once the rest is on screen; the bound only exists so a section cannot spin for ever.

## Diagnostics

Per section, with its own timing: `"ceuta" torrents after 8412ms -> 12`. "Search was slow" could
never say *which* source was slow, which is the only question worth asking about it.

## Coverage

| Level | What it holds |
|---|---|
| JVM unit | `SearchStreamsPerSectionTest` — videos on screen while other sources are still out; a slow source not holding back a fast one; sections settling independently; one failure marking only its own section; the torrent section absent with no home server; **Dewi's exact case** (home server configured, torrents slow, YouTube already listed); and an unanswered section ending `Failed` rather than spinning |
| Instrumented | `SearchSectionStatesTest` — what each of the four states looks like: a waiting section keeps its heading, an empty one disappears, a failing one explains itself, and the home server's failure names the network rather than a fault |

Four of the seven JVM cases were watched failing against the old await-everything shape.

The tests hold the clock deliberately (`advanceTimeBy` past the debounce, then `runCurrent`) rather
than `advanceUntilIdle`, which would run virtual time past the section timeout and report every
source as unanswered before the test could answer for it.
