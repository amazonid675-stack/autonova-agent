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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
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
@Serializable data class MobileBootstrap(val projects: List<MobileProjectWire>, val tasks: List<MobileTaskWire>, val memories: List<MobileMemoryWire>, val activity: List<MobileActivityWire>, val files: List<MobileFileWire> = emptyList(), val toolPermissions: List<MobileToolWire> = emptyList(), val provider: MobileProviderWire? = null)
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

internal sealed interface MobileStreamEvent {
    data class MessageSnapshot(val content: String) : MobileStreamEvent
    data class Error(val message: String) : MobileStreamEvent
    data object Done : MobileStreamEvent
}

/** The protected endpoint emits complete assistant snapshots, so each payload replaces prior content. */
internal fun decodeStreamEvent(data: String): MobileStreamEvent? {
    val event = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
    return when (event["type"]?.jsonPrimitive?.content) {
        "message" -> event["content"]?.jsonPrimitive?.content?.let(MobileStreamEvent::MessageSnapshot)
        "error" -> MobileStreamEvent.Error(event["message"]?.jsonPrimitive?.content ?: "Unable to complete the response.")
        "done" -> MobileStreamEvent.Done
        else -> null
    }
}

class MobileAgentApi(private val baseUrl: String, private val sessionCookie: String) {
    private val client = HttpClient(OkHttp) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    private fun path(value: String) = "${baseUrl.trimEnd('/')}$value"
    private suspend fun requireSuccess(response: HttpResponse) {
        if (response.status.value !in 200..299) throw IllegalStateException(response.bodyAsText().take(300).ifBlank { "Mobile request failed (${response.status.value})." })
    }

    suspend fun bootstrap(): MobileBootstrap = client.get(path("/api/mobile/bootstrap")) { header(HttpHeaders.Cookie, sessionCookie) }.body()
    suspend fun registerDevice(device: DeviceRegistration) { requireSuccess(client.post(path("/api/mobile/devices")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(device) }) }
    suspend fun createProject(name: String, description: String) { requireSuccess(client.post(path("/api/mobile/projects")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(ProjectRequest(name, description.ifBlank { null })) }) }
    suspend fun createTask(requestText: String, projectId: Int? = null) { requireSuccess(client.post(path("/api/mobile/tasks")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(TaskRequest(requestText, projectId)) }) }
    suspend fun changeTaskStatus(id: String, status: String) { requireSuccess(client.put(path("/api/mobile/tasks/$id")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(TaskActionRequest(status)) }) }
    suspend fun createMemory(title: String, content: String, layer: String) { requireSuccess(client.post(path("/api/mobile/memories")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(MemoryRequest(title, content, layer)) }) }
    suspend fun updateMemory(id: String, title: String, content: String, layer: String) { requireSuccess(client.put(path("/api/mobile/memories/$id")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(MemoryRequest(title, content, layer)) }) }
    suspend fun deleteMemory(id: String) { requireSuccess(client.delete(path("/api/mobile/memories/$id")) { header(HttpHeaders.Cookie, sessionCookie) }) }
    suspend fun setToolPolicy(key: String, policy: String) { requireSuccess(client.put(path("/api/mobile/tools/$key")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(ToolPermissionRequest(policy)) }) }
    suspend fun uploadFile(name: String, mimeType: String, dataBase64: String) { requireSuccess(client.post(path("/api/mobile/files")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(FileUploadRequest(name, mimeType, dataBase64)) }) }
    suspend fun saveProvider(input: ProviderRequest) { requireSuccess(client.put(path("/api/mobile/provider")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(input) }) }
    suspend fun usage(): UsageWire = client.get(path("/api/mobile/usage")) { header(HttpHeaders.Cookie, sessionCookie) }.body()
    suspend fun generateImage(prompt: String): GeneratedImageWire { val response = client.post(path("/api/mobile/images")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(ImageRequest(prompt)) }; requireSuccess(response); return response.body() }
    suspend fun inspectGitHub(repository: String): GitHubInspectionWire = client.get(path("/api/mobile/github?repository=$repository")) { header(HttpHeaders.Cookie, sessionCookie) }.body()

    suspend fun sendMessage(content: String, onSnapshot: suspend (String) -> Unit = {}): String? {
        val response = client.post(path("/api/agent/stream")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(StreamRequest(content)) }
        var answer: String? = null
        val channel = response.bodyAsChannel()
        val frame = StringBuilder()
        suspend fun consumeFrame() {
            val data = frame.lineSequence().firstOrNull { it.startsWith("data: ") }?.removePrefix("data: ") ?: return
            when (val event = decodeStreamEvent(data)) {
                is MobileStreamEvent.MessageSnapshot -> { if (event.content != answer) onSnapshot(event.content); answer = event.content }
                is MobileStreamEvent.Error -> throw IllegalStateException(event.message)
                MobileStreamEvent.Done, null -> Unit
            }
        }
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.isEmpty()) { consumeFrame(); frame.clear() } else frame.appendLine(line)
        }
        if (frame.isNotEmpty()) consumeFrame()
        return answer
    }
}
