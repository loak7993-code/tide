package app.tide.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tide.launcher.data.AppEntry
import app.tide.launcher.data.AppRepository
import app.tide.launcher.data.Fuzzy
import app.tide.launcher.data.IconShape
import app.tide.launcher.data.SettingsStore
import app.tide.launcher.data.TideSettings
import app.tide.launcher.debug.logi
import app.tide.launcher.ui.theme.TideTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which top-level surface is showing. */
enum class Surface { Home, Drawer, Settings }

/** What an undoable snackbar would revert. */
enum class UndoAction { Unhide, Undock }

data class Notice(
    val text: String,
    val undoAction: UndoAction? = null,
    val targetKey: String? = null,
)

data class LauncherUiState(
    val surface: Surface = Surface.Home,
    val settings: TideSettings = TideSettings(),
    val allApps: List<AppEntry> = emptyList(),
    val query: String = "",
    val menuFor: AppEntry? = null,
    val notice: Notice? = null,
    val loading: Boolean = true,
) {
    /** Everything the user has not hidden, in alphabetical order. */
    val visibleApps: List<AppEntry> =
        allApps.filterNot { it.key in settings.hiddenApps }

    /** Pinned apps, in the order they were pinned. */
    val dockApps: List<AppEntry> =
        settings.dockedApps.mapNotNull { key -> allApps.firstOrNull { it.key == key } }

    /** Everything that is neither pinned nor hidden — the home grid. */
    val gridApps: List<AppEntry> =
        visibleApps.filterNot { it.key in settings.dockedApps }

    /** Search results, empty when the field is blank. */
    val results: List<AppEntry> =
        if (query.isBlank()) emptyList() else Fuzzy.rank(visibleApps, query)

    val searching: Boolean = query.isNotBlank()
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val store = SettingsStore(application)

    private val surface = MutableStateFlow(Surface.Home)
    private val query = MutableStateFlow("")
    private val menuFor = MutableStateFlow<AppEntry?>(null)
    private val notice = MutableStateFlow<Notice?>(null)
    private val loading = MutableStateFlow(true)

    val state: StateFlow<LauncherUiState> = combine(
        combine(surface, query, menuFor) { s, q, m -> Triple(s, q, m) },
        store.settings,
        repository.apps,
        combine(loading, notice) { l, n -> l to n },
    ) { nav, settings, apps, flags ->
        LauncherUiState(
            surface = nav.first,
            settings = settings,
            allApps = apps,
            query = nav.second,
            menuFor = nav.third,
            notice = flags.second,
            loading = flags.first,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState(),
    )

    init {
        refresh()
    }

    /** Re-query the package manager. Called on resume, since the app list can
     *  change while the launcher sits in the background. */
    fun refresh() {
        viewModelScope.launch {
            loading.value = repository.apps.value.isEmpty()
            val found = repository.refresh()
            loading.value = false
            logi("vm", "refreshed ${found.size} apps")

            // A dock entry can outlive the app it points at (uninstalled, or
            // hidden). Prune so the hotseat never reserves space for a ghost.
            val stale = state.value.settings.dockedApps - found.map { it.key }.toSet()
            if (stale.isNotEmpty()) {
                store.setDocked(state.value.settings.dockedApps - stale)
            }
        }
    }

    fun setSurface(surface: Surface) {
        this.surface.value = surface
        if (surface != Surface.Drawer) query.value = ""
        if (surface != Surface.Home) menuFor.value = null
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun openMenu(entry: AppEntry) {
        menuFor.value = entry
    }

    fun dismissMenu() {
        menuFor.value = null
    }

    fun toggleDock(entry: AppEntry) {
        viewModelScope.launch {
            val pinned = state.value.settings.dockedApps.contains(entry.key)
            val capacity = state.value.settings.dockCapacity
            val atCapacity = !pinned && state.value.settings.dockedApps.size >= capacity
            if (atCapacity) {
                notice.value = Notice("Dock is full — remove one first")
                return@launch
            }
            store.toggleDocked(entry.key)
            notice.value = Notice(
                text = if (pinned) "${entry.label} removed from dock" else "${entry.label} pinned",
                undoAction = if (pinned) UndoAction.Undock else null,
                targetKey = entry.key,
            )
        }
    }

    fun toggleHidden(entry: AppEntry) {
        viewModelScope.launch {
            val wasHidden = state.value.settings.hiddenApps.contains(entry.key)
            store.toggleHidden(entry.key)
            notice.value = Notice(
                text = if (wasHidden) "${entry.label} shown" else "${entry.label} hidden",
                undoAction = if (wasHidden) null else UndoAction.Unhide,
                targetKey = entry.key,
            )
        }
    }

    fun undoNotice() {
        val current = notice.value ?: return
        val key = current.targetKey ?: return
        viewModelScope.launch {
            when (current.undoAction) {
                UndoAction.Unhide -> store.toggleHidden(key)
                UndoAction.Undock -> store.toggleDocked(key)
                null -> Unit
            }
            notice.value = null
        }
    }

    fun consumeNotice() {
        notice.value = null
    }

    // ── settings passthroughs ────────────────────────────────────────────────
    fun setTheme(theme: TideTheme) = viewModelScope.launch { store.setTheme(theme) }
    fun setIconShape(shape: IconShape) = viewModelScope.launch { store.setIconShape(shape) }
    fun setGridColumns(columns: Int) = viewModelScope.launch { store.setGridColumns(columns) }
    fun setShowLabels(show: Boolean) = viewModelScope.launch { store.setShowLabels(show) }
    fun setOceanMotion(on: Boolean) = viewModelScope.launch { store.setOceanMotion(on) }
    fun setBlurPanels(on: Boolean) = viewModelScope.launch { store.setBlurPanels(on) }
    fun setDoubleTapToSearch(on: Boolean) = viewModelScope.launch { store.setDoubleTapToSearch(on) }
    fun setDockCapacity(n: Int) = viewModelScope.launch { store.setDockCapacity(n) }
    fun setDebugOverlay(on: Boolean) = viewModelScope.launch { store.setDebugOverlay(on) }

    /** Apps hidden by the user, for the settings screen's reveal list. */
    val hiddenApps: StateFlow<List<AppEntry>> = combine(
        repository.apps,
        store.settings,
    ) { apps, settings ->
        apps.filter { it.key in settings.hiddenApps }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}