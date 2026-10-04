package app.tide.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import app.tide.launcher.debug.TideLog
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
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

/** 12- or 24-hour clock, or follow the device. */
enum class ClockFormat(val displayName: String) {
    Auto("Follow device"),
    H12("12-hour"),
    H24("24-hour"),
    ;

    companion object {
        fun fromName(name: String?): ClockFormat =
            entries.firstOrNull { it.name == name } ?: Auto
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

    // ── customisation ───────────────────────────────────────────────────────
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val clockFormat: ClockFormat = ClockFormat.Auto,
    /** Multiplier on the grid icon size, 0.8 to 1.2. */
    val iconScale: Float = 1f,
    /** Multiplier on the app label size, 0.85 to 1.2. */
    val labelScale: Float = 1f,
    /** 0 stops the ocean animation entirely; 1 is full drift. */
    val motionIntensity: Float = 1f,
    /** Opacity of the frosted panels, 0.35 to 1. */
    val panelOpacity: Float = 1f,

    // ── layout ──────────────────────────────────────────────────────────────
    /** Global multiplier on every text style, 0.85 to 1.3. */
    val fontScale: Float = 1f,
    /** Gap between grid cells as a fraction of the cell width, 0 to 1. */
    val gridSpacing: Float = 0.35f,
    /** Multiplier on dock icon size only, so the hotseat can differ from the grid. */
    val dockIconScale: Float = 1f,
    val showSearchBar: Boolean = true,
    val showTideInDrawer: Boolean = false,
    val sortOrder: SortOrder = SortOrder.Name,

    /** False until the first-run flow has been completed. */
    val onboarded: Boolean = false,
) {
    companion object {
        /** Column count is clamped so labels never wrap into each other on a
         *  narrow phone. */
        val ColumnRange = 3..6

        fun clampColumns(value: Int): Int = value.coerceIn(ColumnRange)

        fun clampScale(value: Float): Float = value.coerceIn(0.8f, 1.2f)

        fun clampLabelScale(value: Float): Float = value.coerceIn(0.85f, 1.2f)

        fun clampIntensity(value: Float): Float = value.coerceIn(0f, 1f)

        fun clampPanelOpacity(value: Float): Float = value.coerceIn(0.35f, 1f)

        fun clampFontScale(value: Float): Float = value.coerceIn(0.85f, 1.3f)

        fun clampGridSpacing(value: Float): Float = value.coerceIn(0f, 1f)

        fun clampDockScale(value: Float): Float = value.coerceIn(0.7f, 1.3f)
    }
}

/** How the drawer lists apps. */
enum class SortOrder(val displayName: String) {
    Name("Name"),
    Label("Label"),
    Reverse("Reverse"),
    ;

    companion object {
        fun fromName(value: String?): SortOrder =
            entries.firstOrNull { it.name == value } ?: Name
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

        val showClock = booleanPreferencesKey("show_clock")
        val showDate = booleanPreferencesKey("show_date")
        val clockFormat = stringPreferencesKey("clock_format")
        val iconScale = floatPreferencesKey("icon_scale")
        val labelScale = floatPreferencesKey("label_scale")
        val motionIntensity = floatPreferencesKey("motion_intensity")
        val panelOpacity = floatPreferencesKey("panel_opacity")
        val fontScale = floatPreferencesKey("font_scale")
        val gridSpacing = floatPreferencesKey("grid_spacing")
        val dockIconScale = floatPreferencesKey("dock_icon_scale")
        val showSearchBar = booleanPreferencesKey("show_search_bar")
        val showTideInDrawer = booleanPreferencesKey("show_tide_in_drawer")
        val sortOrder = stringPreferencesKey("sort_order")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<TideSettings> = context.dataStore.data
        // A corrupt or unreadable preferences file must not take the launcher
        // down with it — a home screen that crashes on boot bricks the device.
        .catch { cause ->
            // A corrupt or unreadable preferences file must not take the
            // launcher down — a home screen that crashes on boot bricks the
            // device, leaving no way back into the app to fix it.
            //
            // This catches everything, not just IOException. DataStore signals a
            // damaged protobuf file with `CorruptionException`, which is not an
            // IOException, so the narrower check that used to be here rethrew it
            // and killed the whole settings flow.
            TideLog.warn("store", "settings unreadable, starting from defaults", cause)
            emit(emptyPreferences())
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
                showClock = prefs[Keys.showClock] ?: true,
                showDate = prefs[Keys.showDate] ?: true,
                clockFormat = ClockFormat.fromName(prefs[Keys.clockFormat]),
                iconScale = TideSettings.clampScale(prefs[Keys.iconScale] ?: 1f),
                labelScale = TideSettings.clampLabelScale(prefs[Keys.labelScale] ?: 1f),
                motionIntensity = TideSettings.clampIntensity(prefs[Keys.motionIntensity] ?: 1f),
                panelOpacity = TideSettings.clampPanelOpacity(prefs[Keys.panelOpacity] ?: 1f),
                fontScale = TideSettings.clampFontScale(prefs[Keys.fontScale] ?: 1f),
                gridSpacing = TideSettings.clampGridSpacing(prefs[Keys.gridSpacing] ?: 0.35f),
                dockIconScale = TideSettings.clampDockScale(prefs[Keys.dockIconScale] ?: 1f),
                showSearchBar = prefs[Keys.showSearchBar] ?: true,
                showTideInDrawer = prefs[Keys.showTideInDrawer] ?: false,
                sortOrder = SortOrder.fromName(prefs[Keys.sortOrder]),
                onboarded = prefs[Keys.onboarded] ?: false,
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

    suspend fun setShowClock(show: Boolean) = put(Keys.showClock, show)

    suspend fun setShowDate(show: Boolean) = put(Keys.showDate, show)

    suspend fun setClockFormat(format: ClockFormat) = put(Keys.clockFormat, format.name)

    suspend fun setIconScale(scale: Float) =
        put(Keys.iconScale, TideSettings.clampScale(scale))

    suspend fun setLabelScale(scale: Float) =
        put(Keys.labelScale, TideSettings.clampLabelScale(scale))

    suspend fun setMotionIntensity(value: Float) =
        put(Keys.motionIntensity, TideSettings.clampIntensity(value))

    suspend fun setPanelOpacity(value: Float) =
        put(Keys.panelOpacity, TideSettings.clampPanelOpacity(value))

    suspend fun setOnboarded(done: Boolean) = put(Keys.onboarded, done)

    suspend fun setFontScale(value: Float) =
        put(Keys.fontScale, TideSettings.clampFontScale(value))

    suspend fun setGridSpacing(value: Float) =
        put(Keys.gridSpacing, TideSettings.clampGridSpacing(value))

    suspend fun setDockIconScale(value: Float) =
        put(Keys.dockIconScale, TideSettings.clampDockScale(value))

    suspend fun setShowSearchBar(value: Boolean) = put(Keys.showSearchBar, value)

    suspend fun setShowTideInDrawer(value: Boolean) = put(Keys.showTideInDrawer, value)

    suspend fun setSortOrder(order: SortOrder) = put(Keys.sortOrder, order.name)

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