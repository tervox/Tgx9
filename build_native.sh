#!/bin/bash
set -e

NDK_VERSION="23.2.8568313"
NDK="$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"
BUILD_PLATFORM="linux-x86_64"
PREBUILT="$NDK/toolchains/llvm/prebuilt/$BUILD_PLATFORM"
SYSROOT="$PREBUILT/sysroot"
THIRDPARTY="$(pwd)/app/jni/third_party"
CPU_FEATURES="$NDK/sources/android/cpufeatures"
CPU_COUNT=$(nproc)
FLAVOR="legacy"
ANDROID_API=16
NDK_ABIARCH="armv7a-linux-androideabi"
TARGET="armv7-android-gcc --enable-neon --disable-neon-asm"
CPU=armv7-a
CFLAGS_="-DANDROID -fpic -fpie"

export PATH=${PREBUILT}/bin:$PATH
CROSS_PREFIX_CLANG="${PREBUILT}/bin/${NDK_ABIARCH}${ANDROID_API}-"

export AR="${PREBUILT}/bin/llvm-ar"
export CC="${CROSS_PREFIX_CLANG}clang"
export CXX="${CROSS_PREFIX_CLANG}clang++"
export CPP="$CXX"
export AS="$CC"
export LD="$CC"
export STRIP="${PREBUILT}/bin/llvm-strip"
export RANLIB="${PREBUILT}/bin/llvm-ranlib"
export NM="${PREBUILT}/bin/llvm-nm"

export CFLAGS="${CFLAGS_} -Os -march=armv7-a -marm -mfloat-abi=softfp -mfpu=neon -mthumb -D__thumb__ -I${CPU_FEATURES}"
export CPPFLAGS="$CFLAGS"
export CXXFLAGS="$CFLAGS -std=c++11"
export ASFLAGS=""
export LDFLAGS="-L${SYSROOT}/usr/lib"
PREFIX="./build/$FLAVOR/$CPU"

echo "=== Building libvpx for $FLAVOR/$CPU ==="
pushd "$THIRDPARTY/libvpx"
make clean || echo "first time"

# Note: --target without quotes so shell splits "armv7-android-gcc --enable-neon --disable-neon-asm"
./configure \
  --libc="$SYSROOT" \
  --prefix="$PREFIX" \
  --target=${TARGET} \
  --enable-runtime-cpu-detect \
  --as=auto \
  --disable-docs \
  --enable-pic \
  --enable-libyuv \
  --enable-static \
  --enable-small \
  --enable-optimizations \
  --enable-better-hw-compatibility \
  --enable-realtime-only \
  --enable-vp8 \
  --enable-vp9 \
  --disable-webm-io \
  --disable-examples \
  --disable-tools \
  --disable-debug \
  --disable-unit-tests || exit 1

make -j"$CPU_COUNT" install
popd

echo "=== Building ffmpeg for $FLAVOR/$CPU ==="
pushd "$THIRDPARTY/ffmpeg"
make clean || echo "first time"

LIBVPX_INCLUDE="$THIRDPARTY/libvpx/build/$FLAVOR/$CPU/include"
LIBVPX_LIB="$THIRDPARTY/libvpx/build/$FLAVOR/$CPU/lib"
LIBS_DIR="${PREBUILT}/lib64/clang/12.0.9/lib/linux"
LINK="$SYSROOT/usr/lib/arm-linux-androideabi/$ANDROID_API"
ARM_CROSS="${PREBUILT}/bin/arm-linux-androideabi-"

# Set PKG_CONFIG_PATH so ffmpeg finds vpx.pc
export PKG_CONFIG_PATH="$LIBVPX_LIB/pkgconfig"
export PKG_CONFIG_LIBDIR="$LIBVPX_LIB/pkgconfig"

./configure \
  --prefix="./build/$FLAVOR/$CPU" \
  --enable-cross-compile \
  --target-os=android \
  --arch=arm \
  --cpu=armv7-a \
  --cc="$CC" --cxx="$CXX" --ld="$CC" \
  --ar="$AR" --nm="$NM" --strip="$STRIP" --ranlib="$RANLIB" \
  --cross-prefix="$ARM_CROSS" \
  --sysroot="$SYSROOT" \
  --enable-static --disable-shared \
  --disable-programs --disable-doc \
  --disable-everything \
  --enable-version3 --enable-gpl \
  --disable-linux-perf \
  --enable-runtime-cpudetect \
  --enable-pthreads \
  --enable-protocol=file \
  --enable-decoder=h264 --enable-decoder=hevc \
  --enable-decoder=vp8 --enable-decoder=vp9 \
  --enable-decoder=libvpx_vp9 \
  --enable-decoder=aac --enable-decoder=mp3 \
  --enable-decoder=alac \
  --enable-decoder=opus --enable-decoder=flac \
  --enable-demuxer=mov --enable-demuxer=matroska \
  --enable-demuxer=ogg --enable-demuxer=mp3 --enable-demuxer=aac \
  --enable-demuxer=gif \
  --enable-muxer=mp4 \
  --enable-filter=scale --enable-filter=overlay \
  --enable-filter=aresample \
  --enable-encoder=aac --enable-encoder=png \
  --enable-bsf=aac_adtstoasc \
  --enable-libvpx \
  --enable-hwaccels \
  --extra-cflags="-fvisibility=hidden -ffunction-sections -fdata-sections -Os -DCONFIG_LINUX_PERF=0 -DANDROID -marm -march=armv7-a -mfloat-abi=softfp -I$LIBVPX_INCLUDE --static -fPIC" \
  --extra-ldflags="-L$LIBVPX_LIB -L$LINK -L${LIBS_DIR} -lvpx -Wl,-Bsymbolic -Wl,--fix-cortex-a8 -nostdlib -lc -lm -ldl -fPIC" \
  --extra-libs="-lunwind -lclang_rt.builtins-arm-android" \
  --enable-neon --disable-x86asm

make -j"$CPU_COUNT"
make install
popd

echo "=== Native build complete ==="
