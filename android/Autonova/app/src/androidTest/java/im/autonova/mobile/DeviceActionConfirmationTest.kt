package im.autonova.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import im.autonova.mobile.ui.ConfirmDialog
import org.junit.Rule
import org.junit.Test

class DeviceActionConfirmationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun local_device_actions_require_visible_confirmation() {
        compose.setContent { ConfirmDialog("Share local file?", "Share the selected document with another app?", "Share", {}, {}) }
        compose.onNodeWithText("Share local file?").assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
}
