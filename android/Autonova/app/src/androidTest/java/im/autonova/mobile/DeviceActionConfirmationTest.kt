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
import im.autonova.mobile.ui.StorageManagementControls
import im.autonova.mobile.data.LocalKnowledgeUsage
import org.junit.Rule
import org.junit.Test

class DeviceActionConfirmationTest {
    @get:Rule val compose = createComposeRule()

    private fun document() = LocalDocument(Uri.parse("content://test/document"), "draft.txt", "text/plain", 12)
    private fun render(action: LocalStorageAction) = compose.setContent { LocalStorageActionConfirmation(action, "draft", {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}) }

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
    @Test fun local_edit_action_requires_visible_confirmation() { render(LocalStorageAction.Edit(document())); compose.onNodeWithText("Edit local file?").assertIsDisplayed() }
    @Test fun local_save_action_requires_visible_confirmation() { render(LocalStorageAction.SaveEdit(document(), "revised")); compose.onNodeWithText("Save local edits?").assertIsDisplayed() }
    @Test fun local_export_action_requires_visible_confirmation() { render(LocalStorageAction.ExportCopy(document())); compose.onNodeWithText("Export a local copy?").assertIsDisplayed() }
    @Test fun local_archive_action_requires_visible_confirmation() { render(LocalStorageAction.Archive(document())); compose.onNodeWithText("Create local archive?").assertIsDisplayed() }
    @Test fun local_index_and_activity_clear_actions_require_visible_confirmation() { render(LocalStorageAction.ClearLocalIndex); compose.onNodeWithText("Clear local document index?").assertIsDisplayed(); render(LocalStorageAction.ClearActivityCache); compose.onNodeWithText("Clear local activity cache?").assertIsDisplayed() }

    @Test fun files_storage_controls_route_every_local_action_through_confirmation() {
        compose.setContent {
            Column {
                FilesAndStorageActionControls(null, true, onActionRequested = {})
                FilesAndStorageActionControls(document(), true, onActionRequested = {}, onEdit = {}, onExport = {}, onArchive = {})
                StorageManagementControls(LocalKnowledgeUsage(4, 2, 2048), onActionRequested = {})
            }
        }
        compose.onNodeWithText("Create note").assertIsDisplayed()
        compose.onNodeWithText("Open").assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsDisplayed()
        compose.onNodeWithText("Edit").assertIsDisplayed()
        compose.onNodeWithText("Export copy").assertIsDisplayed()
        compose.onNodeWithText("Archive").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNodeWithText("Clear local index").assertIsDisplayed()
        compose.onNodeWithText("Clear activity cache").assertIsDisplayed()
    }
}
