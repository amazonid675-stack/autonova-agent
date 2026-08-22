package im.autonova.mobile.data

import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineQuickAssistantTest {
    @Test fun coding_request_creates_an_honest_local_code_lab_route() {
        val response = OfflineQuickAssistant.respond("Help me code a website", "", "")
        assertTrue(response.contains("local coding work item"))
        assertTrue(response.contains("Code Lab"))
        assertTrue(response.contains("No data was sent off this device"))
    }

    @Test fun research_question_creates_a_visible_research_route() {
        val response = OfflineQuickAssistant.respond("How does offline retrieval work?", "", "")
        assertTrue(response.contains("local research task"))
        assertTrue(response.contains("Research"))
    }
}
