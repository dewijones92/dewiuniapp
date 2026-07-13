#!/usr/bin/env python3
"""Offline, deterministic proof that download-side SponsorBlock removal cuts the
segment out of the finished file — no YouTube, no network.

It exercises the exact postprocessor the app configures (yt-dlp's
ModifyChaptersPP with remove_sponsor_segments = the app's categories) on a
synthetic media file with a known sponsor segment, and asserts the output is
shorter by the segment's length. Pairs with the observed live behaviour that
the SponsorBlock PP fetches segments ("Found N segments"): fetch is proven live,
the cut is proven here.

Run: python3 verify_sponsorblock_cut.py   (needs ffmpeg + yt-dlp on PATH)
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

from yt_dlp import YoutubeDL
from yt_dlp.postprocessor import ModifyChaptersPP

# The app's category set (mirrors SponsorBlockSegmentSource.CATEGORIES).
CATEGORIES = {"sponsor", "selfpromo", "interaction"}
TOTAL = 60.0
SEG_START, SEG_END = 20.0, 40.0  # a 20s "sponsor" in the middle


def ffprobe_duration(path):
    out = subprocess.check_output([
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=nk=1:nw=1", str(path),
    ])
    return float(out.strip())


def synth_media(path):
    # testsrc video + sine audio, H.264/AAC — a normal muxed mp4 with chapters-free content.
    subprocess.check_call([
        "ffmpeg", "-y", "-f", "lavfi", "-i", f"testsrc=size=320x240:rate=15:duration={TOTAL}",
        "-f", "lavfi", "-i", f"sine=frequency=440:duration={TOTAL}",
        "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", "-shortest",
        str(path),
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main():
    with tempfile.TemporaryDirectory() as d:
        src = Path(d) / "clip.mp4"
        synth_media(src)
        original = ffprobe_duration(src)
        print(f"synthetic clip: {original:.1f}s, removing sponsor {SEG_START}-{SEG_END}s")

        ydl = YoutubeDL({"quiet": True})
        pp = ModifyChaptersPP(ydl, remove_sponsor_segments=CATEGORIES)
        info = {
            "title": "synthetic clip",
            "filepath": str(src),
            "duration": original,
            "chapters": [],
            "sponsorblock_chapters": [{
                "start_time": SEG_START, "end_time": SEG_END,
                "category": "sponsor", "categories": ["sponsor"],
                "type": "skip", "title": "Sponsor",
                "name": "Sponsor",
            }],
        }
        files_to_delete, info = pp.run(info)
        out = Path(info["filepath"])
        result = ffprobe_duration(out)
        expected = original - (SEG_END - SEG_START)
        print(f"result: {result:.1f}s (expected ~{expected:.1f}s)")

        ok = abs(result - expected) <= 1.5
        print("PASS" if ok else "FAIL", "- SponsorBlock segment was",
              "removed" if ok else "NOT removed")
        return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
