---
title: Torrents work out of the box
kind: todo
area: torrent
priority: high
status: requested — VERIFY: sign-in may already be shipped, not checked
updated: 2026-08-01
---

# Torrents work out of the box

Dewi, 2026-08-01: *"make sure that the torrent ux works out the box … so i dont have to insert
any config etc etc … we already have it gated to me dewijones92@gmail.com"*.

## What this means

Installing the APK and signing in with Google must be the whole setup. No host to type, no API
key to paste, no toggle to find. The access gate already exists and already knows who is allowed
— the app should lean on it rather than asking the person behind it to re-state what it knows.

## What currently stands in the way

`HttpHomeTorrentServer` is constructed with three things a person would otherwise have to supply:

- `base` — the home host (`https://<host>`; Prowlarr under `/prowlarr/`, TorrServer under `/ts/`)
- `prowlarrApiKey` — Prowlarr wants its own key even behind the proxy, because the gate
  authorises but does not identify
- `token()` — the value replayed as `X-Totum-Token`, which nginx checks before proxying

None of these are secrets *from Dewi*, but all three are things he should never be asked to
enter. The token already exists server-side in `/var/lib/totum-auth/`.

## Shape of the answer

Ship the host in the build and have the app fetch everything else after Google sign-in, from the
gated endpoint that already only answers to `dewijones92@gmail.com`. One request returns the app
token and the Prowlarr key; the app stores them and never asks again. The gate is the
authorisation, so anyone else who reaches that endpoint gets a 401 and no configuration.

That also fixes the failure mode: today a missing key is indistinguishable from the Pi being
unreachable. After this, "not signed in", "not allowed" and "not at home" are three different
messages.

## Constraints carried over

- Nothing torrent-related may leak into a public commit — the host, the token and the Prowlarr
  key stay out of the repo (Dewi, 2026-08-01: *"make sure nothing torrent related is leaked"*).
- The Pi is only reachable at home or over `wg-home`; being elsewhere is the ordinary case and
  must read as such, not as a fault.
- Still owed from the same conversation: a full phone-to-screen e2e (install → sign in → search →
  tap → watch). The emulator cannot reach the Pi, so this needs the real device.
