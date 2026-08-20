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

    @Test fun bottom_navigation_opens_task_surface() {
        compose.onNodeWithText("Tasks").performClick()
        compose.onNodeWithText("Create protected tasks, then observe planning, execution, verification, and completion from the same workspace.").assertIsDisplayed()
    }
}
