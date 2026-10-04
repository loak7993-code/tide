package app.tide.launcher.data

import android.content.ComponentName
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable

/**
 * One launchable entry on the device.
 *
 * [icon] is deliberately absent: loading it is the single most expensive thing
 * a launcher does, so it is resolved lazily through [IconCache] at draw time
 * rather than held on the model.
 */
@Immutable
data class AppEntry(
    val packageName: String,
    val className: String,
    val label: String,
    val component: ComponentName,
) {
    /** Stable identity for Compose keys and diffing. */
    val key: String = "$packageName/$className"

    /** `true` when this entry is Tide itself, so it can be filtered out of the grid. */
    val isSelf: Boolean = packageName == "app.tide.launcher" ||
        packageName == "app.tide.launcher.debug"

    companion object {
        fun of(component: ComponentName, label: String) = AppEntry(
            packageName = component.packageName,
            className = component.className,
            label = label,
            component = component,
        )
    }
}

/** Icon resolution results, kept out of [AppEntry] so the model stays cheap. */
@Immutable
sealed interface IconState {
    data object Loading : IconState
    data class Ready(val drawable: Drawable) : IconState
    data object Missing : IconState
}