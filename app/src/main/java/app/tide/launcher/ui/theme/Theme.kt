package app.tide.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The active [OceanPalette], readable from any composable without threading it
 * through every signature. Components that need a raw colour read this rather
 * than hard-coding one.
 */
val LocalOceanPalette = staticCompositionLocalOf {
    TideTheme.Default.palette
}

/** The active theme, for components that need to branch on light vs dark. */
val LocalTideTheme = staticCompositionLocalOf { TideTheme.Default }

@Composable
fun TideTheme(
    theme: TideTheme = TideTheme.Default,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val palette = theme.palette

    // Material components (settings rows, dialogs, sliders) get a scheme derived
    // from the ocean palette so they sit inside the same colour world instead of
    // importing M3's default purple.
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accentAlt,
            onSecondary = palette.onSurface,
            background = palette.gradientStops.last(),
            onBackground = palette.onSurface,
            surface = palette.glass,
            onSurface = palette.onSurface,
            surfaceVariant = palette.currentB,
            onSurfaceVariant = palette.onSurfaceMuted,
            outline = palette.glassBorder,
            error = Coral,
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF00252F),
            secondary = palette.accentAlt,
            onSecondary = Color(0xFF00252F),
            background = palette.gradientStops.last(),
            onBackground = palette.onSurface,
            surface = palette.glass,
            onSurface = palette.onSurface,
            surfaceVariant = palette.currentB,
            onSurfaceVariant = palette.onSurfaceMuted,
            outline = palette.glassBorder,
            error = Coral,
        )
    }

    CompositionLocalProvider(
        LocalOceanPalette provides palette,
        LocalTideTheme provides theme,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = scaledTypography(fontScale),
            shapes = TideShapes,
            content = content,
        )
    }
}