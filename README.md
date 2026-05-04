# MilkDrop Android TV

A sideloadable Android TV APK that brings the MilkDrop music visualizer experience to Fire TV Sticks, ONN Android TV boxes, and any Android TV device. Built on [projectM](https://github.com/projectM-visualizer/projectm) — the open-source MilkDrop-compatible rendering engine.

---

## Features

- **Full MilkDrop preset support** — loads `.milk` (MilkDrop 2) and `.milk2` (MilkDrop3) preset files
- **9,795 bundled presets** — the full projectM Cream of the Crop collection across 10 categories: Dancer, Drawing, Fractal, Geometric, Hypnotic, Particles, Reaction, Sparkle, Supernova, Waveform
- **Flexible audio reactivity** — Auto Pulse generated input by default, plus optional microphone input on devices that expose one
- **Beat-driven transitions** — optional bass-triggered preset changes with soft-cut cooldowns
- **Smooth preset cycling** — configurable interval (10–300s) with projectM blend transitions
- **Favorites and preset lock** — favorite presets, cycle favorites only, or pin the current preset
- **10-foot TV UI** — D-pad-only navigation, cinematic dark home screen, no touchscreen required
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

### Download

Grab the latest APK from the [Releases page](https://github.com/jwcrowley/milkdrop-android-tv/releases/latest).

### Via ADB (recommended for developers)

1. Enable **ADB Debugging** on your device:
   - Fire TV: Settings → My Fire TV → Developer Options → ADB Debugging ON
   - Android TV / ONN: Settings → Device Preferences → About → Build (click 7×) → Developer Options → USB Debugging ON

2. Connect via ADB (network):
   ```bash
   adb connect <device-ip>:5555
   ```

3. Install:
   ```bash
   adb install -r milkdrop-tv.apk
   ```

### Via Downloader App (Fire TV / ONN)

1. Enable **Apps from Unknown Sources** in Developer Options
2. Install the [Downloader app](https://www.amazon.com/AFTVnews-com-Downloader/dp/B01N0BP507) from the Amazon Appstore (or Google Play on ONN)
3. Open Downloader, enter the APK URL from the releases page, and follow the prompts

---

## Usage

### Navigation

| Button | Action |
|---|---|
| **▶ Right** | Next preset |
| **◀ Left** | Previous preset |
| **▲ Up** | Favorite/unfavorite current preset |
| **▼ Down** | Toggle favorites-only cycling |
| **Select / OK** | Show overlay |
| **Hold Select / OK** | Lock/unlock current preset |
| **Back** | Return to menu |

### Main Menu

The home screen has three options navigable by D-pad:
- **▶ Start Visualizer** — launches fullscreen MilkDrop
- **⊞ Browse Presets** — browse and select specific presets
- **⚙ Settings** — configure cycle interval, audio source, resolution, and more

### Overlay

Press **Select / OK** while the visualizer is running to show the overlay. It displays the current preset name, favorite status, preset lock status, active audio source, and available actions. Auto-hides after 3 seconds.

### Settings

Access via the main menu. Options include:
- Preset cycle interval (10–300 seconds)
- Transition duration (1–10 seconds)
- Beat-driven transitions (on/off)
- Audio source (Auto Pulse / Microphone / Silent)
- Display resolution (Native / 720p / 1080p)
- View crash log (for debugging)

### Audio Sources

Android TV does not provide normal apps with a hidden system-audio loopback device. Capturing real device audio requires Android's media-projection flow, which displays the large recording/casting prompt and is unreliable on TV devices. MilkDrop TV therefore uses these sources:

| Source | Behavior |
|---|---|
| **Auto Pulse** | Default. Generates a reactive beat-like signal for projectM. No microphone permission, green privacy dot, or casting prompt. |
| **Microphone** | Uses a real device microphone if Android exposes one. This is real input, but Android shows the green privacy indicator while active. |
| **Silent** | No audio input. Useful for testing non-reactive rendering. |

### Preset Control

Use **▲ Up** to mark the current preset as a favorite. Use **▼ Down** to switch automatic cycling between all presets and favorites only. Hold **Select / OK** to lock the current preset; while locked, automatic and beat-driven transitions pause until unlocked.

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

### Add More Presets (optional)

The repo ships with all 9,795 presets from the Cream of the Crop collection. To add your own, populate the directories under:
```
app/src/main/assets/presets/
```
See [`app/src/main/assets/presets/README.md`](app/src/main/assets/presets/README.md) for sources.

### Build

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

See [`BUILDING.md`](BUILDING.md) for release builds, signing, and sideloading instructions.

---

## Architecture

```
Android UI (Kotlin)
    └── Application Logic (Kotlin / Coroutines)
            └── NDK Bridge (C++ JNI)
                    └── projectM Core (C++)
```

**Threading:** UI thread → Main thread (coroutines) → Render thread (GL, owns EGL context) ← Audio thread (PCM capture via lock-free ring buffer)

**Key components:**

| Component | Description |
|---|---|
| `ProjectMBridge` | JNI wrapper around the projectM 4.x C API |
| `ProjectMRenderer` | `GLSurfaceView.Renderer` driving the render loop |
| `AudioCaptureManager` | Selects and manages the active audio source |
| `BeatDetector` | Rising-edge transient detection for beat-driven transitions |
| `PresetManager` | Fisher-Yates shuffle, 10-entry history, cycle timer |
| `PresetLibrary` | Indexes bundled and user preset files |
| `AssetExtractor` | Copies bundled presets to internal storage on first launch |
| `SettingsRepository` | Persists user preferences via `SharedPreferences` |
| `CrashLogger` | Writes uncaught exceptions to Downloads for debugging |

---

## Preset Sources

| Collection | Source | Count |
|---|---|---|
| projectM Cream of the Crop | [projectM-visualizer/presets-cream-of-the-crop](https://github.com/projectM-visualizer/presets-cream-of-the-crop) | 9,795 |
| MilkDrop 2 Classic | [Geiss Works](https://www.geisswerks.com/milkdrop/) | bundled |
| MilkDrop3 Community | [milkdrop2077/MilkDrop3](https://github.com/milkdrop2077/MilkDrop3) | bundled |

---

## Releases

| Version | Highlights |
|---|---|
| v1.4.10 | Applied soft-cut transition settings, smoother beat transitions, D-pad Right for next preset, long-press OK preset lock, docs refresh |
| v1.4.9 | Auto Pulse generated audio source, no privacy dot or casting prompt by default |
| v1.4.8 | Safer audio-source handling, microphone level display, disabled unreliable system-audio path |
| v1.4.7 | Faster preset startup via stable extraction and preset index caching |
| v1.4.6 | Favorites support and favorites-only cycling |
| v1.4.0 | All 9,795 Cream of the Crop presets bundled |
| v1.3.x | Cinematic TV home screen UI, proper app icon, crash log in Settings |
| v1.2.0 | 2,000 bundled presets from Cream of the Crop collection |
| v1.1.5 | First working build with real projectM rendering |
| v1.0.9 | Fixed GLSurfaceView crash, visualizer runs without crashing |
| v1.0.5 | Fixed black screen on main menu |
| v1.0.0 | Initial release (stub rendering) |

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

projectM is licensed under the LGPL 2.1. See the [projectM repository](https://github.com/projectM-visualizer/projectm) for details.

Preset files from the Cream of the Crop collection are licensed under their respective original licenses — see the [presets repository](https://github.com/projectM-visualizer/presets-cream-of-the-crop) for details.

---

🦵 it's a leg jam!
