package im.autonova.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import im.autonova.mobile.data.DeviceStorage
import im.autonova.mobile.data.FileItem
import im.autonova.mobile.data.LocalDocument
import im.autonova.mobile.data.MemoryItem
import im.autonova.mobile.data.SecureConfig
import im.autonova.mobile.data.ToolItem

private val Ink = Color(0xFF0B0B15)
private val Panel = Color(0xFF151521)
private val Lavender = Color(0xFFA98BFF)
private val Cloud = Color(0xFFF2F0FF)
private val Muted = Color(0xFFB8B4C8)

@Composable fun AutonovaApp(viewModel: AutonovaViewModel = viewModel()) {
    var section by remember { mutableStateOf("Command") }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = Ink, surface = Panel, primary = Lavender, onPrimary = Ink, onBackground = Cloud, onSurface = Cloud)) {
        Scaffold(containerColor = Ink, bottomBar = { MobileNavigation(section) { section = it } }) { padding ->
            when (section) {
                "Command" -> CommandScreen(viewModel, Modifier.padding(padding))
                "Tasks" -> TasksScreen(viewModel, Modifier.padding(padding))
                "Projects" -> ProjectsScreen(viewModel, Modifier.padding(padding))
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

@Composable private fun PageHeader(kicker: String, title: String, detail: String? = null) {
    Text(kicker.uppercase(), color = Lavender, style = MaterialTheme.typography.labelSmall)
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    detail?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
}

@Composable private fun CommandScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val messages by viewModel.messages.collectAsState(); val configured by viewModel.configured.collectAsState(); var draft by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("Personal agent workspace", "Command center", if (configured) "Protected session connected. Assistant responses appear as they stream." else "Connect a protected session in More → Settings to activate the agent.")
        AssistChip(onClick = { viewModel.refresh() }, label = { Text(if (configured) "Secure session" else "Setup required") })
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Lavender); Spacer(Modifier.height(14.dp)); Text("What would you like to move forward today?"); Spacer(Modifier.height(8.dp)); Text("Ask for research, a plan, a project, a task, or analysis of an uploaded document.", color = Muted, style = MaterialTheme.typography.bodySmall)
            } else LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages, key = { it.id }) { message ->
                    Column(Modifier.fillMaxWidth()) { Text(if (message.role == "user") "YOU" else "AUTONOVA", color = if (message.role == "user") Lavender else Muted, style = MaterialTheme.typography.labelSmall); Text(message.content, color = if (message.role == "user") Lavender else Cloud, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Give Autonova a task or question…") }, minLines = 1, maxLines = 4)
            Spacer(Modifier.width(8.dp)); Button(onClick = { viewModel.submit(draft); draft = "" }, enabled = draft.isNotBlank()) { Text("Send") }
        }
    }
}

@Composable private fun TasksScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val tasks by viewModel.tasks.collectAsState(); var request by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Agent task engine", "Tasks", "Create protected tasks, then observe planning, execution, verification, and completion from the same workspace.")
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = request, onValueChange = { request = it }, modifier = Modifier.weight(1f), label = { Text("New task") }); Spacer(Modifier.width(8.dp)); Button(onClick = { viewModel.createTask(request); request = "" }, enabled = request.isNotBlank()) { Text("Create") } }
        if (tasks.isEmpty()) Text("No synchronized tasks yet.", color = Muted) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(tasks, key = { it.id }) { task -> SurfaceCard { Text(task.status.name, color = Lavender, style = MaterialTheme.typography.labelSmall); Text(task.request, modifier = Modifier.padding(top = 6.dp)); if (task.summary.isNotBlank()) Text(task.summary, color = Muted, style = MaterialTheme.typography.bodySmall) } } }
    }
}

@Composable private fun ProjectsScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val projects by viewModel.projects.collectAsState(); var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Workspaces", "Projects", "Organize conversations, tasks, files, and memory around focused workspaces.")
        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Project name") })
        OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Purpose") })
        Button(onClick = { viewModel.createProject(name, description); name = ""; description = "" }, enabled = name.isNotBlank()) { Text("Create workspace") }
        if (projects.isEmpty()) Text("No synchronized workspaces yet.", color = Muted) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(projects, key = { it.id }) { project -> SurfaceCard { Text(project.name, fontWeight = FontWeight.SemiBold); Text(project.description.ifBlank { "No description provided." }, color = Muted, style = MaterialTheme.typography.bodySmall) } } }
    }
}

@Composable private fun MemoryScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val memories by viewModel.memories.collectAsState(); var title by remember { mutableStateOf("") }; var content by remember { mutableStateOf("") }; var deleting by remember { mutableStateOf<MemoryItem?>(null) }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeader("Persistent context", "Memory", "Review what may carry forward, add personal context, and remove memories you no longer want retained.")
        OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Memory title") })
        OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Memory content") }, minLines = 2)
        Button(onClick = { viewModel.createMemory(title, content); title = ""; content = "" }, enabled = title.isNotBlank() && content.isNotBlank()) { Text("Save memory") }
        if (memories.isEmpty()) Text("No synchronized memories yet.", color = Muted) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(memories, key = { it.id }) { memory -> SurfaceCard { Text(memory.layer, color = Lavender, style = MaterialTheme.typography.labelSmall); Text(memory.title, fontWeight = FontWeight.SemiBold); Text(memory.content, color = Muted, style = MaterialTheme.typography.bodySmall); Row { TextButton(onClick = { title = memory.title; content = memory.content }) { Text("Edit") }; TextButton(onClick = { deleting = memory }) { Text("Remove") } } } } }
    }
    deleting?.let { memory -> ConfirmDialog("Remove memory?", "This removes ‘${memory.title}’ from your Autonova account.", "Remove", { viewModel.deleteMemory(memory.id); deleting = null }) { deleting = null } }
}

@Composable private fun MoreScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    var selected by remember { mutableStateOf<String?>(null) }
    val options = listOf("Files & Storage" to "Secure cloud files and a folder you choose on this device.", "Tools" to "Permissioned capabilities with Ask, Allow, and Deny.", "Settings" to "Encrypted endpoint and session configuration.", "Activity" to "Visible agent action summaries and device events.")
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Control plane", selected ?: "More", if (selected == null) "Every action remains visible and under your control." else null)
        if (selected == null) options.forEach { (title, detail) -> SurfaceCard(Modifier.clickable { selected = title }) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall) } } else {
            when (selected) { "Files & Storage" -> FilesAndStorageScreen(viewModel); "Tools" -> ToolsScreen(viewModel); "Settings" -> SettingsScreen(viewModel); "Activity" -> ActivityScreen(viewModel) }
            TextButton(onClick = { selected = null }) { Text("Back") }
        }
    }
}

@Composable private fun FilesAndStorageScreen(viewModel: AutonovaViewModel) {
    val remoteFiles by viewModel.files.collectAsState(); val context = LocalContext.current; val storage = remember(context) { DeviceStorage(context, SecureConfig(context)) }; var localFiles by remember { mutableStateOf(storage.listFiles()) }; var noteTitle by remember { mutableStateOf("") }; var noteContent by remember { mutableStateOf("") }; var deleting by remember { mutableStateOf<LocalDocument?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) { runCatching { storage.selectTree(uri) }; localFiles = storage.listFiles() } }
    Text("Files & device storage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Autonova can only see the folder you select below. Files are not uploaded unless you choose Upload.", color = Muted, style = MaterialTheme.typography.bodySmall)
    Row { Button(onClick = { folderPicker.launch(null) }) { Text(if (storage.hasFolder()) "Change folder" else "Choose folder") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { localFiles = storage.listFiles() }) { Text("Refresh") } }
    if (storage.hasFolder()) {
        OutlinedTextField(value = noteTitle, onValueChange = { noteTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New local note") })
        OutlinedTextField(value = noteContent, onValueChange = { noteContent = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Note text") }, minLines = 2)
        Button(onClick = { storage.createNote(noteTitle, noteContent); noteTitle = ""; noteContent = ""; localFiles = storage.listFiles() }, enabled = noteContent.isNotBlank()) { Text("Create note") }
        Text("Selected folder", color = Lavender, style = MaterialTheme.typography.labelSmall)
        localFiles.forEach { document -> SurfaceCard { Text(document.name, fontWeight = FontWeight.SemiBold); Text("${document.mimeType} · ${document.sizeBytes} bytes", color = Muted, style = MaterialTheme.typography.bodySmall); Row { TextButton(onClick = { runCatching { storage.open(document) } }) { Text("Open") }; TextButton(onClick = { runCatching { storage.share(document) } }) { Text("Share") }; TextButton(onClick = { storage.readBytes(document)?.let { viewModel.uploadLocalFile(document, it) } }) { Text("Upload") }; TextButton(onClick = { deleting = document }) { Text("Delete") } } } }
    } else Text("Choose a document-provider folder to enable scoped local storage actions.", color = Muted)
    if (remoteFiles.isNotEmpty()) { Text("Autonova workspace files", color = Lavender, style = MaterialTheme.typography.labelSmall); remoteFiles.forEach { file -> RemoteFileRow(file) } }
    deleting?.let { document -> ConfirmDialog("Delete local file?", "This permanently deletes ‘${document.name}’ from the folder you selected.", "Delete", { storage.delete(document); localFiles = storage.listFiles(); deleting = null }) { deleting = null } }
}

@Composable private fun RemoteFileRow(file: FileItem) = SurfaceCard { Text(file.name, fontWeight = FontWeight.SemiBold); Text("${file.mimeType} · ${file.sizeBytes} bytes", color = Muted, style = MaterialTheme.typography.bodySmall) }

@Composable private fun ToolsScreen(viewModel: AutonovaViewModel) {
    val tools by viewModel.tools.collectAsState(); Text("Tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Choose the default approval policy for each capability. Ask is the safest default.", color = Muted, style = MaterialTheme.typography.bodySmall)
    if (tools.isEmpty()) Text("Tool policies appear after your protected session synchronizes.", color = Muted) else tools.forEach { tool -> ToolRow(tool, viewModel) }
}

@Composable private fun ToolRow(tool: ToolItem, viewModel: AutonovaViewModel) {
    val next = when (tool.policy) { "ASK" -> "ALLOW"; "ALLOW" -> "DENY"; else -> "ASK" }
    SurfaceCard { Text(tool.key, fontWeight = FontWeight.SemiBold); Row(verticalAlignment = Alignment.CenterVertically) { Text("Policy: ${tool.policy}", color = Lavender, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.width(8.dp)); TextButton(onClick = { viewModel.setToolPolicy(tool.key, next) }) { Text("Set $next") } } }
}

@Composable private fun SettingsScreen(viewModel: AutonovaViewModel) {
    val configured by viewModel.configured.collectAsState(); val provider by viewModel.provider.collectAsState(); var endpoint by remember { mutableStateOf("") }; var cookie by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    Text("Secure connection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("The endpoint and session cookie are encrypted on this device. Only an HTTPS Autonova endpoint is accepted.", color = Muted, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Autonova HTTPS endpoint") }, singleLine = true)
    OutlinedTextField(value = cookie, onValueChange = { cookie = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Session cookie (session=…)") }, singleLine = true)
    Button(onClick = { if (viewModel.saveConnection(endpoint, cookie)) { error = null } else error = "Use a public HTTPS endpoint and a session= cookie." }, enabled = endpoint.isNotBlank() && cookie.isNotBlank()) { Text("Save secure connection") }
    if (configured) Text("Connected", color = Lavender); error?.let { Text(it, color = Color(0xFFFFA7A7)) }; provider?.let { SurfaceCard { Text("Active provider", color = Lavender, style = MaterialTheme.typography.labelSmall); Text("${it.name} · ${it.costMode}"); Text("Model: ${it.model ?: "automatic"}", color = Muted, style = MaterialTheme.typography.bodySmall) } }
    if (configured) TextButton(onClick = { viewModel.clearConnection() }) { Text("Clear local session") }
}

@Composable private fun ActivityScreen(viewModel: AutonovaViewModel) {
    val activity by viewModel.activity.collectAsState(); Text("Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Action summaries only—never hidden reasoning or credentials.", color = Muted, style = MaterialTheme.typography.bodySmall)
    if (activity.isEmpty()) Text("No synchronized activity yet.", color = Muted) else activity.forEach { item -> SurfaceCard { Text(item.eventType, color = Lavender, style = MaterialTheme.typography.labelSmall); Text(item.title, fontWeight = FontWeight.SemiBold); if (item.detail.isNotBlank()) Text(item.detail, color = Muted, style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content) } }

@Composable private fun ConfirmDialog(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { Button(onClick = onConfirm) { Text(action) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
