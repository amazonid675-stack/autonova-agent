package im.autonova.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import im.autonova.mobile.MainActivity
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun fresh_launch_explains_the_local_work_path_and_a_ready_command_surface() {
        compose.onNodeWithText("Start working locally").assertIsDisplayed()
        compose.onNodeWithText("Plan my day").assertIsDisplayed()
        compose.onNodeWithText("Create local task").assertIsDisplayed()
        compose.onNodeWithText("Give Autonova a task or question").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed()
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

    @Test fun command_center_and_device_capabilities_expose_visible_permission_boundaries() {
        compose.onNodeWithText("Device inputs").assertIsDisplayed()
        compose.onNodeWithText("Voice").assertIsDisplayed()
        compose.onNodeWithText("Camera").assertIsDisplayed()
        compose.onNodeWithText("Screenshot").assertIsDisplayed()
        compose.onNodeWithText("Clipboard").assertIsDisplayed()

        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Device capabilities").performClick()
        compose.onNodeWithText("Task completion notifications").assertIsDisplayed()
        compose.onNodeWithText("On-device local model").assertIsDisplayed()
        compose.onNodeWithText("Browser handoff").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sharing and clipboard").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Background learning review").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Safe automation").performScrollTo().assertIsDisplayed()
    }

    @Test fun more_menu_exposes_selected_source_research_and_reviewable_learning() {
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Research").performClick()
        compose.onNodeWithText("Public HTTPS source URLs — one per line").assertIsDisplayed()
        compose.onNodeWithText("Search web").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Learning & autonomy").performClick()
        compose.onNodeWithText("Propose a memory").assertIsDisplayed()
        compose.onNodeWithText("Capability grants").assertIsDisplayed()
    }

    @Test fun more_menu_exposes_github_confirmation_queue() {
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("GitHub").performScrollTo().performClick()
        compose.onNodeWithText("Connect GitHub").assertIsDisplayed()
        compose.onNodeWithText("Fine-grained GitHub token").assertIsDisplayed()
    }
}
