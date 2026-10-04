package app.tide.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.tide.launcher.data.AppEntry
import app.tide.launcher.data.Folder
import app.tide.launcher.data.IconCache
import app.tide.launcher.data.IconShape
import app.tide.launcher.ui.HomeItem
import app.tide.launcher.ui.components.AppIcon
import app.tide.launcher.ui.components.AppIconTile
import app.tide.launcher.ui.components.GlassPill
import app.tide.launcher.ui.components.toShape
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Radius
import app.tide.launcher.ui.theme.TideTypography
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt

internal const val TEST_TAG_HOME = "home_root"
internal const val TEST_TAG_OPEN_SETTINGS = "open_settings"
internal const val TEST_TAG_OPEN_DRAWER = "open_drawer"

/**
 * The home surface: clock, widget area, scrolling grid and the hotseat dock.
 *
 * ### Where the swipe gesture lives
 *
 * Swipe-up is attached to the clock zone, not to the grid. A vertical drag
 * recogniser on the grid either fights the grid's own scroll or silently does
 * nothing once the list is scrolled — both feel broken. Confining it to the
 * area above the grid keeps it always available and never ambiguous, and the
 * grid gets an explicit hint bar above the dock.
 */
@Composable
fun HomeSurface(
    items: List<HomeItem>,
    dockApps: List<AppEntry>,
    iconShape: IconShape,
    showLabels: Boolean,
    columns: Int,
    contentPadding: PaddingValues,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onLongPressFolder: (Folder) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onReorderDock: (from: Int, to: Int) -> Unit,
    onLongPressEmpty: () -> Unit,
    widgetSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOceanPalette.current
    val configuration = LocalConfiguration.current
    val gridState = rememberLazyGridState()
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) {
        56.dp.toPx()
    }

    val screenHeightDp = configuration.screenHeightDp
    val iconSize = remember(screenHeightDp) {
        when {
            screenHeightDp < 640 -> 46.dp
            screenHeightDp < 720 -> 52.dp
            else -> 58.dp
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag(TEST_TAG_HOME),
    ) {
        // ── clock zone: swipe up for the drawer, double tap for search ────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(onOpenDrawer, swipeThreshold) {
                    // Accumulated rather than per-event, so one upward flick
                    // opens the drawer once instead of re-entering every frame.
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

        Spacer(Modifier.height(16.dp))

        // ── widget area ──────────────────────────────────────────────────────
        widgetSlot()

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Long-pressing empty grid space opens the home menu. Tiles
                // consume their own long press first, so this only fires on
                // genuinely empty space.
                .pointerInput(onLongPressEmpty) {
                    detectTapGestures(onLongPress = { onLongPressEmpty() })
                },
        ) {
            if (items.isEmpty()) {
                Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Long-press an app to add widgets, folders or dock shortcuts",
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
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = items, key = { it.key }) { item ->
                        when (item) {
                            is HomeItem.App -> AppIconTile(
                                entry = item.entry,
                                shape = iconShape,
                                showLabel = showLabels,
                                iconSize = iconSize,
                                onClick = { onLaunch(item.entry) },
                                onLongClick = { onLongPress(item.entry) },
                            )

                            is HomeItem.FolderItem -> FolderTile(
                                folder = item.folder,
                                shape = iconShape,
                                showLabels = showLabels,
                                iconSize = iconSize,
                                onClick = { onOpenFolder(item.folder) },
                                onLongClick = { onLongPressFolder(item.folder) },
                            )
                        }
                    }
                }
            }
        }

        SwipeHint(onClick = onOpenSearch)
        Spacer(Modifier.height(10.dp))

        Dock(
            apps = dockApps,
            iconShape = iconShape,
            iconSize = iconSize,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            onOpenSettings = onOpenSettings,
            onReorder = onReorderDock,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(14.dp))
    }
}

/** A folder tile: a translucent squircle holding miniature icons of its members. */
@Composable
private fun FolderTile(
    folder: Folder,
    shape: IconShape,
    showLabels: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalOceanPalette.current

    // Only the first few members are drawn — a folder showing twenty icons
    // turns to noise at this size.
    val preview = remember(folder.appKeys) { folder.appKeys.take(4) }

    val resolved by produceState<List<ImageBitmap>>(emptyList(), folder.appKeys) {
        value = preview.mapNotNull { IconCache.load(context, it.componentOf()) }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 22))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .shadow(
                    elevation = 8.dp,
                    shape = shape.toShape(),
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                )
                .clip(shape.toShape())
                .background(
                    Brush.linearGradient(
                        listOf(
                            palette.accent.copy(alpha = 0.42f),
                            palette.accentAlt.copy(alpha = 0.30f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            FolderPreview(
                icons = resolved,
                modifier = Modifier.padding(4.dp),
            )
        }

        if (showLabels) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = folder.name,
                style = TideTypography.labelLarge,
                color = palette.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Compact grid of member icons, centred for whatever the count happens to be. */
@Composable
private fun FolderPreview(icons: List<ImageBitmap>, modifier: Modifier = Modifier) {
    val shown = icons.take(4)
    // One or two icons stack in a single column; three or four use two. The
    // per-axis offset is measured from the centre of whichever grid is in use,
    // so a one-app folder renders its icon dead centre rather than nudged into
    // the top-left as it would be with a hard-coded 2x2 offset.
    val columns = if (shown.size <= 2) 1 else 2
    val rows = (shown.size + columns - 1) / columns
    val step = 19.dp

    Box(modifier, contentAlignment = Alignment.Center) {
        shown.forEachIndexed { index, icon ->
            val row = index / columns
            val col = index % columns
            androidx.compose.foundation.Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(17.dp)
                    .offset(
                        x = ((col - (columns - 1) / 2f) * step.value).dp,
                        y = ((row - (rows - 1) / 2f) * step.value).dp,
                    )
                    .clip(RoundedCornerShape(percent = 34)),
            )
        }
    }
}

/**
 * Rebuilds a `ComponentName` from an app key.
 *
 * Keys are `package/class`, so the first separator is the boundary. A
 * ComponentName's class half may itself contain a dot but never a slash, which
 * is what makes the split unambiguous.
 */
private fun String.componentOf(): android.content.ComponentName {
    val slash = indexOf('/')
    return if (slash <= 0) {
        android.content.ComponentName(this, "")
    } else {
        android.content.ComponentName(substring(0, slash), substring(slash + 1))
    }
}

/**
 * The hotseat dock, with drag-to-reorder.
 *
 * Reordering is driven by an index swap computed from the dragged item's
 * horizontal offset rather than by a full drag-and-drop of every other item:
 * the dragged icon follows the finger, the others slide out of the way, and
 * the drop commits a single list move.
 */
@Composable
private fun Dock(
    apps: List<AppEntry>,
    iconShape: IconShape,
    iconSize: Dp,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    // Slot width is uniform, so the target index is derived from how many slots
    // the drag has crossed rather than from measured child bounds.
    val slotWidth = remember(apps.size, iconSize) { iconSize + 22.dp }
    // Converted outside the drag callback: that lambda is not a composable
    // scope, so it cannot read LocalDensity.
    val density = LocalDensity.current
    val slotWidthPx = remember(density, slotWidth) { with(density) { slotWidth.toPx() } }

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
                apps.forEachIndexed { index, entry ->
                    DockIcon(
                        entry = entry,
                        shape = iconShape.toShape(),
                        size = iconSize,
                        dragging = index == draggingIndex,
                        dragOffset = dragOffsetX,
                        onClick = { onLaunch(entry) },
                        onDragStart = {
                            draggingIndex = index
                            dragOffsetX = 0f
                        },
                        onDragDelta = { delta ->
                            val next = dragOffsetX + delta
                            dragOffsetX = next
                            val shift = (next / slotWidthPx).roundToInt()
                            val target = (index + shift).coerceIn(0, apps.lastIndex)
                            if (target != draggingIndex && target != index) {
                                onReorder(draggingIndex, target)
                                draggingIndex = target
                            }
                        },
                        onDragEnd = { moved ->
                            draggingIndex = -1
                            dragOffsetX = 0f
                            // A long press that never travelled is a request
                            // for the app's menu, not a reorder.
                            if (!moved) onLongPress(entry)
                        },
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
    dragging: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    /** [moved] is false when the finger lifted without travelling. */
    onDragEnd: (moved: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val icon by produceState<ImageBitmap?>(
        initialValue = IconCache.peek(entry.component),
        key1 = entry.component,
    ) {
        if (value == null) value = IconCache.load(context, entry.component)
    }

    val scale by animateFloatAsState(
        targetValue = if (dragging) 1.18f else if (pressed) 0.9f else 1f,
        label = "dockScale",
    )

    Box(
        modifier = Modifier
            .size(size + 8.dp)
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(dragOffset.roundToInt(), 0) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            // `combinedClickable` deliberately has no onLongClick here. It and
            // detectDragGesturesAfterLongPress both fire on the same hold, so
            // whichever ran first won and long-pressing a dock icon only ever
            // opened the sheet. Instead the drag recogniser owns the gesture and
            // a release without movement is reinterpreted as a long press,
            // which keeps both behaviours on one recogniser.
            .pointerInput(entry.key) {
                var travelled = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        travelled = 0f
                        onDragStart()
                    },
                    onDrag = { change, amount ->
                        travelled += amount.x
                        change.consume()
                        onDragDelta(amount.x)
                    },
                    onDragEnd = { onDragEnd(kotlin.math.abs(travelled) > 1f) },
                    onDragCancel = { onDragEnd(false) },
                )
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (dragging) {
            // A halo so the lifted icon reads as picked up rather than stuck.
            Box(
                Modifier
                    .size(size + 4.dp)
                    .clip(CircleShape)
                    .border(2.dp, LocalOceanPalette.current.accent, CircleShape),
            )
        }
        AppIcon(icon = icon, shape = shape, size = size, pressed = pressed)
    }
}

@Composable
private fun DockSettingsSlot(onClick: () -> Unit) {
    val palette = LocalOceanPalette.current
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(palette.glassBorder.copy(alpha = 0.16f))
            .testTag(TEST_TAG_OPEN_SETTINGS)
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

@Composable
private fun ColumnScope.SwipeHint(onClick: () -> Unit) {
    val palette = LocalOceanPalette.current
    Spacer(
        Modifier
            .align(Alignment.CenterHorizontally)
            .size(width = 46.dp, height = 4.dp)
            .clip(CircleShape)
            .background(palette.glassBorder.copy(alpha = 0.55f))
            .testTag(TEST_TAG_OPEN_DRAWER)
            .combinedClickable(onClick = onClick),
    )
}

