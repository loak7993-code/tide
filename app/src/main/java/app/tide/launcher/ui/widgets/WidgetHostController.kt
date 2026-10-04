package app.tide.launcher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import app.tide.launcher.debug.logw
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Everything needed to be a home-screen widget host.
 *
 * A launcher cannot reuse another app's `AppWidgetHost`: the framework delivers
 * `onUpdate` and `onAppWidgetOptionsChanged` only to the host that allocated
 * the id, and requires a distinct host id per host. Tide allocates its own and
 * persists it, because a host that forgot its id would silently stop receiving
 * updates for every widget the user had placed.
 */
class WidgetHostController(
    context: Context,
    hostId: Int,
) {
    private val appContext = context.applicationContext
    internal val manager = AppWidgetManager.getInstance(appContext)
    internal val host = AppWidgetHost(appContext, hostId)

    /** Providers the user can choose from, in a stable, human order. */
    fun availableProviders(): List<AppWidgetProviderInfo> = runCatching {
        manager.installedProviders
    }.getOrDefault(emptyList())
        .sortedBy { it.label(appContext).lowercase() }

    /**
     * Places [provider], returning the widget id it was allocated.
     *
     * ### Why this is not a straight bind
     *
     * `AppWidgetManager.bindAppWidgetIdIfAllowed` returns **false** for any
     * provider that declares a configuration activity — which is most of them.
     * AOSP treats an unconfigured widget as unusable and refuses the binding
     * outright, so the host has to run the provider's configuration flow via
     * [AppWidgetHost.startAppWidgetConfigureActivityForResult] instead; the bind
     * only completes when that activity returns `RESULT_OK`.
     *
     * Providers with nothing to configure bind normally.
     *
     * @param activity used to launch the configuration flow; required when the
     *   provider has one.
     * @return the allocated id, or -1 if the widget could not be placed. A
     *   provider with a configure activity returns its id optimistically — the
     *   final outcome arrives later in [onConfigureResult].
     */
    fun addWidget(provider: AppWidgetProviderInfo, activity: Activity?): Int {
        val appWidgetId = runCatching { host.allocateAppWidgetId() }.getOrNull() ?: return -1

        // Bind first. `startAppWidgetConfigureActivityForResult` throws
        // "Widget not bound" for an allocated-but-unbound id, so the order is
        // not interchangeable.
        val bound = runCatching {
            manager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider)
        }.getOrDefault(false)

        if (!bound) {
            // Observed on the API 35 emulator image: the platform refuses the
            // bind for every provider tried, so this is a host-level
            // restriction rather than a per-provider one.
            logw("widget", "bind refused for ${provider.provider}")
            runCatching { host.deleteAppWidgetId(appWidgetId) }
            return -1
        }

        if (provider.configure != null) {
            if (activity == null) {
                runCatching { host.deleteAppWidgetId(appWidgetId) }
                return -1
            }
            try {
                host.startAppWidgetConfigureActivityForResult(
                    activity, appWidgetId, 0, CONFIGURE_REQUEST_CODE, null,
                )
            } catch (e: Exception) {
                logw("widget", "configure flow failed for id=$appWidgetId: ${e.message}")
                runCatching { host.deleteAppWidgetId(appWidgetId) }
                return -1
            }
            return appWidgetId
        }

        runCatching { host.startListening() }
        return appWidgetId
    }

    /**
     * Called when the provider's configuration activity returns.
     *
     * @return true if the widget is now usable and should be kept.
     */
    fun onConfigureResult(succeeded: Boolean, appWidgetId: Int): Boolean {
        if (succeeded) {
            runCatching { host.startListening() }
            return true
        }
        deleteWidget(appWidgetId)
        return false
    }

    fun deleteWidget(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /** True while [appWidgetId] still resolves to a real provider. */
    fun isBound(appWidgetId: Int): Boolean =
        runCatching { manager.getAppWidgetInfo(appWidgetId) != null }.getOrDefault(false)

    fun startListening() {
        runCatching { host.startListening() }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    companion object {
        private const val PREFS = "tide_widget_host"
        private const val KEY_HOST_ID = "host_id"

        /** Request code Tide uses for the provider configuration flow. */
        const val CONFIGURE_REQUEST_CODE = 4201

        /**
         * Reads the persisted host id, minting one on first run. It has to be
         * stable for the process lifetime or previously placed widgets stop
         * receiving updates.
         *
         * `AppWidgetHost.generateHostId()` is `@hide`, so the id is minted
         * here instead. The framework only requires it to be a stable
         * positive int per host, which is what is persisted.
         */
        fun hostId(context: Context): Int {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getInt(KEY_HOST_ID, 0).takeIf { it != 0 }?.let { return it }
            val minted = Random.nextInt(1, Int.MAX_VALUE)
            prefs.edit { putInt(KEY_HOST_ID, minted) }
            return minted
        }

        /**
         * Builds the host view for [appWidgetId].
         *
         * `AppWidgetHostView`'s two-argument constructor is deprecated and the
         * public one needs a user id, which is exactly what `createView`
         * resolves for the current user.
         */
        fun viewFor(
            controller: WidgetHostController,
            context: Context,
            appWidgetId: Int,
        ): android.appwidget.AppWidgetHostView? = runCatching {
            val info = controller.manager.getAppWidgetInfo(appWidgetId) ?: return null
            controller.host.createView(context, appWidgetId, info)
        }.getOrNull()
    }
}

/**
 * A [WidgetHostController] bound to this composition.
 *
 * Listening is tied to the composition lifecycle: the host must listen while
 * on screen to receive `onUpdate`, and should stop when backgrounded or the
 * system holds a live binder for nothing.
 */
@Composable
fun rememberWidgetHost(): WidgetHostController {
    val context = LocalContext.current
    val controller = remember(context) {
        WidgetHostController(context, WidgetHostController.hostId(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, controller) {
        controller.startListening()
        onDispose { controller.stopListening() }
    }

    return controller
}

/**
 * Loads the providers the user can add.
 *
 * Queried off the main thread: `getInstalledProviders` walks every installed
 * package and reads each provider's `appWidgetProvider` XML, which is slow
 * enough to drop frames on a cold open.
 */
@Composable
fun rememberAvailableWidgets(controller: WidgetHostController): List<AppWidgetProviderInfo> {
    val result by produceState<List<AppWidgetProviderInfo>?>(initialValue = null, controller) {
        value = withContext(Dispatchers.IO) { controller.availableProviders() }
    }
    return result ?: emptyList()
}

/** Human label for a provider, falling back to its class name. */
fun AppWidgetProviderInfo.label(context: Context): String =
    runCatching { loadLabel(context.packageManager).toString() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: provider.shortClassName.substringAfterLast('.')