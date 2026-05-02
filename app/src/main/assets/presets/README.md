# MilkDrop Preset Files

## Overview

This directory contains bundled `.milk` preset files for the MilkDrop TV app.
The app bundles 200+ presets across three collections, organized into subdirectories.

## Directory Structure

```
presets/
├── projectm-cream/     # ~100 presets from the projectM "Cream of the Crop" pack
├── milkdrop2/          # ~60 presets from the original MilkDrop 2 collection
└── milkdrop3/          # ~50 presets from the MilkDrop3 community library
```

## ⚠️ Action Required Before Building

These directories must be populated with real `.milk` preset files before building
a production APK. The sample files included here are minimal placeholders for
development and testing purposes only.

## Where to Get Real Presets

### projectM Cream of the Crop (`projectm-cream/`)
- **Source:** [projectM GitHub Releases](https://github.com/projectM-visualizer/projectm/releases)
- Look for the "presets-cream-of-the-crop" asset in any recent release
- Contains ~100 high-quality community-curated presets

### MilkDrop 2 Classic Collection (`milkdrop2/`)
- **Source:** Bundled with the original [MilkDrop 2 Winamp plugin](https://www.geisswerks.com/milkdrop/)
- Also available via the projectM repository under `src/projectM-qt/presets/`
- Contains the classic presets from the original MilkDrop 2 release

### MilkDrop3 Community Presets (`milkdrop3/`)
- **Source:** [MilkDrop3 GitHub Repository](https://github.com/milkdrop2077/MilkDrop3)
- Community-contributed presets compatible with the MilkDrop3 engine
- Contains ~50 modern presets with advanced shader effects

## Preset File Format

Preset files use the `.milk` (MilkDrop 2) or `.milk2` extension.
They are plain-text INI-style files containing:
- Global parameters (zoom, rotation, wave settings, etc.)
- Per-frame equations (evaluated once per rendered frame)
- Per-pixel equations (evaluated per pixel for warp mesh)
- GLSL shader code (warp and composite shaders)

## Adding User Presets

Users can also add their own presets at runtime by placing `.milk` files in:
```
/sdcard/MilkDrop/presets/
```
These are merged with bundled presets in the preset browser.
