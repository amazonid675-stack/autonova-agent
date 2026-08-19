package im.autonova.mobile.ui

import androidx.lifecycle.ViewModel
import im.autonova.mobile.data.AgentRepository
import im.autonova.mobile.data.ChatMessage

class AutonovaViewModel(private val repository: AgentRepository = AgentRepository()) : ViewModel() {
    val messages = repository.messages
    val tasks = repository.tasks
    val projects = repository.projects

    fun submit(text: String) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        repository.appendLocalMessage(ChatMessage("local-$now", "user", text, now))
        if (text.lowercase().startsWith("build") || text.lowercase().startsWith("create")) repository.createLocalTask(text)
    }
}
