package app.tide.launcher.ui.theme

import androidx.compose.runtime.compositionLocalOf
import app.tide.launcher.data.ClockFormat

/**
 * User-adjustable values that many components need but almost none of them own.
 *
 * Threading these as parameters would put `panelOpacity` on every glass panel
 * and `labelScale` on every icon, so they live in CompositionLocals instead and
 * are provided once at the root from the persisted settings.
 */

/** 0.35 to 1. Multiplies the opacity of every frosted panel. */
val LocalPanelOpacity = compositionLocalOf { 1f }

/** 0.85 to 1.2. Multiplies the app label size everywhere it is drawn. */
val LocalLabelScale = compositionLocalOf { 1f }

/** 0.7 to 1.3. Multiplies the app icon size everywhere it is drawn. */
val LocalIconScale = compositionLocalOf { 1f }

/** 0 stops the ocean animation entirely; 1 is full drift. */
val LocalMotionIntensity = compositionLocalOf { 1f }

/** Whether the clock and date are drawn at all. */
val LocalShowClock = compositionLocalOf { true }
val LocalShowDate = compositionLocalOf { true }

/** 12- or 24-hour, or follow the device locale. */
val LocalClockFormat = compositionLocalOf { ClockFormat.Auto }
/** 0.85 to 1.3. Multiplies every text style, not just app labels. */
val LocalFontScale = compositionLocalOf { 1f }

/** Gap between grid cells as a fraction of cell width. */
val LocalGridSpacing = compositionLocalOf { 0.35f }

/** Multiplier on dock icon size only. */
val LocalDockScale = compositionLocalOf { 1f }

/** Whether the swipe-to-search hint sits under the grid. */
val LocalShowSearchBar = compositionLocalOf { true }
