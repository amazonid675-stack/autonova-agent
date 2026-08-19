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
    val files = repository.files
    val tools = repository.tools
    val provider = repository.provider
    init { AgentSyncWorker.enqueue(application) }

    fun submit(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repository.submit(text) }
    }
    fun refresh() = viewModelScope.launch { if (!repository.refresh()) AgentSyncWorker.enqueue(getApplication()) }
}
