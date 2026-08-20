package im.autonova.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import im.autonova.mobile.ui.AutonovaApp
import im.autonova.mobile.ui.AutonovaViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AutonovaViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AutonovaApp(viewModel) }; handleAuthIntent(intent) }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); handleAuthIntent(intent) }
    private fun handleAuthIntent(intent: android.content.Intent?) { intent?.data?.takeIf { it.scheme == "autonova" && it.host == "auth" }?.getQueryParameter("code")?.let(viewModel::completeMobileSignIn) }
}
