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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tide.launcher.R
import app.tide.launcher.core.AppShortcuts
import app.tide.launcher.core.LauncherActions
import app.tide.launcher.data.AppEntry
import app.tide.launcher.data.IconCache
import app.tide.launcher.data.IconShape
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.TideTypography

private data class MenuAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The long-press sheet.
 *
 * Built from plain rows rather than `ListItem` so it can sit on the glass fill
 * without Material's opaque surface and dividers fighting the water behind it.
 */
@Composable
fun AppMenuSheet(
    entry: AppEntry,
    iconShape: IconShape,
    isDocked: Boolean,
    isHidden: Boolean,
    onDismiss: () -> Unit,
    onToggleDock: () -> Unit,
    onToggleHide: () -> Unit,
    onAddToFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalOceanPalette.current
    val scrollState = rememberScrollState()

    // Shortcuts are a binder query, so they arrive after the sheet is already
    // animating in rather than blocking it.
    val shortcuts by produceState(initialValue = emptyList<AppShortcuts.Shortcut>(), entry.key) {
        value = AppShortcuts.shortcutsFor(context, entry.packageName)
    }

    val icon = remember(entry.component) { IconCache.peek(entry.component) }

    val actions = listOf(
        MenuAction(
            icon = Icons.Rounded.PushPin,
            label = stringResource(
                if (isDocked) R.string.action_remove_from_dock else R.string.action_add_to_dock,
            ),
            onClick = { onToggleDock(); onDismiss() },
        ),
        MenuAction(
            icon = Icons.Rounded.CreateNewFolder,
            label = "Add to folder",
            onClick = { onAddToFolder() },
        ),
        MenuAction(
            icon = if (isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            label = stringResource(if (isHidden) R.string.action_unhide else R.string.action_hide),
            onClick = { onToggleHide(); onDismiss() },
        ),
        MenuAction(
            icon = Icons.Rounded.Info,
            label = stringResource(R.string.action_app_info),
            onClick = { LauncherActions.openAppInfo(context, entry); onDismiss() },
        ),
        MenuAction(
            icon = Icons.Rounded.Delete,
            label = stringResource(R.string.action_uninstall),
            destructive = true,
            onClick = { LauncherActions.requestUninstallOrAppInfo(context, entry); onDismiss() },
        ),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        // ── header ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                icon = icon,
                shape = iconShape.toShape(),
                size = 52.dp,
                pressed = false,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = entry.label,
                    style = TideTypography.titleLarge,
                    color = palette.onGlass,
                    maxLines = 1,
                )
                Text(
                    text = entry.packageName,
                    style = TideTypography.bodyMedium,
                    color = palette.onSurfaceMuted,
                    maxLines = 1,
                )
            }
        }

        // ── shortcuts ────────────────────────────────────────────────────────
        if (shortcuts.isNotEmpty()) {
            SheetDivider()
            Text(
                text = stringResource(R.string.action_shortcuts).uppercase(),
                style = TideTypography.labelSmall,
                color = palette.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            shortcuts.forEach { shortcut ->
                MenuRow(
                    icon = Icons.Rounded.Bolt,
                    label = shortcut.label,
                    onClick = {
                        AppShortcuts.launch(context, shortcut)
                        onDismiss()
                    },
                )
            }
        }

        // ── actions ──────────────────────────────────────────────────────────
        SheetDivider()
        actions.forEach { action ->
            MenuRow(
                icon = action.icon,
                label = action.label,
                destructive = action.destructive,
                onClick = action.onClick,
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalOceanPalette.current
    val tint = if (destructive) MaterialTheme.colorScheme.error else palette.onGlass

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = TideTypography.bodyLarge,
            color = tint,
        )
    }
}

@Composable
private fun SheetDivider() {
    val palette = LocalOceanPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.glassBorder.copy(alpha = 0.5f)),
    )
}

/** Circular glyph used by the drawer empty state and the search affordance. */
@Composable
fun GlassGlyph(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    tint: Color? = null,
    contentDescription: String? = null,
) {
    val palette = LocalOceanPalette.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.glassBorder.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: palette.onSurfaceMuted,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Centres short text inside a bounded box. */
@Composable
fun CenteredHint(text: String, modifier: Modifier = Modifier) {
    val palette = LocalOceanPalette.current
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = TideTypography.bodyLarge,
            color = palette.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}