package im.autonova.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import im.autonova.mobile.data.AgentDatabase
import im.autonova.mobile.data.AgentRepository
import im.autonova.mobile.data.LocalDocument
import im.autonova.mobile.data.SecureConfig
import im.autonova.mobile.sync.AgentSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AutonovaViewModel(application: Application) : AndroidViewModel(application) {
    private val config = SecureConfig(application)
    private val repository = AgentRepository(AgentDatabase.create(application).cacheDao(), config)
    private val _configured = MutableStateFlow(config.isConfigured())
    val configured: StateFlow<Boolean> = _configured.asStateFlow()
    val messages = repository.messages; val tasks = repository.tasks; val projects = repository.projects; val memories = repository.memories; val activity = repository.activity; val files = repository.files; val tools = repository.tools; val provider = repository.provider
    init { AgentSyncWorker.enqueue(application) }
    fun submit(text: String) { if (text.isNotBlank()) viewModelScope.launch { repository.submit(text) } }
    fun refresh() = viewModelScope.launch { if (!repository.refresh()) AgentSyncWorker.enqueue(getApplication()) }
    fun saveConnection(endpoint: String, sessionCookie: String): Boolean = runCatching { config.saveApiBaseUrl(endpoint.trim()); config.saveSessionCookie(sessionCookie.trim()); _configured.value = true; refresh() }.isSuccess
    fun clearConnection() { config.clearSession(); _configured.value = false }
    fun createProject(name: String, description: String) = viewModelScope.launch { repository.createProject(name, description) }
    fun createTask(request: String) = viewModelScope.launch { repository.createTask(request) }
    fun createMemory(title: String, content: String, layer: String = "PERSONAL") = viewModelScope.launch { repository.createMemory(title, content, layer) }
    fun updateMemory(id: String, title: String, content: String, layer: String) = viewModelScope.launch { repository.updateMemory(id, title, content, layer) }
    fun deleteMemory(id: String) = viewModelScope.launch { repository.deleteMemory(id) }
    fun setToolPolicy(key: String, policy: String) = viewModelScope.launch { repository.setToolPolicy(key, policy) }
    fun uploadLocalFile(document: LocalDocument, bytes: ByteArray) = viewModelScope.launch { repository.uploadFile(document, bytes) }
}
