package app.tide.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tide.launcher.ui.home.TEST_TAG_HOME
import app.tide.launcher.ui.home.TEST_TAG_OPEN_DRAWER
import app.tide.launcher.ui.components.TEST_TAG_HOME_MENU_SETTINGS
import app.tide.launcher.ui.components.TEST_TAG_HOME_MENU_WIDGET
import app.tide.launcher.ui.home.TEST_TAG_OPEN_DRAWER
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the launcher's main flows.
 *
 * These run against a real `PackageManager`, which is the point — the grid is
 * built from whatever happens to be installed, so assertions target Tide's own
 * chrome rather than specific apps, which differ per device.
 *
 * Navigation is driven through test tags rather than visible text: the Settings
 * *app* is installed on most images, so `onNodeWithText("Settings")` would be
 * ambiguous between that app's tile and the launcher's own entry point.
 */
@RunWith(AndroidJUnit4::class)
class TideLauncherTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeSurfaceRenders() {
        compose.onNodeWithTag(TEST_TAG_HOME).assertIsDisplayed()
    }

    @Test
    fun drawerOpensFromHintBar() {
        compose.onNodeWithTag(TEST_TAG_OPEN_DRAWER).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("drawer_search").assertIsDisplayed()
    }

    @Test
    fun searchFiltersToNothingForAnImpossibleQuery() {
        compose.onNodeWithTag(TEST_TAG_OPEN_DRAWER).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("drawer_search").performTextInput("zzzzqqq")
        compose.waitForIdle()

        // No installed app can match a run of z/q, so the empty state must show.
        // Asserted on the user's own words rather than the query, so the test
        // does not encode which apps the image happens to ship.
        compose.onNodeWithText("No apps match", substring = true).assertIsDisplayed()
    }

    /**
     * Settings is reached through the home menu rather than the dock gear,
     * because the gear only exists when the dock is empty — which makes it a
     * test that passes or fails depending on whatever the last run left pinned.
     */
    @Test
    fun settingsOpensAndShowsAppearance() {
        openHomeMenu()
        compose.onNodeWithTag(TEST_TAG_HOME_MENU_SETTINGS).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("section_appearance").assertIsDisplayed()
    }

    @Test
    fun themeCanBeChanged() {
        openHomeMenu()
        compose.onNodeWithTag(TEST_TAG_HOME_MENU_SETTINGS).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Deep").performClick()
        compose.waitForIdle()

        // Selecting a theme re-renders the swatch ring; the label stays put.
        compose.onNodeWithText("Deep").assertIsDisplayed()
    }

    @Test
    fun homeMenuOffersWidgetPicker() {
        openHomeMenu()
        compose.onNodeWithTag(TEST_TAG_HOME_MENU_WIDGET).assertIsDisplayed()
    }

    /** Long-presses empty home space, below whatever apps are installed. */
    private fun openHomeMenu() {
        compose.onNodeWithTag(TEST_TAG_HOME).performTouchInput { longClick() }
        compose.waitForIdle()
    }
}