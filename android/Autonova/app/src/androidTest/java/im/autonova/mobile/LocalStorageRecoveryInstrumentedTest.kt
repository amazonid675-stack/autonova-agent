package im.autonova.mobile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.autonova.mobile.data.AgentDatabase
import im.autonova.mobile.data.CachedActivity
import im.autonova.mobile.data.CachedKnowledgeChunk
import im.autonova.mobile.data.CachedTask
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStorageRecoveryInstrumentedTest {
    @Test fun local_index_clear_removes_only_retrieval_chunks_and_reports_zero_usage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AgentDatabase::class.java).allowMainThreadQueries().build()
        val cache = database.cacheDao()
        cache.upsertKnowledgeChunks(listOf(CachedKnowledgeChunk("chunk-1", "doc-1", "Notes", "Autonova local retrieval evidence", "autonova local retrieval evidence", 1)))
        val before = cache.localKnowledgeUsage()
        assertEquals(1L, before.chunkCount)
        assertEquals(1L, before.documentCount)
        assertTrue(before.estimatedBytes > 0)
        cache.clearKnowledgeChunks()
        val after = cache.localKnowledgeUsage()
        assertEquals(0L, after.chunkCount)
        assertEquals(0L, after.documentCount)
        assertEquals(0L, after.estimatedBytes)
        database.close()
    }

    @Test fun task_records_survive_database_restart_and_activity_clear_keeps_tasks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "autonova-recovery-${System.nanoTime()}.db"
        val first = Room.databaseBuilder(context, AgentDatabase::class.java, name).allowMainThreadQueries().build()
        first.cacheDao().upsertTasks(listOf(CachedTask("task-restart", "Recover after restart", "QUEUED", 100)))
        first.cacheDao().upsertActivity(listOf(CachedActivity("activity-1", "Local event", "cache only", "LOCAL")))
        first.cacheDao().clearActivity()
        first.close()
        val reopened = Room.databaseBuilder(context, AgentDatabase::class.java, name).allowMainThreadQueries().build()
        val tasks = reopened.cacheDao().observeTasks().first()
        assertEquals(listOf("task-restart"), tasks.map { it.id })
        reopened.close()
        context.deleteDatabase(name)
    }
}
