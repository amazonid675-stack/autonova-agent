package im.autonova.mobile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.autonova.mobile.data.SecureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureConfigInstrumentedTest {
    @Test fun encrypted_configuration_rejects_unsafe_endpoints_and_preserves_device_identity() {
        val config = SecureConfig(ApplicationProvider.getApplicationContext())
        config.clearSession()
        val firstId = config.deviceId()
        assertNotNull(firstId)
        assertEquals(firstId, config.deviceId())
        config.saveApiBaseUrl("https://agent.example.com")
        config.saveSessionCookie("session=mobile-test-value")
        assertEquals("https://agent.example.com", config.apiBaseUrl())
        assertEquals("session=mobile-test-value", config.sessionCookie())
        assertNotEquals("", config.deviceId())
        try { config.saveApiBaseUrl("http://localhost:3000"); fail("Unsafe endpoint should be rejected") } catch (_: IllegalArgumentException) { }
        try { config.saveSessionCookie("access-token") ; fail("Invalid cookie should be rejected") } catch (_: IllegalArgumentException) { }
    }
}
