package app.tide.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.tide.launcher.data.Folder
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Radius
import app.tide.launcher.ui.theme.TideTypography

/**
 * Destination picker for "Add to folder".
 *
 * Reached from an app's long-press menu, so the app in question is implicit —
 * the caller already holds it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onPickExisting: (Folder) -> Unit,
    onCreateNew: () -> Unit,
) {
    val palette = LocalOceanPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.gradientStops[1].copy(alpha = 0.9f),
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = null,
        shape = shape,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Add to folder",
                style = TideTypography.titleLarge,
                color = palette.onGlass,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
            )
            Spacer(Modifier.height(8.dp))

            PickerRow(
                icon = Icons.Rounded.CreateNewFolder,
                label = "New folder",
                onClick = onCreateNew,
            )

            if (folders.isEmpty()) {
                Text(
                    text = "No folders yet.",
                    style = TideTypography.bodyMedium,
                    color = palette.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            } else {
                folders.forEach { folder ->
                    val count = folder.appKeys.size
                    PickerRow(
                        icon = Icons.Rounded.Folder,
                        label = folder.name + if (count == 1) " · 1 app" else " · $count apps",
                        onClick = { onPickExisting(folder) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val palette = LocalOceanPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = TideTypography.bodyLarge,
            color = palette.onGlass,
        )
    }
}