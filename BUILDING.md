# Building MilkDrop Android TV

## Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **Android NDK r25c** or newer (install via SDK Manager → SDK Tools → NDK)
- **CMake 3.22.1** or newer (install via SDK Manager → SDK Tools → CMake)
- **JDK 17** (bundled with recent Android Studio releases)
- **ADB** (included with Android SDK platform-tools)

## Initialize the projectM Submodule

The C++ rendering engine is pulled in as a Git submodule. After cloning this
repository, run:

```bash
git submodule update --init --recursive
```

This populates `app/src/main/cpp/projectm/` with the projectM source tree.
If the submodule was already initialized but is out of date, run:

```bash
git submodule update --recursive --remote
```

## Add Real Preset Files

The `app/src/main/assets/presets/` directory ships with placeholder
subdirectories. To use real MilkDrop presets, follow the instructions in:

```
app/src/main/assets/presets/README.md
```

That file explains which preset packs are compatible and where to place the
`.milk` / `.milk2` files so the app can discover them at runtime.

## Build a Debug APK

From the project root (`milkdrop-android-tv/`):

```bash
./gradlew assembleDebug
```

The output APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Build a Release APK

```bash
./gradlew assembleRelease
```

> **Note:** A signing configuration must be set up before a release build can
> be installed on a device. Add a `signingConfigs` block to
> `app/build.gradle.kts` referencing your keystore, or use Android Studio's
> **Build → Generate Signed Bundle / APK** wizard.

The unsigned (or signed) output is written to:

```
app/build/outputs/apk/release/app-release.apk
```

## Sideload via ADB

Connect your Android TV / Fire TV device with ADB debugging enabled, then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `-r` flag reinstalls over an existing version without wiping data.

To verify the device is reachable before installing:

```bash
adb devices
```

## Sideload via Downloader App (Fire TV)

1. On your Fire TV, go to **Settings → My Fire TV → Developer Options** and
   enable **Apps from Unknown Sources**.
2. Install the **Downloader** app from the Amazon Appstore.
3. Host the APK on a local web server or a file-sharing service that provides
   a direct download URL.
4. Open Downloader, enter the URL, and follow the on-screen prompts to
   download and install the APK.

Alternatively, enable **ADB Debugging** in Developer Options and use the
wireless ADB workflow described above (`adb connect <fire-tv-ip>:5555`).
