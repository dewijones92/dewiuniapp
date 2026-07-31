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

## The duplication cannot be removed — measured, not assumed

Dewi approved trying it. It does not work, and the negative result is worth more than the
attempt was.

Handing the download the info dict the play already extracted — `process_ie_result(info,
download=True)`, the same entry point `--load-info-json` uses — fails with **HTTP 403**, while
a fresh `extract_info(download=True)` with identical options succeeds in 1.7s. Tested against
this exact yt-dlp on 2026-07-31, on the desktop so the variables were controllable:

| Attempt | Result |
|---|---|
| Fresh `extract_info(download=True)` (control) | **OK, 1.7s** |
| Reuse **sanitized** info via `process_ie_result` | 403 Forbidden |
| Reuse **raw** info via `process_ie_result` | 403 Forbidden |
| …with a JS runtime configured, matching the app | 403 Forbidden |

The first run of that test was itself misleading and nearly produced a wrong conclusion: it
extracted in 1.5s with no JS runtime, so its URLs were undeciphered and the 403 was
explainable that way. Re-running with the runtime configured — the app's actual setup — gave
the same 403, which is what makes it a real finding.

YouTube's format URLs do not survive being handed to a second `YoutubeDL`. That is precisely
why yt-dlp re-extracts inside a download. **So the duplicate extraction per queued play is
inherent**, and ordering the two — which is what this change does — is as far as it goes. This change only orders them.
