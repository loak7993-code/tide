package app.tide.launcher.core

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.graphics.drawable.Drawable

/**
 * Reads an app's declared and dynamic launch shortcuts — the list a long press
 * surfaces under "Shortcuts". Separate from [LauncherActions] because this
 * queries the system rather than acting on it.
 */
object AppShortcuts {

    data class Shortcut(
        val id: String,
        val label: String,
        val intent: Intent,
        val icon: Drawable?,
    )

    fun shortcutsFor(context: Context, packageName: String): List<Shortcut> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE)
            as? LauncherApps ?: return emptyList()

        val query = LauncherApps.ShortcutQuery().apply { setPackage(packageName) }

        return runCatching { launcherApps.getShortcuts(query, Process.myUserHandle()) }
            .getOrNull()
            .orEmpty()
            // A disabled shortcut cannot be fired, so there is no point offering
            // it in a menu.
            .filter { it.isEnabled }
            .map { info ->
                Shortcut(
                    id = "${info.`package`}/${info.id}",
                    label = info.shortLabel?.toString().orEmpty()
                        .ifEmpty { info.longLabel?.toString().orEmpty() }
                        .ifEmpty { info.id },
                    // The platform hands back a ready-to-fire intent with the
                    // shortcut's own extras already attached. Rebuilding it from
                    // components would drop that payload, whose keys vary per OEM.
                    intent = Intent(info.intent ?: Intent()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    // Plain ShortcutInfo exposes no icon; only the launcher-
                    // specific LauncherShortcutInfo does, and fetching one per
                    // shortcut to decorate a menu row is not worth the binder
                    // round trip.
                    icon = null,
                )
            }
    }

    fun launch(context: Context, shortcut: Shortcut) {
        runCatching { context.startActivity(shortcut.intent) }
    }
}