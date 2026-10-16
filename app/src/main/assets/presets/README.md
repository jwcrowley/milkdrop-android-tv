# MilkDrop Preset Files

## Overview

This directory contains the bundled `.milk` preset files.
The app includes 9,795 projectM Cream of the Crop presets.

## Directory Structure

```
presets/
└── projectm-cream/
    ├── Dancer/
    ├── Drawing/
    └── ...
```

## Preset Source

### projectM Cream of the Crop (`projectm-cream/`)
- **Source:** [projectM Cream of the Crop](https://github.com/projectM-visualizer/presets-cream-of-the-crop)
- **Count:** 9,795 presets

## Preset File Format

Preset files use the standard `.milk` extension.
They are plain-text INI-style files containing:
- Global parameters (zoom, rotation, wave settings, etc.)
- Per-frame equations (evaluated once per rendered frame)
- Per-pixel equations (evaluated per pixel for warp mesh)
- GLSL shader code (warp and composite shaders)

## Adding User Presets

Add `.milk` files to:
```
/sdcard/Android/data/com.example.milkdrop/files/presets/
```

The app merges these files with the bundled presets after a restart.
