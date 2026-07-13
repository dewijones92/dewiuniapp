# Offline SponsorBlock-removal verification

`verify_sponsorblock_cut.py` proves — deterministically, with **no YouTube and no
network** — that download-side SponsorBlock removal actually cuts the segment out
of the finished file.

## Why this exists

Developing/verifying against real YouTube is unreliable: it bot-checks and
throttles automated clients, and its extraction changes constantly. So the app's
*logic* is tested against the port fakes (`FakeYtDlpEngine`, etc.), and this
script covers the one piece the fakes can't: the real yt-dlp postprocessor that
removes sponsor segments during a download.

## What it does

1. Synthesises a 60s clip with ffmpeg.
2. Runs the exact postprocessor the app configures — yt-dlp's `ModifyChaptersPP`
   with `remove_sponsor_segments` = the app's categories (`sponsor`, `selfpromo`,
   `interaction`, mirroring `SponsorBlockSegmentSource.CATEGORIES`) — over a known
   20s "sponsor" segment.
3. Asserts the output is ~40s (the segment removed).

Together with the observed live behaviour that the SponsorBlock PP *fetches*
segments ("Found N segments in the SponsorBlock database"), this confirms the
whole download-removal path: fetch (proven live) + cut (proven here).

## Run

```bash
python3 verify_sponsorblock_cut.py   # needs ffmpeg + yt-dlp on PATH
```

Exit 0 = the cut worked. Runs in CI (see `.github/workflows/ci.yml`).
