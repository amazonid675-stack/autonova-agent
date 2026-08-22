package im.autonova.mobile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.autonova.mobile.data.AgentDatabase
import im.autonova.mobile.data.AgentRepository
import im.autonova.mobile.data.OperatingMode
import im.autonova.mobile.data.SecureConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FunctionalLocalControlsInstrumentedTest {
    @Test fun local_only_research_learning_grants_tools_and_device_context_produce_visible_records() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SecureConfig(context)
        config.clearSession()
        config.clearLocalModel()
        config.setOperatingMode(OperatingMode.LOCAL_ONLY)
        val database = Room.inMemoryDatabaseBuilder(context, AgentDatabase::class.java).allowMainThreadQueries().build()
        val cache = database.cacheDao()
        val repository = AgentRepository(context, cache, config)

        assertTrue(repository.research("Local research", listOf("https://example.com/source")))
        val research = withTimeout(3_000) { cache.observeResearch().first { items -> items.any { it.query == "Local research" } } }.first { it.query == "Local research" }
        assertEquals("LOCAL_BROWSER_HANDOFF", research.status)

        assertTrue(repository.createLearningCandidate("Local preference", "Use short plans", "PERSONAL"))
        val candidate = withTimeout(3_000) { cache.observeLearningCandidates().first { items -> items.any { it.title == "Local preference" } } }.first { it.title == "Local preference" }
        assertTrue(repository.reviewLearningCandidate(candidate.id, "APPROVED"))
        assertTrue(withTimeout(3_000) { cache.observeMemories().first { items -> items.any { it.title == "Local preference" } } }.any { it.title == "Local preference" })

        assertTrue(repository.createCapabilityGrant("browser.handoff", "example.com", "Visible owner approval"))
        val grant = withTimeout(3_000) { cache.observeCapabilityGrants().first { items -> items.any { it.capability == "browser.handoff" } } }.first { it.capability == "browser.handoff" }
        assertTrue(repository.updateCapabilityGrant(grant.id, "APPROVED"))
        assertEquals("APPROVED", withTimeout(3_000) { cache.observeCapabilityGrants().first { items -> items.firstOrNull { it.id == grant.id }?.status == "APPROVED" } }.first { it.id == grant.id }.status)

        assertTrue(repository.setToolPolicy("browser.handoff", "ALLOW"))
        assertEquals("ALLOW", repository.tools.value.first { it.key == "browser.handoff" }.policy)

        assertTrue(repository.uploadDeviceContext("test-context.txt", "text/plain", "private local context".toByteArray()))
        assertTrue(repository.files.value.any { it.name.endsWith("test-context.txt") })
        database.close()
    }
}
