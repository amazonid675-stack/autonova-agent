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
class LocalFirstPrimaryWorkflowInstrumentedTest {
    @Test fun local_only_project_memory_and_task_creation_persist_without_a_remote_session() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SecureConfig(context)
        config.clearSession()
        config.clearLocalModel()
        config.setOperatingMode(OperatingMode.LOCAL_ONLY)
        val database = Room.inMemoryDatabaseBuilder(context, AgentDatabase::class.java).allowMainThreadQueries().build()
        val cache = database.cacheDao()
        val repository = AgentRepository(context, cache, config)

        assertTrue(repository.createProject("Offline workspace", "A private local project"))
        assertTrue(repository.createMemory("Working style", "Keep plans concise", "PERSONAL"))
        assertTrue(repository.createTask("Review the offline plan"))

        assertEquals("Offline workspace", withTimeout(3_000) { cache.observeProjects().first { items -> items.any { it.name == "Offline workspace" } } }.first { it.name == "Offline workspace" }.name)
        assertEquals("Working style", withTimeout(3_000) { cache.observeMemories().first { items -> items.any { it.title == "Working style" } } }.first { it.title == "Working style" }.title)
        val task = withTimeout(3_000) { cache.observeTasks().first { items -> items.any { it.request == "Review the offline plan" } } }.first { it.request == "Review the offline plan" }
        assertEquals("PLANNING", task.status)
        assertTrue(repository.observeTask(task.id, "Reviewed the local plan", "Local notes"))
        assertTrue(repository.selectTaskTool(task.id, "local.workspace", "Use the selected on-device workspace."))
        assertTrue(repository.approveTaskTool(task.id, "local.workspace", true, "Approved locally"))
        assertTrue(repository.verifyTask(task.id, true, "Finished on this device"))
        assertEquals("COMPLETED", withTimeout(3_000) { cache.observeTasks().first { items -> items.firstOrNull { it.id == task.id }?.status == "COMPLETED" } }.first { it.id == task.id }.status)
        database.close()
    }

    @Test fun local_only_prompt_saves_the_request_and_an_immediate_local_working_response() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SecureConfig(context)
        config.clearSession()
        config.clearLocalModel()
        config.setOperatingMode(OperatingMode.LOCAL_ONLY)
        val database = Room.inMemoryDatabaseBuilder(context, AgentDatabase::class.java).allowMainThreadQueries().build()
        val cache = database.cacheDao()
        val repository = AgentRepository(context, cache, config)

        assertTrue(repository.submit("Plan my local work"))

        val messages = withTimeout(5_000) { cache.observeMessages().first { items -> items.any { it.role == "user" && it.content == "Plan my local work" } && items.any { it.role == "assistant" } } }
        assertTrue(messages.any { it.role == "assistant" && it.content.contains("Local working response") && it.content.contains("created a local planning task") })
        assertTrue(withTimeout(3_000) { cache.observeTasks().first { items -> items.any { it.request == "Plan my local work" } } }.any { it.request == "Plan my local work" })
        database.close()
    }
}
