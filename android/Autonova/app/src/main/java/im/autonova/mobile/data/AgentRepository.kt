package im.autonova.mobile.data

import android.os.Build
import android.util.Base64
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Keeps protected backend access, encrypted configuration, and Room cache updates out of Compose UI. */
class AgentRepository(private val context: Context, private val cache: AgentCacheDao, private val config: SecureConfig) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceAuditSynchronizer = DeviceAuditSynchronizer(cache)
    private val localModel = LocalModelEngine(context, config)
    private val localKnowledge = LocalKnowledgeEngine(cache)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList()); private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList()); private val _projects = MutableStateFlow<List<AgentProject>>(emptyList())
    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList()); private val _activity = MutableStateFlow<List<ActivityItem>>(emptyList())
    private val _files = MutableStateFlow<List<FileItem>>(emptyList()); private val _tools = MutableStateFlow<List<ToolItem>>(emptyList()); private val _provider = MutableStateFlow<ProviderItem?>(null)
    private val _usage = MutableStateFlow<UsageSummary?>(null); private val _github = MutableStateFlow<GitHubSummary?>(null); private val _generatedImageUrl = MutableStateFlow<String?>(null)
    private val _research = MutableStateFlow<List<ResearchSession>>(emptyList()); private val _learningCandidates = MutableStateFlow<List<LearningCandidate>>(emptyList()); private val _capabilityGrants = MutableStateFlow<List<CapabilityGrant>>(emptyList()); private val _githubConnection = MutableStateFlow<GitHubConnection?>(null); private val _githubOperations = MutableStateFlow<List<GitHubOperation>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow(); val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow(); val projects: StateFlow<List<AgentProject>> = _projects.asStateFlow(); val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow(); val activity: StateFlow<List<ActivityItem>> = _activity.asStateFlow(); val files: StateFlow<List<FileItem>> = _files.asStateFlow(); val tools: StateFlow<List<ToolItem>> = _tools.asStateFlow(); val provider: StateFlow<ProviderItem?> = _provider.asStateFlow(); val usage: StateFlow<UsageSummary?> = _usage.asStateFlow(); val github: StateFlow<GitHubSummary?> = _github.asStateFlow(); val generatedImageUrl: StateFlow<String?> = _generatedImageUrl.asStateFlow(); val research: StateFlow<List<ResearchSession>> = _research.asStateFlow(); val learningCandidates: StateFlow<List<LearningCandidate>> = _learningCandidates.asStateFlow(); val capabilityGrants: StateFlow<List<CapabilityGrant>> = _capabilityGrants.asStateFlow(); val githubConnection: StateFlow<GitHubConnection?> = _githubConnection.asStateFlow(); val githubOperations: StateFlow<List<GitHubOperation>> = _githubOperations.asStateFlow()
    init { scope.launch { cache.observeTasks().collect { cached -> _tasks.value = cached.map { AgentTask(it.id, it.request, TaskStatus.valueOf(it.status), it.updatedAt) } } }; scope.launch { cache.observeMessages().collect { cached -> _messages.value = cached.map { ChatMessage(it.id, it.role, it.content, it.createdAt) } } }; scope.launch { cache.observeProjects().collect { cached -> _projects.value = cached.map { AgentProject(it.id, it.name, it.description) } } }; scope.launch { cache.observeMemories().collect { cached -> _memories.value = cached.map { MemoryItem(it.id, it.title, it.content, it.layer) } } }; scope.launch { cache.observeActivity().collect { cached -> _activity.value = cached.map { ActivityItem(it.id, it.title, it.detail, it.eventType) } } }; scope.launch { cache.observeResearch().collect { cached -> _research.value = cached.map { ResearchSession(it.id, it.query, it.status, it.summary) } } }; scope.launch { cache.observeLearningCandidates().collect { cached -> _learningCandidates.value = cached.map { LearningCandidate(it.id, it.title, it.content, it.layer, it.source, it.status) } } }; scope.launch { cache.observeCapabilityGrants().collect { cached -> _capabilityGrants.value = cached.map { CapabilityGrant(it.id, it.capability, it.scope, it.rationale, it.status, it.expiresAt.ifBlank { null }, it.outcome) } } } }
    fun createLocalTask(request: String) { val now = System.currentTimeMillis(); scope.launch { cache.upsertTasks(listOf(CachedTask("local-$now", request, TaskStatus.PLANNING.name, now))) } }
    fun appendLocalMessage(message: ChatMessage) { scope.launch { cache.upsertMessage(CachedMessage(message.id, message.role, message.content, message.createdAt)) } }
    fun recordLocalActivity(eventType: String, title: String, detail: String) { val now = System.currentTimeMillis(); scope.launch { cache.upsertActivity(listOf(CachedActivity("local-audit-$now", title, detail, eventType))) } }
    suspend fun recordDeviceCapability(capability: String, scopeValue: String, detail: String, outcome: String): Boolean = runCatching {
        val remoteAudit = api()?.let { client -> DeviceAuditRemote { event -> client.recordDeviceAudit(event.capability, event.scope, event.detail, event.outcome) } }
        if (!deviceAuditSynchronizer.record(DeviceAuditEvent(capability, scopeValue, detail, outcome), remoteAudit)) return false
        refresh(); true
    }.getOrDefault(false)
    private fun api(): MobileAgentApi? = if (config.remoteAgentEnabled()) config.accessToken()?.let { token -> config.apiBaseUrl()?.let { endpoint -> MobileAgentApi(endpoint, token) } } else null
    suspend fun refresh(): Boolean {
        val api = api() ?: return false
        val previousStatuses = _tasks.value.associate { it.id to it.status.name }
        api.registerDevice(DeviceRegistration(config.deviceId(), "Android ${Build.MODEL}", false))
        val snapshot = api.bootstrap()
        cache.upsertProjects(snapshot.projects.map { CachedProject(it.id.toString(), it.name, it.description ?: "") }); cache.upsertTasks(snapshot.tasks.map { CachedTask(it.id.toString(), it.request, it.status, System.currentTimeMillis()) }); cache.upsertMemories(snapshot.memories.map { CachedMemory(it.id.toString(), it.title, it.content, it.layer) }); cache.upsertActivity(snapshot.activity.map { CachedActivity(it.id.toString(), it.title, it.detail ?: "", it.eventType) })
        cache.upsertResearch(snapshot.research.map { CachedResearch(it.id.toString(), it.query, it.status, it.summary ?: "") }); cache.upsertLearningCandidates(snapshot.learningCandidates.map { CachedLearningCandidate(it.id.toString(), it.title, it.content, it.layer, it.source, it.status) }); cache.upsertCapabilityGrants(snapshot.capabilityGrants.map { CachedCapabilityGrant(it.id.toString(), it.capability, it.scope, it.rationale ?: "", it.status, it.expiresAt ?: "", it.outcome ?: "") })
        _files.value = snapshot.files.map { FileItem(it.id.toString(), it.name, it.mimeType, it.sizeBytes) }; _tools.value = snapshot.toolPermissions.map { ToolItem(it.toolKey, it.policy) }; _provider.value = snapshot.provider?.let { ProviderItem(it.name, it.providerType, it.activeModel, it.costMode, it.hasApiKey) }
        _githubConnection.value = snapshot.githubConnection?.let { GitHubConnection(it.login, it.scopes) }; _githubOperations.value = snapshot.githubOperations.map { GitHubOperation(it.id.toString(), it.repository, it.operation, it.status, it.resultSummary ?: "", it.errorSummary ?: "") }
        if (config.notificationsEnabled()) snapshot.tasks.filter { it.status in listOf("COMPLETED", "FAILED") && previousStatuses[it.id.toString()] != it.status }.forEach { AgentNotifier(context).notifyTask(it.id.toString(), it.request, it.status) }
        return true
    }
    suspend fun submit(text: String): Boolean {
        val now = System.currentTimeMillis(); appendLocalMessage(ChatMessage("local-$now", "user", text, now)); val api = api()
        if (api != null) { val assistantId = "agent-${System.currentTimeMillis()}"; val assistantCreatedAt = System.currentTimeMillis(); var receivedSnapshot = false; val result = runCatching { api.sendMessage(text) { snapshot -> if (snapshot.isNotBlank()) { receivedSnapshot = true; cache.upsertMessage(CachedMessage(assistantId, "assistant", snapshot, assistantCreatedAt)) } } }; if (result.isFailure) return false; val reply = result.getOrNull(); if (!receivedSnapshot && !reply.isNullOrBlank()) cache.upsertMessage(CachedMessage(assistantId, "assistant", reply, assistantCreatedAt)) }
        else {
            val memory = cache.localMemorySnippets().joinToString("\n") { "- ${it.title}: ${it.content.take(500)}" }
            val knowledge = localKnowledge.retrieve(text).joinToString("\n\n") { "[Local document: ${it.title}; lexical relevance ${it.score}]\n${it.excerpt}" }
            val prompt = buildString { append("You are Autonova running fully on this Android device. Be concise, honest about limitations, and do not claim network access or actions you did not perform."); if (memory.isNotBlank()) append("\n\nApproved local memory:\n$memory"); if (knowledge.isNotBlank()) append("\n\nLocal document evidence:\n$knowledge"); append("\n\nUser request:\n$text") }
            val response = localModel.generate(prompt).getOrElse { error -> "Offline request saved locally. A compatible on-device `.task` model is required for local reasoning. Import one in Device Capabilities, or explicitly choose Optional remote agent mode for online tools. Details: ${error.message ?: "local model unavailable"}" }
            appendLocalMessage(ChatMessage("local-model-${System.currentTimeMillis()}", "assistant", response, System.currentTimeMillis()))
            recordLocalActivity("LOCAL_AGENT", "Local agent response", if (response.startsWith("Offline request saved")) "No compatible local model was available." else "Generated with the user-selected local model and local memory context.")
        }
        if (text.lowercase().startsWith("build") || text.lowercase().startsWith("create") || text.lowercase().startsWith("research") || text.lowercase().startsWith("plan")) createLocalTask(text)
        return true
    }
    suspend fun createProject(name: String, description: String): Boolean = runCatching { val client = api(); if (client != null) { client.createProject(name, description); refresh() } else { cache.upsertProjects(listOf(CachedProject("local-project-${System.currentTimeMillis()}", name, description))); recordLocalActivity("LOCAL_PROJECT", "Local workspace created", name) }; true }.getOrDefault(false)
    suspend fun createTask(request: String): Boolean = runCatching { val client = api(); if (client != null) { client.createTask(request); refresh() } else { val now = System.currentTimeMillis(); cache.upsertTasks(listOf(CachedTask("local-task-$now", request, TaskStatus.QUEUED.name, now))); recordLocalActivity("LOCAL_TASK", "Local task queued", request.take(240)) }; true }.getOrDefault(false)
    suspend fun changeTaskStatus(id: String, status: String): Boolean = runCatching { api()?.changeTaskStatus(id, status) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun createMemory(title: String, content: String, layer: String): Boolean = runCatching { val client = api(); if (client != null) { client.createMemory(title, content, layer); refresh() } else { cache.upsertMemories(listOf(CachedMemory("local-memory-${System.currentTimeMillis()}", title, content, layer))); recordLocalActivity("LOCAL_MEMORY", "Local memory saved", title) }; true }.getOrDefault(false)
    suspend fun updateMemory(id: String, title: String, content: String, layer: String): Boolean = runCatching { val client = api(); if (client != null) { client.updateMemory(id, title, content, layer); refresh() } else { val existing = cache.localMemory(id) ?: error("Local memory was not found."); cache.upsertMemories(listOf(existing.copy(title = title, content = content, layer = layer))); recordLocalActivity("LOCAL_MEMORY", "Local memory updated", title) }; true }.getOrDefault(false)
    suspend fun deleteMemory(id: String): Boolean = runCatching { val client = api(); if (client != null) { client.deleteMemory(id); refresh() } else { cache.deleteLocalMemory(id); recordLocalActivity("LOCAL_MEMORY", "Local memory deleted", id) }; true }.getOrDefault(false)
    suspend fun setToolPolicy(key: String, policy: String): Boolean = runCatching { api()?.setToolPolicy(key, policy) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun uploadFile(document: LocalDocument, bytes: ByteArray): Boolean = runCatching { require(bytes.size <= 10 * 1024 * 1024) { "Files must be 10 MB or smaller." }; api()?.uploadFile(document.name, document.mimeType, Base64.encodeToString(bytes, Base64.NO_WRAP)) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun indexLocalDocument(document: LocalDocument, bytes: ByteArray): Boolean = runCatching { val readable = bytes.toString(Charsets.UTF_8); val chunks = localKnowledge.index(document.uri.toString(), document.name, readable).getOrThrow(); recordLocalActivity("LOCAL_KNOWLEDGE", "Document indexed locally", "${document.name}: $chunks local text chunks. No network transfer occurred."); true }.getOrDefault(false)
    suspend fun uploadDeviceContext(name: String, mimeType: String, bytes: ByteArray): Boolean = runCatching { require(bytes.size in 1..10 * 1024 * 1024) { "Device context must be 10 MB or smaller." }; api()?.uploadFile(name, mimeType, Base64.encodeToString(bytes, Base64.NO_WRAP)) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun saveProvider(name: String, providerType: String, baseUrl: String, model: String, apiKey: String, costMode: String): Boolean = runCatching { api()?.saveProvider(ProviderRequest(name, providerType, baseUrl.ifBlank { null }, model.ifBlank { null }, apiKey.ifBlank { null }, costMode)) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun refreshUsage(): Boolean = runCatching { val wire = api()?.usage() ?: error("Connect the secure session first."); _usage.value = UsageSummary(wire.totals.inputTokens, wire.totals.outputTokens, wire.totals.toolCalls, wire.totals.estimatedCostMicros, wire.records.map { UsageRecord(it.model, it.inputTokens, it.outputTokens, it.toolCalls) }) }.isSuccess
    suspend fun generateImage(prompt: String): Boolean = runCatching { _generatedImageUrl.value = (api()?.generateImage(prompt) ?: error("Connect the secure session first.")).url; refresh() }.isSuccess
    suspend fun inspectGitHub(repository: String): Boolean = runCatching { val wire = api()?.inspectGitHub(repository) ?: error("Connect the secure session first."); _github.value = GitHubSummary(wire.repo.fullName, wire.repo.description ?: "No description.", wire.repo.defaultBranch, wire.repo.stars, wire.branches.map { it.name }, wire.issues.map { "#${it.number} ${it.title}" }, wire.pulls.map { "#${it.number} ${it.title}" }, wire.commits.map { "${it.sha.take(7)} ${it.commit.message.lineSequence().firstOrNull().orEmpty()}" }) }.isSuccess
    suspend fun research(query: String, sources: List<String>): Boolean = runCatching { api()?.research(query, sources) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun createLearningCandidate(title: String, content: String, layer: String, source: String = "ANDROID_LOCAL"): Boolean = runCatching { api()?.createLearningCandidate(LearningCandidateRequest(title, content, layer, source)) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun prepareLearningCandidates(): Boolean = runCatching {
        if (!config.learningReviewEnabled()) return@runCatching true
        val client = api() ?: return@runCatching true
        cache.recentMessages().asReversed().filter { it.role == "user" }.take(12).forEach { message ->
            val normalized = message.content.trim()
            val pattern = Regex("(?i)^(remember|i prefer|my preference is|always|never)\\b")
            if (normalized.length in 8..1200 && pattern.containsMatchIn(normalized)) {
                val fingerprint = "${message.id}:${normalized.hashCode()}"
                if (!config.hasLearningFingerprint(fingerprint)) { client.createLearningCandidate(LearningCandidateRequest("Review suggested preference", normalized, "PERSONAL", "ANDROID_BACKGROUND_REVIEW")); config.markLearningFingerprint(fingerprint) }
            }
        }
        refresh(); true
    }.getOrDefault(false)
    suspend fun reviewLearningCandidate(id: String, status: String, title: String? = null, content: String? = null, layer: String? = null): Boolean = runCatching { api()?.reviewLearningCandidate(id, LearningDecisionRequest(status, title, content, layer)) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun createCapabilityGrant(capability: String, scopeValue: String, rationale: String, expiresAt: String? = null): Boolean = runCatching { api()?.createCapabilityGrant(CapabilityGrantRequest(capability, scopeValue, rationale.ifBlank { null }, expiresAt)) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun updateCapabilityGrant(id: String, status: String): Boolean = runCatching { api()?.updateCapabilityGrant(id, status) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun connectGitHub(token: String, scopes: String): Boolean = runCatching { api()?.connectGitHub(token, scopes) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun disconnectGitHub(): Boolean = runCatching { api()?.disconnectGitHub() ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun proposeGitHubOperation(repository: String, operation: String, title: String, body: String, branch: String, fromBranch: String, head: String, base: String): Boolean = runCatching { api()?.proposeGitHubOperation(GitHubOperationRequest(repository, operation, title.ifBlank { null }, body.ifBlank { null }, branch.ifBlank { null }, fromBranch.ifBlank { null }, head.ifBlank { null }, base.ifBlank { null })) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun approveGitHubOperation(id: String): Boolean = runCatching { api()?.approveGitHubOperation(id) ?: error("Connect the secure session first."); refresh() }.isSuccess
    suspend fun cancelGitHubOperation(id: String): Boolean = runCatching { api()?.cancelGitHubOperation(id) ?: error("Connect the secure session first."); refresh() }.isSuccess
}

data class MemoryItem(val id: String, val title: String, val content: String, val layer: String)
data class ActivityItem(val id: String, val title: String, val detail: String, val eventType: String)
data class FileItem(val id: String, val name: String, val mimeType: String, val sizeBytes: Int)
data class ToolItem(val key: String, val policy: String)
data class ProviderItem(val name: String, val type: String, val model: String?, val costMode: String, val hasApiKey: Boolean)
data class UsageRecord(val model: String, val inputTokens: Int, val outputTokens: Int, val toolCalls: Int)
data class UsageSummary(val inputTokens: Int, val outputTokens: Int, val toolCalls: Int, val estimatedCostMicros: Int, val records: List<UsageRecord>)
data class GitHubSummary(val name: String, val description: String, val branch: String, val stars: Int, val branches: List<String>, val issues: List<String>, val pulls: List<String>, val commits: List<String>)
