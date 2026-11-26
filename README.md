<h1 align="center">Gboard Patches</h1>

<p align="center">
  Morphe patches for Gboard, based on <a href="https://github.com/jasonwu1994/Gboard-patches">jasonwu1994/Gboard-patches</a> with extra features.
</p>

<p align="center">
  <a href="https://github.com/havaem/Gboard-patches/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/havaem/Gboard-patches?display_name=tag&label=Release&style=for-the-badge"></a>
  <a href="https://github.com/havaem/Gboard-patches"><img alt="Total downloads" src="https://img.shields.io/github/downloads/havaem/Gboard-patches/total?label=Downloads&style=for-the-badge"></a>
  <a href="https://morphe.software/add-source?github=havaem/Gboard-patches"><img alt="Add to Morphe" src="https://img.shields.io/badge/Morphe-Add%20Source-00A8FF?style=for-the-badge"></a>
</p>

## Overview

This repository is a Morphe source for Gboard. It includes every patch of the original project and keeps following it, then adds the features listed below. For the full list of the original patches, see the [original README](https://github.com/jasonwu1994/Gboard-patches#included-patches).

## What this version adds

### Custom Stickers

Create your own sticker packs from PNG, WebP, JPEG, or GIF images and use them in Gboard's Stickers tab alongside the official packs.

- Pack cover, sticker preview grid, multi-select import (including from other apps such as Google Photos), drag-and-drop reordering, and a Show in keyboard switch per pack.
- Sticker editor with plain-background removal, round corners, and an outline with adjustable width and color. Edits are non-destructive, so a sticker can always be re-edited from its original, and several stickers can be edited at once.
- Each pack can apply default edits to newly added stickers.
- Export and import a pack as a ZIP file.
- Messenger shows only images with a transparent background as stickers, so use Remove background or Outline for photos.

### Other differences

- The default app name is **GboardHavaem**.

## Install

Add this repository as a Morphe source:

- [Open in Morphe](https://morphe.software/add-source?github=havaem/Gboard-patches)
- Or manually add `https://github.com/havaem/Gboard-patches`

## Build

Before running Gradle locally, authenticate to Morphe's GitHub Packages registry with either:

- `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties`
- `GITHUB_ACTOR` and `GITHUB_TOKEN` as environment variables

Build the Android patch bundle:

```powershell
.\gradlew.bat :patches:buildAndroid
```

Regenerate patch metadata:

```powershell
.\gradlew.bat generatePatchesList
```

Generated outputs:

- `patches/build/libs/*.mpp`
- `patches-list.json`
- `patches-bundle.json`

## Credits

Based on [jasonwu1994/Gboard-patches](https://github.com/jasonwu1994/Gboard-patches). Third-party notices are in [NOTICE](NOTICE).

## License

Released under the [GNU General Public License v3.0](LICENSE).
