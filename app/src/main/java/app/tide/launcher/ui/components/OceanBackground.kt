package app.tide.launcher.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import app.tide.launcher.ui.theme.LocalMotionIntensity
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.OceanPalette

/**
 * The background is rendered at a third of the display resolution and blitted
 * up. It is soft everywhere — no high-frequency detail — so the upscale is
 * invisible while the expensive gradient work drops by ~9×.
 */
private const val RENDER_SCALE = 3f

/**
 * The animated water behind everything.
 *
 * ### Why this is offscreen-rendered
 *
 * A depth gradient, a horizon glow, three caustic pools, a shimmer band and a
 * vignette is six large fills per frame, four of them radial — and a radial
 * gradient evaluates a `sqrt` per pixel. At 1080×2400 that is roughly 15M
 * shader invocations per frame: fine on a phone GPU, and enough to ANR a
 * software renderer outright.
 *
 * Rendering the same layers into a third-resolution buffer and blitting up with
 * bilinear filtering cuts the gradient work by ~9×, and the upscale itself is a
 * straight copy with no `sqrt`. On something this smooth, downsampling is the
 * correct tool rather than a compromise.
 *
 * ### What still runs per frame
 *
 * Shaders are built **once** per palette; only canvas `translate()` moves. The
 * animated values are read inside the draw lambda, so they invalidate the draw
 * phase and never trigger recomposition.
 */
@Composable
fun OceanBackground(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalOceanPalette.current
    // Below ~4% there is nothing left to see, so the animation is stopped
    // outright rather than burning frames on motion nobody can perceive.
    val intensity = (LocalMotionIntensity.current * if (enabled) 1f else 0f)
    val animating = intensity > 0.04f

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

    val layers = remember(palette) { OceanLayers(palette) }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Read in draw scope: invalidates draw only.
        val p = if (animating) phase * intensity else 0f
        val g = if (animating) glowPhase else 0.5f

        val buffer = layers.bufferFor(w, h)
        val canvas = buffer.canvas
        // save/restore around the downscale: an android.graphics.Canvas keeps
        // its matrix between frames, so an unbalanced scale would compound
        // (1/3, 1/9, 1/27 …) and walk the artwork into the top-left corner.
        canvas.save()
        canvas.scale(1f / RENDER_SCALE, 1f / RENDER_SCALE)

        // ── 1. depth ────────────────────────────────────────────────────────
        // Covers the whole buffer, so nothing needs clearing between frames.
        canvas.drawRect(0f, 0f, w, h, layers.depthPaint)

        // ── 2. horizon glow ─────────────────────────────────────────────────
        layers.pool(
            canvas, layers.glowPaint,
            cx = w * 0.72f, cy = h * 0.60f,
            radius = minOf(w, h) * 0.80f * (1f + g * 0.06f),
            alpha = 0.34f + g * 0.14f,
        )

        // ── 3. caustics ─────────────────────────────────────────────────────
        // Three pools on different periods and directions; the uneven
        // multipliers stop them from drifting back into alignment.
        layers.pool(
            canvas, layers.causticAPaint,
            cx = w * (0.24f + p * 0.70f), cy = h * (0.80f - p * 0.10f),
            radius = w * 0.65f, alpha = 0.36f,
        )
        layers.pool(
            canvas, layers.causticBPaint,
            cx = w * (0.92f - p * 0.85f), cy = h * (0.52f + p * 0.22f),
            radius = w * 0.78f, alpha = 0.32f,
        )
        layers.pool(
            canvas, layers.causticCPaint,
            cx = w * (0.10f + p * 1.25f), cy = h * (0.66f - p * 0.30f),
            radius = w * 0.48f, alpha = 0.28f,
        )

        // ── 4. shimmer across the horizon ───────────────────────────────────
        layers.shimmerPaint.alpha = ((0.10f + g * 0.05f) * 255f).toInt()
        canvas.drawRect(0f, h * 0.58f, w, h * 0.88f, layers.shimmerPaint)

        // ── vignette ────────────────────────────────────────────────────────
        canvas.drawRect(0f, 0f, w, h, layers.vignettePaint)

        canvas.restore()

        // ── upscale ─────────────────────────────────────────────────────────
        drawIntoCanvas { target ->
            target.nativeCanvas.drawBitmap(buffer.bitmap, null, layers.dst, layers.blitPaint)
        }
    }
}

/** Offscreen target for the low-resolution pass, reused across frames. */
private class OceanBuffer(val bitmap: Bitmap) {
    val canvas = Canvas(bitmap)
}

/**
 * Shaders, paints and the reusable offscreen buffer for one palette.
 *
 * Everything is expressed in full-resolution units; the canvas is scaled down
 * once per frame, so the gradient maths matches the display size.
 */
private class OceanLayers(private val palette: OceanPalette) {

    val depthPaint = flatPaint()
    val glowPaint = flatPaint()
    val causticAPaint = flatPaint()
    val causticBPaint = flatPaint()
    val causticCPaint = flatPaint()
    val shimmerPaint = flatPaint()
    val vignettePaint = flatPaint()

    val blitPaint = Paint().apply {
        isFilterBitmap = true
        isDither = true
    }

    val dst = RectF()

    private var buffer: OceanBuffer? = null

    fun bufferFor(width: Float, height: Float): OceanBuffer {
        val bw = (width / RENDER_SCALE).toInt().coerceAtLeast(1)
        val bh = (height / RENDER_SCALE).toInt().coerceAtLeast(1)

        val existing = buffer
        if (existing != null && existing.bitmap.width == bw && existing.bitmap.height == bh) {
            return existing
        }

        val created = OceanBuffer(Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888))
        buffer = created
        dst.set(0f, 0f, width, height)
        buildShaders(width, height)
        return created
    }

    /**
     * Draws a radial pool centred on ([cx], [cy]).
     *
     * A [RadialGradient] is anchored to canvas coordinates, not to the rect it
     * is drawn into, so the pool is placed by translating the canvas rather than
     * by rebuilding the shader — which is what keeps this off the per-frame
     * shader-compile path.
     */
    fun pool(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        radius: Float,
        alpha: Float,
    ) {
        if (alpha <= 0.002f || radius <= 0f) return
        canvas.save()
        canvas.translate(cx, cy)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawCircle(0f, 0f, radius, paint)
        canvas.restore()
    }

    private fun buildShaders(width: Float, height: Float) {
        val stops = palette.gradientStops

        depthPaint.shader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(
                stops[0].toArgb(), stops[1].toArgb(), stops[2].toArgb(),
                stops[3].toArgb(), stops[4].toArgb(), stops[5].toArgb(),
            ),
            floatArrayOf(0f, 0.20f, 0.44f, 0.66f, 0.85f, 1f),
            Shader.TileMode.CLAMP,
        )

        glowPaint.shader = radial(palette.glow, 0.80f)
        causticAPaint.shader = radial(palette.currentA, 0.85f)
        causticBPaint.shader = radial(palette.currentB, 0.90f)
        causticCPaint.shader = radial(
            palette.glow.copy(alpha = palette.glow.alpha * 0.26f), 0.85f,
        )

        shimmerPaint.shader = LinearGradient(
            0f, height * 0.58f, 0f, height * 0.88f,
            intArrayOf(
                Color.Transparent.toArgb(),
                palette.glow.toArgb(),
                Color.Transparent.toArgb(),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )

        vignettePaint.shader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(
                stops.first().copy(alpha = 0.44f).toArgb(),
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb(),
                Color.Black.copy(alpha = 0.28f).toArgb(),
            ),
            floatArrayOf(0f, 0.26f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun flatPaint() = Paint().apply {
        isAntiAlias = false
        isDither = true
    }
}

/**
 * Radial falloff anchored at the origin so it can be positioned by translation.
 *
 * [softness] controls how fast alpha falls off: higher values carry the colour
 * further before it fades to transparent.
 */
private fun radial(color: Color, softness: Float): Shader = RadialGradient(
    0f, 0f, 1f,
    intArrayOf(
        color.toArgb(),
        color.copy(alpha = color.alpha * (1f - softness * 0.5f)).toArgb(),
        color.copy(alpha = color.alpha * (1f - softness * 0.8f)).toArgb(),
        Color.Transparent.toArgb(),
    ),
    floatArrayOf(0f, 0.38f, 0.72f, 1f),
    Shader.TileMode.CLAMP,
)