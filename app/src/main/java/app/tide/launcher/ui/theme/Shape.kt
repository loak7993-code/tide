package app.tide.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Nothing here is sharper than [Radius.sm] — that is the "less blocky" rule. */
val TideShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/** Rounding applied to launcher icons, above the Material scale. */
val IconCorner = RoundedCornerShape(percent = 34)

/** Rounding for the glass panels — search bar, dock, sheets. */
val GlassCorner = RoundedCornerShape(Radius.lg)

/** Full pill, for the search field and chips. */
val PillCorner = RoundedCornerShape(Radius.pill)