package im.autonova.mobile.data

import java.net.URI

object EndpointPolicy {
    fun isAllowed(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.host !in setOf("localhost", "127.0.0.1", "::1") && !uri.host.endsWith(".local")
    } catch (_: Exception) { false }
}
