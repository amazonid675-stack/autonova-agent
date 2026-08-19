package im.autonova.mobile.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao interface AgentCacheDao { @Query("SELECT * FROM cached_tasks ORDER BY updatedAt DESC") fun observeTasks(): Flow<List<CachedTask>>; @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTasks(tasks: List<CachedTask>); @Query("SELECT * FROM cached_messages ORDER BY createdAt ASC") fun observeMessages(): Flow<List<CachedMessage>>; @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMessage(message: CachedMessage); @Query("SELECT * FROM cached_projects") fun observeProjects(): Flow<List<CachedProject>>; @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProjects(items: List<CachedProject>); @Query("SELECT * FROM cached_memories") fun observeMemories(): Flow<List<CachedMemory>>; @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMemories(items: List<CachedMemory>); @Query("SELECT * FROM cached_activity") fun observeActivity(): Flow<List<CachedActivity>>; @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertActivity(items: List<CachedActivity>) }
@Database(entities = [CachedTask::class, CachedMessage::class, CachedProject::class, CachedMemory::class, CachedActivity::class], version = 2, exportSchema = false) abstract class AgentDatabase : RoomDatabase() { abstract fun cacheDao(): AgentCacheDao; companion object { fun create(context: Context) = Room.databaseBuilder(context, AgentDatabase::class.java, "autonova-agent.db").fallbackToDestructiveMigration().build() } }
