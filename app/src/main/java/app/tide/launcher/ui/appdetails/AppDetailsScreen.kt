package app.tide.launcher.ui.appdetails

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tide.launcher.R
import app.tide.launcher.core.AppDetails
import app.tide.launcher.core.AppDetailsRepository
import app.tide.launcher.core.PermissionRow
import app.tide.launcher.data.AppEntry
import app.tide.launcher.data.IconCache
import app.tide.launcher.data.IconShape
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Radius
import app.tide.launcher.ui.theme.TideTypography
import java.util.Locale

const val TEST_TAG_APP_DETAILS = "app_details"
const val TEST_TAG_APP_DETAILS_BACK = "app_details_back"

/**
 * Per-app settings, in the launcher rather than out in the system Settings app.
 *
 * The point is that everything about one app is reachable without leaving the
 * home screen: what it asked for and what it was granted, whether it is
 * allowed to post notifications, which link handlers it registers, and the
 * destructive actions kept away from a casual tap.
 *
 * Rows that the platform will only let the Settings app perform — changing a
 * permission, granting notifications, choosing a default handler — deliberately
 * hand off to the system screen for that one action. Duplicating them here
 * would either fail silently or, worse, appear to work.
 */
@Composable
fun AppDetailsScreen(
    entry: AppEntry,
    iconShape: IconShape,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOceanPalette.current
    val context = LocalContext.current

    // PackageManager reads walk every package; keep them off the main thread.
    val details by produceState<AppDetails?>(initialValue = null, entry.key) {
        value = withContext(Dispatchers.IO) { AppDetailsRepository.load(context, entry) }
    }

    // Opaque: this page floats over the live home screen, and without a
    // backdrop of its own every row collides with an app icon underneath.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    // Fully opaque: at 0.97 the home screen's clock still ghosts through.
                    palette.gradientStops,
                ),
            )
            .padding(top = contentPadding.calculateTopPadding())
            .testTag(TEST_TAG_APP_DETAILS),
    ) {
        Header(entry = entry, details = details, onBack = onBack)

        if (details == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.appdetails_unavailable),
                    style = TideTypography.bodyLarge,
                    color = palette.onSurfaceMuted,
                )
            }
            return@Column
        }

        val d = details!!
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "quick") {
                QuickActions(
                    details = d,
                    onNotifications = { AppDetailsRepository.openNotificationSettings(context, entry.packageName) },
                    onPermissions = { AppDetailsRepository.openPermissions(context, entry.packageName) },
                    onDefaults = { AppDetailsRepository.openDefaultAppSettings(context) },
                    onLanguage = { AppDetailsRepository.openLanguageSettings(context, entry.packageName) },
                    onAppInfo = { AppDetailsRepository.openAppInfo(context, entry.packageName) },
                )
            }

            item(key = "sec_summary") {
                SectionLabel(stringResource(R.string.appdetails_details))
            }
            item(key = "summary") {
                GlassCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        DetailRow(stringResource(R.string.appdetails_package), d.packageName)
                        DetailRow(stringResource(R.string.appdetails_version), "${d.versionName} (${d.versionCode})")
                        DetailRow(stringResource(R.string.appdetails_target_sdk), targetSdkLabel(d.targetSdk))
                        DetailRow(stringResource(R.string.appdetails_apk_size), formatBytes(d.apkSizeBytes))
                        DetailRow(
                            stringResource(R.string.appdetails_installed),
                            "${formatDate(d.firstInstall)} · ${formatDate(d.lastUpdate)}",
                        )
                        DetailRow(
                            stringResource(R.string.appdetails_type),
                            stringResource(
                                if (d.isSystem) R.string.appdetails_system else R.string.appdetails_user,
                            ),
                        )
                    }
                }
            }

            if (d.permissions.isNotEmpty()) {
                item(key = "sec_perms") {
                    SectionLabel(
                        stringResource(
                            R.string.appdetails_permissions,
                            d.permissions.count { it.granted },
                            d.permissions.size,
                        ),
                    )
                }
                item(key = "perms") {
                    GlassCard {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            d.permissions.forEach { row ->
                                PermissionItem(row)
                            }
                        }
                    }
                }
            }

            item(key = "sec_danger") {
                SectionLabel(stringResource(R.string.appdetails_manage))
            }
            item(key = "danger") {
                GlassCard {
                    Row(Modifier.fillMaxWidth()) {
                        ActionTile(
                            icon = Icons.Rounded.Delete,
                            label = stringResource(R.string.action_uninstall),
                            tint = palette.onSurfaceMuted,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                AppDetailsRepository.requestUninstall(context, entry.packageName)
                                onClose()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    entry: AppEntry,
    details: AppDetails?,
    onBack: () -> Unit,
) {
    val palette = LocalOceanPalette.current
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(
        initialValue = IconCache.peek(entry.component),
        key1 = entry.component,
    ) {
        if (value == null) value = IconCache.load(context, entry.component)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .testTag(TEST_TAG_APP_DETAILS_BACK)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = palette.onGlass)
            }
            Spacer(Modifier.width(8.dp))
            icon?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(percent = 24)),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = details?.label ?: entry.label,
                    style = TideTypography.headlineSmall,
                    color = palette.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = entry.packageName,
                    style = TideTypography.bodySmall,
                    color = palette.onSurfaceMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun QuickActions(
    details: AppDetails,
    onNotifications: () -> Unit,
    onPermissions: () -> Unit,
    onDefaults: () -> Unit,
    onLanguage: () -> Unit,
    onAppInfo: () -> Unit,
) {
    val palette = LocalOceanPalette.current
    GlassCard {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row {
                ActionTile(
                    icon = Icons.Rounded.Notifications,
                    label = stringResource(R.string.appdetails_notifications),
                    tint = if (details.notificationsEnabled) palette.accent else palette.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                    onClick = onNotifications,
                )
                ActionTile(
                    icon = Icons.Rounded.Security,
                    label = stringResource(R.string.appdetails_permissions_short),
                    tint = palette.accent,
                    modifier = Modifier.weight(1f),
                    onClick = onPermissions,
                )
                ActionTile(
                    icon = Icons.Rounded.OpenInNew,
                    label = stringResource(R.string.appdetails_links),
                    tint = palette.accent,
                    modifier = Modifier.weight(1f),
                    onClick = onDefaults,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row {
                ActionTile(
                    icon = Icons.Rounded.Language,
                    label = stringResource(R.string.appdetails_language),
                    tint = palette.accent,
                    modifier = Modifier.weight(1f),
                    onClick = onLanguage,
                )
                ActionTile(
                    icon = Icons.Rounded.Key,
                    label = stringResource(R.string.appdetails_appinfo),
                    tint = palette.accent,
                    modifier = Modifier.weight(1f),
                    onClick = onAppInfo,
                )
            }
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = TideTypography.labelSmall,
            color = LocalOceanPalette.current.onGlass,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionItem(row: PermissionRow) {
    val palette = LocalOceanPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (row.granted) palette.accent else palette.onSurfaceMuted),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.group,
                style = TideTypography.bodyLarge,
                color = palette.onGlass,
            )
            // The raw name only earns its line when the grouped label would
            // hide which permission this actually is.
            if (row.critical && !row.name.equals(row.group, ignoreCase = true)) {
                Text(
                    text = row.name.lowercase(Locale.ROOT)
                        .replaceFirstChar { it.titlecase(Locale.ROOT) },
                    style = TideTypography.bodySmall,
                    color = palette.onSurfaceMuted,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = stringResource(
                if (row.granted) R.string.appdetails_granted else R.string.appdetails_denied,
            ),
            style = TideTypography.labelLarge,
            color = if (row.granted) palette.accent else palette.onSurfaceMuted,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val palette = LocalOceanPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = TideTypography.bodyMedium,
            color = palette.onSurfaceMuted,
            modifier = Modifier.width(104.dp),
        )
        Text(
            text = value,
            style = TideTypography.bodyMedium,
            color = palette.onGlass,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val palette = LocalOceanPalette.current
    Text(
        text = text.uppercase(),
        style = TideTypography.labelSmall,
        color = palette.onSurfaceMuted,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
    )
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(
                Brush.verticalGradient(
                    listOf(
                        LocalOceanPalette.current.glass,
                        LocalOceanPalette.current.glass.copy(alpha = 0.7f),
                    ),
                ),
            )
            .padding(horizontal = 4.dp),
    ) {
        content()
    }
}

/**
 * AOSP packages carry sentinel target SDKs (`1000` means "internal"), which is
 * true but reads as a typo next to an API number.
 */
private fun targetSdkLabel(sdk: Int): String = when (sdk) {
    in 1..100 -> "API $sdk"
    in 1000..1002 -> "System image"
    else -> "API $sdk"
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun formatDate(millis: Long): String = if (millis <= 0L) {
    "—"
} else {
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(millis)
}
