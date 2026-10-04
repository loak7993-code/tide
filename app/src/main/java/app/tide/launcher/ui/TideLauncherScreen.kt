package app.tide.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tide.launcher.R
import app.tide.launcher.core.Haptics
import app.tide.launcher.core.LauncherActions
import app.tide.launcher.data.AppEntry
import app.tide.launcher.debug.DebugOverlay
import app.tide.launcher.data.IconCache
import app.tide.launcher.debug.logi
import app.tide.launcher.ui.components.GlassSurface
import app.tide.launcher.ui.components.HomeMenuSheet
import app.tide.launcher.ui.appdetails.AppDetailsScreen
import app.tide.launcher.ui.onboarding.OnboardingFlow
import app.tide.launcher.ui.components.LaunchEasing
import app.tide.launcher.ui.components.LaunchTransition
import app.tide.launcher.ui.components.FolderPickerSheet
import app.tide.launcher.ui.components.FolderSheet
import app.tide.launcher.ui.components.OceanBackground
import app.tide.launcher.ui.drawer.DrawerScreen
import app.tide.launcher.ui.home.HomeSurface
import app.tide.launcher.ui.settings.SettingsScreen
import app.tide.launcher.ui.theme.LocalClockFormat
import app.tide.launcher.ui.theme.LocalDockScale
import app.tide.launcher.ui.theme.LocalFontScale
import app.tide.launcher.ui.theme.LocalGridSpacing
import app.tide.launcher.ui.theme.LocalShowSearchBar
import app.tide.launcher.ui.theme.LocalIconScale
import app.tide.launcher.ui.theme.LocalLabelScale
import app.tide.launcher.ui.theme.LocalMotionIntensity
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.LocalPanelOpacity
import app.tide.launcher.ui.theme.LocalShowClock
import app.tide.launcher.ui.theme.LocalShowDate
import app.tide.launcher.ui.theme.Motion
import app.tide.launcher.ui.theme.Radius
import app.tide.launcher.ui.theme.TideTheme
import app.tide.launcher.ui.widgets.WidgetArea
import app.tide.launcher.ui.widgets.WidgetHostController
import app.tide.launcher.ui.widgets.WidgetPickerSheet
import app.tide.launcher.ui.widgets.rememberWidgetHost
import app.tide.launcher.ui.theme.TideTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launcher root: the animated water, whichever surface is showing, the
 * long-press sheet, the notice bar, and the debug overlay.
 */
@Composable
fun TideLauncherScreen(viewModel: LauncherViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = LocalOceanPalette.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(state.surface) {
        logi("ui", "surface=${state.surface} apps=${state.allApps.size}")
    }

    val widgetHost = rememberWidgetHost()
    var showWidgetPicker by remember { mutableStateOf(false) }
    // The app whose "Add to folder" was tapped; the picker acts on it.
    var folderTarget by remember { mutableStateOf<AppEntry?>(null) }
    var detailsTarget by remember { mutableStateOf<AppEntry?>(null) }
    var showHomeMenu by remember { mutableStateOf(false) }

    // The launch handover. Tiles report their bounds as they lay out, so the
    // transition can begin exactly where the tapped icon was drawn rather than
    // crossfading the whole screen.
    val iconBounds = remember { mutableStateMapOf<String, Rect>() }
    val launchProgress = remember { Animatable(0f) }
    var launchEntry by remember { mutableStateOf<AppEntry?>(null) }
    var launchOrigin by remember { mutableStateOf<Rect?>(null) }

    fun launch(entry: AppEntry) {
        if (launchEntry != null) return
        Haptics.confirm(context, haptics)

        val origin = iconBounds[entry.key]
        scope.launch {
            launchOrigin = origin
            launchEntry = entry
            launchProgress.snapTo(0f)
            // A launch from the drawer or a folder has no measured rect, so it
            // just crossfades — animating from nowhere would look worse.
            launchProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (origin != null) 300 else 200,
                    easing = LaunchEasing,
                ),
            )
            LauncherActions.launch(context, entry)
            // Cleared once the target has had time to take focus; a launcher
            // left mid-transition shows a frozen scrim on return.
            delay(90)
            launchEntry = null
            launchOrigin = null
            launchProgress.snapTo(0f)
        }
    }

    fun openMenu(entry: AppEntry) {
        Haptics.longPress(context, haptics)
        viewModel.openMenu(entry)
    }

    TideTheme(state.settings.theme, fontScale = state.settings.fontScale) {
        CompositionLocalProvider(
            LocalPanelOpacity provides state.settings.panelOpacity,
            LocalLabelScale provides state.settings.labelScale,
            LocalIconScale provides state.settings.iconScale,
            LocalMotionIntensity provides state.settings.motionIntensity,
            LocalShowClock provides state.settings.showClock,
            LocalShowDate provides state.settings.showDate,
            LocalClockFormat provides state.settings.clockFormat,
            LocalFontScale provides state.settings.fontScale,
            LocalGridSpacing provides state.settings.gridSpacing,
            LocalDockScale provides state.settings.dockIconScale,
            LocalShowSearchBar provides state.settings.showSearchBar,
        ) {
        // The water behind the status bar is bright in the light theme and deep
        // in the dark ones, so the system bar icon colour has to follow the
        // palette rather than the device's dark-mode setting.
        val view = LocalView.current
        val isLightTheme = palette.isLight
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = isLightTheme
                    isAppearanceLightNavigationBars = isLightTheme
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            OceanBackground(enabled = state.settings.oceanMotion)

            // `systemBars`, not `safeDrawing`: safeDrawing unions in the IME, and the
            // drawer consumes that separately with `imePadding()`. Passing
            // safeDrawing down would count the keyboard twice — which showed up
            // as the A-Z rail collapsing to a handful of letters when the search
            // field was focused.
            val surfacePadding = WindowInsets.systemBars.asPaddingValues()

            AnimatedContent(
                targetState = state.surface,
                transitionSpec = {
                    // The drawer rises out of the home screen; returning to home
                    // is a straight crossfade, because sliding the clock back down
                    // on every dismiss reads as sluggish.
                    val fadeInSpec = tween<Float>(durationMillis = Motion.Medium)
                    if (targetState == Surface.Drawer) {
                        (slideInVertically(Motion.SheetOffset) { it / 3 } + fadeIn(fadeInSpec))
                            .togetherWith(fadeOut(tween<Float>(durationMillis = Motion.Fast)))
                    } else {
                        fadeIn(fadeInSpec)
                            .togetherWith(fadeOut(tween<Float>(durationMillis = Motion.Fast)))
                    }
                },
                label = "surface",
            ) { surface ->
                when (surface) {
                    Surface.Home -> HomeSurface(
                        items = state.homeItems,
                        dockApps = state.dockApps,
                        iconShape = state.settings.iconShape,
                        showLabels = state.settings.showLabels,
                        columns = state.settings.gridColumns,
                        contentPadding = surfacePadding,
                        onLaunch = ::launch,
                        onLongPress = ::openMenu,
                        onOpenFolder = viewModel::openFolder,
                        onLongPressFolder = viewModel::openFolderMenu,
                        onOpenDrawer = { viewModel.setSurface(Surface.Drawer) },
                        onOpenSearch = { viewModel.setSurface(Surface.Drawer) },
                        onOpenSettings = { viewModel.setSurface(Surface.Settings) },
                        onReorderDock = viewModel::moveDockEntry,
                        onLongPressEmpty = { showHomeMenu = true },
                        onIconBounds = { key, rect -> iconBounds[key] = rect },
                        widgetSlot = {
                            WidgetArea(
                                widgetIds = state.settings.widgetIds,
                                controller = widgetHost,
                                onAddWidget = { showWidgetPicker = true },
                                onRemoveWidget = { id ->
                                    widgetHost.deleteWidget(id)
                                    viewModel.setWidgetIds(
                                        state.settings.widgetIds - id,
                                    )
                                },
                            )
                        },
                    )

                    Surface.Drawer -> DrawerScreen(
                        apps = state.visibleApps,
                        query = state.query,
                        iconShape = state.settings.iconShape,
                        showLabels = state.settings.showLabels,
                        columns = state.settings.gridColumns,
                        contentPadding = surfacePadding,
                        onQueryChange = viewModel::setQuery,
                        onLaunch = ::launch,
                        onLongPress = ::openMenu,
                        onDismiss = { viewModel.setSurface(Surface.Home) },
                    )

                    Surface.Settings -> SettingsScreen(
                        settings = state.settings,
                        hiddenApps = state.allApps.filter {
                            it.key in state.settings.hiddenApps
                        },
                        contentPadding = surfacePadding,
                        onClose = { viewModel.setSurface(Surface.Home) },
                        onThemeChange = viewModel::setTheme,
                        onIconShapeChange = viewModel::setIconShape,
                        onColumnsChange = viewModel::setGridColumns,
                        onShowLabelsChange = viewModel::setShowLabels,
                        onShowClockChange = viewModel::setShowClock,
                        onShowDateChange = viewModel::setShowDate,
                        onClockFormatChange = viewModel::setClockFormat,
                        onIconScaleChange = viewModel::setIconScale,
                        onLabelScaleChange = viewModel::setLabelScale,
                        onMotionIntensityChange = viewModel::setMotionIntensity,
                        onPanelOpacityChange = viewModel::setPanelOpacity,
                        onFontScaleChange = viewModel::setFontScale,
                        onGridSpacingChange = viewModel::setGridSpacing,
                        onDockIconScaleChange = viewModel::setDockIconScale,
                        onShowSearchBarChange = viewModel::setShowSearchBar,
                        onShowTideInDrawerChange = viewModel::setShowTideInDrawer,
                        onSortOrderChange = viewModel::setSortOrder,
                        onOceanMotionChange = viewModel::setOceanMotion,
                        onBlurChange = viewModel::setBlurPanels,
                        onDoubleTapChange = viewModel::setDoubleTapToSearch,
                        onDockCapacityChange = viewModel::setDockCapacity,
                        onDebugOverlayChange = viewModel::setDebugOverlay,
                        widgetCount = state.settings.widgetIds.size,
                        onAddWidget = { showWidgetPicker = true },
                        onUnhide = viewModel::toggleHidden,
                    )
                }
            }

            // ── notice bar ───────────────────────────────────────────────────
            state.notice?.let { notice ->
                NoticeBar(
                    text = notice.text,
                    undoLabel = if (notice.undoAction != null) {
                        stringResource(R.string.undo)
                    } else {
                        null
                    },
                    onUndo = viewModel::undoNotice,
                    onDismiss = viewModel::consumeNotice,
                    bottomInset = surfacePadding.calculateBottomPadding(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            // ── launch transition ─────────────────────────────────────────────
            launchEntry?.let { entry ->
                val origin = launchOrigin
                if (origin != null) {
                    val icon = remember(entry.key) { IconCache.peek(entry.component) }
                    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                        initialValue = icon,
                        key1 = entry.component,
                    ) {
                        if (value == null) value = IconCache.load(context, entry.component)
                    }
                    LaunchTransition(
                        icon = bitmap,
                        from = origin,
                        progress = launchProgress.value,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            // ── debug overlay ────────────────────────────────────────────────
            if (state.settings.debugOverlay) {
                DebugOverlay(modifier = Modifier.align(Alignment.TopEnd))
            }

            // ── first-run onboarding ─────────────────────────────────────────
            // Above everything and above the sheets, because the first thing a
            // new user does is tap through: nothing else should be reachable
            // until they have either finished or skipped.
            if (!state.settings.onboarded) {
                OnboardingFlow(
                    onThemeChange = viewModel::setTheme,
                    onDone = viewModel::completeOnboarding,
                )
            }
        }

        // ── back ──────────────────────────────────────────────────────────────
        BackHandler(enabled = state.surface != Surface.Home) {
            viewModel.setSurface(Surface.Home)
        }
        // On home, back is swallowed: a launcher that exits on back leaves the
        // user staring at whatever was behind it.
        BackHandler(enabled = state.surface == Surface.Home) { }
        // The per-app page is a plain overlay, so it needs its own back
        // handler; the surface-based one above would not see it.
        BackHandler(enabled = detailsTarget != null) { detailsTarget = null }

        // ── long-press sheet ─────────────────────────────────────────────────
        state.menuFor?.let { entry ->
            AppMenuHost(
                entry = entry,
                state = state,
                onDismiss = viewModel::dismissMenu,
                onToggleDock = { viewModel.toggleDock(entry) },
                onToggleHide = { viewModel.toggleHidden(entry) },
                onAddToFolder = {
                    viewModel.dismissMenu()
                    folderTarget = entry
                },
                onAppSettings = {
                    viewModel.dismissMenu()
                    detailsTarget = entry
                },
            )
        }

        // ── folder ───────────────────────────────────────────────────────────
        state.folderFor?.let { folder ->
            FolderSheet(
                folder = folder,
                apps = state.appsIn(folder),
                iconShape = state.settings.iconShape,
                showLabels = state.settings.showLabels,
                columns = state.settings.gridColumns,
                onLaunch = ::launch,
                onLongPress = { viewModel.dismissFolder(); openMenu(it) },
                onRename = { viewModel.renameFolder(folder, it) },
                onRemove = { viewModel.removeFolder(folder) },
                onDismiss = viewModel::dismissFolder,
            )
        }

        // ── home menu (long-press on empty space) ────────────────────────────
        if (showHomeMenu) {
            HomeMenuSheet(
                onDismiss = { showHomeMenu = false },
                onAddWidget = {
                    showHomeMenu = false
                    showWidgetPicker = true
                },
                onOpenSettings = {
                    showHomeMenu = false
                    viewModel.setSurface(Surface.Settings)
                },
            )
        }

        // ── per-app settings ─────────────────────────────────────────────────
        // An overlay rather than a surface: it sits on top of whichever surface
        // the long-press came from, so backing out of it returns to where the
        // user was instead of always landing on Home.
        detailsTarget?.let { target ->
            AppDetailsScreen(
                entry = target,
                iconShape = state.settings.iconShape,
                contentPadding = WindowInsets.systemBars.asPaddingValues(),
                onBack = { detailsTarget = null },
                onClose = { detailsTarget = null },
            )
        }

        // ── folder picker ────────────────────────────────────────────────────
        folderTarget?.let { target ->
            FolderPickerSheet(
                folders = state.settings.folders,
                onDismiss = { folderTarget = null },
                onPickExisting = { folder ->
                    viewModel.moveToFolder(target.key, folder.id)
                    folderTarget = null
                },
                onCreateNew = {
                    viewModel.createFolder(target.label, listOf(target.key))
                    folderTarget = null
                },
            )
        }

        // ── widgets ──────────────────────────────────────────────────────────
        if (showWidgetPicker) {
            WidgetPickerSheet(
                controller = widgetHost,
                onDismiss = { showWidgetPicker = false },
                onPicked = { provider ->
                    showWidgetPicker = false
                    val activity = context.findActivity()
                    val id = widgetHost.addWidget(provider, activity)
                    if (id == -1) {
                        viewModel.consumeNotice()
                    } else if (provider.configure != null) {
                        // Persist only once configuration reports back.
                        viewModel.beginWidgetConfiguration(id)
                    } else {
                        widgetHost.onConfigureResult(succeeded = true, appWidgetId = id)
                        viewModel.finishWidgetConfiguration(kept = true)
                    }
                },
            )
        }
        }
    }
}

/** Unwraps the Activity from whatever ContextWrapper Compose is holding. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Compact notice bar with an optional undo. */
@Composable
private fun NoticeBar(
    text: String,
    undoLabel: String?,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOceanPalette.current

    LaunchedEffect(text) {
        delay(2_600)
        onDismiss()
    }

    GlassSurface(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = bottomInset + 96.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(Radius.pill),
        elevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = text,
                style = TideTypography.bodyLarge,
                color = palette.onGlass,
                modifier = Modifier.weight(1f),
            )
            if (undoLabel != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = undoLabel,
                    style = TideTypography.labelLarge,
                    color = palette.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable(onClick = onUndo)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}