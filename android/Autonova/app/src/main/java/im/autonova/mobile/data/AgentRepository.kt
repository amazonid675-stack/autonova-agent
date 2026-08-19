package im.autonova.mobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keep OAuth/session exchange and protected backend calls out of Compose UI. */
class AgentRepository {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList()); private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList()); private val _projects = MutableStateFlow<List<AgentProject>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow(); val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow(); val projects: StateFlow<List<AgentProject>> = _projects.asStateFlow()
    fun createLocalTask(request: String) { val now = System.currentTimeMillis(); _tasks.value = listOf(AgentTask("local-$now", request, TaskStatus.PLANNING, now, "Waiting for secure backend sync.")) + _tasks.value }
    fun appendLocalMessage(message: ChatMessage) { _messages.value = _messages.value + message }
}
