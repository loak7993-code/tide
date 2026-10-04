# Tide

An Android home-screen launcher built around moving water: a drifting ocean
gradient, caustic light, and glass surfaces floating on top of it.

<p align="center"><em>Kotlin · Jetpack Compose · Material 3 · minSdk 26 · targetSdk 36</em></p>

## What it does

| | |
|---|---|
| **Home screen** | App grid plus a hotseat dock, both persisted across restarts. |
| **App drawer** | Full list with letter headers and an A–Z jump rail. |
| **Search** | Fuzzy subsequence matching — `gm` finds *Gmail*, `ch` finds *Chrome* and *Clock*. |
| **Long press** | Pin to dock, hide, app info, uninstall, and any shortcuts the app declares. |
| **Ocean motion** | Layered animated background: depth gradient, horizon glow, three drifting caustic pools, and a swell across the horizon. |
| **Four themes** | Sunrise (light), Tide, Deep, Night tide. Each is a full palette, not a hue rotation. |
| **Undo** | Hiding or unpinning is reversible from the notice bar. |
| **Debug overlay** | Live frame timing, p95 frame time, jank percentage, and heap usage. |

## Screens

The home screen renders the ocean full-bleed with the clock floating over it, a
tide gauge showing how far through the day it is, and the dock as a glass pill.

## Building

Requires JDK 17+ and an Android SDK with platform 36 or newer.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Then set it as the home app:

```bash
adb shell cmd package set-home-activity \
  app.tide.launcher.debug/app.tide.launcher.MainActivity
```

## Release builds

`assembleRelease` runs R8 in full mode with resource shrinking. The rules in
`app/proguard-rules.pro` cover the three things that actually break when
minified: kotlinx.serialization's generated serializers, DataStore's protobuf
descriptors, and enums that round-trip through preferences by name.

To sign a real release, drop a `keystore.properties` in the project root:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, `assembleRelease` falls back to the debug key so the build still
produces an installable artifact. That file and any `*.jks` are gitignored.

## Notes on the design

**Icons are rasterised once, into bitmaps.** `PackageManager.getActivityIcon`
returns a `Drawable`, which carries mutable bounds and alpha — sharing one
instance across grid cells that lay it out at different sizes corrupts it.
`IconCache` flattens each icon to an immutable `ImageBitmap` and memoise it in an
LRU, so scrolling never touches the `PackageManager`.

**Adaptive icons are composited unmasked.** `AdaptiveIconDrawable.draw()` applies
its own OEM mask, which would fight the icon-shape setting. Background and
foreground layers are flattened full-bleed instead, and the shape is applied at
draw time — so switching squircle / circle / rounded is instant rather than a
re-rasterise.

**The ocean is a handful of fills, not a shader per frame.** Every gradient is
built once per palette and the animation only drives a canvas `translate()`. The
animated values are read inside the draw lambda, so they invalidate only the
draw phase rather than recomposing the tree every frame.

**Gestures are deliberately conservative.** Swipe-up lives on the clock area,
not the grid: a vertical drag recogniser on a scrollable list either fights the
scroll or silently stops working once scrolled. Both feel broken, so the gesture
is confined to a zone where it is always available and never ambiguous.

## Status

Working and installable. Not yet done: widget host, folder support, drag-to-
reorder in the dock, and the instrumentation tests.

## Licence

Not specified yet.