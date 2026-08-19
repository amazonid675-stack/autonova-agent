package im.autonova.mobile

import im.autonova.mobile.data.EndpointPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test fun accepts_only_public_https_agent_endpoints() {
        assertTrue(EndpointPolicy.isAllowed("https://agent.example.com"))
        assertFalse(EndpointPolicy.isAllowed("http://agent.example.com"))
        assertFalse(EndpointPolicy.isAllowed("https://localhost:3000"))
        assertFalse(EndpointPolicy.isAllowed("https://agent.local"))
    }
}
