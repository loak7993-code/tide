package app.tide.launcher.core

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import app.tide.launcher.debug.TideLog
import app.tide.launcher.data.AppEntry

/**
 * The side-effecting verbs a launcher performs: launching, app info, uninstall.
 *
 * All of these can legitimately fail on a locked-down or headless device, and a
 * launcher's job is not to die when they do — each one degrades to a log line.
 */
object LauncherActions {

    fun launch(context: Context, entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = entry.component
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // The package was uninstalled between the grid query and the tap.
            TideLog.warn("launch", "no activity for ${entry.component}")
        } catch (e: SecurityException) {
            TideLog.warn("launch", "denied for ${entry.component}", e)
        }
    }

    fun openAppInfo(context: Context, entry: AppEntry) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", entry.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivitySafely(intent, "appInfo")
    }

    fun requestUninstall(context: Context, entry: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", entry.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivitySafely(intent, "uninstall")
    }

    /**
     * Some OEM builds refuse `ACTION_DELETE` for system apps and crash the
     * chooser; falling back to app info keeps the user on a usable screen.
     */
    fun requestUninstallOrAppInfo(context: Context, entry: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", entry.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = context.packageManager
            .queryIntentActivities(intent, 0)
            .isNotEmpty()
        if (resolved) context.startActivitySafely(intent, "uninstall")
        else openAppInfo(context, entry)
    }

    fun launchWeb(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivitySafely(intent, "web")
    }

    fun launchDialer(context: Context) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivitySafely(intent, "dialer")
    }

    fun launchClock(context: Context) {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivitySafely(intent, "clock")
    }

    fun launchCamera(context: Context) {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivitySafely(intent, "camera")
    }

    fun launchSettings(context: Context) {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivitySafely(intent, "settings")
    }

    private fun Context.startActivitySafely(intent: Intent, tag: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            TideLog.warn(tag, "unresolvable: ${intent.action}")
        } catch (e: SecurityException) {
            TideLog.warn(tag, "action denied", e)
        }
    }
}

/** Convenience for launching by raw component, used by the external intent path. */
fun Context.launchComponent(component: ComponentName) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        this.component = component
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
    startActivity(intent)
}