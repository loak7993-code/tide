package app.tide.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Rasterises launcher icons once, into immutable bitmaps.
 *
 * Two decisions worth knowing about:
 *
 *  1. **Bitmaps, not [Drawable]s.** A `Drawable` carries mutable bounds and
 *     alpha, so sharing one instance across several composables that lay it out
 *     at different sizes corrupts it. An [ImageBitmap] is a pixel buffer with
 *     no per-draw state, so it is safe to share and cheap to blit.
 *
 *  2. **Adaptive icons are composited unmasked.** `AdaptiveIconDrawable.draw()`
 *     applies its own OEM mask. Flattening background and foreground layers
 *     full-bleed instead lets the UI apply its own shape (squircle / circle /
 *     rounded) at draw time, which is what makes the icon-shape setting
 *     instant instead of a full re-rasterise.
 */
object IconCache {

    /** Icons are drawn at most ~96dp; 2× for density keeps them crisp without
     *  wasting memory across a few hundred apps. */
    private const val RASTER_PX = 192
    private const val CACHE_ENTRIES = 320

    private val cache = LruCache<String, ImageBitmap>(CACHE_ENTRIES)

    /** Guards against two grid cells asking for the same icon concurrently. */
    private val locks = mutableMapOf<String, Mutex>()
    private val locksGuard = Mutex()

    fun peek(component: ComponentName): ImageBitmap? = cache.get(componentKey(component))

    suspend fun load(
        context: Context,
        component: ComponentName,
    ): ImageBitmap? {
        val key = componentKey(component)
        cache.get(key)?.let { return it }

        val lock = locksGuard.withLock { locks.getOrPut(key) { Mutex() } }
        return lock.withLock {
            // Another coroutine may have finished while we waited on the lock.
            cache.get(key)?.let { return@withLock it }

            val bitmap = withContext(Dispatchers.IO) {
                runCatching { rasterise(context, component) }.getOrNull()
            }
            if (bitmap != null) cache.put(key, bitmap)
            bitmap
        }
    }

    /** Called when the launcher loses focus to reclaim raster memory. */
    fun trim() {
        cache.trimToSize(CACHE_ENTRIES / 2)
    }

    private fun componentKey(component: ComponentName) =
        "${component.packageName}/${component.className}"

    private fun rasterise(context: Context, component: ComponentName): ImageBitmap? {
        val pm = context.packageManager
        val drawable: Drawable = try {
            @Suppress("DEPRECATION")
            pm.getActivityIcon(component)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        } ?: return null

        return when (drawable) {
            is AdaptiveIconDrawable -> flattenAdaptive(drawable)
            else -> rasteriseDrawable(drawable)
        }
    }

    /**
     * Flattens background + foreground at full bleed, leaving the mask to the UI.
     * The background layer is scaled to *cover* rather than fit, otherwise the
     * parallax the adaptive-icon spec mandates shows as transparent corners.
     */
    private fun flattenAdaptive(drawable: AdaptiveIconDrawable): ImageBitmap {
        val background = drawable.background
        val foreground = drawable.foreground
        val bitmap = Bitmap.createBitmap(RASTER_PX, RASTER_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        background?.let { drawLayerCover(it, canvas, RASTER_PX) }
        foreground?.let { drawLayerCover(it, canvas, RASTER_PX) }

        return bitmap.asImageBitmap()
    }

    /** Adaptive-icon layers are 108dp with 18dp of padding; centre-crop to 72. */
    private fun drawLayerCover(layer: Drawable, canvas: Canvas, size: Int) {
        val inset = size * 18f / 108f
        val scaled = size + inset * 2f
        val left = -(scaled - size) / 2f
        layer.setBounds(
            left.toInt(),
            left.toInt(),
            (left + scaled).toInt(),
            (left + scaled).toInt(),
        )
        layer.draw(canvas)
    }

    private fun rasteriseDrawable(drawable: Drawable): ImageBitmap {
        (drawable as? BitmapDrawable)?.bitmap?.let { existing ->
            if (existing.width > 0) return existing.asImageBitmap()
        }

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: RASTER_PX
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: RASTER_PX
        val size = maxOf(width, height).coerceAtMost(RASTER_PX)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}