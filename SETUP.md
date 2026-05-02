# Developer Setup

## First-time setup after cloning

Run the following to initialize the projectM submodule:

```bash
git submodule add https://github.com/projectM-visualizer/projectm.git app/src/main/cpp/projectm
git submodule update --init --recursive
```

Then open the project in Android Studio. The NDK build will compile projectM automatically.

## NDK Requirements
- Android NDK r25c or later
- CMake 3.22.1 or later
- Android Studio Hedgehog (2023.1.1) or later
