package im.autonova.mobile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.autonova.mobile.data.SecureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

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
}
