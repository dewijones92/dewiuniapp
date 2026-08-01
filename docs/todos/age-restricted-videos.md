---
title: Age-restricted videos
kind: todo
area: video
priority: high
status: SOLVED in principle — full recipe proven end-to-end; one component (an n-solver) left to build
updated: 2026-08-01
---

# Age-restricted videos

PipePipe and SmartTube play them; Totum does not. Dewi pushed back on an earlier claim that this
was impossible, and he was right to.

**The mechanism is now fully understood and proven end-to-end outside the app**: an
age-restricted video's stream was fetched with the account this app already holds — HTTP 206,
204,800 bytes at ~1MB/s. What is left is engineering, not discovery.

## The proven recipe

Measured 2026-08-01 against `rwcfPqbAx-0` (age-restricted, free — NewPipeExtractor's own test
video), with `jNQXAC9IVRw` as a control on every single step.

1. **Ask InnerTube's player endpoint as the DOWNGRADED TV client, signed in.**
   - `clientName: TVHTML5`, `clientVersion: 5.20260707` — *not* the current `7.x`
   - `User-Agent: Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version`
   - `Authorization: Bearer <the app's existing account token>`
   - `contentCheckOk: true`, `racyCheckOk: true`
   - `playbackContext.contentPlaybackContext.signatureTimestamp` — **mandatory**
   → `status=OK`, 7 formats, **every one with a plain URL, no SABR**.
2. **Solve the `n` query parameter** on each URL against the current player JS.
3. **Fetch.** → `HTTP 206`, real bytes.

The client version is the whole trick. The *same request* at the current `7.20260707.07.00`
returns SABR-only (one URL out of seven). At `5.20260707` it returns seven fetchable URLs. This
is exactly why SmartTube keeps a `TV_DOWNGRADED` client in its list and tries it *before* `TV`.

### signatureTimestamp is not optional, and its absence lies to you

Without it the response is `UNPLAYABLE: "The page needs to be reloaded"` — **on any video,
including unrestricted ones**. Two earlier rounds of this investigation were spent on results
gathered without it, which is why `TVHTML5` appeared to refuse age-restricted content when it was
actually refusing *everything*. Fetch it from the player JS (`HttpSignatureTimestampSource`
already does this; it was 20662 on 2026-08-01).

**Always run a control video through any new client.** Every wrong turn in this investigation
came from testing one restricted video and reading a generic failure as an age-gate failure.

## What is left to build: an `n` solver

The URLs carry a raw `n` parameter and 403 until it is transformed. Stripping `n` does not help —
that 403s too.

`n` is solved by running a function extracted from YouTube's `base.js`, so it needs a JavaScript
engine:

- **yt-dlp cannot do this on Android.** Its n-solving now lives behind a provider architecture
  (`extractor/youtube/jsc/`) whose four providers are `deno`, `bun`, `node` and `quickjs` — all
  external binaries. The pure-Python interpreter is gone. On this laptop `deno` is the default and
  is absent, which is why yt-dlp needed `js_runtimes={'node': {}}` to solve it here.
- **NewPipe/PipePipe use Mozilla Rhino** (`extractor/build.gradle.kts` → `mozilla.rhino.core` +
  `rhino.engine`), a pure-Java JS engine that runs on Android. This is the route with a working
  precedent.

Rhino is a pure-Java jar, so a solver can live in `:lib:innertube` without breaking that module's
pure-JVM rule, and stays unit-testable off-device. Suggested shape: an `NSolver` port plus a
`RhinoNSolver`, alongside the existing `SignatureTimestamp` code.

The open question is **where the solver's JavaScript comes from**, and it is a real trade-off:

- Port NewPipe's `YoutubeThrottlingParameterUtils` regexes, which locate the n-function inside
  `base.js`. Proven on Android; ~13KB of regexes that break whenever YouTube ships a new player.
- Vendor yt-dlp's self-contained `yt.solver.core.js` (from the `yt_dlp_ejs` package, already
  present alongside the embedded yt-dlp) and run *that* in Rhino. It is continuously maintained
  and would update with the yt-dlp wheel the app already self-updates — but it targets
  deno/bun/node, so **whether Rhino can execute it at all is unverified** and is the first thing
  to test.

## What was ruled out, with evidence

| Attempt | Result |
|---|---|
| `ANDROID_VR` anonymous (v1.60.19 and v1.65.10, ± `visitorData`) | `LOGIN_REQUIRED: Sign in to confirm your age` |
| `ANDROID_VR` + OAuth bearer (± matching client headers) | `HTTP 400` |
| `WEB_EMBEDDED_PLAYER`, `TVHTML5_SIMPLY_EMBEDDED_PLAYER` | fail on the **control** video too — not age-gating |
| `ANDROID` + OAuth bearer | `HTTP 400` |
| Every yt-dlp `player_client` (`tv`, `tv_simply`, `web_embedded`, `android_vr`, `mweb`, default) | all fail identically; yt-dlp's own advice is *"use --cookies"* |
| yt-dlp + `--add-header Authorization:...` | `HTTP 400` — the header is global, so a TV token is sent on WEB-client requests |
| Exchanging the OAuth token for cookies (`OAuthLogin`/uberauth) | `403 Error=badauth` — the token holds only the `youtube` scope |

**Auth belongs to TV clients only.** SmartTube's `AppClient.isAuthSupported` is exactly
`TV, TV_LEGACY, TV_EMBED, TV_KIDS, TV_DOWNGRADED`. Sending a bearer with any other client is what
produces the `HTTP 400` — the earlier "token/client mismatch" reading was right, and the fix is
not to authenticate a different client but to use a TV one.

**Upstream NewPipeExtractor has given up on this**: its age-restricted test is
`@Disabled("There is currently no way to extract age-restricted videos")`. It lacks an account;
we have one, which is the advantage to spend.

## The two videos that started this were never age-restricted

Report 0.1.289's failures, `skUpycGyI_A` and `goQ3z52qqD4`, are **Paramount+ paid content** —
"Yogurt Shop / Pizzeria" (a Nathan For You episode) and "The Dictator" (a film), both on the
`Paramount+ Global` channel. The signed-in TV client's `"This video requires payment to watch"`
was **literally true**, and an earlier version of this document dismissed it as misdirection.

No token, client or cookie will ever open those. When the app meets one it should say *"this
needs to be bought on YouTube"*, not fail generically — a separate, small piece of work worth
doing regardless of the n-solver.

## Where the code is

`InnerTubeClient` carries the earlier attempts (`playerAsAccount`, `playerEmbedded`,
`playerAndroidVr`, `playerWebEmbedded`) wired cheapest-first in `AppContainer.accountPlayer`. The
downgraded-TV call is the one to add; the others can go once it works.
