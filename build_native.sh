#!/bin/bash
set -e

# Only build libvpx and ffmpeg for legacy/armeabi-v7a
# This is the minimum needed for assembleLegacyArm32Debug

NDK="$ANDROID_SDK_ROOT/ndk/23.2.8568313"
PREBUILT="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
SYSROOT="$PREBUILT/sysroot"
THIRDPARTY="$(pwd)/app/jni/third_party"
CPU_FEATURES="$NDK/sources/android/cpufeatures"
FLAVOR="legacy"
ABI="armeabi-v7a"
PLATFORM="armv7-a"
ANDROID_API=16
NDK_ABIARCH="armv7a-linux-androideabi"
CROSS_PREFIX="$PREBUILT/bin/arm-linux-androideabi"
CC="${PREBUILT}/bin/${NDK_ABIARCH}${ANDROID_API}-clang"
CXX="${PREBUILT}/bin/${NDK_ABIARCH}${ANDROID_API}-clang++"
AR="$PREBUILT/bin/llvm-ar"
NM="$PREBUILT/bin/llvm-nm"
STRIP="$PREBUILT/bin/llvm-strip"
RANLIB="$PREBUILT/bin/llvm-ranlib"

echo "=== Building libvpx for $FLAVOR/$ABI ==="
pushd "$THIRDPARTY/libvpx"
make clean || true
CFLAGS="-DANDROID -fpic -fpie -Os -march=armv7-a -marm -mfloat-abi=softfp -mfpu=neon -mthumb -D__thumb__ -I${CPU_FEATURES}"
export PREFIX=./build/$FLAVOR/$PLATFORM

./configure \
  --target=armv7-android-gcc \
  --enable-neon \
  --disable-neon-asm \
  --sdk-path="$NDK" \
  --prefix="$PREFIX" \
  --libc="$SYSROOT" \
  --enable-static \
  --disable-shared \
  --disable-unit-tests \
  --disable-webm-io \
  --disable-libyuv \
  --disable-examples \
  --disable-docs \
  --enable-vp8 \
  --enable-vp9 \
  --enable-runtime-cpu-detect \
  --extra-cflags="$CFLAGS" \
  CC="$CC" CXX="$CXX" AR="$AR" NM="$NM" STRIP="$STRIP" \
  AS="$PREBUILT/bin/yasm"

make -j$(nproc) install
popd

echo "=== Building ffmpeg for $FLAVOR/$ABI ==="
pushd "$THIRDPARTY/ffmpeg"
make clean || true

LIBVPX_INCLUDE="$THIRDPARTY/libvpx/build/$FLAVOR/$PLATFORM/include"
LIBVPX_LIB="$THIRDPARTY/libvpx/build/$FLAVOR/$PLATFORM/lib"
LIBS_DIR="${PREBUILT}/lib64/clang/12.0.9/lib/linux"
LINK="$SYSROOT/usr/lib/arm-linux-androideabi/$ANDROID_API"
PREFIX="./build/$FLAVOR/$PLATFORM"

./configure \
  --prefix="$PREFIX" \
  --enable-cross-compile \
  --target-os=android \
  --arch=arm \
  --cpu=armv7-a \
  --cc="$CC" \
  --cxx="$CXX" \
  --ld="$CC" \
  --ar="$AR" \
  --nm="$NM" \
  --strip="$STRIP" \
  --ranlib="$RANLIB" \
  --cross-prefix="$CROSS_PREFIX-" \
  --sysroot="$SYSROOT" \
  --enable-static \
  --disable-shared \
  --disable-programs \
  --disable-doc \
  --disable-everything \
  --enable-protocol=file \
  --enable-decoder=h264 \
  --enable-decoder=hevc \
  --enable-decoder=vp8 \
  --enable-decoder=vp9 \
  --enable-decoder=aac \
  --enable-decoder=mp3 \
  --enable-decoder=opus \
  --enable-decoder=flac \
  --enable-demuxer=mov \
  --enable-demuxer=matroska \
  --enable-demuxer=ogg \
  --enable-demuxer=mp3 \
  --enable-demuxer=aac \
  --enable-muxer=mp4 \
  --enable-filter=scale \
  --enable-filter=aresample \
  --enable-encoder=aac \
  --enable-encoder=png \
  --enable-bsf=aac_adtstoasc \
  --enable-libvpx \
  --extra-cflags="-fvisibility=hidden -ffunction-sections -fdata-sections -Os -DCONFIG_LINUX_PERF=0 -DANDROID -marm -march=armv7-a -mfloat-abi=softfp -I$LIBVPX_INCLUDE --static -fPIC" \
  --extra-ldflags="-L$LINK -L${LIBS_DIR} -Wl,--fix-cortex-a8" \
  --extra-libs="-lunwind -lclang_rt.builtins-arm-android" \
  --pkg-config-flags="--static" \
  --enable-neon \
  --disable-x86asm \
  --disable-asm

make -j$(nproc)
make install
popd

echo "=== Native build complete ==="
