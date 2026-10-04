package app.tide.launcher.core

import android.app.AppOpsManager
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.tide.launcher.data.AppEntry
import java.io.File
import java.util.Locale

/** One permission an app asks for, and whether it currently holds it. */
data class PermissionRow(
    val name: String,
    val group: String,
    val granted: Boolean,
    val critical: Boolean,
)

/**
 * Everything the per-app settings screen shows about one package.
 *
 * Deliberately only what a launcher can actually read. Storage in particular:
 * `StorageStatsManager` and `PackageManager.getPackageSizeInfo` both require
 * `PACKAGE_USAGE_STATS`, an AppOps-gated special permission a launcher cannot
 * be granted, so this reports the APK size it can stat and the data directory
 * as a path rather than inventing a total that would be wrong. Battery is
 * absent for the same reason — there is no per-app battery API.
 */
data class AppDetails(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val isSystem: Boolean,
    val firstInstall: Long,
    val lastUpdate: Long,
    val apkSizeBytes: Long,
    val dataDir: String,
    val notificationsEnabled: Boolean,
    val appLocale: String?,
    val browsableActivities: Int,
    val permissions: List<PermissionRow>,
)

/**
 * Reads the per-app details, and owns the actions the screen offers.
 *
 * Every call walks the package manager, so callers must be off the main
 * thread.
 */
object AppDetailsRepository {

    fun load(context: Context, entry: AppEntry): AppDetails? {
        val pm = context.packageManager
        val pkg = entry.packageName
        val info: PackageInfo = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }

        val appInfo = info.applicationInfo ?: return null
        val label = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            entry.label
        }

        return AppDetails(
            label = label,
            packageName = pkg,
            versionName = info.versionName ?: "?",
            versionCode = longVersionCode(info),
            targetSdk = appInfo.targetSdkVersion,
            isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            firstInstall = info.firstInstallTime,
            lastUpdate = info.lastUpdateTime,
            apkSizeBytes = apkSize(appInfo),
            dataDir = appInfo.dataDir ?: "",
            notificationsEnabled = notificationsEnabled(context, appInfo),
            appLocale = appLocale(context, pkg),
            browsableActivities = countBrowsableActivities(pm, pkg),
            permissions = permissions(context, pkg, info),
        )
    }

    @Suppress("DEPRECATION")
    private fun longVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }

    private fun apkSize(appInfo: ApplicationInfo): Long = try {
        File(appInfo.sourceDir ?: return 0L).length()
    } catch (e: Exception) {
        0L
    }

    /**
     * Whether the user has notifications on for this package.
     *
     * `NotificationManager.areNotificationsEnabled()` is a global setting, so
     * the per-app answer comes from the app-op, which is the same check the
     * system Settings screen makes.
     */
    private fun notificationsEnabled(context: Context, appInfo: ApplicationInfo): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return true
        val mode = runCatching {
            ops.unsafeCheckOpNoThrow(
                // Not exposed as a constant in the public android.jar, though
                // the op name is stable and is what the Settings app checks.
                "OP_POST_NOTIFICATION",
                appInfo.uid,
                appInfo.packageName,
            )
        }.getOrDefault(AppOpsManager.MODE_ALLOWED)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** The per-app language the user has chosen, or null if system default. */
    private fun appLocale(context: Context, pkg: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val lm = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager ?: return null
        val locales = runCatching { lm.getApplicationLocales(pkg) }.getOrNull() ?: return null
        return locales[0]?.toLanguageTag()
    }

    /** How many activities in this package claim it can open web links. */
    private fun countBrowsableActivities(pm: PackageManager, pkg: String): Int = try {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid")).setPackage(pkg),
            PackageManager.MATCH_DEFAULT_ONLY,
        ).size
    } catch (e: Exception) {
        0
    }

    private fun permissions(
        context: Context,
        pkg: String,
        info: PackageInfo,
    ): List<PermissionRow> {
        val requested = info.requestedPermissions ?: return emptyList()
        return requested.map { name ->
            val granted = context.packageManager.checkPermission(name, pkg) ==
                PackageManager.PERMISSION_GRANTED
            PermissionRow(
                name = name.substringAfterLast('.').replace('_', ' ').lowercase(Locale.ROOT),
                group = permissionGroup(name),
                granted = granted,
                critical = criticalPermission(name),
            )
        }.sortedWith(
            compareByDescending<PermissionRow> { it.granted }.thenBy { it.group },
        )
    }

    /** A human label for the permission's family, e.g. "Camera". */
    private fun permissionGroup(name: String): String {
        val simple = name.substringAfterLast('.').replace('_', ' ')
        return when {
            simple.contains("camera", true) -> "Camera"
            simple.contains("location", true) -> "Location"
            simple.contains("microphone", true) || simple.contains("record_audio", true) ->
                "Microphone"
            simple.contains("storage", true) || simple.contains("media", true) -> "Storage"
            simple.contains("contacts", true) -> "Contacts"
            simple.contains("phone", true) || simple.contains("call", true) -> "Phone"
            simple.contains("sms", true) || simple.contains("message", true) -> "Messages"
            simple.contains("notification", true) -> "Notifications"
            simple.contains("calendar", true) -> "Calendar"
            simple.contains("internet", true) || simple.contains("network", true) -> "Network"
            else -> simple.lowercase(Locale.ROOT)
                .replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    /** The permissions that can change what the app can see at any moment. */
    private fun criticalPermission(name: String): Boolean =
        name.contains("CAMERA") ||
            name.contains("RECORD_AUDIO") ||
            name.contains("ACCESS_FINE_LOCATION") ||
            name.contains("ACCESS_COARSE_LOCATION") ||
            name.contains("READ_CONTACTS") ||
            name.contains("READ_SMS") ||
            name.contains("POST_NOTIFICATIONS")

    // ── actions ─────────────────────────────────────────────────────────────

    fun openAppInfo(context: Context, pkg: String) =
        LauncherActions.startSettings(context, appInfoIntent(pkg))

    fun openNotificationSettings(context: Context, pkg: String) =
        LauncherActions.startSettings(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            } else {
                appInfoIntent(pkg)
            },
        )

    fun openPermissions(context: Context, pkg: String) =
        LauncherActions.startSettings(context, appInfoIntent(pkg))

    fun openDefaultAppSettings(context: Context) =
        LauncherActions.startSettings(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))

    fun openLanguageSettings(context: Context, pkg: String) =
        LauncherActions.startSettings(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            } else {
                appInfoIntent(pkg)
            },
        )

    fun requestUninstall(context: Context, pkg: String) =
        LauncherActions.startSettings(
            context,
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)),
        )

    private fun appInfoIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", pkg, null))
}