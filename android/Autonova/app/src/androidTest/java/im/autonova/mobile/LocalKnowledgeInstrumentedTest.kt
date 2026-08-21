package im.autonova.mobile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.autonova.mobile.data.AgentDatabase
import im.autonova.mobile.data.LocalKnowledgeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalKnowledgeInstrumentedTest {
    @Test fun local_text_is_chunked_retrieved_and_replaced_without_network_access() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AgentDatabase::class.java).allowMainThreadQueries().build()
        try {
            val knowledge = LocalKnowledgeEngine(database.cacheDao())
            assertEquals(1, knowledge.index("doc-1", "Kotlin notes", "Kotlin coroutines use structured concurrency for predictable cancellation and safe background work.").getOrThrow())
            assertEquals("Kotlin notes", knowledge.retrieve("How do Kotlin coroutines handle cancellation?").single().title)
            assertTrue(knowledge.index("doc-1", "Kotlin notes", "Room persists local Android data across app restarts with explicit schema migrations.").isSuccess)
            val results = knowledge.retrieve("coroutines cancellation")
            assertTrue(results.isEmpty())
            assertEquals("Kotlin notes", knowledge.retrieve("Room schema migrations").single().title)
        } finally { database.close() }
    }
}
