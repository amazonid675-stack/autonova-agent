package im.autonova.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import im.autonova.mobile.MainActivity
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun fresh_launch_offers_secure_sign_in_and_a_ready_command_surface() {
        compose.onNodeWithText("Connect your agent").assertIsDisplayed()
        compose.onNodeWithText("Connect Autonova").assertIsDisplayed()
        compose.onNodeWithText("Give Autonova a task or question…").assertIsDisplayed()
    }

    @Test fun bottom_navigation_opens_task_surface() {
        compose.onNodeWithText("Tasks").performClick()
        compose.onNodeWithText("Create protected tasks, then observe planning, execution, verification, and completion from the same workspace.").assertIsDisplayed()
    }

    @Test fun more_menu_opens_device_capability_controls() {
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Device capabilities").performClick()
        compose.onNodeWithText("Task completion notifications").assertIsDisplayed()
        compose.onNodeWithText("On-device local model").assertIsDisplayed()
    }
}
