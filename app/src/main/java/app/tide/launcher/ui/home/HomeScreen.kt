package app.tide.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tide.launcher.data.AppEntry
import app.tide.launcher.data.IconCache
import app.tide.launcher.data.IconShape
import app.tide.launcher.ui.components.AppIcon
import app.tide.launcher.ui.components.AppIconTile
import app.tide.launcher.ui.components.GlassPill
import app.tide.launcher.ui.components.toShape
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.TideTypography

/**
 * The home surface: clock, scrolling grid of apps, and the hotseat dock.
 *
 * ### Where the swipe gesture lives
 *
 * Swipe-up is attached to the clock zone, not to the grid. A vertical drag
 * recogniser on the grid either fights the grid's own scroll or silently does
 * nothing once the list is scrolled — both feel broken. Confining it to the area
 * above the grid keeps it always-available and never ambiguous, and the grid
 * itself gets an explicit hint bar above the dock.
 */
@Composable
fun HomeSurface(
    apps: List<AppEntry>,
    dockApps: List<AppEntry>,
    iconShape: IconShape,
    showLabels: Boolean,
    columns: Int,
    contentPadding: PaddingValues,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOceanPalette.current
    val configuration = LocalConfiguration.current
    val gridState = rememberLazyGridState()
    val swipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }

    val screenHeightDp = configuration.screenHeightDp
    // Icons shrink on short screens so the clock, grid and dock still fit
    // without the grid collapsing to a single row.
    val iconSize = remember(screenHeightDp) {
        when {
            screenHeightDp < 640 -> 46.dp
            screenHeightDp < 720 -> 52.dp
            else -> 58.dp
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding),
    ) {
        // ── clock zone: swipe up for the drawer, double tap for search ────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(onOpenDrawer, swipeThreshold) {
                    // Accumulated rather than per-event, so a single upward flick
                    // opens the drawer once instead of re-entering it every frame.
                    var accumulated = 0f
                    detectVerticalDragGestures(
                        onDragStart = { accumulated = 0f },
                        onDragEnd = { accumulated = 0f },
                        onDragCancel = { accumulated = 0f },
                        onVerticalDrag = { _, dy ->
                            accumulated += dy
                            if (accumulated < -swipeThreshold) {
                                onOpenDrawer()
                                accumulated = 0f
                            }
                        },
                    )
                }
                .pointerInput(onOpenSearch) {
                    detectTapGestures(onDoubleTap = { onOpenSearch() })
                },
            contentAlignment = Alignment.Center,
        ) {
            ClockWidget()
        }

        Spacer(Modifier.height(20.dp))

        // ── grid ─────────────────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (apps.isEmpty()) {
                Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Pin apps from the app list to fill this screen",
                        style = TideTypography.bodyLarge,
                        color = palette.onSurfaceMuted,
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = apps, key = { it.key }) { entry ->
                        AppIconTile(
                            entry = entry,
                            shape = iconShape,
                            showLabel = showLabels,
                            iconSize = iconSize,
                            onClick = { onLaunch(entry) },
                            onLongClick = { onLongPress(entry) },
                        )
                    }
                }
            }
        }

        SwipeHint(onClick = onOpenSearch)
        Spacer(Modifier.height(10.dp))

        // ── dock ─────────────────────────────────────────────────────────────
        Dock(
            apps = dockApps,
            iconShape = iconShape,
            iconSize = iconSize,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(14.dp))
    }
}

/** The pill that holds pinned apps, plus a settings affordance when empty. */
@Composable
private fun Dock(
    apps: List<AppEntry>,
    iconShape: IconShape,
    iconSize: Dp,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPill(modifier = modifier.widthIn(max = 440.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (apps.isEmpty()) {
                DockSettingsSlot(onClick = onOpenSettings)
            } else {
                apps.forEach { entry ->
                    DockIcon(
                        entry = entry,
                        shape = iconShape.toShape(),
                        size = iconSize,
                        onClick = { onLaunch(entry) },
                        onLongClick = { onLongPress(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DockIcon(
    entry: AppEntry,
    shape: androidx.compose.ui.graphics.Shape,
    size: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = IconCache.peek(entry.component),
        key1 = entry.component,
    ) {
        if (value == null) value = IconCache.load(context, entry.component)
    }

    Box(
        modifier = Modifier
            .size(size + 8.dp)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon = icon, shape = shape, size = size, pressed = pressed)
    }
}

/** Shown when nothing is pinned — doubles as the way into Settings. */
@Composable
private fun DockSettingsSlot(onClick: () -> Unit) {
    val palette = LocalOceanPalette.current
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(palette.glassBorder.copy(alpha = 0.16f))
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = "Settings",
            tint = palette.onSurfaceMuted,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** The small pill that opens the drawer. */
@Composable
private fun ColumnScope.SwipeHint(onClick: () -> Unit) {
    val palette = LocalOceanPalette.current
    Spacer(
        Modifier
            .align(Alignment.CenterHorizontally)
            .size(width = 46.dp, height = 4.dp)
            .clip(CircleShape)
            .background(palette.glassBorder.copy(alpha = 0.55f))
            .combinedClickable(onClick = onClick),
    )
}