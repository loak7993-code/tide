package app.tide.launcher.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Motion
import app.tide.launcher.ui.theme.TideTypography
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The clock above the grid: time, date, and a tide gauge.
 *
 * The gauge is the day's progress drawn as a filling water line, with the sun or
 * moon riding an arc above it. The brief was ocean, and a plain progress bar
 * would have read as a system widget.
 */
@Composable
fun ClockWidget(modifier: Modifier = Modifier) {
    val palette = LocalOceanPalette.current

    // Ticks once a second. Only this composable reads it, so the grid below does
    // not recompose every second.
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            value = LocalDateTime.now()
        }
    }

    // Recomputed only when the date rolls over, not on every tick.
    val dayFraction = remember(now.toLocalDate()) {
        val seconds = Duration.between(now.toLocalDate().atStartOfDay(), now).seconds
        (seconds / 86_400f).coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())),
            style = TideTypography.displayLarge,
            color = palette.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = greeting(now.hour) + " · " +
                now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
            style = TideTypography.bodyLarge,
            color = palette.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        TideGauge(progress = dayFraction, modifier = Modifier.padding(horizontal = 48.dp))
    }
}

/**
 * A filling water line showing how far through the day it is, with the sun or
 * moon travelling its arc above it.
 *
 * Drawn on a [Canvas] rather than composed from boxes: the arc needs real
 * trigonometry, and one draw node beats a stack of layout nodes for something
 * this small.
 */
@Composable
private fun TideGauge(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOceanPalette.current

    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = Motion.Emphasis,
        label = "tide",
    )

    // A slow swell so the water line breathes instead of sitting perfectly still.
    val swell = rememberInfiniteTransition(label = "gauge")
    val swellPhase by swell.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "swellPhase",
    )

    Canvas(modifier.fillMaxWidth().height(30.dp)) {
        val w = size.width
        val waterY = size.height * 0.86f
        val lineThickness = 3.dp.toPx()
        val isNight = animated < 0.25f || animated > 0.79f
        val clamped = animated.coerceIn(0.01f, 1f)

        // ── the arc the light source rides ──────────────────────────────────
        val arcRect = Size(w, size.height * 0.92f)
        drawArc(
            color = palette.glassBorder.copy(alpha = 0.30f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(0f, size.height * 0.92f - arcRect.height),
            size = arcRect,
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
        )

        // Position on the arc, by angle: -90° at the left, +90° at the right.
        val angle = (clamped * 2f - 1f) * (Math.PI.toFloat() / 2f)
        val arcCx = w / 2f
        val arcCy = size.height * 0.92f
        val arcRx = w / 2f - 6.dp.toPx()
        val arcRy = size.height * 0.86f
        val markerX = arcCx + sin(angle) * arcRx
        val markerY = arcCy - cos(angle) * arcRy

        drawCircle(
            color = if (isNight) palette.accentAlt.copy(alpha = 0.9f) else palette.glow,
            radius = 4.5.dp.toPx(),
            center = Offset(markerX, markerY),
        )

        // ── the water line ──────────────────────────────────────────────────
        // Unfilled remainder.
        drawLine(
            color = palette.glassBorder.copy(alpha = 0.32f + swellPhase * 0.12f),
            start = Offset(0f, waterY),
            end = Offset(w, waterY),
            strokeWidth = lineThickness,
            cap = StrokeCap.Round,
        )

        // Filled portion, drawn as a gradient so it reads as depth rather than a
        // flat bar.
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(palette.accent, palette.accentAlt),
                startX = 0f,
                endX = w,
            ),
            start = Offset(0f, waterY),
            end = Offset(w * clamped, waterY),
            strokeWidth = lineThickness,
            cap = StrokeCap.Round,
        )

        // A soft bloom at the leading edge of the water.
        if (clamped > 0.02f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = 0.5f),
                        Color.Transparent,
                    ),
                    center = Offset(w * clamped, waterY),
                    radius = 14.dp.toPx(),
                ),
                radius = 14.dp.toPx(),
                center = Offset(w * clamped, waterY),
            )
        }
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..21 -> "Good evening"
    else -> "Late night"
}