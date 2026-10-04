package app.tide.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tide.launcher.ui.theme.GlassCorner
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.LocalPanelOpacity
import app.tide.launcher.ui.theme.Radius

/**
 * A frosted panel.
 *
 * ### On the blur
 *
 * This is a *simulation* of glass, not a real backdrop blur. Compose's
 * `Modifier.blur` filters the content of the node it is attached to, not what is
 * already on screen behind it, and a genuine backdrop blur needs either a
 * window-level `RenderEffect` (API 31+, no pre-31 path) or a third-party effect
 * library. Both were judged not worth it here: the background behind these
 * panels is a smooth animated gradient with no high-frequency detail, so a
 * translucent fill with a hairline border and a top-edge highlight is visually
 * indistinguishable from true frosted glass — and it costs one draw instead of
 * an offscreen pass per frame.
 *
 * The [blurPanels] setting still controls the extra sheen, so the toggle is
 * honest about something visible.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassCorner,
    sheen: Boolean = true,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalOceanPalette.current
    val panelOpacity = LocalPanelOpacity.current

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .clip(shape)
            // Slightly darker at the bottom than the top: real glass picks up
            // more light on the face that is angled toward the source.
            .background(
                Brush.verticalGradient(
                    0f to palette.glass.copy(
                        alpha = (palette.glass.alpha * 1.25f * panelOpacity).coerceAtMost(0.95f),
                    ),
                    1f to palette.glass.copy(
                        alpha = (palette.glass.alpha * 0.75f * panelOpacity).coerceAtMost(0.95f),
                    ),
                ),
            )
            .then(
                if (sheen) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            0f to palette.glassBorder.copy(alpha = palette.glassBorder.alpha * 1.6f),
                            0.5f to palette.glassBorder,
                            1f to palette.glassBorder.copy(alpha = palette.glassBorder.alpha * 0.4f),
                        ),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

/**
 * A compact pill, used for the search field and filter chips.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.pill),
    content: @Composable BoxScope.() -> Unit,
) = GlassSurface(modifier = modifier, shape = shape, content = content)