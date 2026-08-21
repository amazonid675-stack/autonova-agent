package im.autonova.mobile.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskStatus { QUEUED, PLANNING, RUNNING, WAITING_FOR_USER, WAITING_FOR_TOOL, VERIFYING, FAILED, COMPLETED, CANCELLED }
enum class ToolPolicy { ASK, ALLOW, DENY }
data class ChatMessage(val id: String, val role: String, val content: String, val createdAt: Long)
data class AgentTask(val id: String, val request: String, val status: TaskStatus, val updatedAt: Long, val summary: String = "")
data class AgentProject(val id: String, val name: String, val description: String)
data class ResearchSession(val id: String, val query: String, val status: String, val summary: String)
data class LearningCandidate(val id: String, val title: String, val content: String, val layer: String, val source: String, val status: String)
data class CapabilityGrant(val id: String, val capability: String, val scope: String, val rationale: String, val status: String, val expiresAt: String? = null, val outcome: String = "")
data class GitHubConnection(val login: String, val scopes: String)
data class GitHubOperation(val id: String, val repository: String, val operation: String, val status: String, val resultSummary: String = "", val errorSummary: String = "")
@Entity(tableName = "cached_tasks") data class CachedTask(@PrimaryKey val id: String, val request: String, val status: String, val updatedAt: Long)
@Entity(tableName = "cached_messages") data class CachedMessage(@PrimaryKey val id: String, val role: String, val content: String, val createdAt: Long)
@Entity(tableName = "cached_projects") data class CachedProject(@PrimaryKey val id: String, val name: String, val description: String)
@Entity(tableName = "cached_memories") data class CachedMemory(@PrimaryKey val id: String, val title: String, val content: String, val layer: String)
@Entity(tableName = "cached_activity") data class CachedActivity(@PrimaryKey val id: String, val title: String, val detail: String, val eventType: String)
@Entity(tableName = "cached_research") data class CachedResearch(@PrimaryKey val id: String, val query: String, val status: String, val summary: String)
@Entity(tableName = "cached_learning_candidates") data class CachedLearningCandidate(@PrimaryKey val id: String, val title: String, val content: String, val layer: String, val source: String, val status: String)
@Entity(tableName = "cached_capability_grants") data class CachedCapabilityGrant(@PrimaryKey val id: String, val capability: String, val scope: String, val rationale: String, val status: String, val expiresAt: String, val outcome: String)
