package app.tide.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tide.launcher.data.AppEntry
import app.tide.launcher.data.IconCache
import app.tide.launcher.data.IconShape
import app.tide.launcher.ui.theme.AppLabelStyle
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Motion

/** Icon mask per user preference. */
fun IconShape.toShape(): Shape = when (this) {
    // 34% is as close as a rounded rect gets to the adaptive-icon squircle; the
    // real superellipse is not expressible as a Compose Shape.
    IconShape.Squircle -> RoundedCornerShape(percent = 34)
    IconShape.Circle -> CircleShape
    IconShape.Rounded -> RoundedCornerShape(percent = 16)
}

/**
 * One app in the grid, with its label.
 *
 * The icon is resolved off the composition thread and memoised in [IconCache],
 * so scrolling never touches the `PackageManager`. `produceState` is keyed on the
 * component, so a recycled grid cell re-resolves rather than briefly showing the
 * previous app's icon.
 */
@Composable
fun AppIconTile(
    entry: AppEntry,
    shape: IconShape,
    showLabel: Boolean,
    iconSize: Dp = 56.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalOceanPalette.current

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val icon by produceState<ImageBitmap?>(
        initialValue = IconCache.peek(entry.component),
        key1 = entry.component,
    ) {
        if (value == null) value = IconCache.load(context, entry.component)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 22))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .semantics { contentDescription = entry.label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            icon = icon,
            shape = shape.toShape(),
            size = iconSize,
            pressed = pressed,
        )

        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.label,
                style = AppLabelStyle,
                color = palette.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The icon itself, plus its press spring.
 *
 * Split from [AppIconTile] so the dock can reuse the icon without inheriting a
 * label column, and so the caller owns the interaction rather than the icon
 * also consuming taps.
 */
@Composable
fun AppIcon(
    icon: ImageBitmap?,
    shape: Shape,
    size: Dp,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    scaleOverride: Float = 1f,
) {
    val palette = LocalOceanPalette.current

    // Press dips and rebounds with a little overshoot. The dip and the rebound
    // use different springs so releasing feels elastic rather than mechanical.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = Motion.Press,
        label = "pressScale",
    )
    val liftScale by animateFloatAsState(
        targetValue = if (pressed) 1.05f else 1f,
        animationSpec = Motion.Spatial,
        label = "liftScale",
    )

    val scale = scaleOverride * pressScale * liftScale

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (pressed) 3.dp else 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.45f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.currentA.copy(alpha = 0.35f),
                        palette.currentB.copy(alpha = 0.20f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Placeholder: a soft wash in the current theme, so an unresolved
            // cell reads as "still loading" rather than as a broken image.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                palette.accent.copy(alpha = 0.22f),
                                palette.glassBorder.copy(alpha = 0.10f),
                            ),
                        ),
                    ),
            )
        }
    }
}