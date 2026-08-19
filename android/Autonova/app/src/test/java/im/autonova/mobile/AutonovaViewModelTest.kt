package im.autonova.mobile

import im.autonova.mobile.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AutonovaViewModelTest {
    @Test fun planning_state_is_available_for_locally_created_agent_work() {
        assertEquals("PLANNING", TaskStatus.PLANNING.name)
    }
}
