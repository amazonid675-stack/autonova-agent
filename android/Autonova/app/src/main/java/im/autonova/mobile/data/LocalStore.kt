package im.autonova.mobile.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

data class LocalKnowledgeUsage(val chunkCount: Long, val documentCount: Long, val estimatedBytes: Long)

@Dao interface AgentCacheDao {
    @Query("SELECT * FROM cached_tasks ORDER BY updatedAt DESC") fun observeTasks(): Flow<List<CachedTask>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTasks(tasks: List<CachedTask>)
    @Query("SELECT * FROM cached_messages ORDER BY createdAt ASC") fun observeMessages(): Flow<List<CachedMessage>>
    @Query("SELECT * FROM cached_messages ORDER BY createdAt DESC LIMIT 40") suspend fun recentMessages(): List<CachedMessage>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMessage(message: CachedMessage)
    @Query("SELECT * FROM cached_projects") fun observeProjects(): Flow<List<CachedProject>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProjects(items: List<CachedProject>)
    @Query("SELECT * FROM cached_memories") fun observeMemories(): Flow<List<CachedMemory>>
    @Query("SELECT * FROM cached_memories WHERE id = :id LIMIT 1") suspend fun localMemory(id: String): CachedMemory?
    @Query("SELECT * FROM cached_memories ORDER BY title LIMIT 8") suspend fun localMemorySnippets(): List<CachedMemory>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMemories(items: List<CachedMemory>)
    @Query("DELETE FROM cached_memories WHERE id = :id") suspend fun deleteLocalMemory(id: String)
    @Query("SELECT * FROM cached_knowledge_chunks LIMIT 400") suspend fun localKnowledgeChunks(): List<CachedKnowledgeChunk>
    @Query("SELECT COUNT(*) AS chunkCount, COUNT(DISTINCT documentId) AS documentCount, COALESCE(SUM(LENGTH(content) + LENGTH(terms) + LENGTH(title)), 0) AS estimatedBytes FROM cached_knowledge_chunks") suspend fun localKnowledgeUsage(): LocalKnowledgeUsage
    @Query("DELETE FROM cached_knowledge_chunks WHERE documentId = :documentId") suspend fun deleteKnowledgeDocument(documentId: String)
    @Query("DELETE FROM cached_knowledge_chunks") suspend fun clearKnowledgeChunks()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertKnowledgeChunks(items: List<CachedKnowledgeChunk>)
    @Query("SELECT * FROM cached_activity") fun observeActivity(): Flow<List<CachedActivity>>
    @Query("DELETE FROM cached_activity") suspend fun clearActivity()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertActivity(items: List<CachedActivity>)
    @Query("SELECT * FROM cached_research") fun observeResearch(): Flow<List<CachedResearch>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertResearch(items: List<CachedResearch>)
    @Query("SELECT * FROM cached_learning_candidates") fun observeLearningCandidates(): Flow<List<CachedLearningCandidate>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLearningCandidates(items: List<CachedLearningCandidate>)
    @Query("SELECT * FROM cached_capability_grants") fun observeCapabilityGrants(): Flow<List<CachedCapabilityGrant>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCapabilityGrants(items: List<CachedCapabilityGrant>)
}

@Database(entities = [CachedTask::class, CachedMessage::class, CachedProject::class, CachedMemory::class, CachedActivity::class, CachedResearch::class, CachedLearningCandidate::class, CachedCapabilityGrant::class, CachedKnowledgeChunk::class], version = 5, exportSchema = false)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun cacheDao(): AgentCacheDao
    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("CREATE TABLE IF NOT EXISTS cached_knowledge_chunks (id TEXT NOT NULL, documentId TEXT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, terms TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))") } }
        fun create(context: Context) = Room.databaseBuilder(context, AgentDatabase::class.java, "autonova-agent.db").addMigrations(MIGRATION_4_5).build()
    }
}
