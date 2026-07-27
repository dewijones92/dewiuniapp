#!/usr/bin/env bash
# Makes the emulator behave more like a phone, for verifying anything where
# "it worked on my machine" is the whole problem.
#
# The emulator's defaults are a fast, low-latency, always-plugged-in, never-throttled
# device on a debug build — which is why a release-only R8 crash and a multi-second stall
# both went unseen here. Everything below is a real emulator capability; only the video
# decode silicon cannot be faked (see NOT COVERED).
#
# Usage:
#   tools/emulator/realistic.sh network 4g-poor   # or 4g, 3g, 2g, off, full
#   tools/emulator/realistic.sh cellular          # metered transport, so quality caps apply
#   tools/emulator/realistic.sh thermal 3         # 0=none 1=light 2=moderate 3=severe
#   tools/emulator/realistic.sh doze              # background restrictions
#   tools/emulator/realistic.sh mute            # Dewi finds emulator audio distracting
#   tools/emulator/realistic.sh reset
#
# NOT COVERED — these still need a real device:
#   * Hardware video decode. The emulator's only hardware decoders are
#     c2.goldfish.{vp9,hevc}; AV1 falls back to software (c2.android.av1-dav1d). A Pixel 7
#     decodes AV1 on Tensor, so stalls caused by decode cost differ fundamentally.
#   * arm64 native code at usable speed (release ships arm64-only; the emulator is x86_64,
#     so ffmpeg's .so and Chaquopy's Python are different binaries).
#   * Radio handover (wifi <-> cellular mid-playback).
set -euo pipefail

case "${1:-}" in
network)
    case "${2:-4g}" in
    # `speed` caps throughput; `delay` adds latency, which is what actually
    # produces mid-playback stalls rather than just a slow start.
    4g-poor) adb emu network speed hsdpa && adb emu network delay umts ;;
    4g)      adb emu network speed lte   && adb emu network delay none ;;
    3g)      adb emu network speed umts  && adb emu network delay umts ;;
    2g)      adb emu network speed edge  && adb emu network delay edge ;;
    off)     adb emu network speed gsm   && adb emu network delay gprs ;;
    full)    adb emu network speed full  && adb emu network delay none ;;
    *) echo "unknown profile: $2" >&2; exit 1 ;;
    esac
    adb emu network status
    ;;
cellular)
    # Metered transport: exercises the data-saver quality caps and the
    # auto-download "wifi only" rule, which never fire on the default wifi.
    adb emu gsm data on
    adb shell svc wifi disable
    echo "cellular only; 'reset' restores wifi"
    ;;
thermal)
    adb shell cmd thermalservice override-status "${2:-2}"
    echo "thermal status ${2:-2}"
    ;;
doze)
    adb shell dumpsys deviceidle enable
    adb shell dumpsys deviceidle force-idle
    echo "dozing; 'reset' wakes it"
    ;;
mute)
    # Silences media output without touching the app: playback, stalls and the volume
    # gesture all still behave, there is just nothing to hear.
    adb shell cmd media_session volume --stream 3 --set 0 >/dev/null
    echo "muted"
    ;;
unmute)
    adb shell cmd media_session volume --stream 3 --set 8 >/dev/null
    echo "unmuted"
    ;;
reset)
    adb emu network speed full
    adb emu network delay none
    adb shell svc wifi enable
    adb shell cmd thermalservice override-status 0 || true
    adb shell dumpsys deviceidle unforce || true
    echo "back to defaults"
    ;;
*)
    sed -n '2,30p' "$0"
    exit 1
    ;;
esac
