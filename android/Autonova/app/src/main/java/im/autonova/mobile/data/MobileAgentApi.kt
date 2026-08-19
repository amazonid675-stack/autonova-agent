package im.autonova.mobile.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class MobileTaskWire(val id: Int, val request: String, val status: String, val updatedAt: String? = null, val errorSummary: String? = null)
@Serializable data class MobileProjectWire(val id: Int, val name: String, val description: String? = null)
@Serializable data class MobileMemoryWire(val id: Int, val title: String, val content: String, val layer: String)
@Serializable data class MobileActivityWire(val id: Int, val title: String, val detail: String? = null, val eventType: String)
@Serializable data class MobileBootstrap(val projects: List<MobileProjectWire>, val tasks: List<MobileTaskWire>, val memories: List<MobileMemoryWire>, val activity: List<MobileActivityWire>)
@Serializable data class DeviceRegistration(val deviceId: String, val label: String, val pushEnabled: Boolean)

class MobileAgentApi(private val baseUrl: String, private val sessionCookie: String) {
    private val client = HttpClient(OkHttp) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    private fun path(value: String) = "${baseUrl.trimEnd('/')}$value"
    suspend fun bootstrap(): MobileBootstrap = client.get(path("/api/mobile/bootstrap")) { header(HttpHeaders.Cookie, sessionCookie) }.body()
    suspend fun registerDevice(device: DeviceRegistration) = client.post(path("/api/mobile/devices")) { header(HttpHeaders.Cookie, sessionCookie); contentType(ContentType.Application.Json); setBody(device) }
}
