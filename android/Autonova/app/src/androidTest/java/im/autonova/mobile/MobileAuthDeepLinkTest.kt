package im.autonova.mobile

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileAuthDeepLinkTest {
    @Test fun accepts_only_the_expected_autonova_auth_deep_link() {
        assertEquals("one-time-code", mobileAuthCodeFromIntent(Intent(Intent.ACTION_VIEW, Uri.parse("autonova://auth?code=one-time-code"))))
        assertNull(mobileAuthCodeFromIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/auth?code=one-time-code"))))
        assertNull(mobileAuthCodeFromIntent(Intent(Intent.ACTION_VIEW, Uri.parse("autonova://other?code=one-time-code"))))
    }
}
