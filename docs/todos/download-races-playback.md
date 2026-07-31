---
title: A download's extraction raced the play that started it
kind: todo
area: playback
priority: high
status: done
updated: 2026-07-31
---

# Tapping play set off its own rival

From the 0.1.238 report, and then reproduced on the emulator:

```
12:45:33.128  [download] start audioOnly=true India's "Cockroach" …
12:45:35.831  [download] extracting for download: xx-KSozQdNU
12:45:49.021  [engine] extract …xx-KSozQdNU in 13192ms
12:45:49.057  [resolve] xx-KSozQdNU in 16031ms for play
```

**The play waited 16 seconds.** yt-dlp's first act inside a download is its own
`extract_info`, and the engine is one embedded interpreter — so two extractions of the same
video were competing for it. `QueueAutoDownloader` reacts to every queue change, so *starting
playback is what set its rival off*.

## What was and was not proven

**Certain:** two full extractions ran for one play, overlapping.
**Not proven:** that contention caused those 16 seconds. That was an x86_64 emulator with a
software JS runtime, which is slow regardless — the same extraction took 4.4s and 2.0s on
other runs.

The ordering is wrong either way, which is what justifies the fix without the measurement:
nobody is waiting on a background download, and someone is always waiting on a play. Deferring
a download by a few seconds cannot be noticed; letting it go first can.

## The fix

`InteractiveFirstEngine` decorates `YtDlpEngine`: `extract`, `searchVideos` and `fetchChannel`
register as interactive, and `download` waits for the interactive count to reach zero before
delegating. Bounded at 60s so a steady trickle of interactive work can never starve downloads
outright — hitting the bound restores the old behaviour, so it is never worse.

## Verified on-device

Two videos shared in quick succession, so a real extraction was in flight when a download was
requested:

```
13:02:35.880  [download] start audioOnly=true 250kg/551lbs Paused Front Squat
13:02:35.991  [engine] extract LjKtI5BvwVM in 7195ms      ← interactive work FINISHES
13:02:36.061  [download] extracting for download: 7zXlyHoDaAE  ← starts 70ms later
```

The download was requested 111ms *before* that extraction finished and did not begin until
70ms *after*. An earlier run where the play was a cache hit proved nothing, because with
nothing in flight the download would have started immediately anyway — worth recording,
because it looked like a successful verification and was not.

## Still open: the duplication itself

Both extractions still happen. Removing that means handing yt-dlp a pre-extracted info dict
for the download (its `--load-info-json` equivalent), which is a real change to the Python
bridge and wants its own pass. This change only orders them.
