package im.autonova.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Ink = Color(0xFF0B0B15)
private val Panel = Color(0xFF151521)
private val Lavender = Color(0xFFA98BFF)
private val Cloud = Color(0xFFF2F0FF)

@Composable fun AutonovaApp(viewModel: AutonovaViewModel = viewModel()) {
    var section by remember { mutableStateOf("Command") }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = Ink, surface = Panel, primary = Lavender, onPrimary = Ink, onBackground = Cloud, onSurface = Cloud)) {
        Scaffold(containerColor = Ink, bottomBar = { MobileNavigation(section) { section = it } }) { padding ->
            when (section) {
                "Command" -> CommandScreen(viewModel, Modifier.padding(padding))
                "Tasks" -> TasksScreen(viewModel, Modifier.padding(padding))
                "Projects" -> DetailScreen("Projects", "Workspaces preserve conversations, files, tools, and durable project context.", Modifier.padding(padding))
                "Memory" -> MemoryScreen(viewModel, Modifier.padding(padding))
                else -> MoreScreen(viewModel, Modifier.padding(padding))
            }
        }
    }
}

@Composable private fun MobileNavigation(selected: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Panel) {
        listOf("Command" to Icons.Outlined.Home, "Tasks" to Icons.Outlined.AutoAwesome, "Projects" to Icons.Outlined.Folder, "Memory" to Icons.Outlined.Memory, "More" to Icons.Outlined.MoreHoriz).forEach { (label, icon) ->
            NavigationBarItem(selected = label == selected, onClick = { onSelect(label) }, icon = { Icon(icon, label) }, label = { Text(label) })
        }
    }
}

@Composable private fun CommandScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val messages by viewModel.messages.collectAsState()
    var draft by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PERSONAL AGENT", color = Lavender, style = MaterialTheme.typography.labelSmall)
        Text("Command center", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        AssistChip(onClick = {}, label = { Text("Secure session") })
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Lavender)
                Spacer(Modifier.height(14.dp))
                Text("What would you like to move forward today?")
                Spacer(Modifier.height(8.dp))
                Text("Try “Build a website for my business” to create a visible task plan.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            } else LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(messages) { message -> Text(message.content, modifier = Modifier.fillMaxWidth(), color = if (message.role == "user") Lavender else Cloud) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask Autonova…") })
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.submit(draft); draft = "" }) { Icon(Icons.Outlined.Add, "Send", tint = Lavender) }
        }
    }
}

@Composable private fun TasksScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val tasks by viewModel.tasks.collectAsState()
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Plan → execute → observe → verify. The server remains the source of truth once synchronized.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        if (tasks.isEmpty()) Text("Tasks created from requests such as “Build a website” will appear here.", color = Color.LightGray)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(tasks) { task -> Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(16.dp)) { Text(task.status.name, color = Lavender, style = MaterialTheme.typography.labelSmall); Text(task.request, modifier = Modifier.padding(top = 6.dp)); Text(task.summary, color = Color.LightGray, style = MaterialTheme.typography.bodySmall) } } } }
    }
}

@Composable private fun MoreScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val activity by viewModel.activity.collectAsState()
    val surfaces = listOf("Files" to "Explicit attachment and document context.", "Tools" to "Permissioned capabilities with Ask, Allow, and Deny.", "GitHub" to "Read-only public context; writes require approval.", "Settings" to "Encrypted mobile configuration and provider session.", "Activity" to "Visible action summaries without hidden reasoning.")
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("More", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); surfaces.forEach { (title, detail) -> Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, color = Color.LightGray, style = MaterialTheme.typography.bodySmall) } } }; if (activity.isNotEmpty()) { Text("Recent activity", color = Lavender, style = MaterialTheme.typography.labelSmall); activity.take(3).forEach { item -> Text(item.title, color = Cloud, style = MaterialTheme.typography.bodySmall) } } }
}

@Composable private fun DetailScreen(title: String, description: String, modifier: Modifier) { Column(modifier.fillMaxSize().padding(20.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text(description, color = Color.LightGray) } }

@Composable private fun MemoryScreen(viewModel: AutonovaViewModel, modifier: Modifier) { val memories by viewModel.memories.collectAsState(); Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Memory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Review, edit, or remove the information the agent may carry forward.", color = Color.LightGray); if (memories.isEmpty()) Text("No synchronized memories yet.", color = Color.LightGray) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(memories) { memory -> Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(14.dp)) { Text(memory.layer, color = Lavender, style = MaterialTheme.typography.labelSmall); Text(memory.title); Text(memory.content, color = Color.LightGray, style = MaterialTheme.typography.bodySmall) } } } } } }
