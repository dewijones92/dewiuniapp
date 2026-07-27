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
mute|unmute)
    # Muted OUTSIDE Android, on WSLg's PulseAudio, so the app's own volume state is
    # untouched — playback, stalls, and the volume gesture all still read normally, there
    # is just nothing to hear. Muting Android's stream instead would corrupt exactly the
    # thing the volume gesture is tested against.
    #
    # Windows-side per-app mute is the wrong layer: the emulator is a WSL process
    # (qemu-system-x86_64), so Windows sees only WSLg's shared session and muting it
    # would silence all of WSL.
    want=$([ "$1" = mute ] && echo 1 || echo 0)
    id=$(pactl list sink-inputs 2>/dev/null |
        awk '/^Sink Input #/ { id = $3 } /application.process.binary = "qemu-system-x86_64"/ { print id; exit }' |
        tr -d '#')
    if [ -z "$id" ]; then
        echo "no emulator audio stream (is it running, and has it played anything?)" >&2
        exit 1
    fi
    pactl set-sink-input-mute "$id" "$want"
    echo "$1d emulator stream #$id"
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
