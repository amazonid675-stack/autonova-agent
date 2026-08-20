package im.autonova.mobile

import im.autonova.mobile.data.MobileStreamEvent
import im.autonova.mobile.data.bearerAuthorizationValue
import im.autonova.mobile.data.decodeStreamEvent
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

    @Test fun `uses a bearer authorization value rather than a browser cookie`() {
        assertEquals("Bearer header.payload.signature", bearerAuthorizationValue("header.payload.signature"))
    }
}
