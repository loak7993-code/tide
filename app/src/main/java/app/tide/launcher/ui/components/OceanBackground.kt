package app.tide.launcher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import app.tide.launcher.ui.theme.OceanPalette
import app.tide.launcher.ui.theme.LocalOceanPalette

/**
 * The animated water behind everything.
 *
 * ### Why it stays smooth
 *
 * The obvious implementation rebuilds a `Brush` from the current animated values
 * every frame, which reallocates and re-uploads a shader sixty times a second.
 * Here every gradient is constructed **once** per palette; the animation only
 * drives `withTransform { translate() }`. Translating an already-built shader is
 * a matrix change on the GPU, so a frame costs a handful of fills no matter how
 * many layers are stacked.
 *
 * The animated values are read *inside* the draw lambda on purpose. A snapshot
 * read during composition would recompose the subtree every frame; the same read
 * inside `Canvas` invalidates only the draw phase.
 *
 * ### Layers
 *  1. Depth gradient — the water column, dark at the top, bright at the horizon.
 *  2. Horizon glow — the sun or moon sitting just under the surface.
 *  3. Caustics — light pools drifting at different speeds, counter-scrolling so
 *     the pattern never visibly repeats.
 *  4. Surface shimmer — slow swell across the horizon, so it is never a hard edge.
 */
@Composable
fun OceanBackground(
    modifier: Modifier = Modifier,
    motion: Boolean = true,
) {
    val palette = LocalOceanPalette.current

    val transition = rememberInfiniteTransition(label = "ocean")

    // Long period: this should read as a current, not as animation.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val glowPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPhase",
    )

    // Built once per palette — these are the expensive objects.
    val brushes = remember(palette) { OceanBrushes(palette) }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Read in draw scope: invalidates draw only, never recomposition.
        val p = if (motion) phase else 0f
        val g = if (motion) glowPhase else 0.5f

        // ── 1. depth ────────────────────────────────────────────────────────
        drawRect(brushes.depth)

        // ── 2. horizon glow ─────────────────────────────────────────────────
        // Sits just below the horizon, breathing gently in radius and strength.
        centeredGlow(
            brush = brushes.glow,
            cx = w * 0.72f,
            cy = h * 0.60f,
            diameter = size.minDimension * 1.60f * (1f + g * 0.06f),
            alpha = 0.34f + g * 0.14f,
        )

        // ── 3. caustics ─────────────────────────────────────────────────────
        // Three pools on different periods and directions. The irrational-ish
        // multipliers stop them from drifting back into alignment.
        centeredGlow(
            brush = brushes.causticA,
            cx = w * (0.24f + p * 0.70f),
            cy = h * (0.80f - p * 0.10f),
            diameter = w * 1.30f,
            alpha = 0.36f,
        )
        centeredGlow(
            brush = brushes.causticB,
            cx = w * (0.92f - p * 0.85f),
            cy = h * (0.52f + p * 0.22f),
            diameter = w * 1.55f,
            alpha = 0.32f,
        )
        centeredGlow(
            brush = brushes.causticC,
            cx = w * (0.10f + p * 1.25f),
            cy = h * (0.66f - p * 0.30f),
            diameter = w * 0.95f,
            alpha = 0.28f,
        )

        // ── 4. shimmer across the horizon ───────────────────────────────────
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to palette.glow.copy(alpha = 0.10f + g * 0.05f),
                1f to Color.Transparent,
                startY = h * 0.58f,
                endY = h * 0.88f,
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
        )

        // ── vignette ────────────────────────────────────────────────────────
        // Keeps the clock and the dock legible over whatever caustic happens to
        // be drifting underneath them.
        drawRect(brushes.vignette)
    }
}

/** All shaders for one palette, built together so there is one memo key. */
private class OceanBrushes(palette: OceanPalette) {
    val depth: Brush = Brush.verticalGradient(
        0f to palette.gradientStops[0],
        0.20f to palette.gradientStops[1],
        0.44f to palette.gradientStops[2],
        0.66f to palette.gradientStops[3],
        0.85f to palette.gradientStops[4],
        1f to palette.gradientStops[5],
    )

    val glow: Brush = radial(palette.glow, softness = 0.80f)
    val causticA: Brush = radial(palette.currentA, softness = 0.85f)
    val causticB: Brush = radial(palette.currentB, softness = 0.90f)
    val causticC: Brush = radial(
        palette.glow.copy(alpha = palette.glow.alpha * 0.26f),
        softness = 0.85f,
    )

    val vignette: Brush = Brush.verticalGradient(
        0f to palette.gradientStops.first().copy(alpha = 0.44f),
        0.26f to Color.Transparent,
        0.72f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.28f),
    )
}

/**
 * Radial falloff anchored at the origin so it can be positioned by translating
 * the canvas rather than by rebuilding the shader.
 *
 * [softness] controls how fast alpha falls off: 1.0 is a tight core fading
 * quickly, lower values carry the colour further out.
 */
private fun radial(color: Color, softness: Float): Brush = Brush.radialGradient(
    colors = listOf(
        color,
        color.copy(alpha = color.alpha * (1f - softness * 0.5f)),
        color.copy(alpha = color.alpha * (1f - softness * 0.8f)),
        Color.Transparent,
    ),
    center = Offset.Zero,
    radius = 1f,
)

/**
 * Draws a [Brush] built by [radial] so its centre lands on ([cx], [cy]).
 *
 * Compose anchors a shader to canvas coordinates, *not* to the rect passed to
 * `drawRect`, so moving the gradient means moving the canvas. The rect is drawn
 * at the origin and the transform does the placing.
 */
private fun DrawScope.centeredGlow(
    brush: Brush,
    cx: Float,
    cy: Float,
    diameter: Float,
    alpha: Float,
) {
    if (alpha <= 0.002f || diameter <= 0f) return
    val radius = diameter / 2f
    withTransform({ translate(cx, cy) }) {
        drawRect(
            brush = brush,
            topLeft = Offset(-radius, -radius),
            size = Size(diameter, diameter),
            alpha = alpha,
        )
    }
}