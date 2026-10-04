package app.tide.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Type scale. The system sans is kept, but weights and tracking are pushed
 * harder than the Material defaults: the launcher leans on large numerals for
 * the clock, where negative tracking is what stops it looking like a spreadsheet.
 */
val TideTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 84.sp,
        lineHeight = 88.sp,
        letterSpacing = (-3).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 60.sp,
        lineHeight = 64.sp,
        letterSpacing = (-2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.3.sp,
    ),
)

/** App labels under grid icons: two lines, ellipsised by the caller. */
val AppLabelStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 11.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.1.sp,
    textAlign = TextAlign.Center,
)

/**
 * The type ramp with the user's font scale applied.
 *
 * Scaling here rather than at each call site means a change reaches the clock,
 * the settings rows, the drawer and the sheets at once — which is the point of
 * a font-size preference. Line heights scale with the size, since they are
 * absolute sp and would otherwise clip descenders at larger settings.
 */
fun scaledTypography(scale: Float): Typography = Typography(
    displayLarge = TideTypography.displayLarge.scaled(scale),
    displayMedium = TideTypography.displayMedium.scaled(scale),
    displaySmall = TideTypography.displaySmall.scaled(scale),
    headlineLarge = TideTypography.headlineLarge.scaled(scale),
    headlineMedium = TideTypography.headlineMedium.scaled(scale),
    headlineSmall = TideTypography.headlineSmall.scaled(scale),
    titleLarge = TideTypography.titleLarge.scaled(scale),
    titleMedium = TideTypography.titleMedium.scaled(scale),
    titleSmall = TideTypography.titleSmall.scaled(scale),
    bodyLarge = TideTypography.bodyLarge.scaled(scale),
    bodyMedium = TideTypography.bodyMedium.scaled(scale),
    bodySmall = TideTypography.bodySmall.scaled(scale),
    labelLarge = TideTypography.labelLarge.scaled(scale),
    labelMedium = TideTypography.labelMedium.scaled(scale),
    labelSmall = TideTypography.labelSmall.scaled(scale),
)

private fun TextStyle.scaled(factor: Float): TextStyle = if (factor == 1f) {
    this
} else {
    copy(
        fontSize = fontSize * factor,
        lineHeight = lineHeight * factor,
    )
}
