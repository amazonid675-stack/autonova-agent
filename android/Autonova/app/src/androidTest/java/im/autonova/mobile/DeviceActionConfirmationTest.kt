package im.autonova.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri
import im.autonova.mobile.data.LocalDocument
import im.autonova.mobile.ui.LocalStorageAction
import im.autonova.mobile.ui.LocalStorageActionConfirmation
import im.autonova.mobile.ui.FilesAndStorageActionControls
import org.junit.Rule
import org.junit.Test

class DeviceActionConfirmationTest {
    @get:Rule val compose = createComposeRule()

    private fun document() = LocalDocument(Uri.parse("content://test/document"), "draft.txt", "text/plain", 12)
    private fun render(action: LocalStorageAction) = compose.setContent { LocalStorageActionConfirmation(action, "draft", {}, {}, {}, {}, {}, {}) }

    @Test fun local_create_action_requires_visible_confirmation() {
        render(LocalStorageAction.CreateNote)
        compose.onNodeWithText("Create local note?").assertIsDisplayed()
        compose.onNodeWithText("Create").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test fun local_workspace_action_requires_visible_confirmation() { render(LocalStorageAction.CreateWorkspace("sample")); compose.onNodeWithText("Create local workspace?").assertIsDisplayed() }

    @Test fun local_open_action_requires_visible_confirmation() { render(LocalStorageAction.Open(document())); compose.onNodeWithText("Open local file?").assertIsDisplayed() }
    @Test fun local_share_action_requires_visible_confirmation() { render(LocalStorageAction.Share(document())); compose.onNodeWithText("Share local file?").assertIsDisplayed() }
    @Test fun local_delete_action_requires_visible_confirmation() { render(LocalStorageAction.Delete(document())); compose.onNodeWithText("Delete local file?").assertIsDisplayed() }

    @Test fun files_storage_controls_route_every_local_action_through_confirmation() {
        compose.setContent {
            Column {
                FilesAndStorageActionControls(null, true, onActionRequested = {})
                FilesAndStorageActionControls(document(), true, onActionRequested = {})
            }
        }
        compose.onNodeWithText("Create note").assertIsDisplayed()
        compose.onNodeWithText("Open").assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
    }
}
