package app.tide.launcher.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptics, funnelled through one place so the vocabulary stays consistent and
 * so every device is guarded once instead of at each call site.
 *
 * Compose's `HapticFeedback` only covers the [androidx.compose.ui.hapticfeedback
 * .HapticFeedbackType] constants, which on older releases collapse to a single
 * undetailed tick. The long-press "clunk" wants a real [VibrationEffect]
 * waveform, so a `Vibrator` path is kept alongside.
 *
 * Every entry point degrades to a no-op on a device with no vibrator.
 */
object Haptics {

    private val vibratorCache = HashMap<Context, Vibrator?>()

    private fun Context.vibrator(): Vibrator? = synchronized(vibratorCache) {
        vibratorCache.getOrPut(this) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                        ?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            }.getOrNull()?.takeIf { it.hasVibrator() }
        }
    }

    /** Light tick — focus moved, an option became available. */
    fun tick(context: Context, haptics: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
    }

    /** The distinct double-tap that opens a long-press menu. */
    fun longPress(
        context: Context,
        haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    ) {
        haptics.performHapticFeedback(
            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
        )
        val v = context.vibrator() ?: return
        // Two short taps with a gap: reads as "clunk" rather than "buzz", and
        // lands under the ~30ms that starts to feel like a phone call.
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 18, 40, 26), -1)) }
    }

    /** Confirmation — an app launched, a setting toggled. */
    fun confirm(
        context: Context,
        haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    ) {
        haptics.performHapticFeedback(
            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
        )
        val v = context.vibrator() ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                // createPredefined landed in API 29; below that, a short one-shot
                // is the closest equivalent the platform offers.
                v.vibrate(
                    VibrationEffect.createOneShot(
                        24L,
                        VibrationEffect.DEFAULT_AMPLITUDE,
                    ),
                )
            }
        }
    }

    /** Rejection — a launch failed, a limit was hit. */
    fun reject(haptics: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        haptics.performHapticFeedback(
            androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
        )
    }
}