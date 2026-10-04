package app.tide.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tide.launcher.ui.theme.TideTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/** Shape applied to every launcher icon. Set independently of the theme. */
enum class IconShape(val displayName: String) {
    Squircle("Squircle"),
    Circle("Circle"),
    Rounded("Rounded"),
    ;

    companion object {
        fun fromName(name: String?): IconShape =
            entries.firstOrNull { it.name == name } ?: Squircle
    }
}

/**
 * A named group of apps.
 *
 * [appKeys] is ordered so the folder opens in a predictable sequence, and
 * [id] is stable across renames so the grid slot does not jump when the user
 * edits the label.
 */
@Serializable
data class Folder(
    val id: String,
    val name: String,
    val appKeys: List<String> = emptyList(),
)

/** Everything the user can change, as one immutable snapshot. */
data class TideSettings(
    val theme: TideTheme = TideTheme.Default,
    val iconShape: IconShape = IconShape.Squircle,
    val gridColumns: Int = 4,
    val showLabels: Boolean = true,
    val oceanMotion: Boolean = true,
    val blurPanels: Boolean = true,
    val doubleTapToSearch: Boolean = true,
    val dockCapacity: Int = 5,
    val debugOverlay: Boolean = false,
    /** Pinned apps, **in dock order**. This was a Set once, which silently
     *  discarded the ordering the user could see; drag-to-reorder needs it. */
    val dockedApps: List<String> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val folders: List<Folder> = emptyList(),
    val widgetIds: List<Int> = emptyList(),
) {
    companion object {
        /** Column count is clamped so labels never wrap into each other on a
         *  narrow phone. */
        val ColumnRange = 3..6

        fun clampColumns(value: Int): Int = value.coerceIn(ColumnRange)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tide_settings")

/**
 * Persistence for [TideSettings].
 *
 * Preferences DataStore rather than Room: the configuration is a handful of
 * scalars plus three small collections, so a typed object on disk would cost
 * more in migration code than it saves. The app list itself is re-queried from
 * the `PackageManager` on every launch — only the *user's* choices need to
 * survive.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val iconShape = stringPreferencesKey("icon_shape")
        val gridColumns = intPreferencesKey("grid_columns")
        val showLabels = booleanPreferencesKey("show_labels")
        val oceanMotion = booleanPreferencesKey("ocean_motion")
        val blurPanels = booleanPreferencesKey("blur_panels")
        val doubleTapToSearch = booleanPreferencesKey("double_tap_search")
        val dockCapacity = intPreferencesKey("dock_capacity")
        val debugOverlay = booleanPreferencesKey("debug_overlay")
        val hiddenApps = stringSetPreferencesKey("hidden_apps")

        // Deliberately a new key: the old one stored a string *set* of dock
        // entries, and reusing the name for an ordered string would make the
        // Preferences decoder throw on every read of an existing profile.
        const val DOCK_SEP = "\u001F"
        val dockedApps = stringPreferencesKey("docked_apps_ordered")

        const val LIST_SEP = "\u001E"
        val folders = stringPreferencesKey("folders_json")
        val widgetIds = stringPreferencesKey("widget_ids")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<TideSettings> = context.dataStore.data
        // A corrupt or unreadable preferences file must not take the launcher
        // down with it — a home screen that crashes on boot bricks the device.
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs ->
            TideSettings(
                theme = TideTheme.fromName(prefs[Keys.theme]),
                iconShape = IconShape.fromName(prefs[Keys.iconShape]),
                gridColumns = TideSettings.clampColumns(prefs[Keys.gridColumns] ?: 4),
                showLabels = prefs[Keys.showLabels] ?: true,
                oceanMotion = prefs[Keys.oceanMotion] ?: true,
                blurPanels = prefs[Keys.blurPanels] ?: true,
                doubleTapToSearch = prefs[Keys.doubleTapToSearch] ?: true,
                dockCapacity = (prefs[Keys.dockCapacity] ?: 5).coerceIn(3, 7),
                debugOverlay = prefs[Keys.debugOverlay] ?: false,
                dockedApps = prefs[Keys.dockedApps].decodeList(),
                hiddenApps = prefs[Keys.hiddenApps] ?: emptySet(),
                folders = runCatching {
                    prefs[Keys.folders]?.takeIf { it.isNotBlank() }
                        ?.let { json.decodeFromString<List<Folder>>(it) }
                }.getOrNull().orEmpty(),
                widgetIds = prefs[Keys.widgetIds].decodeIntList(),
            )
        }

    suspend fun setTheme(theme: TideTheme) = put(Keys.theme, theme.name)

    suspend fun setIconShape(shape: IconShape) = put(Keys.iconShape, shape.name)

    suspend fun setGridColumns(columns: Int) =
        put(Keys.gridColumns, TideSettings.clampColumns(columns))

    suspend fun setShowLabels(show: Boolean) = put(Keys.showLabels, show)

    suspend fun setOceanMotion(enabled: Boolean) = put(Keys.oceanMotion, enabled)

    suspend fun setBlurPanels(enabled: Boolean) = put(Keys.blurPanels, enabled)

    suspend fun setDoubleTapToSearch(enabled: Boolean) = put(Keys.doubleTapToSearch, enabled)

    suspend fun setDockCapacity(capacity: Int) = put(Keys.dockCapacity, capacity.coerceIn(3, 7))

    suspend fun setDebugOverlay(enabled: Boolean) = put(Keys.debugOverlay, enabled)

    /** Sets the whole dock, in order. */
    suspend fun setDocked(appKeys: List<String>) = put(Keys.dockedApps, appKeys.joined())

    /**
     * Moves the entry at [from] to [to], shifting the rest along. Used by
     * dock drag-to-reorder; doing it as one list operation means the UI never
     * sees an intermediate state with a duplicate or a dropped entry.
     */
    suspend fun moveDocked(from: Int, to: Int) = edit { prefs ->
        val current = prefs[Keys.dockedApps].decodeList()
        if (from !in current.indices || to !in current.indices || from == to) return@edit
        val moved = current.toMutableList()
        moved.add(to, moved.removeAt(from))
        prefs[Keys.dockedApps] = moved.joined()
    }

    suspend fun toggleDocked(appKey: String) = edit { prefs ->
        val current = prefs[Keys.dockedApps].decodeList()
        prefs[Keys.dockedApps] = when {
            appKey in current -> current - appKey
            else -> current + appKey
        }.joined()
    }

    suspend fun toggleHidden(appKey: String) = edit { prefs ->
        val current = prefs[Keys.hiddenApps] ?: emptySet()
        prefs[Keys.hiddenApps] = if (appKey in current) current - appKey else current + appKey
    }

    suspend fun setFolders(folders: List<Folder>) = put(
        Keys.folders,
        runCatching { json.encodeToString<List<Folder>>(folders) }.getOrDefault("[]"),
    )

    suspend fun setWidgetIds(ids: List<Int>) = put(Keys.widgetIds, ids.joinToString(Keys.LIST_SEP))

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) = edit { prefs ->
        prefs[key] = value
    }

    private suspend fun edit(
        block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ) {
        context.dataStore.edit(block)
    }

    /**
     * Component keys are `package/class`, so they never contain a comma. A
     * unit separator is used anyway rather than relying on that.
     */
    private fun List<String>.joined(): String = joinToString(Keys.DOCK_SEP)

    private fun String?.decodeList(): List<String> =
        this?.split(Keys.DOCK_SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private fun String?.decodeIntList(): List<Int> =
        this?.split(Keys.LIST_SEP)?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
}