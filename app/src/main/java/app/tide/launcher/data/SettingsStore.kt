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
    val dockedApps: Set<String> = emptySet(),
    val hiddenApps: Set<String> = emptySet(),
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
 * Preferences DataStore rather than Room: the whole configuration is a handful
 * of scalars and two string sets, so a typed object on disk would cost more in
 * migration code than it saves. App lists are re-queried from the
 * `PackageManager` on every launch anyway — only the *user's* choices here
 * need to survive.
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
        val dockedApps = stringSetPreferencesKey("docked_apps")
        val hiddenApps = stringSetPreferencesKey("hidden_apps")
    }

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
                dockedApps = prefs[Keys.dockedApps] ?: emptySet(),
                hiddenApps = prefs[Keys.hiddenApps] ?: emptySet(),
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

    suspend fun toggleDocked(appKey: String) = edit { prefs ->
        val current = prefs[Keys.dockedApps] ?: emptySet()
        prefs[Keys.dockedApps] = if (appKey in current) current - appKey else current + appKey
    }

    suspend fun setDocked(appKeys: Set<String>) = edit { prefs ->
        prefs[Keys.dockedApps] = appKeys
    }

    suspend fun toggleHidden(appKey: String) = edit { prefs ->
        val current = prefs[Keys.hiddenApps] ?: emptySet()
        prefs[Keys.hiddenApps] = if (appKey in current) current - appKey else current + appKey
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) = edit { prefs ->
        prefs[key] = value
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}