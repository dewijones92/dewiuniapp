---
title: Finding public-domain films and TV
kind: todo
area: search
priority: medium
status: ready — decisions made 2026-08-01, not yet started
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

## Decided (Dewi, 2026-08-01, after two reversals worth keeping)

**The Pi torrents; the phone is a remote control and a screen.** Search, add, progress and
playback all happen over plain HTTP to the Pi, gated by the existing Google login and restricted
to `dewijones92@gmail.com`. Nothing on the phone ever speaks to a peer.

Two earlier decisions were reversed on the way here, and the reasons matter more than the
conclusions:

**A torrent client in the app, proxied through SOCKS5, was wrong — because torrenting is
substantially UDP.** Dewi asked "is this a TCP thing??? surely udp is what we want???" and that
question killed the design. Modern BitTorrent runs mostly over µTP (BitTorrent's own protocol on
UDP), plus UDP for DHT and UDP trackers. A SOCKS5 proxy handles UDP badly: libtorrent ends up
TCP-only with DHT disabled, which means far fewer peers and magnet links that struggle to
resolve at all. The WebSocket-tunnel idea was worse still — WebSockets are TCP by definition.
Both were elaborate ways of avoiding a device VPN, and cost more than the thing they avoided.

**"The Google gate cannot protect this" was true, and then stopped being true.** It genuinely
cannot wrap SOCKS5, which is binary TCP with no place to carry a cookie. But once the Pi does
the torrenting, the phone↔Pi channel is entirely HTTP — which is exactly what nginx
`auth_request` + oauth2-proxy gates. The obstacle was the design, not the requirement.

### What gets built

**Pi** — all behind the existing oauth2-proxy, whose `--authenticated-emails-file` is already an
exact-address allowlist:

- Jackett or Prowlarr for search, so indexer breakage is upstream's problem rather than a
  parser treadmill here.
- qBittorrent is already installed, already inside gluetun, already gated at `qbit.<domain>` —
  full DHT and µTP behind PureVPN's kill-switch, which is precisely what a proxy could not give.
  **Sequential download must be on**, or the file has holes and range requests land in gaps.
- A file endpoint serving the in-progress file with HTTP range requests.

**App**:

- A one-time Google sign-in to the Pi via a Custom Tab, keeping the `_oauth2_proxy` cookie in
  an OkHttp `CookieJar`. No Pi auth changes needed. (Native sign-in with a Bearer ID token and
  `--skip-jwt-bearer-tokens` is neater and needs an extra client ID — a later refinement.)
- A `SearchSource` over the Pi's search endpoint, sitting alongside iTunes, InnerTube and the
  Internet Archive.
- Add-magnet and progress, through qBittorrent's API.
- **No new playback code at all.** A file served over HTTP from the Pi is exactly what the
  podcast pillar already plays: `MediaItem.mediaUrl`, the existing `PlaybackController`, the
  existing download strategy. The torrent-ness stops at the Pi's edge, which is the strongest
  argument for this shape.

### Consequences to accept

- **Home upload is the streaming ceiling** when away — the video comes off the broadband's
  upstream, not PureVPN's downstream.
- **The Pi must be up.** It already hosts the crash sink, so this is not a new dependency.
- No VPN profile on the phone, ever, which is what Dewi asked for.

## Superseded: the original route-C plan (kept for its reasoning)

A torrent engine in the app, streaming while it downloads, with **every byte of torrent traffic
leaving via PureVPN** so peers never see the home IP. Search covers both torrent indexers and
the Internet Archive.

### Routing — SOCKS5 inside gluetun's netns

Not a device VPN, and this is the important one. `wg-vpn` already exists and exits PureVPN, but
pointing the phone at it tunnels **all** of Totum's traffic — and YouTube bot-checks
non-residential IPs. That is not a guess: it is why the CI live-YouTube test needed the *home*
IP via `wg-home` (see `tools/ci/live-test-via-home.sh`). A whole-app tunnel would fix torrent
privacy and break the video pillar.

So only the torrent engine is proxied. A small SOCKS5 container joins the vpn-stack with
`network_mode: container:gluetun` — the same pattern `webproxy` already uses — and its port goes
in gluetun's `FIREWALL_INPUT_PORTS`. libtorrent is then configured to send peer, tracker AND DHT
connections through it, leaving every other socket in the app alone.

### Access control — and why the Google gate does NOT apply

Dewi asked for the proxy to be Google-account-gated like `privatearea`, `openclaw` and `qbit`.
**It cannot be, and pretending otherwise would be worse than not trying:** that gate is nginx
`auth_request` + oauth2-proxy, which checks a cookie a *browser* obtained through a Google
redirect. SOCKS5 is binary TCP — libtorrent cannot perform an OAuth flow or carry a session
cookie, and nginx cannot `auth_request` a non-HTTP stream.

The goal — nobody else can use it — is met more strongly by what the Pi already has:

- **No public listener.** Torrenting is home/`wg-home`-only by decision, so no port is
  forwarded and nothing on the internet can reach the proxy at all.
- **WireGuard key auth** to get near it, which is stronger than a Google session cookie.
- **Firewalled** to the `wg-home` subnet and LAN, refusing anything else — asserted the way
  `wg-home-init/10-ci-peer-lockdown.sh` is, not assumed.
- **SOCKS5 username/password** on top, since the protocol supports it and libtorrent sends it.

If a torrent *status page* is ever wanted on the Pi, that IS http and goes behind oauth2-proxy
exactly like `qbit`.

### Leak prevention — non-negotiable, and asserted

A proxy that silently stops proxying is worse than none, because the failure is invisible from
the phone. So: `anonymous_mode`, `proxy_hostnames`, `proxy_peer_connections`,
`proxy_tracker_connections`, and LSD / UPnP / NAT-PMP off (each of those bypasses a proxy by
design). Plus the app **refuses to start a torrent unless it has confirmed its apparent exit IP
is PureVPN's and not the home one** — the same discipline as the CI peer, which asserts both
its egress IP and that the LAN is unreachable on every run.

### Availability

Home wifi or `wg-home` only. Off both, torrenting is unavailable and says so plainly rather than
falling back to a direct connection — falling back is precisely the failure this must never have.

### Still open, to be settled during the build

- **Indexers**: Jackett or Prowlarr on the Pi (one API, dozens of sites, someone else maintains
  the parsers) versus scrapers in the app. Strongly prefer the former; it needs a container and
  is a Pi change, so worth confirming before adding it.
- **Engine**: `libtorrent4j` is the realistic Android option. Adds roughly 10-20MB per ABI to a
  33MB release APK.
- **Streaming**: sequential piece priority plus a local HTTP server feeding ExoPlayer, which is
  how Stremio does it. The largest single piece of work here.
