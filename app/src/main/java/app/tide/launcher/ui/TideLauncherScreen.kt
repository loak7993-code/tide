package app.tide.launcher.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import app.tide.launcher.debug.logi
import app.tide.launcher.ui.components.GlassSurface
import app.tide.launcher.ui.components.OceanBackground
import app.tide.launcher.ui.drawer.DrawerScreen
import app.tide.launcher.ui.home.HomeSurface
import app.tide.launcher.ui.settings.SettingsScreen
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Motion
import app.tide.launcher.ui.theme.Radius
import app.tide.launcher.ui.theme.TideTheme
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

    // Home shrinks away before the target activity takes over, so the handover
    // reads as leaving the surface rather than as the launcher blinking out.
    var launching by remember { mutableStateOf(false) }
    val launchScale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (launching) 1.14f else 1f,
        animationSpec = Motion.Emphasis,
        label = "launchScale",
    )
    val launchAlpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (launching) 0f else 1f,
        animationSpec = tween(durationMillis = 190),
        label = "launchAlpha",
    )

    fun launch(entry: AppEntry) {
        if (launching) return
        launching = true
        Haptics.confirm(context, haptics)
        scope.launch {
            delay(170)
            LauncherActions.launch(context, entry)
            // Cleared after the activity has had time to take focus; a launcher
            // that stayed invisible would show a blank wall on return.
            delay(320)
            launching = false
        }
    }

    fun openMenu(entry: AppEntry) {
        Haptics.longPress(context, haptics)
        viewModel.openMenu(entry)
    }

    TideTheme(state.settings.theme) {
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

        Box(
            Modifier
                .fillMaxSize()
                .scale(launchScale.value)
                .alpha(launchAlpha.value),
        ) {
            OceanBackground(motion = state.settings.oceanMotion)

            val surfacePadding = WindowInsets.safeDrawing.asPaddingValues()

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
                        apps = state.gridApps,
                        dockApps = state.dockApps,
                        iconShape = state.settings.iconShape,
                        showLabels = state.settings.showLabels,
                        columns = state.settings.gridColumns,
                        contentPadding = surfacePadding,
                        onLaunch = ::launch,
                        onLongPress = ::openMenu,
                        onOpenDrawer = { viewModel.setSurface(Surface.Drawer) },
                        onOpenSearch = { viewModel.setSurface(Surface.Drawer) },
                        onOpenSettings = { viewModel.setSurface(Surface.Settings) },
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
                        onOceanMotionChange = viewModel::setOceanMotion,
                        onBlurChange = viewModel::setBlurPanels,
                        onDoubleTapChange = viewModel::setDoubleTapToSearch,
                        onDockCapacityChange = viewModel::setDockCapacity,
                        onDebugOverlayChange = viewModel::setDebugOverlay,
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

            // ── debug overlay ────────────────────────────────────────────────
            if (state.settings.debugOverlay) {
                DebugOverlay(modifier = Modifier.align(Alignment.TopEnd))
            }
        }

        // ── back ──────────────────────────────────────────────────────────────
        BackHandler(enabled = state.surface != Surface.Home) {
            viewModel.setSurface(Surface.Home)
        }
        // On home, back is swallowed: a launcher that exits on back leaves the
        // user staring at whatever was behind it.
        BackHandler(enabled = state.surface == Surface.Home) { }

        // ── long-press sheet ─────────────────────────────────────────────────
        state.menuFor?.let { entry ->
            AppMenuHost(
                entry = entry,
                state = state,
                onDismiss = viewModel::dismissMenu,
                onToggleDock = { viewModel.toggleDock(entry) },
                onToggleHide = { viewModel.toggleHidden(entry) },
            )
        }
    }
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