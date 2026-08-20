package im.autonova.mobile

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentTest {
    @Test fun accepts_text_and_stream_share_payloads_only() {
        val text = sharedAgentContentFromIntent(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Research this"))
        assertEquals("Research this", text?.text)
        val uri = Uri.parse("content://example.provider/file.pdf")
        val stream = sharedAgentContentFromIntent(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri))
        assertEquals(uri, stream?.uri)
        assertEquals("application/pdf", stream?.mimeType)
        assertNull(sharedAgentContentFromIntent(Intent(Intent.ACTION_VIEW, uri)))
        assertTrue(text?.uri == null)
    }
}
