package im.autonova.mobile.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskStatus { QUEUED, PLANNING, RUNNING, WAITING_FOR_USER, WAITING_FOR_TOOL, VERIFYING, FAILED, COMPLETED, CANCELLED }
enum class ToolPolicy { ASK, ALLOW, DENY }
data class ChatMessage(val id: String, val role: String, val content: String, val createdAt: Long)
data class AgentTask(val id: String, val request: String, val status: TaskStatus, val updatedAt: Long, val summary: String = "")
data class AgentProject(val id: String, val name: String, val description: String)
@Entity(tableName = "cached_tasks") data class CachedTask(@PrimaryKey val id: String, val request: String, val status: String, val updatedAt: Long)
@Entity(tableName = "cached_messages") data class CachedMessage(@PrimaryKey val id: String, val role: String, val content: String, val createdAt: Long)
