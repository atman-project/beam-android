#!/bin/bash
#
# Build atman as a dynamic library for Android and copy the .so per ABI
# + the UniFFI-generated Kotlin bindings into app/src/main/ so Gradle
# picks them up.
#
# Usage:
#   ./build_atman.sh                # debug, all three ABIs
#   ./build_atman.sh --release      # release, all three ABIs

set -uexo pipefail

ANDROID_DIR="$(cd "$(dirname "$0")" && pwd)"
ATMAN_DIR="$(cd "$ANDROID_DIR/submodules/atman" && pwd)"

cd "$ATMAN_DIR/atman"
bash ./build_bindings.sh --features blobs --android "$@"

MODE="debug"
if [[ "$*" == *"--release"* ]]; then
  MODE="release"
fi

JNI_DIR="$ANDROID_DIR/app/src/main/jniLibs"
KOTLIN_DIR="$ANDROID_DIR/app/src/main/java/uniffi/atman"

# Wipe + remake so stale .so / .kt files from earlier builds don't linger.
rm -rf "$JNI_DIR" "$KOTLIN_DIR"
mkdir -p "$JNI_DIR/arm64-v8a" "$JNI_DIR/armeabi-v7a" "$JNI_DIR/x86_64" "$KOTLIN_DIR"

cp "$ATMAN_DIR/target/aarch64-linux-android/$MODE/libatman.so"     "$JNI_DIR/arm64-v8a/libatman.so"
cp "$ATMAN_DIR/target/armv7-linux-androideabi/$MODE/libatman.so"   "$JNI_DIR/armeabi-v7a/libatman.so"
cp "$ATMAN_DIR/target/x86_64-linux-android/$MODE/libatman.so"      "$JNI_DIR/x86_64/libatman.so"
cp "$ATMAN_DIR/target/uniffi-bindings/kotlin/uniffi/atman/atman.kt" "$KOTLIN_DIR/atman.kt"

ls -lh "$JNI_DIR"/*/libatman.so "$KOTLIN_DIR/atman.kt"
