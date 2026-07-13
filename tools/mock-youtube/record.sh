#!/usr/bin/env bash
# Record a real yt-dlp extraction as a replayable cassette. Records raw (so
# cookies reach YouTube and extraction succeeds), then scrubs credentials from
# the saved cassette in a second offline pass — the committed cassette has none.
# usage: record.sh <youtube-url> [cassette-file]
set -euo pipefail
URL="${1:?usage: record.sh <url> [cassette]}"
CASSETTE="${2:-cassette.flows}"
PORT=8899
HERE="$(cd "$(dirname "$0")" && pwd)"
FF_PROFILE="/mnt/c/Users/DewiJones/AppData/Roaming/Mozilla/Firefox/Profiles/sj2vrl3j.dewijones92"
RAW="$(mktemp).flows"

echo "recording $URL (raw) ..."
mitmdump -q -w "$RAW" --listen-port "$PORT" >/tmp/mitm-rec.log 2>&1 &
MPID=$!
trap 'kill $MPID 2>/dev/null || true; rm -f "$RAW"' EXIT
for _ in $(seq 1 20); do curl -sf -x "http://127.0.0.1:$PORT" http://example.com >/dev/null 2>&1 && break; sleep 0.5; done

yt-dlp --proxy "http://127.0.0.1:$PORT" --no-check-certificates \
    --js-runtimes node --cookies-from-browser "firefox:$FF_PROFILE" \
    --skip-download --no-warnings \
    --print "%(id)s|%(title)s|%(duration)s|%(format_count)s" "$URL"
sleep 1
kill $MPID 2>/dev/null || true; sleep 1

echo "scrubbing credentials from cassette ..."
mitmdump -q -nr "$RAW" -w "$CASSETTE" -s "$HERE/scrub.py" >/tmp/mitm-scrub.log 2>&1 || true
echo "cassette: $(du -h "$CASSETTE" 2>/dev/null | cut -f1)  flows: raw=$(du -h "$RAW"|cut -f1)"
