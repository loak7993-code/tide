package app.tide.launcher.debug

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tide.launcher.ui.theme.Radius

/** One second of frame timings, summarised. */
private data class FrameStats(
    val fps: Int,
    val p95FrameMs: Float,
    val jankPercent: Int,
)

/**
 * A live frame-timing overlay.
 *
 * This is the tool that actually tells you whether the ocean animation is
 * holding frame rate, which is otherwise invisible from the outside. Frame times
 * are sampled with `withFrameNanos` inside the composition, so the numbers
 * reflect the real vsync cadence rather than a wall-clock timer that would
 * include time the UI thread was asleep.
 */
@Composable
fun DebugOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var stats by remember { mutableStateOf(FrameStats(0, 0f, 0)) }
    var heapMb by remember { mutableFloatStateOf(0f) }
    var appsLoaded by remember { mutableIntStateOf(0) }

    // Sample every frame, keep a rolling window of the last second.
    LaunchedEffect(Unit) {
        val window = FloatArray(120)
        var index = 0
        var filled = 0
        var lastFrame = 0L

        while (true) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val deltaMs = (now - lastFrame) / 1_000_000f
                    // Ignore the multi-hundred-ms gaps that follow a cold start
                    // or a background trip; they are not jank the user can see.
                    if (deltaMs < 500f) {
                        window[index] = deltaMs
                        index = (index + 1) % window.size
                        if (filled < window.size) filled++
                    }
                }
                lastFrame = now

                if (filled > 0 && index % 15 == 0) {
                    val sorted = window.take(filled).sorted()
                    val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
                    // A frame slower than ~1.75 vsyncs at 60Hz is what the user
                    // perceives as a dropped frame.
                    val janky = sorted.count { it > 17.5f }
                    stats = FrameStats(
                        fps = if (p95 > 0f) (1000f / p95).toInt() else 0,
                        p95FrameMs = p95,
                        jankPercent = (janky * 100 / sorted.size),
                    )
                }
            }
        }
    }

    // Memory and app count are polled on a timer instead of every frame.
    LaunchedEffect(Unit) {
        while (true) {
            heapMb = usedHeapMb(context)
            appsLoaded = TideLog.snapshot().size
            kotlinx.coroutines.delay(1_000)
        }
    }

    Box(
        modifier = modifier
            .padding(top = 44.dp, end = 12.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column {
            OverlayLine("frame", "${stats.fps} fps")
            OverlayLine("p95", "${"%.1f".format(stats.p95FrameMs)} ms")
            OverlayLine("jank", "${stats.jankPercent}%")
            OverlayLine("heap", "${heapMb.toInt()} MB")
            OverlayLine("logs", "$appsLoaded")
        }
    }
}

@Composable
private fun OverlayLine(label: String, value: String) {
    Row {
        Spacer(Modifier.width(0.dp))
        Text8(label, Color(0xFF8ED8E8))
        Spacer(Modifier.width(8.dp))
        Text8(value, Color.White)
    }
}

@Composable
private fun Text8(text: String, color: Color) {
    androidx.compose.material3.Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontFamily = FontFamily.Monospace,
    )
}

private fun usedHeapMb(context: Context): Float {
    val runtime = Runtime.getRuntime()
    val used = runtime.totalMemory() - runtime.freeMemory()
    return used / 1_048_576f
}