package im.autonova.mobile.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable data class MobileTaskWire(val id: Int, val request: String, val status: String, val updatedAt: String? = null, val errorSummary: String? = null)
@Serializable data class MobileProjectWire(val id: Int, val name: String, val description: String? = null)
@Serializable data class MobileMemoryWire(val id: Int, val title: String, val content: String, val layer: String)
@Serializable data class MobileActivityWire(val id: Int, val title: String, val detail: String? = null, val eventType: String)
@Serializable data class MobileFileWire(val id: Int, val name: String, val mimeType: String, val sizeBytes: Int)
@Serializable data class MobileToolWire(val toolKey: String, val policy: String)
@Serializable data class MobileProviderWire(val name: String, val providerType: String, val activeModel: String? = null, val costMode: String, val hasApiKey: Boolean)
@Serializable data class MobileResearchWire(val id: Int, val query: String, val status: String, val summary: String? = null, val errorSummary: String? = null)
@Serializable data class MobileResearchSourceWire(val id: Int, val sessionId: Int, val sourceUrl: String, val host: String, val title: String? = null, val excerpt: String? = null, val citationLabel: String, val fetchStatus: String)
@Serializable data class MobileLearningCandidateWire(val id: Int, val title: String, val content: String, val layer: String, val source: String, val status: String)
@Serializable data class MobileCapabilityGrantWire(val id: Int, val capability: String, val scope: String, val rationale: String? = null, val outcome: String? = null, val status: String, val expiresAt: String? = null)
@Serializable data class MobileGitHubConnectionWire(val login: String, val scopes: String)
@Serializable data class MobileGitHubOperationWire(val id: Int, val repository: String, val operation: String, val status: String, val resultSummary: String? = null, val errorSummary: String? = null)
@Serializable data class MobileImprovementWire(val id: Int, val scope: String, val title: String, val proposedChange: String, val evidence: String, val testOutcome: String? = null, val benchmarkSummary: String? = null, val versionLabel: String, val status: String, val reviewNote: String? = null)
@Serializable data class MobileTaskEvidenceWire(val id: Int, val taskId: Int, val kind: String, val toolKey: String? = null, val summary: String, val evidence: String? = null, val outcome: String)
@Serializable data class MobileModelCapabilityWire(val modality: String, val route: String, val availability: String, val note: String)
@Serializable data class MobileBootstrap(val projects: List<MobileProjectWire>, val tasks: List<MobileTaskWire>, val memories: List<MobileMemoryWire>, val activity: List<MobileActivityWire>, val files: List<MobileFileWire> = emptyList(), val toolPermissions: List<MobileToolWire> = emptyList(), val provider: MobileProviderWire? = null, val modelCapabilities: List<MobileModelCapabilityWire> = emptyList(), val research: List<MobileResearchWire> = emptyList(), val learningCandidates: List<MobileLearningCandidateWire> = emptyList(), val capabilityGrants: List<MobileCapabilityGrantWire> = emptyList(), val githubConnection: MobileGitHubConnectionWire? = null, val githubOperations: List<MobileGitHubOperationWire> = emptyList(), val improvements: List<MobileImprovementWire> = emptyList(), val taskEvidence: List<MobileTaskEvidenceWire> = emptyList())
@Serializable data class DeviceRegistration(val deviceId: String, val label: String, val pushEnabled: Boolean)
@Serializable data class StreamRequest(val content: String)
@Serializable data class ProjectRequest(val name: String, val description: String? = null)
@Serializable data class TaskRequest(val request: String, val projectId: Int? = null)
@Serializable data class MemoryRequest(val title: String, val content: String, val layer: String = "PERSONAL")
@Serializable data class ToolPermissionRequest(val policy: String)
@Serializable data class FileUploadRequest(val name: String, val mimeType: String, val dataBase64: String)
@Serializable data class TaskActionRequest(val status: String)
@Serializable data class ProviderRequest(val name: String, val providerType: String, val baseUrl: String? = null, val activeModel: String? = null, val apiKey: String? = null, val costMode: String = "BALANCED")
@Serializable data class ImageRequest(val prompt: String)
@Serializable data class GeneratedImageWire(val url: String)
@Serializable data class ResearchRequest(val query: String, val sources: List<String>)
@Serializable data class ResearchResultWire(val session: MobileResearchWire? = null, val sources: List<MobileResearchSourceWire> = emptyList())
@Serializable data class LearningCandidateRequest(val title: String, val content: String, val layer: String = "PERSONAL", val source: String = "ANDROID_LOCAL")
@Serializable data class LearningDecisionRequest(val status: String, val title: String? = null, val content: String? = null, val layer: String? = null)
@Serializable data class ImprovementRequest(val scope: String, val title: String, val proposedChange: String, val evidence: String, val testOutcome: String, val benchmarkSummary: String, val versionLabel: String)
@Serializable data class ImprovementDecisionRequest(val status: String, val note: String)
@Serializable data class TaskObservationRequest(val summary: String, val evidence: String? = null)
@Serializable data class TaskToolRequest(val toolKey: String, val rationale: String)
@Serializable data class TaskToolApprovalRequest(val toolKey: String, val approved: Boolean, val note: String? = null)
@Serializable data class TaskRepairRequest(val diagnosis: String, val repairSteps: List<TaskRepairStepRequest>)
@Serializable data class TaskRepairStepRequest(val title: String, val detail: String? = null, val toolKey: String? = null)
@Serializable data class TaskEscalationRequest(val level: String, val summary: String)
@Serializable data class TaskVerificationRequest(val passed: Boolean, val evidence: String, val nextAction: String? = null)
@Serializable data class CapabilityGrantRequest(val capability: String, val scope: String, val rationale: String? = null, val expiresAt: String? = null)
@Serializable data class CapabilityGrantDecisionRequest(val status: String)
@Serializable data class DeviceAuditRequest(val capability: String, val scope: String, val detail: String, val outcome: String)
@Serializable data class GitHubConnectionRequest(val token: String, val scopes: String)
@Serializable data class GitHubOperationRequest(val repository: String, val operation: String, val title: String? = null, val body: String? = null, val branch: String? = null, val fromBranch: String? = null, val head: String? = null, val base: String? = null, val filePath: String? = null, val content: String? = null, val expectedSha: String? = null)
@Serializable data class GitHubOperationResultWire(val resultSummary: String)
@Serializable data class UsageTotalsWire(val inputTokens: Int, val outputTokens: Int, val toolCalls: Int, val estimatedCostMicros: Int)
@Serializable data class UsageRecordWire(val id: Int, val model: String, val inputTokens: Int, val outputTokens: Int, val toolCalls: Int, val estimatedCostMicros: Int)
@Serializable data class UsageWire(val totals: UsageTotalsWire, val records: List<UsageRecordWire>)
@Serializable data class GitHubRepoWire(@SerialName("full_name") val fullName: String, val description: String? = null, @SerialName("default_branch") val defaultBranch: String, @SerialName("html_url") val htmlUrl: String, @SerialName("stargazers_count") val stars: Int)
@Serializable data class GitHubBranchWire(val name: String)
@Serializable data class GitHubIssueWire(val number: Int, val title: String)
@Serializable data class GitHubPullWire(val number: Int, val title: String)
@Serializable data class GitHubCommitWire(val sha: String, val commit: GitHubCommitMessageWire)
@Serializable data class GitHubCommitMessageWire(val message: String)
@Serializable data class GitHubInspectionWire(val repo: GitHubRepoWire, val branches: List<GitHubBranchWire> = emptyList(), val issues: List<GitHubIssueWire> = emptyList(), val pulls: List<GitHubPullWire> = emptyList(), val commits: List<GitHubCommitWire> = emptyList())
@Serializable data class MobileAuthExchangeRequest(val code: String, val codeVerifier: String)
@Serializable data class MobileAuthExchangeWire(val accessToken: String)

internal sealed interface MobileStreamEvent {
    data class MessageSnapshot(val content: String) : MobileStreamEvent
    data class Error(val message: String) : MobileStreamEvent
    data object Done : MobileStreamEvent
}

internal fun decodeStreamEvent(data: String): MobileStreamEvent? {
    val event = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
    return when (event["type"]?.jsonPrimitive?.content) {
        "message" -> event["content"]?.jsonPrimitive?.content?.let(MobileStreamEvent::MessageSnapshot)
        "error" -> MobileStreamEvent.Error(event["message"]?.jsonPrimitive?.content ?: "Unable to complete the response.")
        "done" -> MobileStreamEvent.Done
        else -> null
    }
}

internal fun bearerAuthorizationValue(accessToken: String): String = "Bearer $accessToken"

class MobileAgentApi(private val baseUrl: String, private val accessToken: String) {
    private val client = HttpClient(OkHttp) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    private fun path(value: String) = "${baseUrl.trimEnd('/')}$value"
    private fun authorized() = Pair(HttpHeaders.Authorization, bearerAuthorizationValue(accessToken))
    private suspend fun requireSuccess(response: HttpResponse) { if (response.status.value !in 200..299) throw IllegalStateException(response.bodyAsText().take(300).ifBlank { "Mobile request failed (${response.status.value})." }) }

    suspend fun bootstrap(): MobileBootstrap = client.get(path("/api/mobile/bootstrap")) { header(authorized().first, authorized().second) }.body()
    suspend fun registerDevice(device: DeviceRegistration) { requireSuccess(client.post(path("/api/mobile/devices")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(device) }) }
    suspend fun createProject(name: String, description: String) { requireSuccess(client.post(path("/api/mobile/projects")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(ProjectRequest(name, description.ifBlank { null })) }) }
    suspend fun createTask(requestText: String, projectId: Int? = null) { requireSuccess(client.post(path("/api/mobile/tasks")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(TaskRequest(requestText, projectId)) }) }
    suspend fun changeTaskStatus(id: String, status: String) { requireSuccess(client.put(path("/api/mobile/tasks/$id")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(TaskActionRequest(status)) }) }
    suspend fun createMemory(title: String, content: String, layer: String) { requireSuccess(client.post(path("/api/mobile/memories")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(MemoryRequest(title, content, layer)) }) }
    suspend fun updateMemory(id: String, title: String, content: String, layer: String) { requireSuccess(client.put(path("/api/mobile/memories/$id")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(MemoryRequest(title, content, layer)) }) }
    suspend fun deleteMemory(id: String) { requireSuccess(client.delete(path("/api/mobile/memories/$id")) { header(authorized().first, authorized().second) }) }
    suspend fun setToolPolicy(key: String, policy: String) { requireSuccess(client.put(path("/api/mobile/tools/$key")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(ToolPermissionRequest(policy)) }) }
    suspend fun uploadFile(name: String, mimeType: String, dataBase64: String) { requireSuccess(client.post(path("/api/mobile/files")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(FileUploadRequest(name, mimeType, dataBase64)) }) }
    suspend fun saveProvider(input: ProviderRequest) { requireSuccess(client.put(path("/api/mobile/provider")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }) }
    suspend fun usage(): UsageWire = client.get(path("/api/mobile/usage")) { header(authorized().first, authorized().second) }.body()
    suspend fun generateImage(prompt: String): GeneratedImageWire { val response = client.post(path("/api/mobile/images")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(ImageRequest(prompt)) }; requireSuccess(response); return response.body() }
    suspend fun inspectGitHub(repository: String): GitHubInspectionWire = client.get(path("/api/mobile/github?repository=$repository")) { header(authorized().first, authorized().second) }.body()
    suspend fun research(query: String, sources: List<String>): ResearchResultWire { val response = client.post(path("/api/mobile/research")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(ResearchRequest(query, sources)) }; requireSuccess(response); return response.body() }
    suspend fun createLearningCandidate(input: LearningCandidateRequest): MobileLearningCandidateWire { val response = client.post(path("/api/mobile/learning-candidates")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }; requireSuccess(response); return response.body() }
    suspend fun reviewLearningCandidate(id: String, input: LearningDecisionRequest) { requireSuccess(client.put(path("/api/mobile/learning-candidates/$id")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }) }
    suspend fun createImprovement(input: ImprovementRequest): MobileImprovementWire { val response = client.post(path("/api/mobile/improvements")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }; requireSuccess(response); return response.body() }
    suspend fun reviewImprovement(id: String, input: ImprovementDecisionRequest) { requireSuccess(client.put(path("/api/mobile/improvements/$id")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }) }
    suspend fun observeTask(id: String, input: TaskObservationRequest) { requireSuccess(client.post(path("/api/mobile/tasks/$id/observe")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }) }
    suspend fun selectTaskTool(id: String, input: TaskToolRequest) { requireSuccess(client.post(path("/api/mobile/tasks/$id/select-tool")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }) }
    suspend fun approveTaskTool(id: String, input: TaskToolApprovalRequest) { requireSuccess(client.post(path("/api/mobile/tasks/$id/tool-approval")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }) }
    suspend fun repairTask(id: String, input: TaskRepairRequest) { requireSuccess(client.post(path("/api/mobile/tasks/$id/repair")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }) }
    suspend fun escalateTask(id: String, input: TaskEscalationRequest) { requireSuccess(client.post(path("/api/mobile/tasks/$id/escalate")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }) }
    suspend fun verifyTask(id: String, input: TaskVerificationRequest) { requireSuccess(client.post(path("/api/mobile/tasks/$id/verify")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }) }
    suspend fun createCapabilityGrant(input: CapabilityGrantRequest): MobileCapabilityGrantWire { val response = client.post(path("/api/mobile/capability-grants")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }; requireSuccess(response); return response.body() }
    suspend fun updateCapabilityGrant(id: String, status: String) { requireSuccess(client.put(path("/api/mobile/capability-grants/$id")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(CapabilityGrantDecisionRequest(status)) }) }
    suspend fun recordDeviceAudit(capability: String, scope: String, detail: String, outcome: String) { requireSuccess(client.post(path("/api/mobile/device-audit")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(DeviceAuditRequest(capability, scope, detail, outcome)) }) }
    suspend fun connectGitHub(token: String, scopes: String): MobileGitHubConnectionWire { val response = client.post(path("/api/mobile/github/connection")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(GitHubConnectionRequest(token, scopes)) }; requireSuccess(response); return response.body() }
    suspend fun disconnectGitHub() { requireSuccess(client.delete(path("/api/mobile/github/connection")) { header(authorized().first, authorized().second) }) }
    suspend fun proposeGitHubOperation(input: GitHubOperationRequest): MobileGitHubOperationWire { val response = client.post(path("/api/mobile/github/operations")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(input) }; requireSuccess(response); return response.body() }
    suspend fun approveGitHubOperation(id: String): GitHubOperationResultWire { val response = client.post(path("/api/mobile/github/operations/$id/approve")) { header(authorized().first, authorized().second) }; requireSuccess(response); return response.body() }
    suspend fun cancelGitHubOperation(id: String) { requireSuccess(client.post(path("/api/mobile/github/operations/$id/cancel")) { header(authorized().first, authorized().second) }) }

    suspend fun sendMessage(content: String, onSnapshot: suspend (String) -> Unit = {}): String? {
        val response = client.post(path("/api/agent/stream")) { header(authorized().first, authorized().second); contentType(ContentType.Application.Json); setBody(StreamRequest(content)) }
        var answer: String? = null
        val channel = response.bodyAsChannel(); val frame = StringBuilder()
        suspend fun consumeFrame() {
            val data = frame.lineSequence().firstOrNull { it.startsWith("data: ") }?.removePrefix("data: ") ?: return
            when (val event = decodeStreamEvent(data)) {
                is MobileStreamEvent.MessageSnapshot -> { if (event.content != answer) onSnapshot(event.content); answer = event.content }
                is MobileStreamEvent.Error -> throw IllegalStateException(event.message)
                MobileStreamEvent.Done, null -> Unit
            }
        }
        while (!channel.isClosedForRead) { val line = channel.readUTF8Line() ?: break; if (line.isEmpty()) { consumeFrame(); frame.clear() } else frame.appendLine(line) }
        if (frame.isNotEmpty()) consumeFrame()
        return answer
    }

    companion object {
        suspend fun exchangeMobileGrant(baseUrl: String, code: String, codeVerifier: String): String {
            val client = HttpClient(OkHttp) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            return try {
                val response = client.post("${baseUrl.trimEnd('/')}/api/mobile/auth/exchange") { contentType(ContentType.Application.Json); setBody(MobileAuthExchangeRequest(code, codeVerifier)) }
                if (response.status.value !in 200..299) throw IllegalStateException(response.bodyAsText().take(300).ifBlank { "Unable to complete sign-in." })
                response.body<MobileAuthExchangeWire>().accessToken
            } finally { client.close() }
        }
    }
}
