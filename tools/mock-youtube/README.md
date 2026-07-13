# Mock YouTube — record / replay

A deterministic, offline stand-in for YouTube so the **real** yt-dlp extraction
path can be tested without depending on live YouTube (which bot-checks and
throttles automated clients and changes its extraction constantly).

It's an HTTPS record/replay built on mitmproxy: record a real yt-dlp session
once, replay it forever with no network.

## Files

- `record.sh <youtube-url> [cassette]` — records a real extraction into a
  cassette. Needs `node` (yt-dlp's JS runtime) and the signed-in Firefox cookies
  (dev machine only). Records raw so extraction succeeds, then **scrubs
  credentials** from the saved cassette in a second pass (`scrub.py`) — the
  committed `cassette.flows` contains no cookies/auth (verified: 0 cookie tokens).
- `verify_replay.sh [cassette]` — serves the cassette as a fake YouTube
  (mitmproxy server-replay) and asserts yt-dlp extraction reproduces the recorded
  video **offline**. Runs in CI.
- `scrub.py` — mitmproxy addon that strips cookie/auth/identity headers.
- `cassette.flows` — the committed recording (MKBHD "Top 5 Android 17 Features").

## Why the replay is genuinely offline

`verify_replay.sh` passes **no cookies**. Against real YouTube, a cookie-less
automated request always bot-checks — so a successful, correct extraction can
only have come from the cassette, not the network.

## Limitations (by design)

- Covers the **extraction/parse** path. The download→merge→SponsorBlock-cut path
  is covered offline by `tools/sponsorblock-verify/` (synthetic media). Media
  bytes aren't recorded here (YouTube throttles them; they're not needed to test
  extraction).
- A cassette **replays old traffic**, so it cannot detect when real YouTube
  changes/breaks — that's what the runtime yt-dlp self-update and occasional real
  smoke tests are for. This exists to make our own pipeline testable
  deterministically, not to monitor YouTube.

## Re-record (when extraction shape changes)

```bash
./record.sh "https://www.youtube.com/watch?v=QrT4S9i3agE" cassette.flows
./verify_replay.sh
```
