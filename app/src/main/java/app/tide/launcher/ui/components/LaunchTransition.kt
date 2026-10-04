package app.tide.launcher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp

/**
 * Bridges the gap between tapping an icon and the target activity taking over.
 *
 * Without this the launcher simply stops being drawn the instant the app is
 * launched, which reads as an instant cut. Here the tapped icon travels from
 * where it was drawn to the centre of the screen and out, over a scrim that
 * fades up — so the handover looks like the app *opening from its icon* rather
 * than the home screen being switched off.
 *
 * [progress] runs 0 → 1. The icon stays opaque while it travels and fades over
 * the last stretch, so the incoming app is fully visible when it takes focus.
 */
@Composable
fun LaunchTransition(
    icon: ImageBitmap?,
    from: Rect,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    if (from.width <= 0f || from.height <= 0f) return
    val p = progress.coerceIn(0f, 1f)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }

        // Scrim first so the icon draws over it.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f * p)),
        )

        if (icon != null && screenWidth > 0f && screenHeight > 0f) {
            // Grow enough for the icon to cover the screen on its short side.
            val targetScale = maxOf(screenWidth / from.width, screenHeight / from.height)
            val dx = screenWidth / 2f - from.center.x
            val dy = screenHeight / 2f - from.center.y

            Box(
                modifier = Modifier
                    .offset { IntOffset(from.left.toInt(), from.top.toInt()) }
                    .size(
                        width = with(density) { from.width.toDp() },
                        height = with(density) { from.height.toDp() },
                    )
                    .graphicsLayer {
                        // transformOrigin defaults to the centre, so translating
                        // by (dx, dy) carries that centre to the screen's.
                        val scale = lerp(1f, targetScale, p)
                        scaleX = scale
                        scaleY = scale
                        translationX = lerp(0f, dx, p)
                        translationY = lerp(0f, dy, p)
                        alpha = (1f - ((p - 0.5f) / 0.5f)).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Easing shared by the launch and onboarding transitions. */
val LaunchEasing = FastOutSlowInEasing