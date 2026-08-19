package im.autonova.mobile.data

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Keep OAuth/session exchange and protected backend calls out of Compose UI. */
class AgentRepository(private val cache: AgentCacheDao, private val config: SecureConfig) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList()); private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList()); private val _projects = MutableStateFlow<List<AgentProject>>(emptyList())
    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList()); private val _activity = MutableStateFlow<List<ActivityItem>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow(); val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow(); val projects: StateFlow<List<AgentProject>> = _projects.asStateFlow(); val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow(); val activity: StateFlow<List<ActivityItem>> = _activity.asStateFlow()
    init { scope.launch { cache.observeTasks().collect { cached -> _tasks.value = cached.map { AgentTask(it.id, it.request, TaskStatus.valueOf(it.status), it.updatedAt) } } }; scope.launch { cache.observeMessages().collect { cached -> _messages.value = cached.map { ChatMessage(it.id, it.role, it.content, it.createdAt) } } }; scope.launch { cache.observeProjects().collect { cached -> _projects.value = cached.map { AgentProject(it.id, it.name, it.description) } } }; scope.launch { cache.observeMemories().collect { cached -> _memories.value = cached.map { MemoryItem(it.id, it.title, it.content, it.layer) } } }; scope.launch { cache.observeActivity().collect { cached -> _activity.value = cached.map { ActivityItem(it.id, it.title, it.detail, it.eventType) } } } }
    fun createLocalTask(request: String) { val now = System.currentTimeMillis(); scope.launch { cache.upsertTasks(listOf(CachedTask("local-$now", request, TaskStatus.PLANNING.name, now))) } }
    fun appendLocalMessage(message: ChatMessage) { scope.launch { cache.upsertMessage(CachedMessage(message.id, message.role, message.content, message.createdAt)) } }
    suspend fun refresh(): Boolean {
        val endpoint = config.apiBaseUrl() ?: return false; val cookie = config.sessionCookie() ?: return false
        val api = MobileAgentApi(endpoint, cookie)
        api.registerDevice(DeviceRegistration(config.deviceId(), "Android ${Build.MODEL}", false))
        val snapshot = api.bootstrap()
        cache.upsertProjects(snapshot.projects.map { CachedProject(it.id.toString(), it.name, it.description ?: "") })
        cache.upsertTasks(snapshot.tasks.map { CachedTask(it.id.toString(), it.request, it.status, System.currentTimeMillis()) })
        cache.upsertMemories(snapshot.memories.map { CachedMemory(it.id.toString(), it.title, it.content, it.layer) })
        cache.upsertActivity(snapshot.activity.map { CachedActivity(it.id.toString(), it.title, it.detail ?: "", it.eventType) })
        return true
    }
}

data class MemoryItem(val id: String, val title: String, val content: String, val layer: String)
data class ActivityItem(val id: String, val title: String, val detail: String, val eventType: String)
