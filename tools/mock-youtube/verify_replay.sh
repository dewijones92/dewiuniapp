#!/usr/bin/env bash
# Replay the recorded cassette as a fake YouTube and prove yt-dlp extraction
# works offline against it (no real network). Exit 0 on match.
# usage: verify_replay.sh [cassette-file]
set -uo pipefail
CASSETTE="${1:-$(cd "$(dirname "$0")" && pwd)/cassette.flows}"
PORT=8898
EXPECT_TITLE="Top 5 Android 17 Features"
EXPECT_DUR="563"

mitmdump -q -S "$CASSETTE" \
  --set server_replay_reuse=true \
  --set server_replay_ignore_content=true \
  --set server_replay_ignore_host=true \
  --listen-port "$PORT" >/tmp/mitm-replay.log 2>&1 &
MPID=$!
trap 'kill $MPID 2>/dev/null || true' EXIT
for _ in $(seq 1 20); do curl -sf -x "http://127.0.0.1:$PORT" http://example.com >/dev/null 2>&1 && break; sleep 0.5; done

OUT=$(yt-dlp --proxy "http://127.0.0.1:$PORT" --no-check-certificates \
  --js-runtimes node --skip-download --no-warnings \
  --print "%(title)s|%(duration)s" \
  "https://www.youtube.com/watch?v=QrT4S9i3agE" 2>/tmp/ytreplay.err)
echo "replay output: ${OUT:-<none>}"
if echo "$OUT" | grep -q "$EXPECT_TITLE" && echo "$OUT" | grep -q "$EXPECT_DUR"; then
  echo "PASS - offline replay extraction matches"; exit 0
else
  echo "FAIL - replay did not reproduce extraction"; echo "--- yt-dlp stderr ---"; tail -8 /tmp/ytreplay.err; exit 1
fi
