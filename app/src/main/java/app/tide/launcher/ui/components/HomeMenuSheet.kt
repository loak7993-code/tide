package app.tide.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Radius
import app.tide.launcher.ui.theme.TideTypography

const val TEST_TAG_HOME_MENU_SETTINGS = "home_menu_settings"
const val TEST_TAG_HOME_MENU_WIDGET = "home_menu_widget"

/**
 * Menu shown when the user long-presses empty space on the home screen.
 *
 * This is the same affordance stock launchers offer, and it is the only route to
 * the widget picker that does not require something to already be pinned to the
 * dock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMenuSheet(
    onDismiss: () -> Unit,
    onAddWidget: () -> Unit,
    onOpenSettings: () -> Unit,
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
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = "Home screen",
                style = TideTypography.titleLarge,
                color = palette.onGlass,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
            )
            Spacer(Modifier.padding(top = 8.dp))

            HomeMenuRow(
                icon = Icons.Rounded.Add,
                label = "Add widget",
                tag = TEST_TAG_HOME_MENU_WIDGET,
                onClick = onAddWidget,
            )
            HomeMenuRow(
                icon = Icons.Rounded.Settings,
                label = "Home screen settings",
                tag = TEST_TAG_HOME_MENU_SETTINGS,
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun HomeMenuRow(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    val palette = LocalOceanPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
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
