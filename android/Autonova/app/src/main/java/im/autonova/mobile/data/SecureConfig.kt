package im.autonova.mobile.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class SecureConfig(context: Context) { 
    private val prefs = EncryptedSharedPreferences.create(context, "autonova-secure", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun saveApiBaseUrl(value: String) { require(EndpointPolicy.isAllowed(value)) { "Only public HTTPS endpoints are permitted." }; prefs.edit().putString("api_base_url", value).apply() }
    fun apiBaseUrl(): String? = prefs.getString("api_base_url", null)
    fun saveSessionCookie(value: String) { require(value.startsWith("session=")) { "Only the session cookie is stored." }; prefs.edit().putString("session_cookie", value).apply() }
    fun sessionCookie(): String? = prefs.getString("session_cookie", null)
    fun deviceId(): String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
    fun isConfigured(): Boolean = apiBaseUrl() != null && sessionCookie() != null
    fun clearSession() = prefs.edit().clear().apply()
}
