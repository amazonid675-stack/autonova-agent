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
    private val _connectionState = MutableStateFlow(if (config.isConfigured()) ConnectionState.CONNECTED else ConnectionState.READY_TO_CONNECT)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _feedback = MutableStateFlow<MobileFeedback?>(null)
    val feedback: StateFlow<MobileFeedback?> = _feedback.asStateFlow()
    private val _pendingCloudFallback = MutableStateFlow<String?>(null)
    val pendingCloudFallback: StateFlow<String?> = _pendingCloudFallback.asStateFlow()
    val messages = repository.messages; val tasks = repository.tasks; val projects = repository.projects; val memories = repository.memories; val activity = repository.activity; val files = repository.files; val tools = repository.tools; val provider = repository.provider; val usage = repository.usage; val github = repository.github; val generatedImageUrl = repository.generatedImageUrl; val research = repository.research; val learningCandidates = repository.learningCandidates; val capabilityGrants = repository.capabilityGrants; val githubConnection = repository.githubConnection; val githubOperations = repository.githubOperations
    init { AgentSyncWorker.enqueue(application) }
    fun clearFeedback() { _feedback.value = null }
    fun showError(message: String) { _feedback.value = MobileFeedback(message, FeedbackTone.ERROR) }
    fun recordDeviceAction(capability: String, detail: String, outcome: String = "APPROVED") = viewModelScope.launch { repository.recordDeviceCapability(capability, "Android device", detail, outcome) }
    private fun connectedAction(label: String, action: suspend () -> Boolean) = viewModelScope.launch {
        if (!config.isConfigured()) { _feedback.value = MobileFeedback("Connect Autonova to $label.", FeedbackTone.ERROR); return@launch }
        _feedback.value = MobileFeedback("$label…", FeedbackTone.INFO, true)
        _feedback.value = if (runCatching { action() }.getOrDefault(false)) MobileFeedback("$label complete.", FeedbackTone.SUCCESS) else MobileFeedback("$label could not be completed. Check your connection and try again.", FeedbackTone.ERROR)
    }
    fun submit(text: String) { if (text.isNotBlank()) viewModelScope.launch { val connected = config.isConfigured(); _feedback.value = MobileFeedback(if (connected) "Sending agent request…" else "Saving a local plan…", FeedbackTone.INFO, true); val sent = repository.submit(text); _feedback.value = if (sent) MobileFeedback(if (connected) "Agent request sent." else "Local plan saved. Connect Autonova to execute it.", FeedbackTone.SUCCESS) else MobileFeedback("The agent request could not be sent. Check your connection and try again.", FeedbackTone.ERROR) } }
    fun refresh() = connectedAction("refresh your workspace") { repository.refresh() }
    fun beginMobileSignIn(): String {
        val verifier = "${UUID.randomUUID()}-${UUID.randomUUID()}"
        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()).joinToString("") { "%02x".format(it) }
        config.saveCodeVerifier(verifier)
        _connectionState.value = ConnectionState.CONNECTING
        _feedback.value = MobileFeedback("Opening secure Autonova sign-in…", FeedbackTone.INFO, true)
        val origin = config.apiBaseUrl()
        return "$origin/api/mobile/auth/start?serverOrigin=${Uri.encode(origin)}&codeChallenge=$challenge"
    }
    fun completeMobileSignIn(code: String) = viewModelScope.launch {
        _connectionState.value = ConnectionState.CONNECTING
        val verifier = config.consumeCodeVerifier()
        if (verifier == null) { _connectionState.value = ConnectionState.ERROR; _feedback.value = MobileFeedback("This sign-in link is no longer valid. Start secure sign-in again.", FeedbackTone.ERROR); return@launch }
        runCatching { MobileAgentApi.exchangeMobileGrant(config.apiBaseUrl(), code, verifier) }
            .onSuccess { token -> config.saveAccessToken(token); _configured.value = true; _connectionState.value = ConnectionState.CONNECTED; _feedback.value = MobileFeedback("Autonova is connected and ready to work.", FeedbackTone.SUCCESS); refresh() }
            .onFailure { _connectionState.value = ConnectionState.ERROR; _feedback.value = MobileFeedback("Secure sign-in could not be completed. Start sign-in again and approve access in your browser.", FeedbackTone.ERROR) }
    }
    fun saveConnection(endpoint: String, sessionCookie: String): Boolean = runCatching { config.saveApiBaseUrl(endpoint.trim()); config.saveSessionCookie(sessionCookie.trim()); _configured.value = true; refresh() }.isSuccess
    fun clearConnection() { config.clearSession(); _configured.value = false; _connectionState.value = ConnectionState.READY_TO_CONNECT; _feedback.value = MobileFeedback("Local Autonova session cleared.", FeedbackTone.INFO) }
    fun createProject(name: String, description: String) = connectedAction("create the workspace") { repository.createProject(name, description) }
    fun createTask(request: String) = connectedAction("create the task") { repository.createTask(request) }
    fun changeTaskStatus(id: String, status: String) = connectedAction("update the task") { repository.changeTaskStatus(id, status) }
    fun createMemory(title: String, content: String, layer: String = "PERSONAL") = connectedAction("save the memory") { repository.createMemory(title, content, layer) }
    fun updateMemory(id: String, title: String, content: String, layer: String) = connectedAction("update the memory") { repository.updateMemory(id, title, content, layer) }
    fun deleteMemory(id: String) = connectedAction("remove the memory") { repository.deleteMemory(id) }
    fun setToolPolicy(key: String, policy: String) = connectedAction("update the tool policy") { repository.setToolPolicy(key, policy) }
    fun uploadLocalFile(document: LocalDocument, bytes: ByteArray) = connectedAction("upload ${document.name}") { repository.uploadFile(document, bytes) }
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
    fun runLocalModel(prompt: String) = viewModelScope.launch { _feedback.value = MobileFeedback("Running the local model…", FeedbackTone.INFO, true); _feedback.value = localModel.generate(prompt).fold({ answer -> repository.appendLocalMessage(im.autonova.mobile.data.ChatMessage("local-model-${System.currentTimeMillis()}", "assistant", answer, System.currentTimeMillis())); MobileFeedback("Local model response added to your workspace.", FeedbackTone.SUCCESS) }, { _pendingCloudFallback.value = prompt; MobileFeedback("Local model could not run. You can choose a connected cloud fallback without retyping your request.", FeedbackTone.ERROR) }) }
    fun confirmCloudFallback() { val prompt = _pendingCloudFallback.value ?: return; _pendingCloudFallback.value = null; submit("Use the connected cloud agent because local inference is unavailable. Original request:\n$prompt") }
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
    fun createCapabilityGrant(capability: String, scope: String, rationale: String) = connectedAction("propose the capability grant") { repository.createCapabilityGrant(capability, scope, rationale) }
    fun updateCapabilityGrant(id: String, status: String) = connectedAction("update the capability grant") { repository.updateCapabilityGrant(id, status) }
    fun connectGitHub(token: String, scopes: String) = connectedAction("connect GitHub") { repository.connectGitHub(token, scopes) }
    fun disconnectGitHub() = connectedAction("remove the GitHub connection") { repository.disconnectGitHub() }
    fun proposeGitHubOperation(repositoryName: String, operation: String, title: String, body: String, branch: String, fromBranch: String, head: String, base: String) = connectedAction("prepare the GitHub operation for confirmation") { repository.proposeGitHubOperation(repositoryName, operation, title, body, branch, fromBranch, head, base) }
    fun approveGitHubOperation(id: String) = connectedAction("run the confirmed GitHub operation") { repository.approveGitHubOperation(id) }
    fun cancelGitHubOperation(id: String) = connectedAction("cancel the GitHub operation") { repository.cancelGitHubOperation(id) }
    fun setBackgroundProfile(enabled: Boolean, requiresCharging: Boolean, requiresUnmetered: Boolean, intervalMinutes: Long, learningReviewEnabled: Boolean) { config.setBackgroundSyncEnabled(enabled); config.setBackgroundRequiresCharging(requiresCharging); config.setBackgroundRequiresUnmeteredNetwork(requiresUnmetered); config.setBackgroundIntervalMinutes(intervalMinutes); config.setLearningReviewEnabled(learningReviewEnabled); AgentSyncWorker.enqueue(getApplication()); _feedback.value = MobileFeedback(if (enabled) "Background review is enabled with your selected device constraints." else "Background review is paused.", FeedbackTone.SUCCESS) }
    fun backgroundSyncEnabled(): Boolean = config.backgroundSyncEnabled()
    fun backgroundRequiresCharging(): Boolean = config.backgroundRequiresCharging()
    fun backgroundRequiresUnmeteredNetwork(): Boolean = config.backgroundRequiresUnmeteredNetwork()
    fun backgroundIntervalMinutes(): Long = config.backgroundIntervalMinutes()
    fun learningReviewEnabled(): Boolean = config.learningReviewEnabled()
}
