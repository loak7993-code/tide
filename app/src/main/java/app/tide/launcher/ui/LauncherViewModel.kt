package app.tide.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tide.launcher.data.AppEntry
import app.tide.launcher.data.AppRepository
import app.tide.launcher.data.Folder
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

/** One entry in the home grid: either an app or a folder. */
sealed interface HomeItem {
    val key: String

    data class App(val entry: AppEntry) : HomeItem {
        override val key: String get() = entry.key
    }

    data class FolderItem(val folder: Folder) : HomeItem {
        override val key: String get() = "folder:${folder.id}"
    }
}

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
    val folderFor: Folder? = null,
    val folderMenuFor: Folder? = null,
    val notice: Notice? = null,
    val loading: Boolean = true,
) {
    /** Everything the user has not hidden, in alphabetical order. */
    val visibleApps: List<AppEntry> =
        allApps.filterNot { it.key in settings.hiddenApps }

    /** Pinned apps, in the order the user arranged them. */
    val dockApps: List<AppEntry> =
        settings.dockedApps.mapNotNull { key -> allApps.firstOrNull { it.key == key } }

    /**
     * What the home grid shows: apps that are neither pinned nor filed into a
     * folder, interleaved with the folders themselves.
     *
     * Apps inside a folder are hidden from the grid but stay in the drawer, so
     * a folder never makes an app unreachable.
     */
    val homeItems: List<HomeItem> = run {
        val filedKeys = settings.folders.flatMapTo(HashSet()) { it.appKeys }
        val loose = visibleApps.filterNot {
            it.key in settings.dockedApps || it.key in filedKeys
        }
        // Folders first, then loose apps — the section a folder represents reads
        // as belonging to it.
        buildList {
            settings.folders.forEach { folder ->
                // A folder whose apps have all been uninstalled would render as
                // an unopenable tile; drop it from the grid but keep it stored so
                // reinstalling the app restores the folder.
                if (folder.appKeys.any { key -> allApps.any { it.key == key } }) {
                    add(HomeItem.FolderItem(folder))
                }
            }
            loose.forEach { add(HomeItem.App(it)) }
        }
    }

    /** Search results, empty when the field is blank. */
    val results: List<AppEntry> =
        if (query.isBlank()) emptyList() else Fuzzy.rank(visibleApps, query)

    val searching: Boolean = query.isNotBlank()

    fun appsIn(folder: Folder): List<AppEntry> =
        folder.appKeys.mapNotNull { key -> allApps.firstOrNull { it.key == key } }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val store = SettingsStore(application)

    private val surface = MutableStateFlow(Surface.Home)
    private val query = MutableStateFlow("")
    private val menuFor = MutableStateFlow<AppEntry?>(null)
    private val folderFor = MutableStateFlow<Folder?>(null)
    private val folderMenuFor = MutableStateFlow<Folder?>(null)
    private val notice = MutableStateFlow<Notice?>(null)
    private val loading = MutableStateFlow(true)

    val state: StateFlow<LauncherUiState> = combine(
        combine(
            surface, query, menuFor, folderFor, folderMenuFor,
        ) { s, q, m, f, fm -> Nav(s, q, m, f, fm) },
        store.settings,
        repository.apps,
        loading,
        notice,
    ) { nav, settings, apps, isLoading, currentNotice ->
        LauncherUiState(
            surface = nav.surface,
            settings = settings,
            allApps = apps,
            query = nav.query,
            menuFor = nav.app,
            folderFor = nav.folder,
            folderMenuFor = nav.folderMenu,
            notice = currentNotice,
            loading = isLoading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState(),
    )

    private data class Nav(
        val surface: Surface,
        val query: String,
        val app: AppEntry?,
        val folder: Folder?,
        val folderMenu: Folder?,
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

            val settings = state.value.settings

            // A dock entry or folder member can outlive the app it points at.
            // Prune so the hotseat never reserves space for a ghost.
            val live = found.map { it.key }.toSet()
            val staleDock = settings.dockedApps.filterNot { it in live }
            if (staleDock.isNotEmpty()) {
                store.setDocked(settings.dockedApps.filter { it in live })
            }
            if (settings.folders.any { folder -> folder.appKeys.any { it !in live } }) {
                store.setFolders(
                    settings.folders.map { folder ->
                        folder.copy(appKeys = folder.appKeys.filter { it in live })
                    },
                )
            }
        }
    }

    fun setSurface(surface: Surface) {
        this.surface.value = surface
        if (surface != Surface.Drawer) query.value = ""
        if (surface != Surface.Home) {
            menuFor.value = null
            folderFor.value = null
            folderMenuFor.value = null
        }
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

    fun openFolder(folder: Folder) {
        folderFor.value = folder
    }

    fun dismissFolder() {
        folderFor.value = null
    }

    fun openFolderMenu(folder: Folder) {
        folderMenuFor.value = folder
    }

    fun dismissFolderMenu() {
        folderMenuFor.value = null
    }

    fun toggleDock(entry: AppEntry) {
        viewModelScope.launch {
            val settings = state.value.settings
            val pinned = settings.dockedApps.contains(entry.key)
            val atCapacity = !pinned && settings.dockedApps.size >= settings.dockCapacity
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

    /** Dock drag-to-reorder. */
    fun moveDockEntry(from: Int, to: Int) {
        if (from == to) return
        viewModelScope.launch { store.moveDocked(from, to) }
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

    // ── folders ─────────────────────────────────────────────────────────────

    fun createFolder(name: String, members: List<String>) {
        viewModelScope.launch {
            if (members.isEmpty()) return@launch
            val memberSet = members.toSet()

            // Pull the new members out of whichever folder already held them,
            // then dissolve any folder left empty rather than leaving a
            // permanently blank tile on the grid.
            //
            // Single-app folders are allowed on purpose: creating one from a
            // long press is the only path to renaming an app, and a folder is
            // the natural container for that.
            val survivors = state.value.settings.folders
                .map { folder -> folder.copy(appKeys = folder.appKeys.filterNot { it in memberSet }) }
                .filter { it.appKeys.isNotEmpty() }

            val folder = Folder(
                id = "f" + System.currentTimeMillis().toString(36),
                name = name.trim().ifBlank { "Folder" },
                appKeys = members,
            )
            store.setFolders(survivors + folder)
            notice.value = Notice("${folder.name} created")
        }
    }

    fun renameFolder(folder: Folder, name: String) {
        viewModelScope.launch {
            val trimmed = name.trim().ifBlank { folder.name }
            store.setFolders(
                state.value.settings.folders.map {
                    if (it.id == folder.id) it.copy(name = trimmed) else it
                },
            )
        }
    }

    /** Dissolves the folder and returns its apps to the grid. */
    fun removeFolder(folder: Folder) {
        viewModelScope.launch {
            store.setFolders(state.value.settings.folders.filterNot { it.id == folder.id })
            folderFor.value = null
            folderMenuFor.value = null
            notice.value = Notice("${folder.name} removed")
        }
    }

    /** Moves an app into a folder, out of whichever folder currently holds it. */
    fun moveToFolder(appKey: String, folderId: String) {
        viewModelScope.launch {
            val settings = state.value.settings
            store.setFolders(
                settings.folders.map { folder ->
                    when {
                        folder.id == folderId -> folder.copy(appKeys = folder.appKeys + appKey)
                        appKey in folder.appKeys -> folder.copy(appKeys = folder.appKeys - appKey)
                        else -> folder
                    }
                },
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

    fun setWidgetIds(ids: List<Int>) = viewModelScope.launch { store.setWidgetIds(ids) }

    /**
     * Widget id awaiting the provider's configuration activity. The bind only
     * completes once that activity returns, so the id is not persisted before
     * then.
     */
    var pendingWidgetId: Int? = null
        private set

    fun beginWidgetConfiguration(appWidgetId: Int) {
        pendingWidgetId = appWidgetId
    }

    /**
     * Result of the provider configuration flow.
     *
     * @param kept true when the controller confirmed the widget is usable.
     */
    fun finishWidgetConfiguration(kept: Boolean) {
        val id = pendingWidgetId
        pendingWidgetId = null
        if (id == null) return
        viewModelScope.launch {
            val current = state.value.settings.widgetIds
            if (kept) {
                if (id !in current) store.setWidgetIds(current + id)
            } else {
                store.setWidgetIds(current - id)
            }
        }
    }
}