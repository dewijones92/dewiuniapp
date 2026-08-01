---
title: Finding public-domain films and TV
kind: todo
area: search
priority: medium
status: refining — needs a routing decision from Dewi
updated: 2026-08-01
---

# Finding public-domain films and TV

Dewi, 2026-08-01: wants the app to find classic public-domain shows and films — naming *The
Beverly Hillbillies*, *The Andy Griffith Show* and *Dragnet* — and to download or stream them
"just like YouTube", suggesting torrent sites as the source.

The goal is clearly right for this app: it is a media app with a unified search seam and a
unified playback path, and a third catalogue of legally-free films and TV fits both without
inventing anything. **The source is the decision**, and it is Dewi's to make, so this is written
up rather than built.

## Two corrections worth having before choosing

**Public-domain status is per episode, and widely misreported.** Some early *Beverly Hillbillies*
episodes did lapse through renewal oversights, and some original *Dragnet* episodes are free —
those are real. *The Andy Griffith Show* is generally still under copyright; the "select early
episodes" claim circulates but does not hold up. Nothing here should be taken as legal advice,
and the practical rule is simple: **a site labelling something public domain is not evidence
that it is.** A source that curates and states provenance is worth far more than one that does
not.

**A general torrent index is mostly not that.** It will happily return current films under the
same search that returns *Dragnet*. That is a product problem as much as anything: a feature
aimed at classic TV that mostly surfaces new releases is not the feature asked for.

## Three routes

### A — Internet Archive (recommended)

A large, curated, genuinely-free collection with a real search API, and it contains exactly the
material named. It fits the app as it stands with no new machinery:

- A `SearchSource` returning a new `SearchHit` variant, alongside the iTunes and InnerTube ones.
- Items expose direct HTTP media URLs, so `MediaItem.mediaUrl` works unchanged and both playback
  and downloads (`HttpDownloadStrategy`) already handle them.
- No torrent engine, no new permissions, no ambiguity about what is being fetched.

Roughly a day's work, most of it search-result parsing and artwork.

### B — Hand off to the qBittorrent already on the Pi

His Pi runs qBittorrent inside gluetun behind PureVPN with a kill-switch (dot-files
`vpn-stack`), plus Jellyfin and a file browser. So the app need not torrent at all: it could add
a magnet to that qBittorrent over its WebUI API and play the finished file from the Pi.

Keeps torrent traffic where it already belongs — behind the VPN, not on a phone's connection —
and adds no torrent code to the app. Needs the phone to reach the Pi (it does, via `wg-home`),
and is only useful at home or on the VPN.

### C — A torrent client inside the app

Sequential-download streaming, as Stremio does. By far the largest build: a torrent engine
(`libtorrent4j` is the realistic option), piece prioritisation for playback, storage management,
and a peer-exposed IP on mobile data unless it is tunnelled. It also carries the highest chance
of pulling in material that is not what was asked for.

Not recommended as a first move, and pointless if A covers the actual want.

## The question for Dewi

Is the goal **"watch these specific old shows"** — in which case A gets there quickest and
cleanest — or **"the app can fetch anything from a torrent"**, which is B or C and a different
project? A and B are not exclusive; A is the better first step either way.
