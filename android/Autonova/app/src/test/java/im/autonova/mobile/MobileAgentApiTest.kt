package im.autonova.mobile

import im.autonova.mobile.data.MobileStreamEvent
import im.autonova.mobile.data.bearerAuthorizationValue
import im.autonova.mobile.data.decodeStreamEvent
import im.autonova.mobile.data.remoteRecoveryGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileAgentApiTest {
    @Test fun `decodes message snapshots as replacement payloads`() {
        assertEquals(
            MobileStreamEvent.MessageSnapshot("First completed sentence."),
            decodeStreamEvent("{\"type\":\"message\",\"content\":\"First completed sentence.\"}")
        )
    }

    @Test fun `decodes terminal and ignores status frames`() {
        assertEquals(MobileStreamEvent.Done, decodeStreamEvent("{\"type\":\"done\"}"))
        assertNull(decodeStreamEvent("{\"type\":\"status\",\"label\":\"Reviewing approved context\"}"))
    }

    @Test fun `preserves provider or GitHub failure details for visible recovery guidance`() {
        assertEquals(
            MobileStreamEvent.Error("GitHub authorization failed or the branch has changed. Review the operation and retry."),
            decodeStreamEvent("{\"type\":\"error\",\"message\":\"GitHub authorization failed or the branch has changed. Review the operation and retry.\"}")
        )
    }

    @Test fun `uses a bearer authorization value rather than a browser cookie`() {
        assertEquals("Bearer header.payload.signature", bearerAuthorizationValue("header.payload.signature"))
    }

    @Test fun `maps provider authorization and network failures to retry or local-only guidance`() {
        assertEquals("The remote agent rejected access. Reconnect the optional remote agent and retry, or continue with Local Only capabilities.", remoteRecoveryGuidance(IllegalStateException("HTTP 401 unauthorized")))
        assertEquals("The optional remote agent is unavailable. Check your network or endpoint, retry the action, or continue with Local Only capabilities.", remoteRecoveryGuidance(IllegalStateException("Unable to connect")))
    }
}
