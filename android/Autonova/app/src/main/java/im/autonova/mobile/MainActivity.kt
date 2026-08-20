package im.autonova.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import im.autonova.mobile.ui.AutonovaApp
import im.autonova.mobile.ui.AutonovaViewModel

internal fun mobileAuthCodeFromIntent(intent: android.content.Intent?): String? = intent?.data
    ?.takeIf { it.scheme == "autonova" && it.host == "auth" }
    ?.getQueryParameter("code")

data class SharedAgentContent(val text: String? = null, val uri: android.net.Uri? = null, val mimeType: String = "text/plain")

internal fun sharedAgentContentFromIntent(intent: android.content.Intent?): SharedAgentContent? {
    if (intent?.action != android.content.Intent.ACTION_SEND) return null
    val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() }
    val uri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
    return if (text != null || uri != null) SharedAgentContent(text, uri, intent.type ?: "application/octet-stream") else null
}

class MainActivity : ComponentActivity() {
    private val viewModel: AutonovaViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AutonovaApp(viewModel) }; handleIncomingIntent(intent) }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); handleIncomingIntent(intent) }
    private fun handleIncomingIntent(intent: android.content.Intent?) { handleAuthIntent(intent); sharedAgentContentFromIntent(intent)?.let(viewModel::importSharedContent) }
    private fun handleAuthIntent(intent: android.content.Intent?) { mobileAuthCodeFromIntent(intent)?.let(viewModel::completeMobileSignIn) }
}
