package app.tide.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour the launcher paints lives here. Nothing in the UI layer is
 * allowed to name a literal [Color] — components read from [OceanPalette] so a
 * theme swap is a single object replacement rather than a sweep through the
 * tree.
 *
 * The palettes are tuned for a *deep vertical gradient* rather than flat fills:
 * the screen is mostly gradient, and contrast has to hold against the darkest
 * point at the top and the brightest at the horizon.
 */

// ── Shared ocean ramp ────────────────────────────────────────────────────────
// Building blocks. Individual themes pull from these rather than repeating hex.

val Abyss = Color(0xFF04141F)
val DeepSea = Color(0xFF062A3D)
val Shelf = Color(0xFF0A4A63)
val OceanMid = Color(0xFF0B6E8F)
val Lagoon = Color(0xFF17A2B8)
val Shallow = Color(0xFF3FC8D8)
val Foam = Color(0xFFE8F7FA)
val FoamDim = Color(0xFFB6DEE6)
val Sand = Color(0xFFE4D3B0)
val SandDeep = Color(0xFFC9B287)
val Coral = Color(0xFFFF7A66)

// Light-end stops, for the Sunrise theme where the "water" is a shallow lagoon
// under a bright sky.
val SkyPale = Color(0xFFDFF3F7)
val SkyLight = Color(0xFFA8DCE8)
val SkyWarm = Color(0xFFF2E4C8)

@Immutable
data class OceanPalette(
    /** Top-to-bottom stops for the full-screen background gradient. */
    val gradientStops: List<Color>,
    /** Colour of the drifting light source near the horizon. */
    val glow: Color,
    /** Two caustic layers that scroll across each other. */
    val currentA: Color,
    val currentB: Color,
    /** Primary interactive colour. */
    val accent: Color,
    /** Secondary interactive colour, used for the second dock slot ring. */
    val accentAlt: Color,
    /** Text on the gradient. */
    val onSurface: Color,
    val onSurfaceMuted: Color,
    /** Frosted panel fill and its hairline border. */
    val glass: Color,
    val glassBorder: Color,
    /** Label colour for the hotseat and search. */
    val onGlass: Color,
    val isLight: Boolean,
)

/** The four themes surfaced in Settings. */
enum class TideTheme(
    val displayName: String,
    val palette: OceanPalette,
) {
    Sunrise(
        displayName = "Sunrise",
        palette = OceanPalette(
            gradientStops = listOf(
                SkyWarm,
                SkyPale,
                SkyLight,
                Shallow,
                Lagoon,
                OceanMid,
            ),
            glow = Color(0xFFFFF3D6),
            currentA = Color(0x66FFFFFF),
            currentB = Color(0x4D7FE3F0),
            accent = Color(0xFF0E7C99),
            accentAlt = Color(0xFFD9A441),
            onSurface = Color(0xFF07303F),
            onSurfaceMuted = Color(0xCC0B4A5C),
            glass = Color(0x59FFFFFF),
            glassBorder = Color(0x80FFFFFF),
            onGlass = Color(0xFF07303F),
            isLight = true,
        ),
    ),
    Tide(
        displayName = "Tide",
        palette = OceanPalette(
            gradientStops = listOf(
                DeepSea,
                Shelf,
                OceanMid,
                Lagoon,
                Shallow,
                Color(0xFF7FE0EA),
            ),
            glow = Color(0xFFFFE9B8),
            currentA = Color(0x4D9CF0FF),
            currentB = Color(0x3D48D1E0),
            accent = Color(0xFF57E0EF),
            accentAlt = Color(0xFFF3D79B),
            onSurface = Foam,
            onSurfaceMuted = Color(0xBFE8F7FA),
            glass = Color(0x1FFFFFFF),
            glassBorder = Color(0x33FFFFFF),
            onGlass = Foam,
            isLight = false,
        ),
    ),
    Deep(
        displayName = "Deep",
        palette = OceanPalette(
            gradientStops = listOf(
                Abyss,
                DeepSea,
                Shelf,
                OceanMid,
                Color(0xFF13809E),
                Lagoon,
            ),
            glow = Color(0xFFBFE9FF),
            currentA = Color(0x401D7FA0),
            currentB = Color(0x2E0E5A73),
            accent = Color(0xFF3FC8D8),
            accentAlt = Color(0xFF7FB6C4),
            onSurface = Color(0xFFDFF2F7),
            onSurfaceMuted = Color(0xA8B4D4DC),
            glass = Color(0x14FFFFFF),
            glassBorder = Color(0x26FFFFFF),
            onGlass = Color(0xFFDFF2F7),
            isLight = false,
        ),
    ),
    Night(
        displayName = "Night tide",
        palette = OceanPalette(
            gradientStops = listOf(
                Color(0xFF02090F),
                Abyss,
                DeepSea,
                Color(0xFF073246),
                Shelf,
                Color(0xFF0A4A63),
            ),
            glow = Color(0xFF9FD6E8),
            currentA = Color(0x2E2C6B82),
            currentB = Color(0x1F0B3D52),
            accent = Color(0xFF4FB8CC),
            accentAlt = Color(0xFF6E8FA8),
            onSurface = Color(0xFFC8E4EE),
            onSurfaceMuted = Color(0x8C9FC0CC),
            glass = Color(0x0DFFFFFF),
            glassBorder = Color(0x1FFFFFFF),
            onGlass = Color(0xFFC8E4EE),
            isLight = false,
        ),
    ),
    ;

    companion object {
        /** Applied when nothing has been persisted yet. */
        val Default = Tide

        fun fromName(name: String?): TideTheme =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/** Status/navigation bar scrim for a theme, kept in sync with its gradient. */
fun TideTheme.barTint(): Color = when (this) {
    TideTheme.Sunrise -> SkyLight
    TideTheme.Tide -> Shelf
    TideTheme.Deep -> DeepSea
    TideTheme.Night -> Color(0xFF02090F)
}