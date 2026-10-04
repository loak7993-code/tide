package app.tide.launcher

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import app.tide.launcher.core.launchComponent
import app.tide.launcher.debug.logi
import app.tide.launcher.ui.LauncherViewModel
import app.tide.launcher.ui.TideLauncherScreen

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The ocean background reaches the very edge of the display, so the
        // window must not reserve system bar insets itself; the Compose tree
        // consumes them instead.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TideLauncherScreen(viewModel = viewModel)
        }

        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Pressing HOME re-delivers MAIN/HOME to the existing instance rather
        // than creating a new one, so anything requested has to be read here.
        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Packages can be installed or removed while the launcher sits behind
        // another app.
        viewModel.refresh()
    }

    /** Honours `app.tide.launcher.action.LAUNCH_APP`, if another app sent one. */
    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.action != ACTION_LAUNCH_APP) return
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val cls = intent.getStringExtra(EXTRA_CLASS) ?: return

        logi("activity", "external launch request for $pkg")
        runCatching {
            launchComponent(ComponentName(pkg, cls))
        }.onFailure { logi("activity", "external launch failed for $pkg") }

        // Consumed, so a configuration change does not relaunch the app.
        intent.action = null
    }

    companion object {
        const val ACTION_LAUNCH_APP = "app.tide.launcher.action.LAUNCH_APP"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_CLASS = "class"
    }
}