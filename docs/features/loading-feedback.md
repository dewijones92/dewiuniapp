---
title: Loading feedback — one global bar, and not needing it in the first place
kind: feature
status: shipped
area: ui
updated: 2026-07-31
---

# "Why does go to channel take AGES"

Dewi, 2026-07-31, with a diagnostics report: *"things like 'go to channel' take ages ....
why ?????????? also we need GLOBALLY middleware maybe??? a spinner gui when ANYTHING is
loading please"*.

Two requests, and they are not the same thing. The second is feedback; the first is a bug the
feedback would only have made visible. Both were done, in that order of importance.

## 1. "Go to channel" was 12.5 seconds. It is now 59 milliseconds.

Measured from the report, not guessed:

```
12:27:55.024  [place] videos left …                          ← the tap
12:28:03.054  [engine] JS runtime: …/qjs                     ← 8s: Python + JS runtime start
12:28:07.501  [engine] extract watch?v=sF7V5VrnH_I in 4446ms ← extracting a VIDEO
12:28:07.563  [channel] subscribed? Owen Jones id=UCSYCo8…
```

To open a channel, `DefaultSourceLocator` ran a **full yt-dlp extraction of one of its
videos** — an embedded CPython interpreter, a QuickJS runtime, and a network round trip —
purely to read `uploader_url` off the result. The report also shows the same video extracted
twice, 4.5 seconds apart.

**YouTube had already sent the channel id.** Every tile in a TV feed response carries it, in
the tile's own long-press menu (the "Go to channel" entry YouTube renders itself): verified
**45 of 45** against a live subscriptions feed. `FeedVideo.channelId` now reads it, and
`MediaItem.sourceUrl` carries it to `SourceLocator`, which prefers it over the engine.

Measured after, the same way:

```
12:45:08.293  [place] videos left …
12:45:08.352  [channel] subscribed? The Rest Is Politics id=UCsufaClk5if2RGqABb-09Uw
```

**59ms, and no `[engine]` line at all.** 210× faster, because the work was never necessary.

Two details worth keeping:

- The menu entry is found **by shape, not position**. In the live response it sat at index 3,
  behind two other `menuNavigationItemRenderer`s carrying no `browseEndpoint`. A channel
  browse is the only entry whose `browseId` is a `UC…`, so that is the test. A fixed index
  would have worked on that feed and quietly broken on the next.
- `MediaItem.sourceUrl` is named pillar-neutrally on purpose. `sourceId` is the *listing* an
  item arrived in — `ytfeed:SUBSCRIPTIONS`, not the channel — which is precisely why the app
  had to go and discover the source at all.

The yt-dlp path stays as the fallback, for a channel reached by a pasted handle whose URL
carries no id.

## 2. A global loading bar

One `Busy` seam in `:lib:common`, reported to from **two boundaries** rather than from every
screen — a per-screen flag is a thing you can forget; a boundary is not:

| Boundary | Covers |
|---|---|
| `BusyInterceptor` (OkHttp) | InnerTube, podcast feeds, SponsorBlock, iTunes, signature timestamps |
| `BusyYtDlpEngine` (decorator) | extraction, search, channel fetch — the slow work |

`BusyBar` in `AppShell` draws one thin indeterminate bar, last in the Box so it sits over
every screen including the full player, and as an overlay so nothing shifts when it appears.

Work is **named**, not counted, so a report can say what the app was waiting on rather than
just that it was.

### The timing is the feature

- **Waits 250ms before appearing.** Most calls finish in tens of milliseconds; a bar that
  flashed on all of them is noise the eye learns to ignore.
- **Stays 400ms once shown**, so it reads as feedback rather than a glitch.

### What is deliberately excluded

**Downloads.** They run for minutes, and a bar lit for the whole of one says nothing — the
entire value is telling working from idle. `AppContainer` keeps a `transferClient` without the
interceptor for `HttpDownloadStrategy`, and `BusyYtDlpEngine` passes `download` straight
through. Downloads keep their own progress row and notification.

Thumbnails are not affected either way: Coil builds its own OkHttp client, so scrolling never
touches this.

## Files

- `lib/common/…/Busy.kt` — the seam
- `app/…/busy/BusyInterceptor.kt`, `BusyYtDlpEngine.kt`, `BusyBar.kt`
- `lib/common/…/WatchUrl.kt` — `isYouTubeChannelId` / `findYouTubeChannelId`, one copy of the shape
- `lib/innertube/…/feeds/VideoTileParser.kt` — reads the id off the tile menu
- `core/data/…/source/SourceLocator.kt` — prefers the stated source

## Tests

`BusyTest` (5: reported while running and gone after; a **throw still releases** — the case
that would light the bar forever; two identical labels counted separately; double-close is
harmless; work is named). `VideoTileParserTest` (+1: the id is read wherever it sits in the
menu, and is null when absent). `SourceLocatorTest` (+1: a listing that named its channel
needs **no extraction at all** — asserted via `FakeYtDlpEngine.extractCalls`, because
asserting the engine was never called is asserting the 12.5 seconds are gone).
