package im.autonova.mobile.ui

import android.Manifest
import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import im.autonova.mobile.data.DeviceStorage
import im.autonova.mobile.data.FileItem
import im.autonova.mobile.data.LocalDocument
import im.autonova.mobile.data.LocalKnowledgeUsage
import im.autonova.mobile.data.MemoryItem
import im.autonova.mobile.data.OperatingMode
import im.autonova.mobile.data.SecureConfig
import im.autonova.mobile.data.ToolItem
import im.autonova.mobile.data.VoiceAssistant
import im.autonova.mobile.data.ScreenshotCapture
import java.io.ByteArrayOutputStream

private val Ink = Color(0xFF0B0B15)
private val Panel = Color(0xFF151521)
private val Lavender = Color(0xFFA98BFF)
private val Cloud = Color(0xFFF2F0FF)
private val Muted = Color(0xFFB8B4C8)
internal sealed interface LocalStorageAction {
    data object CreateNote : LocalStorageAction
    data class CreateWorkspace(val name: String) : LocalStorageAction
    data class Open(val document: LocalDocument) : LocalStorageAction
    data class Share(val document: LocalDocument) : LocalStorageAction
    data class Delete(val document: LocalDocument) : LocalStorageAction
    data class Edit(val document: LocalDocument) : LocalStorageAction
    data class SaveEdit(val document: LocalDocument, val content: String) : LocalStorageAction
    data class ExportCopy(val document: LocalDocument) : LocalStorageAction
    data class Archive(val document: LocalDocument) : LocalStorageAction
    data object ClearLocalIndex : LocalStorageAction
    data object ClearActivityCache : LocalStorageAction
}

@Composable fun AutonovaApp(viewModel: AutonovaViewModel = viewModel()) {
    var section by remember { mutableStateOf("Command") }
    val feedback by viewModel.feedback.collectAsState()
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = Ink, surface = Panel, surfaceVariant = Panel, primary = Lavender, onPrimary = Ink, onBackground = Cloud, onSurface = Cloud, onSurfaceVariant = Muted, outline = Muted)) {
        Scaffold(containerColor = Ink, bottomBar = { MobileNavigation(section) { section = it } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                feedback?.let { FeedbackBanner(it, viewModel::clearFeedback) }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (section) {
                        "Command" -> CommandScreen(viewModel, Modifier.fillMaxSize())
                        "Tasks" -> TasksScreen(viewModel, Modifier.fillMaxSize())
                        "Projects" -> ProjectsScreen(viewModel, Modifier.fillMaxSize())
                        "Memory" -> MemoryScreen(viewModel, Modifier.fillMaxSize())
                        else -> MoreScreen(viewModel, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable private fun MobileNavigation(selected: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Panel) {
        listOf(Triple("Command", "Home", Icons.Outlined.Home), Triple("Tasks", "Tasks", Icons.Outlined.AutoAwesome), Triple("Projects", "Projects", Icons.Outlined.Folder), Triple("Memory", "Memory", Icons.Outlined.Memory), Triple("More", "More", Icons.Outlined.MoreHoriz)).forEach { (section, label, icon) ->
            NavigationBarItem(selected = section == selected, onClick = { onSelect(section) }, icon = { Icon(icon, label) }, label = { Text(label) })
        }
    }
}

@Composable private fun PageHeader(kicker: String, title: String, detail: String? = null) {
    Text(kicker.uppercase(), color = Lavender, style = MaterialTheme.typography.labelSmall)
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    detail?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
}

@Composable private fun FeedbackBanner(feedback: MobileFeedback, onDismiss: () -> Unit) {
    val accent = when (feedback.tone) { FeedbackTone.ERROR -> Color(0xFFFFA7A7); FeedbackTone.SUCCESS -> Color(0xFF8FE6BE); FeedbackTone.INFO -> Lavender }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (feedback.isLoading) "WORKING" else "AUTONOVA", color = accent, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(10.dp)); Text(feedback.message, color = Cloud, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (!feedback.isLoading) TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CommandScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val messages by viewModel.messages.collectAsState(); val configured by viewModel.configured.collectAsState(); val connectionState by viewModel.connectionState.collectAsState(); val operatingMode by viewModel.operatingMode.collectAsState(); val context = LocalContext.current; var draft by remember { mutableStateOf("") }; var confirmClipboard by remember { mutableStateOf<String?>(null) }
    val voice = remember(context) { VoiceAssistant(context) }
    val screenshotCapture = remember(context) { ScreenshotCapture(context) }
    DisposableEffect(voice) { onDispose { voice.close() } }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) voice.listen({ draft = it }, { draft = it; viewModel.submit(it) }, { viewModel.showError(it) }) else viewModel.showError("Microphone permission is required for voice input.") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> bitmap?.let { uploadCameraBitmap(it, viewModel) } }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) camera.launch(null) else viewModel.showError("Camera permission is required before taking a picture.") }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { uploadSelectedVisual(context, it, "image", viewModel) } }
    val screenshotConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> screenshotCapture.capture(result.resultCode, result.data) { bytes -> bytes.onSuccess { viewModel.uploadDeviceContext("screenshot-${System.currentTimeMillis()}.jpg", "image/jpeg", it) }.onFailure { viewModel.showError(it.message ?: "Screenshot capture failed.") } } }
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeader("Personal agent workspace", "Command center", if (configured) "Your optional remote agent is connected. Responses and task updates appear here." else "Local projects, memories, tasks, and document storage work now. Private offline answers need a compatible local model.")
        if (!configured) SurfaceCard {
            Text("Start working locally", color = Lavender, style = MaterialTheme.typography.labelSmall)
            Text("You can create a project, memory, or task without signing in. Prompts are saved locally; import a compatible `.task` model from More → Device capabilities to receive private offline answers.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(viewModel.localModelStatus(), color = if (viewModel.localModelReady()) Color(0xFF8FE6BE) else Muted, style = MaterialTheme.typography.bodySmall)
            if (operatingMode == OperatingMode.OPTIONAL_REMOTE_AGENT) {
                Text("Remote work is optional. Sign in only if you choose to use your configured remote agent.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { viewModel.beginMobileSignIn()?.let { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it)) } }) { Text(if (connectionState == ConnectionState.CONNECTING) "Continue sign-in" else "Connect optional agent") }
            }
        } else AssistChip(onClick = { viewModel.refresh() }, label = { Text("Optional agent connected") })
        SurfaceCard {
            Text("Device inputs", color = Lavender, style = MaterialTheme.typography.labelSmall)
            Text("Voice, camera, screenshots, files, shared items, URLs, and clipboard content become agent context only after you choose an action.", color = Muted, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) { TextButton(onClick = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Voice") }; TextButton(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("Camera") }; TextButton(onClick = { photoPicker.launch("image/*") }) { Text("Image") }; TextButton(onClick = { screenshotConsent.launch(screenshotCapture.consentIntent()) }) { Text("Screenshot") }; TextButton(onClick = { val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; val clip = clipboard.primaryClip; val text = if (clip != null && clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) clip.getItemAt(0).coerceToText(context).toString() else ""; confirmClipboard = text.ifBlank { null }; if (text.isBlank()) viewModel.showError("Clipboard does not contain text to import.") }) { Text("Clipboard") } }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Lavender); Spacer(Modifier.height(14.dp)); Text("What would you like to move forward today?"); Spacer(Modifier.height(8.dp)); Text("Try: “Build a website for my business”, “Research this topic”, or “Plan my project”. Autonova creates visible tasks for multi-step work.", color = Muted, style = MaterialTheme.typography.bodySmall)
            } else LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages, key = { it.id }) { message ->
                    Column(Modifier.fillMaxWidth()) { Text(if (message.role == "user") "YOU" else "AUTONOVA", color = if (message.role == "user") Lavender else Muted, style = MaterialTheme.typography.labelSmall); Text(message.content, color = if (message.role == "user") Lavender else Cloud, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
        }
        Text("Prompt", color = Lavender, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), label = { Text("Give Autonova a task or question") }, placeholder = { Text("Example: Plan my week") }, minLines = 1, maxLines = 3)
            Spacer(Modifier.width(8.dp)); Button(onClick = { val request = draft.trim(); if (request.isBlank()) viewModel.showError("Write a prompt before sending it.") else { viewModel.submit(request); draft = "" } }) { Text("Send") }
        }
        Text(if (configured) "Your prompt will use the connected optional agent." else "Your prompt is saved locally. Without an imported model, Autonova will explain the next setup step instead of pretending it completed an answer.", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (messages.lastOrNull()?.role == "assistant") TextButton(onClick = { if (!voice.speak(messages.last().content)) viewModel.showError("Voice output is unavailable on this device.") }) { Text("Read latest response aloud") }
    }
    confirmClipboard?.let { text -> ConfirmDialog("Import clipboard text?", "This sends the selected clipboard text to your agent workspace.", "Import", { viewModel.recordDeviceAction("clipboard.import", "User confirmed importing clipboard text into the agent workspace."); viewModel.submit("Clipboard context from Android:\n$text"); confirmClipboard = null }) { confirmClipboard = null } }
}

@Composable private fun TasksScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val tasks by viewModel.tasks.collectAsState(); val taskEvidence by viewModel.taskEvidence.collectAsState(); var request by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Agent task engine", "Tasks", "Create protected tasks, then observe planning, execution, verification, and completion from the same workspace.")
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = request, onValueChange = { request = it }, modifier = Modifier.weight(1f), label = { Text("New task") }); Spacer(Modifier.width(8.dp)); Button(onClick = { viewModel.createTask(request); request = "" }, enabled = request.isNotBlank()) { Text("Create") } }
        Text("Tasks created here are queued locally in Local Only mode. Connected tasks add remote lifecycle updates when you choose to sign in.", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (tasks.isEmpty()) Text("No local or synchronized tasks yet. Write a task above and tap Create.", color = Muted) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(tasks, key = { it.id }) { task ->
            var note by remember(task.id) { mutableStateOf("") }; var selectedTool by remember(task.id) { mutableStateOf("web_search") }; val evidenceForTask = taskEvidence.filter { it.taskId == task.id }
            SurfaceCard {
                Text(task.status.name, color = Lavender, style = MaterialTheme.typography.labelSmall); Text(task.request, modifier = Modifier.padding(top = 6.dp)); if (task.summary.isNotBlank()) Text(task.summary, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (evidenceForTask.isNotEmpty()) { Text("Evidence & decisions", color = Lavender, style = MaterialTheme.typography.labelSmall); evidenceForTask.take(3).forEach { item -> Text("${item.kind.lowercase().replace('_', ' ')} · ${item.outcome}: ${item.summary}", color = Muted, style = MaterialTheme.typography.bodySmall); if (item.evidence.isNotBlank()) Text(item.evidence, color = Muted, style = MaterialTheme.typography.bodySmall) } }
                if (task.status.name !in listOf("COMPLETED", "CANCELLED")) OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Observation, evidence, or decision note") }, minLines = 2)
                when (task.status.name) {
                    "RUNNING", "PLANNING" -> { Row { Button(onClick = { viewModel.observeTask(task.id, note, note) }, enabled = note.length >= 3) { Text("Record observation") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { viewModel.selectTaskTool(task.id, selectedTool, note.ifBlank { "Owner selected this scoped tool for the task." }) }) { Text("Select web tool") } }; Text("Tool selection follows its Ask, Allow, or Deny policy; it is never silently executed.", color = Muted, style = MaterialTheme.typography.bodySmall) }
                    "WAITING_FOR_USER" -> { val pendingTool = evidenceForTask.firstOrNull { it.kind == "TOOL_SELECTION" && it.outcome == "PENDING" }?.toolKey ?: selectedTool; Row { Button(onClick = { viewModel.approveTaskTool(task.id, pendingTool, true, note) }) { Text("Approve $pendingTool") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { viewModel.approveTaskTool(task.id, pendingTool, false, note) }) { Text("Decline") } } }
                    "VERIFYING" -> Row { Button(onClick = { viewModel.verifyTask(task.id, true, note) }, enabled = note.length >= 3) { Text("Verify passed") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { viewModel.verifyTask(task.id, false, note) }, enabled = note.length >= 3) { Text("Verify failed") } }
                    "FAILED", "WAITING_FOR_TOOL" -> Row { Button(onClick = { viewModel.repairTask(task.id, note) }, enabled = note.length >= 3) { Text("Start repair") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { viewModel.escalateTask(task.id, "HUMAN_REVIEW", note) }, enabled = note.length >= 3) { Text("Escalate") } }
                }
                if (task.status.name !in listOf("COMPLETED", "CANCELLED", "FAILED")) TextButton(onClick = { viewModel.changeTaskStatus(task.id, "CANCELLED") }) { Text("Cancel task") } else if (task.status.name in listOf("CANCELLED", "FAILED")) TextButton(onClick = { viewModel.changeTaskStatus(task.id, "QUEUED") }) { Text("Retry task") }
            }
        } }
    }
}

@Composable private fun ProjectsScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val projects by viewModel.projects.collectAsState(); var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Workspaces", "Projects", "Organize conversations, tasks, files, and memory around focused workspaces.")
        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Project name") })
        OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Purpose") })
        Button(onClick = { viewModel.createProject(name, description); name = ""; description = "" }, enabled = name.isNotBlank()) { Text("Create workspace") }
        Text("Enter a project name to create a local workspace immediately. It stays on this phone unless you later choose a remote workflow.", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (projects.isEmpty()) Text("No local workspaces yet. Create one above to start organizing work offline.", color = Muted) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(projects, key = { it.id }) { project -> SurfaceCard { Text(project.name, fontWeight = FontWeight.SemiBold); Text(project.description.ifBlank { "No description provided." }, color = Muted, style = MaterialTheme.typography.bodySmall) } } }
    }
}

@Composable private fun MemoryScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    val memories by viewModel.memories.collectAsState(); var title by remember { mutableStateOf("") }; var content by remember { mutableStateOf("") }; var editingId by remember { mutableStateOf<String?>(null) }; var deleting by remember { mutableStateOf<MemoryItem?>(null) }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeader("Persistent context", "Memory", "Review what may carry forward, add personal context, and remove memories you no longer want retained.")
        OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Memory title") })
        OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Memory content") }, minLines = 2)
        Button(onClick = { val existingId = editingId; if (existingId == null) viewModel.createMemory(title, content) else viewModel.updateMemory(existingId, title, content, "PERSONAL"); title = ""; content = ""; editingId = null }, enabled = title.isNotBlank() && content.isNotBlank()) { Text(if (editingId == null) "Save memory" else "Update memory") }
        Text("Saved memories are available to local prompts on this phone. Enter both fields to save one; remote synchronization is optional.", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (editingId != null) TextButton(onClick = { title = ""; content = ""; editingId = null }) { Text("Cancel edit") }
        if (memories.isEmpty()) Text("No saved memories yet. Add one above; it is stored locally in Local Only mode.", color = Muted) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(memories, key = { it.id }) { memory -> SurfaceCard { Text(memory.layer, color = Lavender, style = MaterialTheme.typography.labelSmall); Text(memory.title, fontWeight = FontWeight.SemiBold); Text(memory.content, color = Muted, style = MaterialTheme.typography.bodySmall); Row { TextButton(onClick = { title = memory.title; content = memory.content; editingId = memory.id }) { Text("Edit") }; TextButton(onClick = { deleting = memory }) { Text("Remove") } } } } }
    }
    deleting?.let { memory -> ConfirmDialog("Remove memory?", "This removes ‘${memory.title}’ from your Autonova account.", "Remove", { viewModel.deleteMemory(memory.id); deleting = null }) { deleting = null } }
}

@Composable private fun ColumnScope.ResearchScreen(viewModel: AutonovaViewModel) {
    val research by viewModel.research.collectAsState(); val context = LocalContext.current; var query by remember { mutableStateOf("") }; var sources by remember { mutableStateOf("") }; var confirmSearch by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Research", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Search visibly in your browser, then paste or share one to five public HTTPS source URLs. Autonova reads only the sources you select and synthesizes them with gpt-5-mini, recording citations and usage.", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Research question") }, minLines = 2)
        Row { Button(onClick = { confirmSearch = query.trim().length >= 3 }, enabled = query.trim().length >= 3) { Text("Search web") }; Spacer(Modifier.width(8.dp)); Button(onClick = { viewModel.runResearch(query, sources.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()) }, enabled = query.trim().length >= 3 && sources.lineSequence().any { it.trim().startsWith("https://") }) { Text("Research selected sources") } }
        OutlinedTextField(value = sources, onValueChange = { sources = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Public HTTPS source URLs — one per line") }, minLines = 3)
        if (research.isEmpty()) Text("No completed research sessions yet.", color = Muted) else research.forEach { session -> SurfaceCard { Text(session.status, color = Lavender, style = MaterialTheme.typography.labelSmall); Text(session.query, fontWeight = FontWeight.SemiBold); if (session.summary.isNotBlank()) Text(session.summary, color = Muted, style = MaterialTheme.typography.bodySmall) } }
    }
    if (confirmSearch) ConfirmDialog("Open web search?", "Open your visible browser to search for: ${query.trim()}? Share or paste only the public sources you want Autonova to read.", "Open search", { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://www.google.com/search?q=${Uri.encode(query.trim())}")); confirmSearch = false }, { confirmSearch = false })
}

@Composable private fun ColumnScope.LearningScreen(viewModel: AutonovaViewModel) {
    val candidates by viewModel.learningCandidates.collectAsState(); val grants by viewModel.capabilityGrants.collectAsState(); val improvements by viewModel.improvements.collectAsState(); var title by remember { mutableStateOf("") }; var content by remember { mutableStateOf("") }; var capability by remember { mutableStateOf("web.research") }; var scope by remember { mutableStateOf("") }; var rationale by remember { mutableStateOf("") }; var improvementScope by remember { mutableStateOf("WORKFLOW") }; var improvementTitle by remember { mutableStateOf("") }; var improvementChange by remember { mutableStateOf("") }; var improvementEvidence by remember { mutableStateOf("") }; var improvementTest by remember { mutableStateOf("") }; var improvementBenchmark by remember { mutableStateOf("") }; var improvementVersion by remember { mutableStateOf("1.0") }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Learning & autonomy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Autonova proposes memories and improvement records for review. Approval never changes model weights or external services by itself; every change requires evidence, a version label, and an explicit review or rollback decision.", color = Muted, style = MaterialTheme.typography.bodySmall)
        SurfaceCard { Text("Propose a memory", fontWeight = FontWeight.SemiBold); OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Memory title") }); OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxWidth(), label = { Text("What should Autonova remember?") }, minLines = 2); Button(onClick = { viewModel.createLearningCandidate(title, content); title = ""; content = "" }, enabled = title.isNotBlank() && content.isNotBlank()) { Text("Add for review") } }
        SurfaceCard { Text("Propose a controlled improvement", fontWeight = FontWeight.SemiBold); Text("Use this to record a prompt, tool, workflow, or model-routing change with evidence, a test result, and a benchmark comparison before it can be approved.", color = Muted, style = MaterialTheme.typography.bodySmall); Row { listOf("PROMPT", "TOOL", "WORKFLOW", "MODEL_ROUTING").forEach { item -> AssistChip(onClick = { improvementScope = item }, label = { Text(if (improvementScope == item) "✓ ${item.lowercase().replace('_', ' ')}" else item.lowercase().replace('_', ' ')) }) } }; OutlinedTextField(value = improvementTitle, onValueChange = { improvementTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Change title") }); OutlinedTextField(value = improvementChange, onValueChange = { improvementChange = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Proposed change") }, minLines = 2); OutlinedTextField(value = improvementEvidence, onValueChange = { improvementEvidence = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Evidence") }, minLines = 2); OutlinedTextField(value = improvementTest, onValueChange = { improvementTest = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Test outcome") }, minLines = 2); OutlinedTextField(value = improvementBenchmark, onValueChange = { improvementBenchmark = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Benchmark comparison") }, minLines = 2); OutlinedTextField(value = improvementVersion, onValueChange = { improvementVersion = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Version label") }); Button(onClick = { viewModel.createImprovement(improvementScope, improvementTitle, improvementChange, improvementEvidence, improvementTest, improvementBenchmark, improvementVersion); improvementTitle = ""; improvementChange = ""; improvementEvidence = ""; improvementTest = ""; improvementBenchmark = "" }, enabled = improvementTitle.isNotBlank() && improvementChange.isNotBlank() && improvementEvidence.isNotBlank() && improvementTest.isNotBlank() && improvementBenchmark.isNotBlank() && improvementVersion.isNotBlank()) { Text("Save for review") } }
        SurfaceCard { Text("Capability grants", fontWeight = FontWeight.SemiBold); Text("A grant records a narrow user-approved scope. It does not override Android permissions, service account controls, or confirmation for consequential actions.", color = Muted, style = MaterialTheme.typography.bodySmall); OutlinedTextField(value = capability, onValueChange = { capability = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Capability key") }); OutlinedTextField(value = scope, onValueChange = { scope = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Scope") }); OutlinedTextField(value = rationale, onValueChange = { rationale = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Why this scope?") }); Button(onClick = { viewModel.createCapabilityGrant(capability, scope, rationale); scope = ""; rationale = "" }, enabled = capability.isNotBlank() && scope.isNotBlank()) { Text("Propose grant") } }
        if (candidates.isNotEmpty()) { Text("Learning review queue", color = Lavender, style = MaterialTheme.typography.labelSmall); candidates.forEach { candidate -> SurfaceCard { Text(candidate.status, color = Lavender, style = MaterialTheme.typography.labelSmall); Text(candidate.title, fontWeight = FontWeight.SemiBold); Text(candidate.content, color = Muted, style = MaterialTheme.typography.bodySmall); if (candidate.status == "PENDING") Row { Button(onClick = { viewModel.reviewLearningCandidate(candidate.id, "APPROVED") }) { Text("Approve") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { viewModel.reviewLearningCandidate(candidate.id, "DISMISSED") }) { Text("Dismiss") } } } } }
        if (improvements.isNotEmpty()) { Text("Controlled improvement history", color = Lavender, style = MaterialTheme.typography.labelSmall); improvements.forEach { improvement -> SurfaceCard { Text("${improvement.scope} · ${improvement.status}", color = Lavender, style = MaterialTheme.typography.labelSmall); Text(improvement.title, fontWeight = FontWeight.SemiBold); Text("Version ${improvement.versionLabel}", color = Muted, style = MaterialTheme.typography.bodySmall); Text(improvement.proposedChange, color = Muted, style = MaterialTheme.typography.bodySmall); Text("Evidence: ${improvement.evidence}", color = Muted, style = MaterialTheme.typography.bodySmall); if (improvement.testOutcome.isNotBlank()) Text("Test: ${improvement.testOutcome}", color = Muted, style = MaterialTheme.typography.bodySmall); if (improvement.benchmarkSummary.isNotBlank()) Text("Benchmark: ${improvement.benchmarkSummary}", color = Muted, style = MaterialTheme.typography.bodySmall); if (improvement.reviewNote.isNotBlank()) Text("Review: ${improvement.reviewNote}", color = Muted, style = MaterialTheme.typography.bodySmall); if (improvement.status == "PENDING") Row { Button(onClick = { viewModel.reviewImprovement(improvement.id, "APPROVED", "Approved by the Android owner after reviewing the supplied evidence.") }) { Text("Approve") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { viewModel.reviewImprovement(improvement.id, "REJECTED", "Rejected by the Android owner after reviewing the supplied evidence.") }) { Text("Reject") } } else if (improvement.status == "APPROVED") TextButton(onClick = { viewModel.reviewImprovement(improvement.id, "ROLLED_BACK", "Rolled back by the Android owner after review.") }) { Text("Roll back") } } } }
        if (grants.isNotEmpty()) { Text("Capability grant history", color = Lavender, style = MaterialTheme.typography.labelSmall); grants.forEach { grant -> SurfaceCard { Text("${grant.capability} · ${grant.status}", fontWeight = FontWeight.SemiBold); Text(grant.scope, color = Muted, style = MaterialTheme.typography.bodySmall); if (grant.outcome.isNotBlank()) Text("Outcome: ${grant.outcome}", color = Lavender, style = MaterialTheme.typography.bodySmall); if (grant.rationale.isNotBlank()) Text(grant.rationale, color = Muted, style = MaterialTheme.typography.bodySmall); if (grant.status == "PENDING") Row { Button(onClick = { viewModel.updateCapabilityGrant(grant.id, "APPROVED") }) { Text("Approve") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { viewModel.updateCapabilityGrant(grant.id, "DECLINED") }) { Text("Decline") } } else if (grant.status == "APPROVED") TextButton(onClick = { viewModel.updateCapabilityGrant(grant.id, "REVOKED") }) { Text("Revoke") } } } }
    }
}

@Composable private fun MoreScreen(viewModel: AutonovaViewModel, modifier: Modifier) {
    var selected by remember { mutableStateOf<String?>(null) }
    val options = listOf("Files & Storage" to "Secure cloud files and a folder you choose on this device.", "Research" to "Search visibly, select public sources, and receive source-cited research.", "Learning & autonomy" to "Review proposed memories and narrow capability grants.", "Device capabilities" to "Voice, camera, screenshots, local AI, notifications, sharing, and background review.", "Tools" to "Permissioned capabilities with Ask, Allow, and Deny.", "Image Studio" to "Generate images through the protected Autonova image service.", "GitHub" to "Inspect repositories and prepare confirmation-gated GitHub operations.", "Usage" to "Review token, tool, and cost totals for your account.", "Settings" to "Browser sign-in, encrypted local credentials, and provider configuration.", "Activity" to "Visible agent action summaries and device events.")
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Control plane", selected ?: "More", if (selected == null) "Every action remains visible and under your control." else null)
        if (selected == null) Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { options.forEach { (title, detail) -> SurfaceCard(Modifier.clickable { selected = title }) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall) } } } else {
            when (selected) { "Files & Storage" -> FilesAndStorageScreen(viewModel); "Research" -> ResearchScreen(viewModel); "Learning & autonomy" -> LearningScreen(viewModel); "Device capabilities" -> DeviceCapabilitiesScreen(viewModel); "Tools" -> ToolsScreen(viewModel); "Image Studio" -> ImageStudioScreen(viewModel); "GitHub" -> GitHubScreen(viewModel); "Usage" -> UsageScreen(viewModel); "Settings" -> SettingsScreen(viewModel); "Activity" -> ActivityScreen(viewModel) }
            TextButton(onClick = { selected = null }) { Text("Back") }
        }
    }
}

@Composable private fun ColumnScope.DeviceCapabilitiesScreen(viewModel: AutonovaViewModel) {
    val context = LocalContext.current; val notifications = remember { mutableStateOf(viewModel.notificationsEnabled()) }; val pendingCloudFallback by viewModel.pendingCloudFallback.collectAsState(); val modelCapabilities by viewModel.modelCapabilities.collectAsState(); var localPrompt by remember { mutableStateOf("") }; var browserUrl by remember { mutableStateOf("") }; var browserConfirmation by remember { mutableStateOf(false) }; var backgroundEnabled by remember { mutableStateOf(viewModel.backgroundSyncEnabled()) }; var requiresCharging by remember { mutableStateOf(viewModel.backgroundRequiresCharging()) }; var requiresUnmetered by remember { mutableStateOf(viewModel.backgroundRequiresUnmeteredNetwork()) }; var learningReview by remember { mutableStateOf(viewModel.learningReviewEnabled()) }; var interval by remember { mutableStateOf(viewModel.backgroundIntervalMinutes().toString()) }
    val notificationsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> notifications.value = granted; viewModel.setNotificationsEnabled(granted); if (!granted) viewModel.showError("Notifications remain disabled until Android permission is granted.") }
    val localModelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importLocalModel) }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Device capabilities", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Autonova asks before it uses sensitive phone features. It never performs background browser, clipboard, file, or external actions without a visible user request.", color = Muted, style = MaterialTheme.typography.bodySmall)
        SurfaceCard { Text("Task completion notifications", fontWeight = FontWeight.SemiBold); Text("Receive a local alert when a synchronized agent task completes or fails.", color = Muted, style = MaterialTheme.typography.bodySmall); Button(onClick = { notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(if (notifications.value) "Notifications enabled" else "Enable notifications") } }
        SurfaceCard { Text("On-device local model", fontWeight = FontWeight.SemiBold); Text(viewModel.localModelStatus(), color = Muted, style = MaterialTheme.typography.bodySmall); Text("Private model storage: ${viewModel.localModelStorageBytes() / (1024 * 1024)} MB. Models stay in Android private storage and are not uploaded to Autonova.", color = Muted, style = MaterialTheme.typography.bodySmall); Row { Button(onClick = { localModelPicker.launch(arrayOf("application/octet-stream")) }) { Text("Import local model") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { viewModel.removeLocalModel() }) { Text("Remove model") } }; OutlinedTextField(value = localPrompt, onValueChange = { localPrompt = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Private local prompt") }); Button(onClick = { viewModel.runLocalModel(localPrompt) }, enabled = localPrompt.isNotBlank()) { Text("Run locally") } }
        SurfaceCard { Text("Model capability routing", fontWeight = FontWeight.SemiBold); Text("Each modality is labelled by its actual route. A listed capability does not grant access or silently enable a remote provider.", color = Muted, style = MaterialTheme.typography.bodySmall); if (modelCapabilities.isEmpty()) Text("Connect the optional remote agent to retrieve its capability matrix. Local model, scoped files, and Android services remain available under their own device controls.", color = Muted, style = MaterialTheme.typography.bodySmall) else modelCapabilities.forEach { capability -> Text("${capability.modality} · ${capability.route} · ${capability.availability}", color = Lavender, style = MaterialTheme.typography.labelSmall); Text(capability.note, color = Muted, style = MaterialTheme.typography.bodySmall) } }
        SurfaceCard { Text("Browser handoff", fontWeight = FontWeight.SemiBold); Text("Open a site visibly in your preferred browser. Autonova does not silently browse, log in, post, or purchase on your behalf.", color = Muted, style = MaterialTheme.typography.bodySmall); OutlinedTextField(value = browserUrl, onValueChange = { browserUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("https://example.com") }, singleLine = true); Button(onClick = { browserConfirmation = browserUrl.startsWith("https://") }) { Text("Open website") } }
        SurfaceCard { Text("Sharing and clipboard", fontWeight = FontWeight.SemiBold); Text("Use Android Share from another app to open Autonova with text, images, PDFs, or files. Clipboard import is available from Command and always asks for confirmation.", color = Muted, style = MaterialTheme.typography.bodySmall) }
        SurfaceCard { Text("Background learning review", fontWeight = FontWeight.SemiBold); Text("When enabled, Android may refresh your protected workspace and prepare reviewable preference candidates from your own Autonova messages. It never trains model weights, browses websites, or posts to services in the background. Outcomes appear in Activity and, if enabled, as a notification.", color = Muted, style = MaterialTheme.typography.bodySmall); Row { AssistChip(onClick = { backgroundEnabled = !backgroundEnabled }, label = { Text(if (backgroundEnabled) "✓ Background sync" else "Background sync") }); AssistChip(onClick = { requiresCharging = !requiresCharging }, label = { Text(if (requiresCharging) "✓ Charging only" else "Charging only") }); AssistChip(onClick = { requiresUnmetered = !requiresUnmetered }, label = { Text(if (requiresUnmetered) "✓ Unmetered only" else "Unmetered only") }); AssistChip(onClick = { learningReview = !learningReview }, label = { Text(if (learningReview) "✓ Propose learning" else "Propose learning") }) }; OutlinedTextField(value = interval, onValueChange = { interval = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Minimum minutes between checks (15 or more)") }, singleLine = true); Button(onClick = { viewModel.setBackgroundProfile(backgroundEnabled, requiresCharging, requiresUnmetered, interval.toLongOrNull()?.coerceAtLeast(15L) ?: 15L, learningReview) }) { Text(if (backgroundEnabled) "Save background profile" else "Pause background review") } }
        SurfaceCard { Text("Safe automation", fontWeight = FontWeight.SemiBold); Text("Background sync only reads your protected workspace when Android allows network work. Browser automation, external posting, file deletion, sharing, and clipboard imports require a direct, visible confirmation.", color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
    if (browserConfirmation) ConfirmDialog("Open website?", "Open $browserUrl in your browser?", "Open", { viewModel.recordDeviceAction("browser.handoff", "User confirmed opening $browserUrl in the device browser."); runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(browserUrl))) }.onFailure { viewModel.showError("No browser could open this URL.") }; browserConfirmation = false }, { browserConfirmation = false })
    pendingCloudFallback?.let { prompt -> ConfirmDialog("Use cloud fallback?", "Your local model could not complete this request. Send this prompt to your connected cloud agent instead?\n\n${prompt.take(300)}", "Use cloud", { viewModel.confirmCloudFallback() }, { viewModel.dismissCloudFallback() }) }
}

private fun uploadSelectedVisual(context: Context, uri: Uri, label: String, viewModel: AutonovaViewModel) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read selected $label.") }.onSuccess { viewModel.uploadDeviceContext("$label-${System.currentTimeMillis()}.bin", context.contentResolver.getType(uri) ?: "application/octet-stream", it) }.onFailure { viewModel.showError(it.message ?: "Unable to import $label.") } }
private fun uploadCameraBitmap(bitmap: Bitmap, viewModel: AutonovaViewModel) { val bytes = ByteArrayOutputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream); stream.toByteArray() }; viewModel.uploadDeviceContext("camera-${System.currentTimeMillis()}.jpg", "image/jpeg", bytes) }

@Composable private fun FilesAndStorageScreen(viewModel: AutonovaViewModel) {
    val remoteFiles by viewModel.files.collectAsState(); val localKnowledgeUsage by viewModel.localKnowledgeUsage.collectAsState(); val context = LocalContext.current; val storage = remember(context) { DeviceStorage(context, SecureConfig(context)) }
    var localFiles by remember { mutableStateOf(storage.listFiles()) }; var noteTitle by remember { mutableStateOf("") }; var noteContent by remember { mutableStateOf("") }; var workspaceName by remember { mutableStateOf("") }; var searchQuery by remember { mutableStateOf("") }; var pendingAction by remember { mutableStateOf<LocalStorageAction?>(null) }; var editingDocument by remember { mutableStateOf<LocalDocument?>(null) }; var editingText by remember { mutableStateOf("") }; var pendingExport by remember { mutableStateOf<LocalDocument?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) { runCatching { storage.selectTree(uri) }.onSuccess { viewModel.recordDeviceAction("storage.folder_access", "User selected a scoped document-provider folder.") }; localFiles = storage.listFiles() } }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val source = pendingExport
        if (source != null && uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            if (storage.exportCopy(source, uri) != null) viewModel.recordDeviceAction("storage.export_copy", "User exported a copy of ${source.name} to another selected Android document tree.") else viewModel.showError("Autonova could not create an export copy in that folder. The destination must allow writing.")
        }
        pendingExport = null
    }
    val visibleFiles = if (searchQuery.isBlank()) localFiles else storage.searchFiles(searchQuery)
    Text("Files & device storage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Autonova can only see folders you select. Local index, edit, search, and export actions stay on-device. Upload sends a selected file to your optional protected remote workspace only after you tap Upload; all other controls below make no external transmission.", color = Muted, style = MaterialTheme.typography.bodySmall)
    StorageManagementControls(localKnowledgeUsage) { pendingAction = it }
    Row { Button(onClick = { folderPicker.launch(null) }) { Text(if (storage.hasFolder()) "Change folder" else "Choose folder") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { localFiles = storage.listFiles() }) { Text("Refresh") }; if (storage.hasFolder()) TextButton(onClick = { storage.clearTree(); localFiles = emptyList(); viewModel.recordDeviceAction("storage.folder_access", "User revoked the selected document-provider folder access.", "REVOKED") }) { Text("Remove access") } }
    if (storage.hasFolder()) {
        OutlinedTextField(value = noteTitle, onValueChange = { noteTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New local note") })
        OutlinedTextField(value = noteContent, onValueChange = { noteContent = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Note text") }, minLines = 2)
        FilesAndStorageActionControls(null, noteContent.isNotBlank(), onActionRequested = { pendingAction = it })
        OutlinedTextField(value = workspaceName, onValueChange = { workspaceName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New local project workspace") })
        Button(onClick = { pendingAction = LocalStorageAction.CreateWorkspace(workspaceName) }, enabled = workspaceName.isNotBlank()) { Text("Create scoped workspace") }
        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search selected folder and workspaces") }, singleLine = true)
        Text(if (searchQuery.isBlank()) "Selected folder" else "Search results (within selected folder only)", color = Lavender, style = MaterialTheme.typography.labelSmall)
        visibleFiles.forEach { document -> SurfaceCard { Text(document.name, fontWeight = FontWeight.SemiBold); Text(if (document.isDirectory) "Local workspace folder" else "${document.mimeType} · ${document.sizeBytes} bytes", color = Muted, style = MaterialTheme.typography.bodySmall); if (!document.isDirectory) FilesAndStorageActionControls(document, true, { pendingAction = it }, onEdit = if (storage.isEditable(document)) { { pendingAction = LocalStorageAction.Edit(document) } } else null, onExport = { pendingAction = LocalStorageAction.ExportCopy(document) }, onArchive = { pendingAction = LocalStorageAction.Archive(document) }, onUpload = { storage.readBytes(document)?.let { viewModel.recordDeviceAction("storage.upload", "User selected ${document.name} for upload to the protected workspace."); viewModel.uploadLocalFile(document, it) } }, onIndex = { storage.readBytes(document)?.let { viewModel.recordDeviceAction("document.local_index", "User chose ${document.name} for local knowledge indexing."); viewModel.indexLocalDocument(document, it) } }) else { Text("Folders can be searched, exported as a copy, or archived locally. Android does not expose unrestricted shell execution for them.", color = Muted, style = MaterialTheme.typography.bodySmall); Row { TextButton(onClick = { pendingAction = LocalStorageAction.ExportCopy(document) }) { Text("Export workspace copy") }; TextButton(onClick = { pendingAction = LocalStorageAction.Archive(document) }) { Text("Archive workspace") } } } } }
    } else Text("Choose a document-provider folder to enable scoped local storage actions.", color = Muted)
    if (remoteFiles.isNotEmpty()) { Text("Autonova workspace files", color = Lavender, style = MaterialTheme.typography.labelSmall); remoteFiles.forEach { file -> RemoteFileRow(file) } }
    editingDocument?.let { document -> AlertDialog(onDismissRequest = { editingDocument = null }, title = { Text("Edit ${document.name}") }, text = { OutlinedTextField(value = editingText, onValueChange = { editingText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Local text") }, minLines = 8) }, confirmButton = { Button(onClick = { pendingAction = LocalStorageAction.SaveEdit(document, editingText); editingDocument = null }) { Text("Review save") } }, dismissButton = { TextButton(onClick = { editingDocument = null }) { Text("Cancel") } }) }
    LocalStorageActionConfirmation(pendingAction, noteTitle, { storage.createNote(noteTitle, noteContent); viewModel.recordDeviceAction("storage.create", "User confirmed creating a note in the selected folder."); noteTitle = ""; noteContent = ""; localFiles = storage.listFiles(); pendingAction = null }, { name -> storage.createWorkspace(name); viewModel.recordDeviceAction("workspace.create", "User confirmed creating the scoped local workspace $name."); workspaceName = ""; localFiles = storage.listFiles(); pendingAction = null }, { document -> runCatching { storage.open(document) }; viewModel.recordDeviceAction("storage.open", "User confirmed opening ${document.name}."); pendingAction = null }, { document -> runCatching { storage.share(document) }; viewModel.recordDeviceAction("storage.share", "User confirmed sharing ${document.name}."); pendingAction = null }, { document -> storage.delete(document); viewModel.recordDeviceAction("storage.delete", "User confirmed deleting ${document.name}."); localFiles = storage.listFiles(); pendingAction = null }, { document -> val text = storage.readText(document); if (text == null) viewModel.showError("This file cannot be edited here. Autonova supports text files up to 1 MB in the selected folder.") else { editingDocument = document; editingText = text }; pendingAction = null }, { document, content -> if (storage.writeText(document, content)) { viewModel.recordDeviceAction("storage.edit", "User confirmed saving edits to ${document.name}."); localFiles = storage.listFiles() } else viewModel.showError("Autonova could not save this file. Check document-provider write access."); pendingAction = null }, { document -> pendingExport = document; exportPicker.launch(null); pendingAction = null }, { document -> if (storage.archive(document) != null) { viewModel.recordDeviceAction("storage.archive", "User created a local ZIP archive for ${document.name}."); localFiles = storage.listFiles() } else viewModel.showError("Autonova could not archive this item. Check selected-folder access and retry."); pendingAction = null }, { viewModel.clearLocalKnowledgeIndex(); viewModel.recordDeviceAction("document.local_index", "User cleared local document-index chunks; source files were retained."); pendingAction = null }, { viewModel.clearLocalActivityCache(); viewModel.recordDeviceAction("storage.activity_cache", "User cleared local activity summaries; source files and memories were retained."); pendingAction = null }, { pendingAction = null })
}

@Composable internal fun StorageManagementControls(usage: LocalKnowledgeUsage, onActionRequested: (LocalStorageAction) -> Unit) = SurfaceCard {
    Text("Offline storage controls", fontWeight = FontWeight.SemiBold)
    Text("Local document index: ${usage.documentCount} documents · ${usage.chunkCount} chunks · about ${usage.estimatedBytes / 1024} KB. Clearing the index removes only offline retrieval copies, never original files.", color = Muted, style = MaterialTheme.typography.bodySmall)
    Row { TextButton(onClick = { onActionRequested(LocalStorageAction.ClearLocalIndex) }) { Text("Clear local index") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { onActionRequested(LocalStorageAction.ClearActivityCache) }) { Text("Clear activity cache") } }
    Text("Export creates a copy in another folder you select; Android document providers do not guarantee atomic cross-provider moves, so Autonova does not remove the original automatically.", color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable internal fun LocalStorageActionConfirmation(action: LocalStorageAction?, noteTitle: String, onCreate: () -> Unit, onCreateWorkspace: (String) -> Unit, onOpen: (LocalDocument) -> Unit, onShare: (LocalDocument) -> Unit, onDelete: (LocalDocument) -> Unit, onEdit: (LocalDocument) -> Unit, onSaveEdit: (LocalDocument, String) -> Unit, onExport: (LocalDocument) -> Unit, onArchive: (LocalDocument) -> Unit, onClearIndex: () -> Unit, onClearActivity: () -> Unit, onDismiss: () -> Unit) {
    when (action) {
        LocalStorageAction.CreateNote -> ConfirmDialog("Create local note?", "Create ‘${noteTitle.ifBlank { "Autonova note" }}.txt’ in the selected folder?", "Create", onCreate, onDismiss)
        is LocalStorageAction.CreateWorkspace -> ConfirmDialog("Create local workspace?", "Create ‘${action.name}’ with a local README in the selected folder?", "Create", { onCreateWorkspace(action.name) }, onDismiss)
        is LocalStorageAction.Open -> ConfirmDialog("Open local file?", "Open ‘${action.document.name}’ with an installed application?", "Open", { onOpen(action.document) }, onDismiss)
        is LocalStorageAction.Share -> ConfirmDialog("Share local file?", "Share ‘${action.document.name}’ with another application?", "Share", { onShare(action.document) }, onDismiss)
        is LocalStorageAction.Delete -> ConfirmDialog("Delete local file?", "This permanently deletes ‘${action.document.name}’ from the folder you selected.", "Delete", { onDelete(action.document) }, onDismiss)
        is LocalStorageAction.Edit -> ConfirmDialog("Edit local file?", "Open ‘${action.document.name}’ for local text editing? Its content stays in the selected Android folder unless you separately choose Upload.", "Edit", { onEdit(action.document) }, onDismiss)
        is LocalStorageAction.SaveEdit -> ConfirmDialog("Save local edits?", "Overwrite ‘${action.document.name}’ in the selected folder with your edited text? This does not upload the file.", "Save", { onSaveEdit(action.document, action.content) }, onDismiss)
        is LocalStorageAction.ExportCopy -> ConfirmDialog("Export a local copy?", "Choose another Android folder for a copy of ‘${action.document.name}’. The original remains unchanged.", "Choose folder", { onExport(action.document) }, onDismiss)
        is LocalStorageAction.Archive -> ConfirmDialog("Create local archive?", "Create a ZIP archive for ‘${action.document.name}’ in the selected folder? The original remains unchanged and nothing is uploaded.", "Create archive", { onArchive(action.document) }, onDismiss)
        LocalStorageAction.ClearLocalIndex -> ConfirmDialog("Clear local document index?", "Remove all offline retrieval chunks. Original documents in the folders you selected will not be deleted or uploaded.", "Clear index", onClearIndex, onDismiss)
        LocalStorageAction.ClearActivityCache -> ConfirmDialog("Clear local activity cache?", "Remove cached activity summaries on this device. Tasks, memories, original documents, and remote records will not be deleted.", "Clear cache", onClearActivity, onDismiss)
        null -> Unit
    }
}

@Composable internal fun FilesAndStorageActionControls(document: LocalDocument?, createEnabled: Boolean, onActionRequested: (LocalStorageAction) -> Unit, onEdit: (() -> Unit)? = null, onExport: (() -> Unit)? = null, onArchive: (() -> Unit)? = null, onUpload: (() -> Unit)? = null, onIndex: (() -> Unit)? = null) {
    if (document == null) Button(onClick = { onActionRequested(LocalStorageAction.CreateNote) }, enabled = createEnabled) { Text("Create note") }
    else Column { Row { TextButton(onClick = { onActionRequested(LocalStorageAction.Open(document)) }) { Text("Open") }; TextButton(onClick = { onActionRequested(LocalStorageAction.Share(document)) }) { Text("Share") }; onEdit?.let { TextButton(onClick = it) { Text("Edit") } }; onExport?.let { TextButton(onClick = it) { Text("Export copy") } }; onArchive?.let { TextButton(onClick = it) { Text("Archive") } } }; Row { onIndex?.let { TextButton(onClick = it) { Text("Index local") } }; onUpload?.let { TextButton(onClick = it) { Text("Upload") } }; TextButton(onClick = { onActionRequested(LocalStorageAction.Delete(document)) }) { Text("Delete") } } }
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

@Composable private fun ImageStudioScreen(viewModel: AutonovaViewModel) {
    val imageUrl by viewModel.generatedImageUrl.collectAsState(); var prompt by remember { mutableStateOf("") }; val context = LocalContext.current
    Text("Image studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Describe an image to generate through Autonova’s protected server-side image service.", color = Muted, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Image prompt") }, minLines = 3)
    Button(onClick = { viewModel.generateImage(prompt) }, enabled = prompt.trim().length >= 3) { Text("Generate image") }
    imageUrl?.let { url -> SurfaceCard { Text("Generated image ready", color = Lavender, style = MaterialTheme.typography.labelSmall); Text(url, color = Muted, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }) { Text("Open image") } } }
}

@Composable private fun GitHubScreen(viewModel: AutonovaViewModel) {
    val github by viewModel.github.collectAsState(); val connection by viewModel.githubConnection.collectAsState(); val operations by viewModel.githubOperations.collectAsState(); val context = LocalContext.current; val workspaceStorage = remember(context) { DeviceStorage(context, SecureConfig(context)) }; var workspaceFiles by remember { mutableStateOf(workspaceStorage.searchFiles("").filter { workspaceStorage.isEditable(it) }) }; var selectedWorkspaceFile by remember { mutableStateOf<LocalDocument?>(null) }; var syncPath by remember { mutableStateOf("") }; var syncMessage by remember { mutableStateOf("Sync selected Autonova workspace file") }; var syncBranch by remember { mutableStateOf("") }; var repository by remember { mutableStateOf("") }; var token by remember { mutableStateOf("") }; var scopes by remember { mutableStateOf("Contents: read/write; Issues: read/write; Pull requests: read/write") }; var operation by remember { mutableStateOf("CREATE_ISSUE") }; var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var branch by remember { mutableStateOf("") }; var sourceBranch by remember { mutableStateOf("main") }; var head by remember { mutableStateOf("") }; var base by remember { mutableStateOf("main") }
    Text("GitHub context", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Inspect public repositories, then optionally connect a fine-grained token for the exact repositories and permissions you choose. Token material is encrypted server-side and never returned to Android.", color = Muted, style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = repository, onValueChange = { repository = it }, modifier = Modifier.weight(1f), label = { Text("owner/repository") }, singleLine = true); Spacer(Modifier.width(8.dp)); Button(onClick = { viewModel.inspectGitHub(repository) }, enabled = repository.contains("/")) { Text("Inspect") } }
    github?.let { result -> SurfaceCard { Text(result.name, fontWeight = FontWeight.Bold); Text(result.description, color = Muted, style = MaterialTheme.typography.bodySmall); Text("${result.stars} stars · ${result.branch}", color = Lavender, style = MaterialTheme.typography.bodySmall); if (result.branches.isNotEmpty()) Text("Branches: ${result.branches.joinToString()}", color = Muted, style = MaterialTheme.typography.bodySmall); if (result.issues.isNotEmpty()) { Text("Open issues", color = Lavender, style = MaterialTheme.typography.labelSmall); result.issues.forEach { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) } }; if (result.pulls.isNotEmpty()) { Text("Pull requests", color = Lavender, style = MaterialTheme.typography.labelSmall); result.pulls.forEach { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) } }; if (result.commits.isNotEmpty()) { Text("Recent commits", color = Lavender, style = MaterialTheme.typography.labelSmall); result.commits.forEach { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) } } } }
    if (connection == null) SurfaceCard { Text("Connect GitHub", fontWeight = FontWeight.SemiBold); Text("Use a fine-grained GitHub token restricted to repositories and permissions you intend to delegate. Do not use a broad account token.", color = Muted, style = MaterialTheme.typography.bodySmall); OutlinedTextField(value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Fine-grained GitHub token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true); OutlinedTextField(value = scopes, onValueChange = { scopes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Selected permissions") }); Button(onClick = { viewModel.connectGitHub(token, scopes); token = "" }, enabled = token.length >= 20 && scopes.isNotBlank()) { Text("Connect encrypted token") } } else connection?.let { activeConnection -> SurfaceCard { Text("Connected as ${activeConnection.login}", fontWeight = FontWeight.SemiBold); Text(activeConnection.scopes, color = Muted, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { viewModel.disconnectGitHub() }) { Text("Disconnect GitHub") } } }
    if (connection != null) SurfaceCard { Text("Prepare a GitHub operation", fontWeight = FontWeight.SemiBold); Text("Preparation does not change GitHub. Review the exact pending operation below and confirm it separately.", color = Muted, style = MaterialTheme.typography.bodySmall); Row { listOf("CREATE_ISSUE", "CREATE_BRANCH", "CREATE_PULL_REQUEST").forEach { item -> AssistChip(onClick = { operation = item }, label = { Text(if (operation == item) "✓ ${item.removePrefix("CREATE_").lowercase()}" else item.removePrefix("CREATE_").lowercase()) }) } }; if (operation == "CREATE_ISSUE" || operation == "CREATE_PULL_REQUEST") { OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }); OutlinedTextField(value = body, onValueChange = { body = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Body") }, minLines = 2) }; if (operation == "CREATE_BRANCH") { OutlinedTextField(value = branch, onValueChange = { branch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New branch") }); OutlinedTextField(value = sourceBranch, onValueChange = { sourceBranch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Source branch") }) }; if (operation == "CREATE_PULL_REQUEST") { OutlinedTextField(value = head, onValueChange = { head = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Head branch") }); OutlinedTextField(value = base, onValueChange = { base = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Base branch") }) }; Button(onClick = { viewModel.proposeGitHubOperation(repository, operation, title, body, branch, sourceBranch, head, base) }, enabled = repository.contains("/") && (operation == "CREATE_ISSUE" && title.isNotBlank() || operation == "CREATE_BRANCH" && branch.isNotBlank() && sourceBranch.isNotBlank() || operation == "CREATE_PULL_REQUEST" && title.isNotBlank() && head.isNotBlank() && base.isNotBlank())) { Text("Prepare for confirmation") } }
    if (connection != null) SurfaceCard { Text("Sync one scoped workspace text file", fontWeight = FontWeight.SemiBold); Text("Autonova reads only the file you choose below from your previously selected Android folder. It prepares a pending GitHub change; no content is uploaded until you press Confirm and run in the operation history.", color = Muted, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { workspaceFiles = workspaceStorage.searchFiles("").filter { workspaceStorage.isEditable(it) } }) { Text("Refresh scoped files") }; if (workspaceFiles.isEmpty()) Text("No supported text file is available in the selected workspace. Choose a folder and create or select a text file in Files & Storage first.", color = Muted, style = MaterialTheme.typography.bodySmall) else Row { workspaceFiles.take(8).forEach { file -> AssistChip(onClick = { selectedWorkspaceFile = file; syncPath = file.name }, label = { Text(if (selectedWorkspaceFile?.uri == file.uri) "✓ ${file.name}" else file.name) }) } }; selectedWorkspaceFile?.let { file -> Text("Selected: ${file.name} · ${file.sizeBytes} bytes", color = Lavender, style = MaterialTheme.typography.bodySmall) }; OutlinedTextField(value = syncPath, onValueChange = { syncPath = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Repository relative path") }); OutlinedTextField(value = syncBranch, onValueChange = { syncBranch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Target branch (optional)") }); OutlinedTextField(value = syncMessage, onValueChange = { syncMessage = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Commit message") }); Button(onClick = { val selected = selectedWorkspaceFile; val text = selected?.let(workspaceStorage::readText); if (selected == null || text == null) viewModel.showError("Choose a readable text file from the scoped workspace before preparing a sync.") else viewModel.proposeWorkspaceFileSync(repository, syncMessage, syncBranch, syncPath, text) }, enabled = repository.contains("/") && selectedWorkspaceFile != null && syncPath.isNotBlank() && syncMessage.isNotBlank()) { Text("Prepare selected file for confirmation") } }
    if (operations.isNotEmpty()) { Text("GitHub operation history", color = Lavender, style = MaterialTheme.typography.labelSmall); operations.forEach { request -> SurfaceCard { Text("${request.operation} · ${request.status}", fontWeight = FontWeight.SemiBold); Text(request.repository, color = Muted, style = MaterialTheme.typography.bodySmall); if (request.resultSummary.isNotBlank()) Text(request.resultSummary, color = Muted, style = MaterialTheme.typography.bodySmall); if (request.errorSummary.isNotBlank()) Text(request.errorSummary, color = Color(0xFFFFA7A7), style = MaterialTheme.typography.bodySmall); if (request.status == "PENDING") Row { Button(onClick = { viewModel.approveGitHubOperation(request.id) }) { Text("Confirm and run") }; Spacer(Modifier.width(8.dp)); TextButton(onClick = { viewModel.cancelGitHubOperation(request.id) }) { Text("Cancel") } } } } }
}

@Composable private fun UsageScreen(viewModel: AutonovaViewModel) {
    val usage by viewModel.usage.collectAsState(); Text("Usage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Token and tool activity is calculated server-side from your protected account records.", color = Muted, style = MaterialTheme.typography.bodySmall); Button(onClick = { viewModel.refreshUsage() }) { Text("Refresh usage") }
    usage?.let { summary -> SurfaceCard { Text("${summary.inputTokens + summary.outputTokens} total tokens", fontWeight = FontWeight.Bold); Text("${summary.inputTokens} input · ${summary.outputTokens} output · ${summary.toolCalls} tool calls", color = Muted, style = MaterialTheme.typography.bodySmall); Text("Estimated cost: ${summary.estimatedCostMicros} μ", color = Lavender, style = MaterialTheme.typography.bodySmall); summary.records.forEach { record -> Text("${record.model}: ${record.inputTokens + record.outputTokens} tokens, ${record.toolCalls} tools", color = Muted, style = MaterialTheme.typography.bodySmall) } } }
}

@Composable private fun SettingsScreen(viewModel: AutonovaViewModel) {
    val configured by viewModel.configured.collectAsState(); val provider by viewModel.provider.collectAsState(); val connectionState by viewModel.connectionState.collectAsState(); val operatingMode by viewModel.operatingMode.collectAsState(); val context = LocalContext.current; var selectedMode by remember(operatingMode) { mutableStateOf(operatingMode) }; var remoteEndpoint by remember { mutableStateOf(viewModel.remoteEndpoint()) }; var providerName by remember { mutableStateOf(provider?.name ?: "Autonova built-in") }; var providerType by remember { mutableStateOf(provider?.type ?: "BUILT_IN") }; var providerUrl by remember { mutableStateOf("") }; var providerModel by remember { mutableStateOf(provider?.model ?: "") }; var providerKey by remember { mutableStateOf("") }; var costMode by remember { mutableStateOf(provider?.costMode ?: "BALANCED") }
    Text("Operating mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Autonova starts local-first. It never requires a packaged cloud endpoint. Select the narrowest mode that fits your task; Android stores mode and endpoint settings with encrypted preferences.", color = Muted, style = MaterialTheme.typography.bodySmall)
    SurfaceCard { Text("Choose where Autonova may work", fontWeight = FontWeight.SemiBold); Row { listOf(OperatingMode.LOCAL_ONLY, OperatingMode.LOCAL_PLUS_INTERNET, OperatingMode.OPTIONAL_REMOTE_AGENT).forEach { mode -> AssistChip(onClick = { selectedMode = mode }, label = { Text(if (selectedMode == mode) "✓ ${mode.name.lowercase().replace('_', ' ')}" else mode.name.lowercase().replace('_', ' ')) }) } }; Text(if (selectedMode == OperatingMode.LOCAL_ONLY) "Private local model, local Room data, and scoped files only. Browser, GitHub, and remote providers are disabled." else if (selectedMode == OperatingMode.LOCAL_PLUS_INTERNET) "Local model remains primary. Browser handoff and selected public-source research stay user-confirmed; no remote agent account is used." else "Use a user-provided compatible HTTPS Autonova agent endpoint for remote tasks after secure sign-in.", color = Muted, style = MaterialTheme.typography.bodySmall); if (selectedMode == OperatingMode.OPTIONAL_REMOTE_AGENT) OutlinedTextField(value = remoteEndpoint, onValueChange = { remoteEndpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Optional remote-agent HTTPS endpoint") }, singleLine = true); Button(onClick = { viewModel.setOperatingMode(selectedMode, remoteEndpoint) }) { Text("Save operating mode") } }
    if (selectedMode == OperatingMode.OPTIONAL_REMOTE_AGENT && !configured) Button(onClick = { viewModel.beginMobileSignIn()?.let { url -> CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) } }) { Text(if (connectionState == ConnectionState.CONNECTING) "Continue optional remote sign-in" else "Sign in to selected remote agent") }
    if (configured) { Text("Optional remote agent connected", color = Lavender); Text("Provider configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); OutlinedTextField(value = providerName, onValueChange = { providerName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Provider name") }); Row { listOf("BUILT_IN", "OPENAI_COMPATIBLE").forEach { type -> AssistChip(onClick = { providerType = type }, label = { Text(if (providerType == type) "✓ $type" else type) }) } }; if (providerType == "OPENAI_COMPATIBLE") { OutlinedTextField(value = providerUrl, onValueChange = { providerUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Provider HTTPS endpoint") }); OutlinedTextField(value = providerModel, onValueChange = { providerModel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Model") }); OutlinedTextField(value = providerKey, onValueChange = { providerKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API key") }) }; Row { listOf("LOCAL_ONLY", "BALANCED", "POWER").forEach { mode -> AssistChip(onClick = { costMode = mode }, label = { Text(if (costMode == mode) "✓ ${mode.lowercase()}" else mode.lowercase()) }) } }; Button(onClick = { viewModel.saveProvider(providerName, providerType, providerUrl, providerModel, providerKey, costMode) }, enabled = providerName.isNotBlank()) { Text("Save provider") } }
    provider?.let { SurfaceCard { Text("Active remote provider", color = Lavender, style = MaterialTheme.typography.labelSmall); Text("${it.name} · ${it.costMode}"); Text("Model: ${it.model ?: "automatic"}", color = Muted, style = MaterialTheme.typography.bodySmall) } }
    if (configured) TextButton(onClick = { viewModel.clearConnection() }) { Text("Clear local remote session") }
}

@Composable private fun ActivityScreen(viewModel: AutonovaViewModel) {
    val activity by viewModel.activity.collectAsState(); Text("Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Action summaries only—never hidden reasoning or credentials.", color = Muted, style = MaterialTheme.typography.bodySmall)
    if (activity.isEmpty()) Text("No synchronized activity yet.", color = Muted) else activity.forEach { item -> SurfaceCard { Text(item.eventType, color = Lavender, style = MaterialTheme.typography.labelSmall); Text(item.title, fontWeight = FontWeight.SemiBold); if (item.detail.isNotBlank()) Text(item.detail, color = Muted, style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content) } }

@Composable internal fun ConfirmDialog(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { Button(onClick = onConfirm) { Text(action) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
