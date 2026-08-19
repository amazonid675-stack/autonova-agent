package im.autonova.mobile

import im.autonova.mobile.ui.AutonovaViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class AutonovaViewModelTest {
    @Test fun build_request_creates_a_visible_planning_task() {
        val model = AutonovaViewModel()
        model.submit("Build a website for my business")
        assertEquals(1, model.tasks.value.size)
        assertEquals("PLANNING", model.tasks.value.first().status.name)
    }
}
