package im.autonova.mobile.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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
@Serializable data class MobileBootstrap(val projects: List<MobileProjectWire>, val tasks: List<MobileTaskWire>, val memories: List<MobileMemoryWire>, val activity: List<MobileActivityWire>, val files: List<MobileFileWire> = emptyList(), val toolPermissions: List<MobileToolWire> = emptyList(), val provider: MobileProviderWire? = null)
@Serializable data class DeviceRegistration(val deviceId: String, val label: String, val pushEnabled: Boolean)
@Serializable data class StreamRequest(val content: String)

class MobileAgentApi(private val baseUrl: String, private val sessionCookie: String) {
    private val client = HttpClient(OkHttp) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    private fun path(value: String) = "${baseUrl.trimEnd('/')}$value"
    suspend fun bootstrap(): MobileBootstrap = client.get(path("/api/mobile/bootstrap")) { header(HttpHeaders.Cookie, sessionCookie) }.body()
    suspend fun registerDevice(device: DeviceRegistration) = client.post(path("/api/mobile/devices")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(device) }
    suspend fun sendMessage(content: String): String? {
        val response = client.post(path("/api/agent/stream")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(StreamRequest(content)) }
        var answer: String? = null
        response.bodyAsText().split("\n\n").forEach { frame ->
            val data = frame.lineSequence().firstOrNull { it.startsWith("data: ") }?.removePrefix("data: ") ?: return@forEach
            runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull()?.let { event -> if (event["type"]?.jsonPrimitive?.content == "message") answer = event["content"]?.jsonPrimitive?.content }
        }
        return answer
    }
}
