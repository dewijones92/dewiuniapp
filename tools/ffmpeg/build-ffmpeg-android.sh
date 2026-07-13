#!/usr/bin/env bash
# Cross-compile a minimal static ffmpeg + ffprobe for Android (arm64-v8a, x86_64)
# using the project's NDK. Output: libffmpeg.so / libffprobe.so per ABI (PIE
# executables named lib*.so so Android extracts them to the executable
# nativeLibraryDir). Merge/mux uses stream copy; aac+pcm encoders kept as a
# cheap hedge for future audio extraction.
set -euo pipefail

WORK=/home/dewi/code/dewiuniapp-ffmpeg-build
SRC="$WORK/ffmpeg-7.1.1"
NDK=/home/dewi/code/android-sdk/ndk/28.2.13676358
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
API=34
OUT="$WORK/out"
mkdir -p "$OUT"

build_abi() {
  local abi="$1" triple="$2" arch="$3" extra="$4"
  echo "===================== building $abi ====================="
  local bdir="$WORK/build-$abi"
  rm -rf "$bdir"; mkdir -p "$bdir"; cd "$bdir"

  local CC="$TOOLCHAIN/bin/${triple}${API}-clang"

  # Remux-only build: yt-dlp merges streams and cuts SponsorBlock segments with
  # `-c copy`, so no decoders/encoders are needed; ffprobe is not used either.
  # Demuxers, muxers, parsers, bitstream filters and the file protocol are kept
  # (defaults) so every real YouTube container/codec can be remuxed.
  "$SRC/configure" \
    --prefix="$OUT/$abi" \
    --target-os=android \
    --arch="$arch" \
    --enable-cross-compile \
    --cc="$CC" \
    --cxx="${CC}++" \
    --ar="$TOOLCHAIN/bin/llvm-ar" \
    --ranlib="$TOOLCHAIN/bin/llvm-ranlib" \
    --nm="$TOOLCHAIN/bin/llvm-nm" \
    --strip="$TOOLCHAIN/bin/llvm-strip" \
    --sysroot="$TOOLCHAIN/sysroot" \
    ${extra} \
    --disable-shared --enable-static \
    --enable-pic \
    --disable-doc --disable-htmlpages --disable-manpages --disable-podpages --disable-txtpages \
    --disable-ffplay --disable-ffprobe --disable-avdevice \
    --disable-network \
    --disable-debug \
    --disable-decoders --disable-encoders \
    --enable-small \
    --enable-ffmpeg \
    --pkg-config=false

  make -j"$(nproc)"
  make install
  echo "----- $abi sizes (stripped by --strip during install) -----"
  ls -la "$OUT/$abi/bin"
}

build_abi arm64-v8a aarch64-linux-android aarch64 "--cpu=armv8-a"
build_abi x86_64     x86_64-linux-android  x86_64  "--disable-x86asm"

echo "===================== DONE ====================="
for abi in arm64-v8a x86_64; do
  f="$OUT/$abi/bin/ffmpeg"
  printf "%-12s %s\n" "$abi" "$(du -h "$f" | cut -f1) $(file "$f" | cut -d, -f1-3)"
done
