package im.autonova.mobile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.autonova.mobile.data.SecureConfig
import im.autonova.mobile.data.OperatingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecureConfigInstrumentedTest {
    @Test fun encrypted_configuration_protects_bearer_sign_in_and_preserves_device_identity() {
        val config = SecureConfig(ApplicationProvider.getApplicationContext())
        config.clearSession()
        val firstId = config.deviceId()
        assertNotNull(firstId)
        assertEquals(firstId, config.deviceId())
        config.saveApiBaseUrl("https://agent.example.com")
        val token = "header-portion-long-enough.payload-portion-long-enough.signature-portion-long-enough"
        config.saveAccessToken(token)
        config.saveCodeVerifier("one-time-mobile-proof-with-at-least-thirty-two-characters")
        assertEquals("https://agent.example.com", config.apiBaseUrl())
        assertEquals(token, config.accessToken())
        assertTrue(config.isConfigured())
        assertEquals("one-time-mobile-proof-with-at-least-thirty-two-characters", config.consumeCodeVerifier())
        assertNull(config.consumeCodeVerifier())
        config.clearSession()
        assertFalse(config.isConfigured())
        assertNull(config.accessToken())
        assertEquals(firstId, config.deviceId())
        assertNotEquals("", config.deviceId())
        try { config.saveApiBaseUrl("http://localhost:3000"); fail("Unsafe endpoint should be rejected") } catch (_: IllegalArgumentException) { }
        try { config.saveAccessToken("access-token") ; fail("Invalid bearer token should be rejected") } catch (_: IllegalArgumentException) { }
        try { config.saveStorageTree("https://example.com/documents"); fail("Non-document URI should be rejected") } catch (_: IllegalArgumentException) { }
    }

    @Test fun encrypted_configuration_persists_private_local_model_choice_and_notification_preference() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = SecureConfig(context)
        val modelPath = File(context.filesDir, "models/test-local.task").absolutePath
        config.saveLocalModelPath(modelPath)
        config.setNotificationsEnabled(true)
        assertEquals(modelPath, config.localModelPath())
        assertTrue(config.notificationsEnabled())
        config.clearLocalModel()
        config.setNotificationsEnabled(false)
        assertNull(config.localModelPath())
        assertFalse(config.notificationsEnabled())
        try { config.saveLocalModelPath("/sdcard/unsafe.task"); fail("Non-private model path should be rejected") } catch (_: IllegalArgumentException) { }
    }

    @Test fun encrypted_configuration_persists_user_controlled_background_review_constraints() {
        val config = SecureConfig(ApplicationProvider.getApplicationContext())
        config.setBackgroundSyncEnabled(true)
        config.setBackgroundRequiresCharging(true)
        config.setBackgroundRequiresUnmeteredNetwork(true)
        config.setBackgroundIntervalMinutes(30)
        config.setLearningReviewEnabled(true)
        assertTrue(config.backgroundSyncEnabled())
        assertTrue(config.backgroundRequiresCharging())
        assertTrue(config.backgroundRequiresUnmeteredNetwork())
        assertEquals(30L, config.backgroundIntervalMinutes())
        assertTrue(config.learningReviewEnabled())
        assertFalse(config.hasLearningFingerprint("candidate-1"))
        config.markLearningFingerprint("candidate-1")
        assertTrue(config.hasLearningFingerprint("candidate-1"))
        try { config.setBackgroundIntervalMinutes(14); fail("Android background review must honor the 15-minute minimum") } catch (_: IllegalArgumentException) { }
    }

    @Test fun local_first_mode_has_no_packaged_remote_endpoint_and_requires_explicit_remote_selection() {
        val config = SecureConfig(ApplicationProvider.getApplicationContext())
        config.clearSession()
        config.setOperatingMode(OperatingMode.LOCAL_ONLY)
        assertEquals(OperatingMode.LOCAL_ONLY, config.operatingMode())
        assertFalse(config.internetEnabled())
        assertFalse(config.remoteAgentEnabled())
        assertNull(config.apiBaseUrl())
        config.saveApiBaseUrl("https://agent.example.com")
        config.setOperatingMode(OperatingMode.LOCAL_PLUS_INTERNET)
        assertTrue(config.internetEnabled())
        assertFalse(config.remoteAgentEnabled())
        config.setOperatingMode(OperatingMode.OPTIONAL_REMOTE_AGENT)
        assertTrue(config.remoteAgentEnabled())
        assertFalse(config.isConfigured())
    }
}
