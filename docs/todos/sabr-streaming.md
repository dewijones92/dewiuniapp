---
title: SABR streaming (kids videos stuck at 360p)
kind: todo
area: video
priority: medium
status: open
updated: 2026-07-30
---

# SABR-only formats

Made-for-kids videos (Ms Rachel and everything like her) play at **360p only**, while
SmartTube plays the same videos at full quality. Dewi spotted the discrepancy, and it is
real — 360p is not what YouTube has, it is what yt-dlp can currently reach.

## What is actually happening

Measured 2026-07-30 against yt-dlp 2026.07.04 (which IS the newest on PyPI):

- Only the `android` player client returns any usable stream: format 18, 360p progressive.
- The higher formats **are in the player response** but carry no URL. yt-dlp says so:
  *"Some android client https formats have been skipped as they are missing a URL. YouTube
  may have enabled the SABR-only streaming experiment"* — [yt-dlp#12482].
- Ruled out, each tested rather than assumed: every one of the 12 player clients (none
  beat 360p); cookies exported from a signed-in browser (made it worse — the authenticated
  web client is fully SABR); `formats=missing_pot`; a newer yt-dlp (there isn't one).

Ordinary videos are unaffected — they still resolve to 1080p+ ladders.

## Why SmartTube manages it

SmartTube is a full InnerTube client: it speaks YouTube's own streaming protocol (SABR/UMP)
rather than extracting a plain URL and handing it to a player. That is the difference, and
it is a project rather than a flag.

## Options, none of them small

1. **Implement SABR/UMP in `:lib:innertube`.** We already own an InnerTube layer with TV
   OAuth, which is the hard half of what SmartTube has. Needs the UMP protobuf framing and
   a Media3 `DataSource` that speaks it. Biggest job, best outcome, and would also protect
   us when YouTube widens SABR beyond kids content — which is the way this is trending.
2. **PO token provider.** Restores URLs for web clients. Needs BotGuard JS execution, which
   Chaquopy cannot do — it would mean shipping a JS runtime or calling out to a server.
3. **Leave it.** Kids content plays at 360p; everything else is unaffected.

Worth deciding before YouTube extends the experiment, not after.

[yt-dlp#12482]: https://github.com/yt-dlp/yt-dlp/issues/12482
