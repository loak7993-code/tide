package app.tide.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.tide.launcher.data.AppEntry
import app.tide.launcher.ui.components.AppMenuSheet
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Radius

/**
 * Wraps [AppMenuSheet] in a Material bottom sheet whose surface is translucent
 * rather than Material's opaque background, so the water stays visible behind
 * the options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuHost(
    entry: AppEntry,
    state: LauncherUiState,
    onDismiss: () -> Unit,
    onToggleDock: () -> Unit,
    onToggleHide: () -> Unit,
    onAddToFolder: () -> Unit,
    onAppSettings: () -> Unit,
) {
    val palette = LocalOceanPalette.current

    val sheetState = rememberModalBottomSheetState(
        // The menu is a fixed set of rows; a half-expanded state would only add
        // a second, uglier resting position.
        skipPartiallyExpanded = true,
    )

    val shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.gradientStops[1].copy(alpha = 0.88f),
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = { SheetGrip() },
        shape = shape,
    ) {
        AppMenuSheet(
            entry = entry,
            iconShape = state.settings.iconShape,
            isDocked = state.settings.dockedApps.contains(entry.key),
            isHidden = state.settings.hiddenApps.contains(entry.key),
            onDismiss = onDismiss,
            onToggleDock = onToggleDock,
            onToggleHide = onToggleHide,
            onAddToFolder = onAddToFolder,
            onAppSettings = onAppSettings,
        )
    }
}

/** The grab bar, tinted to the theme. */
@Composable
private fun SheetGrip() {
    val palette = LocalOceanPalette.current
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .fillMaxWidth(0.12f)
                .height(4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(palette.onSurfaceMuted.copy(alpha = 0.45f)),
        )
    }
}