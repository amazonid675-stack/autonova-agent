package im.autonova.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import im.autonova.mobile.data.AgentDatabase
import im.autonova.mobile.data.AgentRepository
import im.autonova.mobile.data.ChatMessage
import im.autonova.mobile.data.SecureConfig
import im.autonova.mobile.sync.AgentSyncWorker
import kotlinx.coroutines.launch

class AutonovaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AgentRepository(AgentDatabase.create(application).cacheDao(), SecureConfig(application))
    val messages = repository.messages
    val tasks = repository.tasks
    val projects = repository.projects
    val memories = repository.memories
    val activity = repository.activity
    init { AgentSyncWorker.enqueue(application) }

    fun submit(text: String) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        repository.appendLocalMessage(ChatMessage("local-$now", "user", text, now))
        if (text.lowercase().startsWith("build") || text.lowercase().startsWith("create")) repository.createLocalTask(text)
    }
    fun refresh() = viewModelScope.launch { if (!repository.refresh()) AgentSyncWorker.enqueue(getApplication()) }
}
