package im.autonova.mobile.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import im.autonova.mobile.BuildConfig
import java.util.UUID

enum class OperatingMode { LOCAL_ONLY, LOCAL_PLUS_INTERNET, OPTIONAL_REMOTE_AGENT }

class SecureConfig(private val context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "autonova-secure", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun saveApiBaseUrl(value: String) { require(EndpointPolicy.isAllowed(value)) { "Only public HTTPS endpoints are permitted." }; prefs.edit().putString("api_base_url", value).apply() }
    fun apiBaseUrl(): String? = prefs.getString("api_base_url", null)?.takeIf { EndpointPolicy.isAllowed(it) } ?: BuildConfig.DEFAULT_SERVER_ORIGIN.takeIf { EndpointPolicy.isAllowed(it) }
    fun setOperatingMode(mode: OperatingMode) = prefs.edit().putString("operating_mode", mode.name).apply()
    fun operatingMode(): OperatingMode = prefs.getString("operating_mode", null)?.let { runCatching { OperatingMode.valueOf(it) }.getOrNull() } ?: OperatingMode.LOCAL_ONLY
    fun internetEnabled(): Boolean = operatingMode() != OperatingMode.LOCAL_ONLY
    fun remoteAgentEnabled(): Boolean = operatingMode() == OperatingMode.OPTIONAL_REMOTE_AGENT && apiBaseUrl() != null
    fun saveSessionCookie(value: String) { require(value.startsWith("session=")) { "Only the session cookie is stored." }; prefs.edit().putString("session_cookie", value).apply() }
    fun sessionCookie(): String? = prefs.getString("session_cookie", null)
    fun saveAccessToken(value: String) { require(value.length >= 32 && value.count { it == '.' } == 2) { "Invalid mobile access token." }; prefs.edit().putString("access_token", value).apply() }
    fun accessToken(): String? = prefs.getString("access_token", null)
    fun saveCodeVerifier(value: String) { require(value.length in 32..256) { "Invalid sign-in verifier." }; prefs.edit().putString("code_verifier", value).apply() }
    fun consumeCodeVerifier(): String? = prefs.getString("code_verifier", null)?.also { prefs.edit().remove("code_verifier").apply() }
    fun deviceId(): String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
    fun saveStorageTree(value: String) { require(value.startsWith("content://")) { "Only Android document-provider folders are permitted." }; prefs.edit().putString("storage_tree", value).apply() }
    fun storageTree(): String? = prefs.getString("storage_tree", null)
    fun clearStorageTree() = prefs.edit().remove("storage_tree").apply()
    fun saveLocalModelPath(value: String) { require(value.startsWith(contextFilesPrefix())) { "Local model must be imported into private app storage." }; prefs.edit().putString("local_model_path", value).apply() }
    fun localModelPath(): String? = prefs.getString("local_model_path", null)
    fun clearLocalModel() = prefs.edit().remove("local_model_path").apply()
    fun setNotificationsEnabled(enabled: Boolean) = prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    fun notificationsEnabled(): Boolean = prefs.getBoolean("notifications_enabled", false)
    fun setBackgroundSyncEnabled(enabled: Boolean) = prefs.edit().putBoolean("background_sync_enabled", enabled).apply()
    fun backgroundSyncEnabled(): Boolean = prefs.getBoolean("background_sync_enabled", true)
    fun setBackgroundRequiresCharging(enabled: Boolean) = prefs.edit().putBoolean("background_requires_charging", enabled).apply()
    fun backgroundRequiresCharging(): Boolean = prefs.getBoolean("background_requires_charging", false)
    fun setBackgroundRequiresUnmeteredNetwork(enabled: Boolean) = prefs.edit().putBoolean("background_requires_unmetered", enabled).apply()
    fun backgroundRequiresUnmeteredNetwork(): Boolean = prefs.getBoolean("background_requires_unmetered", false)
    fun setBackgroundIntervalMinutes(minutes: Long) { require(minutes >= 15) { "Android background work requires at least a 15-minute interval." }; prefs.edit().putLong("background_interval_minutes", minutes).apply() }
    fun backgroundIntervalMinutes(): Long = prefs.getLong("background_interval_minutes", 60L).coerceAtLeast(15L)
    fun setLearningReviewEnabled(enabled: Boolean) = prefs.edit().putBoolean("learning_review_enabled", enabled).apply()
    fun learningReviewEnabled(): Boolean = prefs.getBoolean("learning_review_enabled", false)
    fun hasLearningFingerprint(fingerprint: String): Boolean = prefs.getStringSet("learning_fingerprints", emptySet())?.contains(fingerprint) == true
    fun markLearningFingerprint(fingerprint: String) { val current = (prefs.getStringSet("learning_fingerprints", emptySet()) ?: emptySet()).toMutableSet(); current.add(fingerprint); while (current.size > 80) current.remove(current.first()); prefs.edit().putStringSet("learning_fingerprints", current).apply() }
    private fun contextFilesPrefix(): String = context.filesDir.absolutePath
    fun isConfigured(): Boolean = remoteAgentEnabled() && accessToken() != null
    fun clearSession() = prefs.edit().remove("access_token").remove("session_cookie").remove("code_verifier").apply()
}
