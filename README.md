# Beam for Android

Native Android client for [Beam](https://github.com/atman-project/beam), the peer-to-peer file sharing app built on [atman](https://github.com/atman-project/atman).

Counterpart to [beam-ios](https://github.com/atman-project/beam-ios). The desktop versions (macOS / Windows / Linux) live in the [beam](https://github.com/atman-project/beam) repo and are built with Tauri.


## Develop

You'll need **Android Studio** (which bundles JDK 17) with the **NDK** installed side-by-side through its SDK Manager, plus a **Rust** toolchain to cross-compile atman.

Set `ANDROID_NDK_HOME` so `cargo-ndk` can find it, add the three Android Rust targets, and install `cargo-ndk`:
```sh
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/<version>"
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
```

`build_atman.sh` cross-compiles atman per ABI, dropping `libatman.so` into `app/src/main/jniLibs/<abi>/` and the UniFFI-generated Kotlin into `app/src/main/java/uniffi/atman/atman.kt`:
```sh
git submodule update --init --recursive
./build_atman.sh             # debug, all three ABIs
./build_atman.sh --release   # release, all three ABIs
```
`AtmanClientFactory.kt`, hand-written next to the generated `atman.kt`, works around UniFFI 0.28's Kotlin codegen skipping async constructors. Delete it once atman moves to UniFFI ≥ 0.29.

Open the project in Android Studio and run on a connected device, or install from the CLI:

```sh
./gradlew installDebug
```


## Release build

```sh
./gradlew bundleRelease           # → app/build/outputs/bundle/release/app-release.aab
```

Sign with a release keystore before uploading to Play Console.
