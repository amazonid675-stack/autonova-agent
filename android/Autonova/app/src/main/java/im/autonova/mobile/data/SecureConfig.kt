package im.autonova.mobile.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureConfig(context: Context) { private val prefs = EncryptedSharedPreferences.create(context, "autonova-secure", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM); fun saveApiBaseUrl(value: String) = prefs.edit().putString("api_base_url", value).apply(); fun apiBaseUrl(): String? = prefs.getString("api_base_url", null); fun clearSession() = prefs.edit().clear().apply() }
