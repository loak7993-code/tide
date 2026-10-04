package app.tide.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tide.launcher.data.AppEntry
import app.tide.launcher.data.Folder
import app.tide.launcher.data.IconShape
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Radius
import app.tide.launcher.ui.theme.TideTypography

/**
 * A folder, opened.
 *
 * Presented as a sheet rather than navigating into a second screen: the folder
 * is an overlay on the home grid, and dismissing it should feel like the lid
 * closing rather than like going back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSheet(
    folder: Folder,
    apps: List<AppEntry>,
    iconShape: IconShape,
    showLabels: Boolean,
    columns: Int,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalOceanPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)

    var renaming by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.gradientStops[1].copy(alpha = 0.9f),
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = null,
        shape = shape,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = folder.name,
                    style = TideTypography.titleLarge,
                    color = palette.onGlass,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.glassBorder.copy(alpha = 0.18f))
                        .clickable { renaming = !renaming },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Rename folder",
                        tint = palette.onGlass,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.glassBorder.copy(alpha = 0.18f))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove folder",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.glassBorder.copy(alpha = 0.18f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = palette.onGlass,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (renaming) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(palette.glassBorder.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = folder.name,
                        onValueChange = { onRename(it) },
                        singleLine = true,
                        textStyle = TideTypography.bodyLarge.copy(color = palette.onGlass),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (apps.isEmpty()) {
                Text(
                    text = "Nothing left in this folder",
                    style = TideTypography.bodyLarge,
                    color = palette.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns.coerceIn(3, 5)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, bottom = 12.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                ) {
                    items(items = apps, key = { it.key }) { entry ->
                        AppIconTile(
                            entry = entry,
                            shape = iconShape,
                            showLabel = showLabels,
                            iconSize = 52.dp,
                            onClick = {
                                onLaunch(entry)
                                onDismiss()
                            },
                            onLongClick = { onLongPress(entry) },
                        )
                    }
                }
            }
        }
    }
}