---
title: Watch history / progress is not reaching the account
kind: todo
area: video
priority: high
status: done
updated: 2026-07-31
---

# "Feeding the algorithm" — what actually works

Dewi asked whether we had ever verified the signals we send to YouTube by READING them back,
rather than trusting the API's own response. We had not, for most of them. So it was measured
on 2026-07-31 — and one of the three turned out to be doing nothing at all.

| Signal | Route | Verified by read-back? | Works? |
|---|---|---|---|
| Watch Later | InnerTube action + bearer | Yes — read the playlist back | **Yes** |
| Subscribe / unsubscribe | InnerTube action + bearer | Indirectly — the subs list reflects it | **Yes** |
| Watch history / progress | authenticated `/player` tracking URL | Yes — read `FEhistory` back | **Yes, now** |

## The measurement that found it

Played `dQw4w9WgXcQ`, absent from the account's history, via the share intent. Five pings
reported Success at 0s, 14s, 30s, 45s and 60s. Read `FEhistory` back with the account's own
token: the video was **absent**, and the history was byte-identical before and after — the
same fifteen entries in the same order. Still absent 75 seconds later, so it was not lag.

## The cause

The ping went to `videostatsWatchtimeUrl` from **yt-dlp's** player response, and yt-dlp
extracts unauthenticated. The URL therefore belonged to an anonymous session: its parameters
were `cl, docid, ei, el, fexp, len, ns, of, plid, vm` — not one of them an account identity.
Pinging it credited nobody. Every `-> Success` only ever meant "the HTTP request did not
fail".

## The fix — and the one integer it turned on

The tracking URL now comes from an **authenticated** `/player` call as the TV client, made by
`HttpYouTubeWatchHistory.beginSession` itself rather than handed in by the caller.

That request had been tried before and refused with `UNPLAYABLE: "The page needs to be
reloaded."`, which the old note here blamed on a missing full session (visitor data, a PO
token). That was wrong. Bisecting yt-dlp's own TV request field by field showed the refusal
survives dropping `visitorData`, `originalUrl`, `userAgent`, `platform` and the
`X-Goog-Visitor-Id` header — and disappears the moment
`playbackContext.contentPlaybackContext.signatureTimestamp` is present **and current**. A
timestamp four player releases old is rejected exactly like none at all.

So the whole minimum is: TVHTML5 client + bearer token + today's signature timestamp. The
timestamp is read from YouTube's own player JavaScript (`iframe_api` names the build, the
build's script carries the number) by `HttpSignatureTimestampSource`, and cached for the
process — YouTube ships a new player roughly weekly.

## Verified, the same way the bug was found

Two videos absent from the account's history — `dQw4w9WgXcQ` and `jNQXAC9IVRw` — each
appeared at the **top of `FEhistory` within twenty seconds** of the same ping sequence sent to
an authenticated tracking URL. The authenticated URL differs from the anonymous one by exactly
one parameter, `uga`, which is the signed-in signal. The `cpn` does **not** need to be
declared in the player request; a nonce invented at ping time is accepted.

## What this does not fix

The TV player response carries **no fetchable stream URLs at all** — 27 formats, every one
URL-less, plus a `serverAbrStreamingUrl`. So it is no help to
[sabr-streaming](sabr-streaming.md), which the old note here hoped it would unlock. yt-dlp
remains the stream source; this call is purely for tracking.

The dead plumbing that carried yt-dlp's anonymous URLs (python capture hook →
`MediaMetadata.playbackTrackingUrl` → `VideoResolver.Resolved`) has been removed rather than
left in place, so nobody wires it back up.
