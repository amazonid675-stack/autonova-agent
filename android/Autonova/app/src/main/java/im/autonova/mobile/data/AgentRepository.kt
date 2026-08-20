package im.autonova.mobile.data

import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Keeps protected backend access, encrypted configuration, and Room cache updates out of Compose UI. */
class AgentRepository(private val cache: AgentCacheDao, private val config: SecureConfig) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList()); private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList()); private val _projects = MutableStateFlow<List<AgentProject>>(emptyList())
    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList()); private val _activity = MutableStateFlow<List<ActivityItem>>(emptyList())
    private val _files = MutableStateFlow<List<FileItem>>(emptyList()); private val _tools = MutableStateFlow<List<ToolItem>>(emptyList()); private val _provider = MutableStateFlow<ProviderItem?>(null)
    private val _usage = MutableStateFlow<UsageSummary?>(null); private val _github = MutableStateFlow<GitHubSummary?>(null); private val _generatedImageUrl = MutableStateFlow<String?>(null)
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow(); val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow(); val projects: StateFlow<List<AgentProject>> = _projects.asStateFlow(); val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow(); val activity: StateFlow<List<ActivityItem>> = _activity.asStateFlow(); val files: StateFlow<List<FileItem>> = _files.asStateFlow(); val tools: StateFlow<List<ToolItem>> = _tools.asStateFlow(); val provider: StateFlow<ProviderItem?> = _provider.asStateFlow(); val usage: StateFlow<UsageSummary?> = _usage.asStateFlow(); val github: StateFlow<GitHubSummary?> = _github.asStateFlow(); val generatedImageUrl: StateFlow<String?> = _generatedImageUrl.asStateFlow()
    init { scope.launch { cache.observeTasks().collect { cached -> _tasks.value = cached.map { AgentTask(it.id, it.request, TaskStatus.valueOf(it.status), it.updatedAt) } } }; scope.launch { cache.observeMessages().collect { cached -> _messages.value = cached.map { ChatMessage(it.id, it.role, it.content, it.createdAt) } } }; scope.launch { cache.observeProjects().collect { cached -> _projects.value = cached.map { AgentProject(it.id, it.name, it.description) } } }; scope.launch { cache.observeMemories().collect { cached -> _memories.value = cached.map { MemoryItem(it.id, it.title, it.content, it.layer) } } }; scope.launch { cache.observeActivity().collect { cached -> _activity.value = cached.map { ActivityItem(it.id, it.title, it.detail, it.eventType) } } } }
    fun createLocalTask(request: String) { val now = System.currentTimeMillis(); scope.launch { cache.upsertTasks(listOf(CachedTask("local-$now", request, TaskStatus.PLANNING.name, now))) } }
    fun appendLocalMessage(message: ChatMessage) { scope.launch { cache.upsertMessage(CachedMessage(message.id, message.role, message.content, message.createdAt)) } }
    private fun api(): MobileAgentApi? = config.accessToken()?.let { MobileAgentApi(config.apiBaseUrl(), it) }
    suspend fun refresh(): Boolean {
        val api = api() ?: return false
        api.registerDevice(DeviceRegistration(config.deviceId(), "Android ${Build.MODEL}", false))
        val snapshot = api.bootstrap()
        cache.upsertProjects(snapshot.projects.map { CachedProject(it.id.toString(), it.name, it.description ?: "") }); cache.upsertTasks(snapshot.tasks.map { CachedTask(it.id.toString(), it.request, it.status, System.currentTimeMillis()) }); cache.upsertMemories(snapshot.memories.map { CachedMemory(it.id.toString(), it.title, it.content, it.layer) }); cache.upsertActivity(snapshot.activity.map { CachedActivity(it.id.toString(), it.title, it.detail ?: "", it.eventType) })
        _files.value = snapshot.files.map { FileItem(it.id.toString(), it.name, it.mimeType, it.sizeBytes) }; _tools.value = snapshot.toolPermissions.map { ToolItem(it.toolKey, it.policy) }; _provider.value = snapshot.provider?.let { ProviderItem(it.name, it.providerType, it.activeModel, it.costMode, it.hasApiKey) }
        return true
    }
    suspend fun submit(text: String): Boolean {
        val now = System.currentTimeMillis(); appendLocalMessage(ChatMessage("local-$now", "user", text, now)); val api = api()
        if (api != null) { val assistantId = "agent-${System.currentTimeMillis()}"; val assistantCreatedAt = System.currentTimeMillis(); var receivedSnapshot = false; val result = runCatching { api.sendMessage(text) { snapshot -> if (snapshot.isNotBlank()) { receivedSnapshot = true; cache.upsertMessage(CachedMessage(assistantId, "assistant", snapshot, assistantCreatedAt)) } } }; if (result.isFailure) return false; val reply = result.getOrNull(); if (!receivedSnapshot && !reply.isNullOrBlank()) cache.upsertMessage(CachedMessage(assistantId, "assistant", reply, assistantCreatedAt)) }
        else appendLocalMessage(ChatMessage("local-agent-${System.currentTimeMillis()}", "assistant", "I saved this as a local agent request. Connect your Autonova account to execute cloud research, GitHub, files, image generation, and multi-step verification. You can still review the task plan in Tasks.", System.currentTimeMillis()))
        if (text.lowercase().startsWith("build") || text.lowercase().startsWith("create") || text.lowercase().startsWith("research") || text.lowercase().startsWith("plan")) createLocalTask(text)
        return true
    }
    suspend fun createProject(name: String, description: String): Boolean = runCatching { api()?.createProject(name, description) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun createTask(request: String): Boolean = runCatching { api()?.createTask(request) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun changeTaskStatus(id: String, status: String): Boolean = runCatching { api()?.changeTaskStatus(id, status) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun createMemory(title: String, content: String, layer: String): Boolean = runCatching { api()?.createMemory(title, content, layer) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun updateMemory(id: String, title: String, content: String, layer: String): Boolean = runCatching { api()?.updateMemory(id, title, content, layer) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun deleteMemory(id: String): Boolean = runCatching { api()?.deleteMemory(id) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun setToolPolicy(key: String, policy: String): Boolean = runCatching { api()?.setToolPolicy(key, policy) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun uploadFile(document: LocalDocument, bytes: ByteArray): Boolean = runCatching { require(bytes.size <= 10 * 1024 * 1024) { "Files must be 10 MB or smaller." }; api()?.uploadFile(document.name, document.mimeType, Base64.encodeToString(bytes, Base64.NO_WRAP)) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun saveProvider(name: String, providerType: String, baseUrl: String, model: String, apiKey: String, costMode: String): Boolean = runCatching { api()?.saveProvider(ProviderRequest(name, providerType, baseUrl.ifBlank { null }, model.ifBlank { null }, apiKey.ifBlank { null }, costMode)) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun refreshUsage(): Boolean = runCatching { val wire = api()?.usage() ?: error("Connect the secure session first."); _usage.value = UsageSummary(wire.totals.inputTokens, wire.totals.outputTokens, wire.totals.toolCalls, wire.totals.estimatedCostMicros, wire.records.map { UsageRecord(it.model, it.inputTokens, it.outputTokens, it.toolCalls) }) }.isSuccess
    suspend fun generateImage(prompt: String): Boolean = runCatching { _generatedImageUrl.value = (api()?.generateImage(prompt) ?: error("Connect the secure session first.")).url; refresh() }.isSuccess
    suspend fun inspectGitHub(repository: String): Boolean = runCatching { val wire = api()?.inspectGitHub(repository) ?: error("Connect the secure session first."); _github.value = GitHubSummary(wire.repo.fullName, wire.repo.description ?: "No description.", wire.repo.defaultBranch, wire.repo.stars, wire.branches.map { it.name }, wire.issues.map { "#${it.number} ${it.title}" }, wire.pulls.map { "#${it.number} ${it.title}" }, wire.commits.map { "${it.sha.take(7)} ${it.commit.message.lineSequence().firstOrNull().orEmpty()}" }) }.isSuccess
}

data class MemoryItem(val id: String, val title: String, val content: String, val layer: String)
data class ActivityItem(val id: String, val title: String, val detail: String, val eventType: String)
data class FileItem(val id: String, val name: String, val mimeType: String, val sizeBytes: Int)
data class ToolItem(val key: String, val policy: String)
data class ProviderItem(val name: String, val type: String, val model: String?, val costMode: String, val hasApiKey: Boolean)
data class UsageRecord(val model: String, val inputTokens: Int, val outputTokens: Int, val toolCalls: Int)
data class UsageSummary(val inputTokens: Int, val outputTokens: Int, val toolCalls: Int, val estimatedCostMicros: Int, val records: List<UsageRecord>)
data class GitHubSummary(val name: String, val description: String, val branch: String, val stars: Int, val branches: List<String>, val issues: List<String>, val pulls: List<String>, val commits: List<String>)
