package app.tide.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import app.tide.launcher.ui.components.GlassSurface
import app.tide.launcher.ui.theme.LocalOceanPalette
import app.tide.launcher.ui.theme.Radius
import app.tide.launcher.ui.theme.TideTypography

private const val MIN_WIDGET_DP = 40
private const val MAX_WIDGET_DP = 320

/**
 * The home-screen widget strip.
 *
 * Widgets are rendered through [AndroidView] because `AppWidgetHostView` is a
 * real View that hosts each provider's own `RemoteViews`, and a Compose widget
 * cannot be substituted for it — the provider decides what to draw.
 */
@Composable
fun WidgetArea(
    widgetIds: List<Int>,
    controller: WidgetHostController,
    modifier: Modifier = Modifier,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
) {
    if (widgetIds.isEmpty()) return

    val context = LocalContext.current

    Column(modifier.fillMaxWidth()) {
        widgetIds.forEach { appWidgetId ->
            // Re-resolved per id so a widget deleted from Settings elsewhere
            // stops rendering instead of leaving a blank frame behind.
            val info by produceState<AppWidgetProviderInfo?>(initialValue = null, appWidgetId) {
                value = withContext(Dispatchers.IO) {
                    AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)
                }
            }

            val providerInfo = info
            if (providerInfo != null) {
                // An AppWidgetHostView measures to zero unless it is given an
                // explicit height: it reports no intrinsic size, so without
                // this the slot collapses and the widget silently never draws.
                val heightDp = remember(providerInfo) {
                    providerInfo.minHeight.coerceIn(MIN_WIDGET_DP, MAX_WIDGET_DP)
                }
                AndroidView(
                    factory = { ctx -> WidgetHostController.viewFor(controller, ctx, appWidgetId)!! },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heightDp.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(Radius.md)),
                )
            }
        }
    }
}

/** One tappable row in the widget picker. */
@Composable
private fun ProviderRow(
    provider: AppWidgetProviderInfo,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalOceanPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = provider.label(context),
            style = TideTypography.bodyLarge,
            color = palette.onGlass,
        )
    }
}

/**
 * Picker listing every widget provider installed on the device.
 *
 * Presented as a sheet rather than a separate screen so adding a widget never
 * leaves the home screen the user is arranging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    controller: WidgetHostController,
    onDismiss: () -> Unit,
    onPicked: (AppWidgetProviderInfo) -> Unit,
) {
    val palette = LocalOceanPalette.current
    val providers = rememberAvailableWidgets(controller)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Add widget",
                    style = TideTypography.titleLarge,
                    color = palette.onGlass,
                    modifier = Modifier.weight(1f),
                )
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

            if (providers.isEmpty()) {
                Text(
                    text = "No widgets installed on this device.",
                    style = TideTypography.bodyLarge,
                    color = palette.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                providers.forEach { provider -> ProviderRow(provider) { onPicked(provider) } }
            }
        }
    }
}