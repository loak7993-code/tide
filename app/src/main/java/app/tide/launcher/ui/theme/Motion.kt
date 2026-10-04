package app.tide.launcher.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Motion constants. Animation is what separates "a grid of icons" from
 * something that feels alive, so the specs are named and shared rather than
 * inline `spring(dampingRatio = 0.8f)` scattered through the UI.
 *
 * Two families:
 *  - **Spatial** springs for anything the user directly grabs (grids, sheets).
 *    Low damping, so it overshoots slightly and settles.
 *  - **Emphasis** springs for state changes the user did not drag (an app
 *    resolving from a launch). Fast and dead-stopped.
 */

object Motion {
    /** Grabbing and dragging. Springsy, no dead stop. */
    val Spatial: SpringSpec<Float> = spring(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Snap-back after a release. */
    val SpatialSnap: SpringSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Press feedback. Fast in, dead stop. */
    val Press: SpringSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessHigh,
    )

    /** Elements appearing. Quick, no wobble. */
    val Emphasis: SpringSpec<Float> = spring(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Sheets and full-screen surfaces travelling in and out. */
    val Sheet: SpringSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessLow,
    )

    /** Drawer ↔ home crossfade. Deliberately slow enough to read as travel. */
    val Crossfade: SpringSpec<Float> = spring(
        dampingRatio = 0.94f,
        stiffness = Spring.StiffnessLow,
    )

    /**
     * Offset-based spring, for the API surfaces that animate an `IntOffset`
     * (sheet and drawer travel). A [SpringSpec] is a [FiniteAnimationSpec], so
     * this can be handed straight to `slideInVertically`.
     */
    val SheetOffset: SpringSpec<androidx.compose.ui.unit.IntOffset> = spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessLow,
    )

    const val Fast = 140
    const val Medium = 260
    const val Slow = 420
}

/**
 * Corner radii. The brief was "less blocky", so nothing in the launcher uses a
 * radius below [sm]; the grid icons sit well above that.
 */
object Radius {
    val xs: Dp = 8.dp
    val sm: Dp = 14.dp
    val md: Dp = 20.dp
    val lg: Dp = 28.dp
    val xl: Dp = 36.dp
    val pill: Dp = 999.dp
}