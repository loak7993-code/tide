package app.tide.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Owns the installed-app list.
 *
 * `PackageManager.queryIntentActivities` is a binder round trip that costs tens
 * of milliseconds on a cold device with a few hundred packages, so it runs on
 * [Dispatchers.IO] and never on the main thread. The result is published as a
 * [StateFlow]; the UI collects it once and recomposes the grid.
 *
 * Lifecycle-triggered refreshes are the caller's job — the ViewModel re-queries
 * on resume, which keeps this class free of Looper callbacks that could outlive
 * the screen.
 */
class AppRepository(private val context: Context) {

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    suspend fun refresh(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved: List<ResolveInfo> = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())

        val entries = resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                .ifEmpty { activity.loadLabel(pm)?.toString().orEmpty() }
            AppEntry.of(
                component = ComponentName(activity.packageName, activity.name),
                label = label.ifEmpty { activity.packageName },
            )
        }

        val sorted = entries
            .distinctBy { it.key }
            .filterNot { it.isSelf }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        _apps.value = sorted
        sorted
    }

    fun find(component: ComponentName): AppEntry? =
        _apps.value.firstOrNull { it.component == component }
}