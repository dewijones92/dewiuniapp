---
title: Watch history / progress is not reaching the account
kind: todo
area: video
priority: high
status: open
updated: 2026-07-31
---

# "Feeding the algorithm" — what actually works

Dewi asked whether we had ever verified the signals we send to YouTube by READING them back,
rather than trusting the API's own response. We had not, for most of them. So it was measured
on 2026-07-31.

| Signal | Route | Verified by read-back? | Works? |
|---|---|---|---|
| Watch Later | InnerTube action + bearer | Yes — read the playlist back | **Yes** |
| Subscribe / unsubscribe | InnerTube action + bearer | Indirectly — the subs list reflects it | **Yes** |
| Watch history / progress | yt-dlp's tracking URL, plain GET | Yes — read `FEhistory` back | **No** |

## The measurement

Played `dQw4w9WgXcQ`, absent from the account's history, via the share intent. Five pings
reported Success at 0s, 14s, 30s, 45s and 60s. Read `FEhistory` back with the account's own
token: the video was **absent**, and the history was byte-identical before and after — the
same fifteen entries in the same order. Still absent 75 seconds later, so it is not lag.

## Why

The ping goes to `videostatsWatchtimeUrl` from yt-dlp's player response, and **yt-dlp
extracts unauthenticated**. The URL therefore belongs to an anonymous session: its parameters
are `cl, docid, ei, el, fexp, len, ns, of, plid, vm` — not one of them an account identity.
Pinging it credits nobody.

Every `-> Success` only ever meant "the HTTP request did not fail". The log now says
`-> sent`, because a log that overstates what it knows is worse than no log: this one misled
me for weeks.

## To fix

The tracking URL has to come from an **authenticated** player request. A bearer token alone
is not enough — the TV client answers "The page needs to be reloaded" without a full session
(visitor data, and probably a PO token). That is the same blocker as
[sabr-streaming](sabr-streaming.md), and solving it would unlock both.

Until then the pings are harmless but useless. They are left in place because the plumbing
(position tracking, finished detection, throttling) is correct and only the identity is
missing — but nothing should claim the algorithm is being fed.
