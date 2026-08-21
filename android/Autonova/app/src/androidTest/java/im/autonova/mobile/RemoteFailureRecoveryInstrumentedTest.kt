package im.autonova.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import im.autonova.mobile.data.MobileAgentApi
import im.autonova.mobile.data.remoteRecoveryGuidance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteFailureRecoveryInstrumentedTest {
    @Test fun unreachable_optional_remote_agent_returns_recovery_guidance_without_claiming_success() = runBlocking {
        val result = runCatching { MobileAgentApi("https://127.0.0.1:1", "header.payload.signature").bootstrap() }
        val failure = result.exceptionOrNull()
        assertNotNull(failure)
        val guidance = remoteRecoveryGuidance(failure)
        assertTrue(guidance.contains("unavailable"))
        assertTrue(guidance.contains("retry"))
        assertTrue(guidance.contains("Local Only"))
    }
}
