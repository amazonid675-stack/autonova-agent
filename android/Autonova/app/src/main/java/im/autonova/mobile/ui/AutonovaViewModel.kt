package im.autonova.mobile.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import im.autonova.mobile.data.AgentDatabase
import im.autonova.mobile.data.AgentRepository
import im.autonova.mobile.data.LocalModelEngine
import im.autonova.mobile.data.LocalDocument
import im.autonova.mobile.data.MobileAgentApi
import im.autonova.mobile.data.OperatingMode
import im.autonova.mobile.data.SecureConfig
import im.autonova.mobile.sync.AgentSyncWorker
import im.autonova.mobile.SharedAgentContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

enum class ConnectionState { READY_TO_CONNECT, CONNECTING, CONNECTED, ERROR }
enum class FeedbackTone { INFO, SUCCESS, ERROR }
data class MobileFeedback(val message: String, val tone: FeedbackTone = FeedbackTone.INFO, val isLoading: Boolean = false)

class AutonovaViewModel(application: Application) : AndroidViewModel(application) {
    private val config = SecureConfig(application)
    private val repository = AgentRepository(application, AgentDatabase.create(application).cacheDao(), config)
    private val localModel = LocalModelEngine(application, config)
    private val _configured = MutableStateFlow(config.isConfigured())
    val configured: StateFlow<Boolean> = _configured.asStateFlow()
    private val _operatingMode = MutableStateFlow(config.operatingMode())
    val operatingMode: StateFlow<OperatingMode> = _operatingMode.asStateFlow()
    private val _connectionState = MutableStateFlow(if (config.isConfigured()) ConnectionState.CONNECTED else ConnectionState.READY_TO_CONNECT)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _feedback = MutableStateFlow<MobileFeedback?>(null)
    val feedback: StateFlow<MobileFeedback?> = _feedback.asStateFlow()
    private val _pendingCloudFallback = MutableStateFlow<String?>(null)
    val pendingCloudFallback: StateFlow<String?> = _pendingCloudFallback.asStateFlow()
    val messages = repository.messages; val tasks = repository.tasks; val projects = repository.projects; val memories = repository.memories; val activity = repository.activity; val files = repository.files; val tools = repository.tools; val provider = repository.provider; val usage = repository.usage; val github = repository.github; val generatedImageUrl = repository.generatedImageUrl; val research = repository.research; val learningCandidates = repository.learningCandidates; val capabilityGrants = repository.capabilityGrants; val githubConnection = repository.githubConnection; val githubOperations = repository.githubOperations; val improvements = repository.improvements; val taskEvidence = repository.taskEvidence; val modelCapabilities = repository.modelCapabilities; val localKnowledgeUsage = repository.localKnowledgeUsage
    init { AgentSyncWorker.enqueue(application) }
    fun clearFeedback() { _feedback.value = null }
    fun showError(message: String) { _feedback.value = MobileFeedback(message, FeedbackTone.ERROR) }
    fun recordDeviceAction(capability: String, detail: String, outcome: String = "APPROVED") = viewModelScope.launch { repository.recordDeviceCapability(capability, "Android device", detail, outcome) }
    private fun connectedAction(label: String, action: suspend () -> Boolean) = viewModelScope.launch {
        if (!config.isConfigured()) { _feedback.value = MobileFeedback("Connect Autonova to $label.", FeedbackTone.ERROR); return@launch }
        _feedback.value = MobileFeedback("$label…", FeedbackTone.INFO, true)
        _feedback.value = if (runCatching { action() }.getOrDefault(false)) MobileFeedback("$label complete.", FeedbackTone.SUCCESS) else MobileFeedback("$label could not be completed. The optional remote agent may be unavailable or rejected the request; check your network or endpoint, retry, or continue with Local Only capabilities.", FeedbackTone.ERROR)
    }
    private fun localFirstAction(label: String, action: suspend () -> Boolean) = viewModelScope.launch {
        _feedback.value = MobileFeedback("$label…", FeedbackTone.INFO, true)
        _feedback.value = if (runCatching { action() }.getOrDefault(false)) MobileFeedback("$label complete.", FeedbackTone.SUCCESS) else MobileFeedback("$label could not be completed. Check your local storage or selected mode and try again.", FeedbackTone.ERROR)
    }
    fun submit(text: String) { if (text.isNotBlank()) viewModelScope.launch { val connected = config.isConfigured(); _feedback.value = MobileFeedback(if (connected) "Sending agent request…" else "Running with local capabilities…", FeedbackTone.INFO, true); val sent = repository.submit(text); _feedback.value = if (sent) MobileFeedback(if (connected) "Agent request sent." else "Local agent result saved. Online tools remain off until you explicitly enable them.", FeedbackTone.SUCCESS) else MobileFeedback("The agent request could not be completed. Check your selected mode; retry the remote agent or continue with Local Only capabilities.", FeedbackTone.ERROR) } }
    fun refresh() = connectedAction("refresh your workspace") { repository.refresh() }
    fun beginMobileSignIn(): String? {
        val origin = config.apiBaseUrl() ?: run { _feedback.value = MobileFeedback("Enter an optional remote-agent HTTPS endpoint before signing in.", FeedbackTone.ERROR); return null }
        val verifier = "${UUID.randomUUID()}-${UUID.randomUUID()}"
        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()).joinToString("") { "%02x".format(it) }
        config.saveCodeVerifier(verifier)
        _connectionState.value = ConnectionState.CONNECTING
        _feedback.value = MobileFeedback("Opening secure Autonova sign-in…", FeedbackTone.INFO, true)
        return "$origin/api/mobile/auth/start?serverOrigin=${Uri.encode(origin)}&codeChallenge=$challenge"
    }
    fun completeMobileSignIn(code: String) = viewModelScope.launch {
        _connectionState.value = ConnectionState.CONNECTING
        val verifier = config.consumeCodeVerifier()
        if (verifier == null) { _connectionState.value = ConnectionState.ERROR; _feedback.value = MobileFeedback("This sign-in link is no longer valid. Start secure sign-in again.", FeedbackTone.ERROR); return@launch }
        val endpoint = config.apiBaseUrl() ?: run { _connectionState.value = ConnectionState.ERROR; _feedback.value = MobileFeedback("A remote-agent endpoint is required to complete remote sign-in.", FeedbackTone.ERROR); return@launch }
        runCatching { MobileAgentApi.exchangeMobileGrant(endpoint, code, verifier) }
            .onSuccess { token -> config.saveAccessToken(token); _configured.value = true; _connectionState.value = ConnectionState.CONNECTED; _feedback.value = MobileFeedback("Autonova is connected and ready to work.", FeedbackTone.SUCCESS); refresh() }
            .onFailure { _connectionState.value = ConnectionState.ERROR; _feedback.value = MobileFeedback("Secure sign-in could not be completed. Start sign-in again and approve access in your browser.", FeedbackTone.ERROR) }
    }
    fun setOperatingMode(mode: OperatingMode, endpoint: String): Boolean = runCatching { if (mode == OperatingMode.OPTIONAL_REMOTE_AGENT) config.saveApiBaseUrl(endpoint.trim()); config.setOperatingMode(mode); _operatingMode.value = mode; _configured.value = config.isConfigured(); _feedback.value = MobileFeedback(if (mode == OperatingMode.LOCAL_ONLY) "Local-only mode enabled. Network and remote tools are off." else if (mode == OperatingMode.LOCAL_PLUS_INTERNET) "Local-plus-internet mode enabled. Browser handoff remains user-confirmed." else "Optional remote-agent mode configured. Sign in to use the selected endpoint.", FeedbackTone.SUCCESS); true }.getOrElse { _feedback.value = MobileFeedback(it.message ?: "Could not save operating mode.", FeedbackTone.ERROR); false }
    fun remoteEndpoint(): String = config.apiBaseUrl().orEmpty()
    fun clearConnection() { config.clearSession(); _configured.value = false; _connectionState.value = ConnectionState.READY_TO_CONNECT; _feedback.value = MobileFeedback("Local Autonova session cleared.", FeedbackTone.INFO) }
    fun createProject(name: String, description: String) = localFirstAction("create the workspace") { repository.createProject(name, description) }
    fun createTask(request: String) = localFirstAction("create the task") { repository.createTask(request) }
    fun changeTaskStatus(id: String, status: String) = connectedAction("update the task") { repository.changeTaskStatus(id, status) }
    fun createMemory(title: String, content: String, layer: String = "PERSONAL") = localFirstAction("save the memory") { repository.createMemory(title, content, layer) }
    fun updateMemory(id: String, title: String, content: String, layer: String) = localFirstAction("update the memory") { repository.updateMemory(id, title, content, layer) }
    fun deleteMemory(id: String) = localFirstAction("remove the memory") { repository.deleteMemory(id) }
    fun setToolPolicy(key: String, policy: String) = connectedAction("update the tool policy") { repository.setToolPolicy(key, policy) }
    fun uploadLocalFile(document: LocalDocument, bytes: ByteArray) = connectedAction("upload ${document.name}") { repository.uploadFile(document, bytes) }
    fun indexLocalDocument(document: LocalDocument, bytes: ByteArray) = localFirstAction("index ${document.name} locally") { repository.indexLocalDocument(document, bytes) }
    fun clearLocalKnowledgeIndex() = localFirstAction("clear the local document index") { repository.clearLocalKnowledgeIndex() }
    fun clearLocalActivityCache() = localFirstAction("clear local activity summaries") { repository.clearLocalActivityCache() }
    fun uploadDeviceContext(name: String, mimeType: String, bytes: ByteArray) = connectedAction("upload $name") { repository.uploadDeviceContext(name, mimeType, bytes) }
    fun importSharedContent(content: SharedAgentContent) = viewModelScope.launch {
        content.text?.let { submit("Shared from Android:\n$it") }
        content.uri?.let { uri ->
            _feedback.value = MobileFeedback("Importing shared content…", FeedbackTone.INFO, true)
            val result = runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { input -> input.readBytes() } ?: error("The shared item could not be read.") }
            val bytes = result.getOrElse { _feedback.value = MobileFeedback(it.message ?: "The shared item could not be read.", FeedbackTone.ERROR); return@launch }
            val uploaded = repository.uploadDeviceContext("shared-${System.currentTimeMillis()}", content.mimeType, bytes)
            _feedback.value = if (uploaded) MobileFeedback("Shared content is available to your connected agent.", FeedbackTone.SUCCESS) else MobileFeedback("Shared content could not be uploaded. Connect Autonova and try again.", FeedbackTone.ERROR)
        }
    }
    fun importLocalModel(uri: Uri) = viewModelScope.launch { _feedback.value = MobileFeedback("Importing local model…", FeedbackTone.INFO, true); _feedback.value = localModel.importModel(uri).fold({ MobileFeedback(it, FeedbackTone.SUCCESS) }, { MobileFeedback(it.message ?: "Could not import the local model.", FeedbackTone.ERROR) }) }
    fun localModelStatus(): String = localModel.status()
    fun localModelStorageBytes(): Long = localModel.storageBytes()
    fun removeLocalModel() = viewModelScope.launch { _feedback.value = localModel.removeImportedModel().fold({ MobileFeedback(it, FeedbackTone.SUCCESS) }, { MobileFeedback(it.message ?: "Could not remove the local model.", FeedbackTone.ERROR) }) }
    fun runLocalModel(prompt: String) = viewModelScope.launch { _feedback.value = MobileFeedback("Running the local model…", FeedbackTone.INFO, true); _feedback.value = localModel.generate(prompt).fold({ answer -> repository.appendLocalMessage(im.autonova.mobile.data.ChatMessage("local-model-${System.currentTimeMillis()}", "assistant", answer, System.currentTimeMillis())); MobileFeedback("Local model response added to your workspace.", FeedbackTone.SUCCESS) }, { _pendingCloudFallback.value = prompt; MobileFeedback("Local model could not run. You can choose a connected cloud fallback without retyping your request.", FeedbackTone.ERROR) }) }
    fun confirmCloudFallback() { val prompt = _pendingCloudFallback.value ?: return; _pendingCloudFallback.value = null; viewModelScope.launch { _feedback.value = MobileFeedback("Sending the user-approved cloud fallback…", FeedbackTone.INFO, true); val sent = repository.submit(prompt, forceRemote = true); _feedback.value = if (sent) MobileFeedback("Cloud fallback response saved.", FeedbackTone.SUCCESS) else MobileFeedback("The approved cloud fallback could not be completed. Check the selected remote agent, retry, or continue with Local Only capabilities.", FeedbackTone.ERROR) } }
    fun dismissCloudFallback() { _pendingCloudFallback.value = null }
    fun setNotificationsEnabled(enabled: Boolean) { config.setNotificationsEnabled(enabled); _feedback.value = MobileFeedback(if (enabled) "Task completion notifications enabled." else "Task completion notifications disabled.", FeedbackTone.SUCCESS) }
    fun notificationsEnabled(): Boolean = config.notificationsEnabled()
    fun saveProvider(name: String, providerType: String, baseUrl: String, model: String, apiKey: String, costMode: String) = connectedAction("save the provider") { repository.saveProvider(name, providerType, baseUrl, model, apiKey, costMode) }
    fun refreshUsage() = connectedAction("refresh usage") { repository.refreshUsage() }
    fun generateImage(prompt: String) = connectedAction("generate the image") { repository.generateImage(prompt) }
    fun inspectGitHub(repositoryName: String) = connectedAction("inspect the repository") { repository.inspectGitHub(repositoryName) }
    fun runResearch(query: String, sources: List<String>) = connectedAction("research the selected public sources with gpt-5-mini") { repository.research(query, sources) }
    fun createLearningCandidate(title: String, content: String, layer: String = "PERSONAL") = connectedAction("save the learning candidate for review") { repository.createLearningCandidate(title, content, layer) }
    fun reviewLearningCandidate(id: String, status: String, title: String? = null, content: String? = null, layer: String? = null) = connectedAction(if (status == "APPROVED") "save the reviewed learning" else "dismiss the learning candidate") { repository.reviewLearningCandidate(id, status, title, content, layer) }
    fun createImprovement(scope: String, title: String, proposedChange: String, evidence: String, testOutcome: String, benchmarkSummary: String, versionLabel: String) = connectedAction("save the improvement record for review") { repository.createImprovement(scope, title, proposedChange, evidence, testOutcome, benchmarkSummary, versionLabel) }
    fun reviewImprovement(id: String, status: String, note: String) = connectedAction(if (status == "APPROVED") "approve the improvement" else if (status == "ROLLED_BACK") "roll back the improvement" else "reject the improvement") { repository.reviewImprovement(id, status, note) }
    fun observeTask(id: String, summary: String, evidence: String) = connectedAction("record the task observation") { repository.observeTask(id, summary, evidence) }
    fun selectTaskTool(id: String, toolKey: String, rationale: String) = connectedAction("select the task tool") { repository.selectTaskTool(id, toolKey, rationale) }
    fun approveTaskTool(id: String, toolKey: String, approved: Boolean, note: String) = connectedAction(if (approved) "approve the task tool" else "decline the task tool") { repository.approveTaskTool(id, toolKey, approved, note) }
    fun repairTask(id: String, diagnosis: String) = connectedAction("start the task repair") { repository.repairTask(id, diagnosis) }
    fun escalateTask(id: String, level: String, summary: String) = connectedAction("escalate the task") { repository.escalateTask(id, level, summary) }
    fun verifyTask(id: String, passed: Boolean, evidence: String) = connectedAction("record task verification") { repository.verifyTask(id, passed, evidence) }
    fun createCapabilityGrant(capability: String, scope: String, rationale: String) = connectedAction("propose the capability grant") { repository.createCapabilityGrant(capability, scope, rationale) }
    fun updateCapabilityGrant(id: String, status: String) = connectedAction("update the capability grant") { repository.updateCapabilityGrant(id, status) }
    fun connectGitHub(token: String, scopes: String) = connectedAction("connect GitHub") { repository.connectGitHub(token, scopes) }
    fun disconnectGitHub() = connectedAction("remove the GitHub connection") { repository.disconnectGitHub() }
    fun proposeGitHubOperation(repositoryName: String, operation: String, title: String, body: String, branch: String, fromBranch: String, head: String, base: String) = connectedAction("prepare the GitHub operation for confirmation") { repository.proposeGitHubOperation(repositoryName, operation, title, body, branch, fromBranch, head, base) }
    fun proposeWorkspaceFileSync(repositoryName: String, commitMessage: String, branch: String, filePath: String, content: String) = connectedAction("prepare the selected workspace file for GitHub confirmation") { repository.proposeWorkspaceFileSync(repositoryName, commitMessage, branch, filePath, content) }
    fun approveGitHubOperation(id: String) = connectedAction("run the confirmed GitHub operation") { repository.approveGitHubOperation(id) }
    fun cancelGitHubOperation(id: String) = connectedAction("cancel the GitHub operation") { repository.cancelGitHubOperation(id) }
    fun setBackgroundProfile(enabled: Boolean, requiresCharging: Boolean, requiresUnmetered: Boolean, intervalMinutes: Long, learningReviewEnabled: Boolean) { config.setBackgroundSyncEnabled(enabled); config.setBackgroundRequiresCharging(requiresCharging); config.setBackgroundRequiresUnmeteredNetwork(requiresUnmetered); config.setBackgroundIntervalMinutes(intervalMinutes); config.setLearningReviewEnabled(learningReviewEnabled); AgentSyncWorker.enqueue(getApplication()); _feedback.value = MobileFeedback(if (enabled) "Background review is enabled with your selected device constraints." else "Background review is paused.", FeedbackTone.SUCCESS) }
    fun backgroundSyncEnabled(): Boolean = config.backgroundSyncEnabled()
    fun backgroundRequiresCharging(): Boolean = config.backgroundRequiresCharging()
    fun backgroundRequiresUnmeteredNetwork(): Boolean = config.backgroundRequiresUnmeteredNetwork()
    fun backgroundIntervalMinutes(): Long = config.backgroundIntervalMinutes()
    fun learningReviewEnabled(): Boolean = config.learningReviewEnabled()
}
