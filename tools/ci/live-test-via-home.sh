#!/usr/bin/env bash
# Run the live-YouTube instrumented test through Dewi's home broadband.
#
# WHY: a GitHub runner is a datacentre IP and YouTube bot-checks it, so SabrPlaybackTest comes
# back `Unplayable` and skips. His Pi already runs a WireGuard server whose clients exit the
# home connection (dot-files vpn-stack, `wg-home`), so CI borrows it for this one test. Note the
# Pi's OTHER exits are useless here on purpose: the web proxy and `wg-vpn` both leave via
# PureVPN by design, and a commercial VPN IP is bot-checked at least as hard as a datacentre one.
#
# A file rather than inline YAML for two reasons: `android-emulator-runner` rewrites backslash
# line-continuations inside its `script:` (its own log shows `sh -c \yes | sdkmanager`), which
# silently turned a wrapped gradle command into a task named backslash; and this has to run
# INSIDE that action's script, because it kills the emulator the moment the script returns.
#
# Exit codes are deliberate:
#   0  ran, or was skipped because the tunnel could not come up (a home connection is not a
#      build dependency, and CI should not claim the app is broken because a router rebooted)
#   1  ONLY when the peer can still reach the LAN — the entire risk of doing this at all, so it
#      fails loudly rather than quietly proceeding
set -uo pipefail

if [ -z "${WG_CI_CONF:-}" ]; then
  echo "[live-test] no tunnel configured — skipping the live YouTube test"
  exit 0
fi

sudo apt-get update -qq && sudo apt-get install -y -qq wireguard-tools || {
  echo "[live-test] could not install wireguard-tools — skipping"
  exit 0
}

printf '%s\n' "$WG_CI_CONF" | sudo tee /etc/wireguard/wg0.conf >/dev/null
sudo chmod 600 /etc/wireguard/wg0.conf

if ! sudo wg-quick up wg0; then
  echo "[live-test] tunnel would not come up — skipping (home connection is not a build dependency)"
  exit 0
fi
trap 'sudo wg-quick down wg0 >/dev/null 2>&1 || true' EXIT

# Never print the IP itself: this repo is PUBLIC, so its logs are. Compare and state a verdict.
EGRESS=$(curl -s --max-time 20 https://api.ipify.org || true)
if [ "$EGRESS" != "${WG_EXPECTED_EGRESS_IP:-}" ]; then
  echo "[live-test] egress is NOT the expected residential IP — tunnel is not carrying traffic; skipping"
  exit 0
fi
echo "[live-test] egress is the expected residential IP"

# Asserted, never assumed. `wg-home` peers normally get FULL LAN access and this key lives in a
# public repo's secrets; the Pi firewalls this peer to internet-only egress
# (vpn-stack/wg-home-init/10-ci-peer-lockdown.sh). If that has stopped working, everything else
# here is irrelevant and the run must go red.
for TARGET in 192.168.0.1 192.168.0.19; do
  if timeout 5 bash -c "cat < /dev/null > /dev/tcp/$TARGET/80" 2>/dev/null; then
    echo "[live-test] LAN host reachable through the CI peer — LOCKDOWN FAILED"
    exit 1
  fi
done
echo "[live-test] LAN unreachable from the CI peer, as intended"

# :app only. A bare `connectedDebugAndroidTest` runs every module, and :core:database has no
# class by this name, so the filter matches nothing there and the runner reports
# `initializationError` — a real red build caused entirely by asking the wrong module.
./gradlew :app:connectedDebugAndroidTest --no-daemon -Pandroid.testInstrumentationRunnerArguments.class=com.dewijones92.totum.sabr.SabrPlaybackTest
