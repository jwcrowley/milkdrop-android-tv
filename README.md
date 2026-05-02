# MilkDrop Android TV

A sideloadable Android TV APK that brings the MilkDrop music visualizer experience to Fire TV Sticks, ONN Android TV boxes, and any Android TV device. Built on [projectM](https://github.com/projectM-visualizer/projectm) — the open-source MilkDrop-compatible rendering engine.

![MilkDrop Android TV](https://raw.githubusercontent.com/milkdrop2077/MilkDrop3/main/MilkDrop3.jpg)

---

## Features

- **Full MilkDrop preset support** — loads `.milk` (MilkDrop 2) and `.milk2` (MilkDrop3) preset files
- **200+ bundled presets** — curated from the projectM Cream of the Crop pack, the classic MilkDrop 2 collection, and the MilkDrop3 community library
- **Real-time audio reactivity** — reacts to music via the device microphone; system audio capture available on Android 10+
- **Beat-driven transitions** — optional bass-triggered preset changes
- **Smooth preset cycling** — configurable interval (10–300s) with blend transitions
- **10-foot TV UI** — D-pad-only navigation, no touchscreen required
- **Fire TV & ONN compatible** — sideloadable APK, no Google Play account needed
- **Dual ABI** — single fat APK supports both 32-bit (armeabi-v7a) and 64-bit (arm64-v8a) ARM devices

---

## Supported Devices

| Device | Status |
|---|---|
| Amazon Fire TV Stick 4K / 4K Max | ✅ Supported |
| Amazon Fire TV Stick (2nd gen+) | ✅ Supported |
| ONN Android TV Box (4K) | ✅ Supported |
| Any Android TV device (API 21+) | ✅ Supported |

---

## Installation

### Via ADB (recommended for developers)

1. Enable **ADB Debugging** on your device:
   - Fire TV: Settings → My Fire TV → Developer Options → ADB Debugging ON
   - Android TV: Settings → Device Preferences → About → Build (click 7×) → Developer Options → USB Debugging ON

2. Connect via ADB (USB or network):
   ```bash
   adb connect <device-ip>:5555
   ```

3. Install the APK:
   ```bash
   adb install -r milkdrop-tv.apk
   ```

### Via Downloader App (Fire TV)

1. Enable **Apps from Unknown Sources**: Settings → My Fire TV → Developer Options → Install Unknown Apps → Downloader → ON
2. Install the [Downloader app](https://www.amazon.com/AFTVnews-com-Downloader/dp/B01N0BP507) from the Amazon Appstore
3. Open Downloader, enter the APK URL, and follow the prompts

---

## Usage

### Controls

| Button | Action |
|---|---|
| **Select / OK** | Next preset |
| **◀ Left** | Previous preset |
| **Any button** | Show/hide overlay |
| **Back** | Return to menu |

### Overlay

Press any button while the visualizer is running to show the overlay. It displays the current preset name, active audio source, and available actions. Auto-hides after 3 seconds.

### Adding Your Own Presets

Copy `.milk` or `.milk2` preset files to:
```
/sdcard/MilkDrop/presets/
```
They'll appear in the preset browser alongside the bundled presets on next launch.

---

## Building from Source

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android NDK r25c or newer
- CMake 3.22.1 or newer
- JDK 17

### Setup

```bash
git clone git@github.com:jwcrowley/milkdrop-android-tv.git
cd milkdrop-android-tv

# Pull the projectM C++ rendering engine
git submodule update --init --recursive
```

### Add Presets

The bundled preset directories ship with placeholder files. To build with a full preset library, populate the following directories before building:

```
app/src/main/assets/presets/projectm-cream/   # ~100 presets
app/src/main/assets/presets/milkdrop2/         # ~60 presets
app/src/main/assets/presets/milkdrop3/         # ~50 presets
```

See [`app/src/main/assets/presets/README.md`](app/src/main/assets/presets/README.md) for sources.

### Build

```bash
# Debug APK
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

See [`BUILDING.md`](BUILDING.md) for release builds and signing.

---

## Architecture

```
Android UI (Kotlin / Leanback)
    └── Application Logic (Kotlin)
            └── NDK Bridge (C++ JNI)
                    └── projectM Core (C++)
```

**Key components:**

| Component | Description |
|---|---|
| `ProjectMBridge` | JNI wrapper around the projectM 4.x C API |
| `ProjectMRenderer` | `GLSurfaceView.Renderer` driving the render loop |
| `AudioCaptureManager` | Selects and manages the active audio source |
| `BeatDetector` | Rising-edge transient detection for beat-driven transitions |
| `PresetManager` | Fisher-Yates shuffle, history, and cycle timer |
| `PresetLibrary` | Indexes bundled and user preset files |
| `SettingsRepository` | Persists user preferences via `SharedPreferences` |

**Threading model:** UI thread → Main thread (Kotlin coroutines) → Render thread (GL) ← Audio thread (PCM capture). Audio frames pass through a lock-free ring buffer to the render thread.

---

## Preset Sources

| Collection | Source |
|---|---|
| projectM Cream of the Crop | [projectM GitHub Releases](https://github.com/projectM-visualizer/projectm/releases) |
| MilkDrop 2 Classic | [Geiss Works / projectM repo](https://www.geisswerks.com/milkdrop/) |
| MilkDrop3 Community | [MilkDrop3 GitHub](https://github.com/milkdrop2077/MilkDrop3) |

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

projectM is licensed under the LGPL 2.1. See the [projectM repository](https://github.com/projectM-visualizer/projectm) for details.
