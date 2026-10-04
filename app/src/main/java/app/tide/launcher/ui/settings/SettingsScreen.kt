package app.tide.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tide.launcher.R
import app.tide.launcher.data.AppEntry
import app.tide.launcher.data.IconShape
import app.tide.launcher.data.TideSettings
import app.tide.launcher.ui.components.GlassSurface
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Radius
import app.tide.launcher.ui.theme.TideTheme
import app.tide.launcher.ui.theme.TideTypography

/**
 * Settings as a full-screen overlay rather than a separate activity.
 *
 * A launcher cannot afford to leave its own process to change a toggle — the
 * home screen has to stay resident or the device shows the stock launcher while
 * the user is still in ours.
 */
@Composable
fun SettingsScreen(
    settings: TideSettings,
    hiddenApps: List<AppEntry>,
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    onThemeChange: (TideTheme) -> Unit,
    onIconShapeChange: (IconShape) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onOceanMotionChange: (Boolean) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onDoubleTapChange: (Boolean) -> Unit,
    onDockCapacityChange: (Int) -> Unit,
    onDebugOverlayChange: (Boolean) -> Unit,
    onUnhide: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOceanPalette.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 18.dp,
            end = 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            SettingsHeader(onClose = onClose)
        }

        // ── appearance ──────────────────────────────────────────────────────
        item(key = "sec_appearance") {
            SectionLabel(stringResource(R.string.section_appearance))
        }

        item(key = "theme") {
            GlassCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.setting_theme),
                        style = TideTypography.titleMedium,
                        color = palette.onGlass,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(TideTheme.entries.size) { index ->
                            val theme = TideTheme.entries[index]
                            ThemeSwatch(
                                theme = theme,
                                selected = settings.theme == theme,
                                onClick = { onThemeChange(theme) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "icon_shape") {
            GlassCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.setting_icon_shape),
                        style = TideTypography.titleMedium,
                        color = palette.onGlass,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconShape.entries.forEach { shape ->
                            ShapeOption(
                                shape = shape,
                                selected = settings.iconShape == shape,
                                onClick = { onIconShapeChange(shape) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "columns") {
            GlassCard {
                StepperRow(
                    title = stringResource(R.string.setting_grid_columns),
                    value = settings.gridColumns.toString(),
                    onDecrement = {
                        onColumnsChange(settings.gridColumns - 1)
                    },
                    onIncrement = { onColumnsChange(settings.gridColumns + 1) },
                    canDecrement = settings.gridColumns > TideSettings.ColumnRange.first,
                    canIncrement = settings.gridColumns < TideSettings.ColumnRange.last,
                )
            }
        }

        item(key = "dock_capacity") {
            GlassCard {
                StepperRow(
                    title = stringResource(R.string.setting_dock_count),
                    value = settings.dockCapacity.toString(),
                    onDecrement = { onDockCapacityChange(settings.dockCapacity - 1) },
                    onIncrement = { onDockCapacityChange(settings.dockCapacity + 1) },
                    canDecrement = settings.dockCapacity > 3,
                    canIncrement = settings.dockCapacity < 7,
                )
            }
        }

        item(key = "labels") {
            GlassCard {
                SwitchRow(
                    title = stringResource(R.string.setting_hide_labels),
                    summary = stringResource(R.string.setting_hide_labels_summary),
                    checked = settings.showLabels,
                    onCheckedChange = onShowLabelsChange,
                )
            }
        }

        // ── behaviour ───────────────────────────────────────────────────────
        item(key = "sec_behaviour") {
            SectionLabel(stringResource(R.string.section_behaviour))
        }

        item(key = "motion") {
            GlassCard {
                SwitchRow(
                    title = stringResource(R.string.setting_wallpaper),
                    summary = stringResource(R.string.setting_wallpaper_summary),
                    checked = settings.oceanMotion,
                    onCheckedChange = onOceanMotionChange,
                )
            }
        }

        item(key = "blur") {
            GlassCard {
                SwitchRow(
                    title = stringResource(R.string.setting_blur),
                    summary = null,
                    checked = settings.blurPanels,
                    onCheckedChange = onBlurChange,
                )
            }
        }

        item(key = "double_tap") {
            GlassCard {
                SwitchRow(
                    title = stringResource(R.string.setting_double_tap),
                    summary = stringResource(R.string.setting_double_tap_summary),
                    checked = settings.doubleTapToSearch,
                    onCheckedChange = onDoubleTapChange,
                )
            }
        }

        item(key = "debug") {
            GlassCard {
                SwitchRow(
                    title = stringResource(R.string.setting_debug_overlay),
                    summary = stringResource(R.string.setting_debug_overlay_summary),
                    checked = settings.debugOverlay,
                    onCheckedChange = onDebugOverlayChange,
                )
            }
        }

        // ── hidden apps ──────────────────────────────────────────────────────
        if (hiddenApps.isNotEmpty()) {
            item(key = "sec_hidden") {
                SectionLabel("Hidden apps")
            }
            item(key = "hidden_list") {
                GlassCard {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        hiddenApps.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = entry.label,
                                    style = TideTypography.bodyLarge,
                                    color = palette.onGlass,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "Show",
                                    style = TideTypography.labelLarge,
                                    color = palette.accent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.pill))
                                        .clickable { onUnhide(entry) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(onClose: () -> Unit) {
    val palette = LocalOceanPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = TideTypography.headlineMedium,
            color = palette.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.glassBorder.copy(alpha = 0.18f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close),
                tint = palette.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val palette = LocalOceanPalette.current
    Text(
        text = text.uppercase(),
        style = TideTypography.labelSmall,
        color = palette.onSurfaceMuted,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
    ) {
        Box(Modifier.padding(4.dp)) { content() }
    }
}

@Composable
private fun ThemeSwatch(
    theme: TideTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalOceanPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 62.dp, height = 62.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(theme.palette.gradientStops),
                )
                .then(
                    if (selected) {
                        Modifier.border(2.5.dp, palette.accent, CircleShape)
                    } else {
                        Modifier.border(1.dp, palette.glassBorder.copy(alpha = 0.5f), CircleShape)
                    },
                )
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = theme.displayName,
            style = TideTypography.labelSmall,
            color = if (selected) palette.accent else palette.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShapeOption(
    shape: IconShape,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalOceanPalette.current
    val preview = when (shape) {
        IconShape.Squircle -> RoundedCornerShape(percent = 34)
        IconShape.Circle -> CircleShape
        IconShape.Rounded -> RoundedCornerShape(percent = 16)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(preview)
                .background(palette.accent.copy(alpha = if (selected) 0.9f else 0.35f))
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = shape.displayName,
            style = TideTypography.labelSmall,
            color = if (selected) palette.accent else palette.onSurfaceMuted,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalOceanPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = TideTypography.bodyLarge, color = palette.onGlass)
            if (summary != null) {
                Text(
                    text = summary,
                    style = TideTypography.bodyMedium,
                    color = palette.onSurfaceMuted,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.accent,
                uncheckedThumbColor = palette.onSurfaceMuted,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = palette.glassBorder,
            ),
        )
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    canDecrement: Boolean,
    canIncrement: Boolean,
) {
    val palette = LocalOceanPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TideTypography.bodyLarge,
            color = palette.onGlass,
            modifier = Modifier.weight(1f),
        )
        StepperButton(
            icon = Icons.Rounded.Remove,
            enabled = canDecrement,
            onClick = onDecrement,
        )
        Text(
            text = value,
            style = TideTypography.titleMedium,
            color = palette.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        StepperButton(
            icon = Icons.Rounded.Add,
            enabled = canIncrement,
            onClick = onIncrement,
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalOceanPalette.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                if (enabled) palette.glassBorder.copy(alpha = 0.2f)
                else palette.glassBorder.copy(alpha = 0.08f),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) palette.onGlass else palette.onSurfaceMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}