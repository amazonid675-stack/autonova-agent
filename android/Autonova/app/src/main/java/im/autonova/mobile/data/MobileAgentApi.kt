package im.autonova.mobile.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
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
    suspend fun bootstrap(): MobileBootstrap = client.get(path("/api/mobile/bootstrap")) { header(HttpHeaders.Cookie, sessionCookie) }.body()
    suspend fun registerDevice(device: DeviceRegistration) = client.post(path("/api/mobile/devices")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(device) }

    suspend fun sendMessage(content: String, onSnapshot: suspend (String) -> Unit = {}): String? {
        val response = client.post(path("/api/agent/stream")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(StreamRequest(content)) }
        var answer: String? = null
        val channel = response.bodyAsChannel()
        val frame = StringBuilder()
        suspend fun consumeFrame() {
            val data = frame.lineSequence().firstOrNull { it.startsWith("data: ") }?.removePrefix("data: ") ?: return
            when (val event = decodeStreamEvent(data)) {
                is MobileStreamEvent.MessageSnapshot -> {
                    if (event.content != answer) onSnapshot(event.content)
                    answer = event.content
                }
                is MobileStreamEvent.Error -> throw IllegalStateException(event.message)
                MobileStreamEvent.Done, null -> Unit
            }
        }
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.isEmpty()) {
                consumeFrame()
                frame.clear()
            } else {
                frame.appendLine(line)
            }
        }
        if (frame.isNotEmpty()) consumeFrame()
        return answer
    }
}
