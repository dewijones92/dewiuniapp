---
title: Age-restricted videos
kind: todo
area: video
priority: high
status: evidenced, unsolved — five client identities refused, auth mechanism is the lead
updated: 2026-08-01
---

# Age-restricted videos

PipePipe and SmartTube play them; Totum does not. Dewi pushed back on an earlier claim that this
was impossible, and he was right to — that claim was made without evidence, from a null return
rather than from anything YouTube said.

## What YouTube actually says

Measured 2026-08-01 against `skUpycGyI_A` and `goQ3z52qqD4`, both from report 0.1.289, with a
valid signed-in account on the device. Every identity refuses, and **every one refuses
differently**:

| Client | Auth | Response |
|---|---|---|
| `TVHTML5` | signed in | `UNPLAYABLE: This video requires payment to watch` |
| `TVHTML5_SIMPLY_EMBEDDED_PLAYER` | signed in | `ERROR: YouTube is no longer supported in this application or device` |
| `TVHTML5_SIMPLY_EMBEDDED_PLAYER` | anonymous | same |
| `ANDROID_VR` | anonymous | **`LOGIN_REQUIRED: Sign in to confirm your age`** |
| `ANDROID_VR` | OAuth bearer | `HTTP 400` |
| `WEB_EMBEDDED_PLAYER` | anonymous | `ERROR: This video is unavailable` |

## The auth mechanism was tried, and the failure is specific

Two attempts at making `ANDROID_VR` authenticate, both 2026-08-01:

1. **OAuth bearer alone** → `HTTP 400`.
2. **Bearer plus matching client headers** (`X-YouTube-Client-Name: 28`, matching
   `X-YouTube-Client-Version`, and a headset user agent) → `HTTP 400` again.

So it is not missing headers. The 400 appears if and only if an `Authorization` header is
present — anonymous requests to the same endpoint with the same body return a clean
`LOGIN_REQUIRED`. That points at a **token/client mismatch rather than a malformed request**:
the app's token is issued to the *YouTube on TV* OAuth client (the device-code flow it signs in
with), and presenting a TV-client token on a VR-client request is not a thing YouTube accepts.

**Which makes the next step a credentials question, not a protocol one.** Either obtain a token
whose OAuth client matches the requesting InnerTube client, or use the mechanism SmartTube
actually uses — worth reading its source rather than probing further, since six blind
combinations have now been tried and each cost a build-and-test cycle.

## What that means

**`ANDROID_VR` is the promising one and the others are noise.** `LOGIN_REQUIRED` is the request
being ACCEPTED and asked to identify itself — the only response that says "prove who you are"
rather than "no". Every other client refuses on its own terms before age enters into it.

**The blocker is the auth MECHANISM, not the identity.** `ANDROID_VR` rejects an OAuth bearer
with HTTP 400, so it wants credentials in a form this app does not currently send. SmartTube
signs its requests differently, and replicating that is the actual next step. Adding a sixth
client identity is not — five have now been tried and the pattern is clear.

**Do not trust a client's stated reason.** `TVHTML5` reports "requires payment" for a video that
is age-restricted. Two rounds were spent on that misdirection.

## Also worth knowing

`"YouTube is no longer supported in this application"` is a stale `clientVersion`, not a refusal
to serve — worth a current version string if the embedded route is revisited, though it is the
weaker lead.

## Where the code is

All five attempts live in `AppContainer.accountPlayer`, ordered cheapest-first, each logging what
it received. They cost a handful of requests on a video that was not going to play anyway, and
they mean the next attempt starts from evidence. `InnerTubeClient` carries `playerAsAccount`,
`playerEmbedded`, `playerAndroidVr` and `playerWebEmbedded`.
