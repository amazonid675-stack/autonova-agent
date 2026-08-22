package im.autonova.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class LocalDocument(val uri: Uri, val name: String, val mimeType: String, val sizeBytes: Long, val isDirectory: Boolean = false)

/** Scoped Storage helper. Autonova only accesses a folder deliberately selected by the device owner. */
class DeviceStorage(private val context: Context, private val config: SecureConfig) {
    private fun root(): DocumentFile? = config.storageTree()?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }
    private fun asLocal(file: DocumentFile) = LocalDocument(file.uri, file.name ?: "Untitled file", file.type ?: "application/octet-stream", if (file.isFile) file.length() else 0L, file.isDirectory)
    private fun safeName(value: String, limit: Int = 120) = value.trim().ifBlank { "Autonova file" }.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(limit)

    fun selectTree(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        config.saveStorageTree(uri.toString())
    }

    fun clearTree() {
        config.storageTree()?.let { value -> runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(value), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        config.clearStorageTree()
    }

    fun hasFolder(): Boolean = root()?.canRead() == true

    fun listFiles(): List<LocalDocument> = root()?.listFiles()?.map(::asLocal)?.sortedWith(compareBy<LocalDocument> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList()

    /** Searches only inside the previously selected document tree; no storage permission beyond that tree is requested. */
    fun searchFiles(query: String, limit: Int = 50): List<LocalDocument> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return listFiles()
        val results = mutableListOf<LocalDocument>()
        fun visit(folder: DocumentFile) {
            if (results.size >= limit) return
            folder.listFiles().forEach { child ->
                if (results.size >= limit) return@forEach
                if ((child.name ?: "").lowercase().contains(normalized)) results += asLocal(child)
                if (child.isDirectory) visit(child)
            }
        }
        root()?.let(::visit)
        return results.sortedWith(compareBy<LocalDocument> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun createNote(title: String, content: String): LocalDocument? {
        val safeName = safeName(title)
        val file = root()?.createFile("text/plain", if (safeName.endsWith(".txt")) safeName else "$safeName.txt") ?: return null
        context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(content) } ?: return null
        return asLocal(file)
    }

    /** Creates a user-named text or code artifact inside the selected document tree. */
    fun createTextArtifact(name: String, content: String): LocalDocument? {
        val safeName = safeName(name, 120)
        val extension = safeName.substringAfterLast('.', "").lowercase()
        val mimeType = when (extension) {
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "md" -> "text/markdown"
            else -> "text/plain"
        }
        val file = root()?.createFile(mimeType, safeName.ifBlank { "autonova-artifact.txt" }) ?: return null
        context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(content) } ?: return null
        return asLocal(file)
    }

    fun createWorkspace(title: String): LocalDocument? {
        val safeName = safeName(title, 80)
        val directory = root()?.createDirectory(safeName) ?: return null
        val readme = directory.createFile("text/markdown", "README.md") ?: return null
        context.contentResolver.openOutputStream(readme.uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write("# $safeName\n\nCreated locally by Autonova in a user-selected Android document folder.\n\nThis workspace is scoped to this folder. Autonova does not receive unrestricted shell or device-file access.\n\nAndroid document providers do not safely expose arbitrary shell execution or cross-provider atomic moves. Autonova can edit supported text files here and export a local copy after confirmation.\n")
        } ?: return null
        return asLocal(directory)
    }

    fun isEditable(document: LocalDocument): Boolean = !document.isDirectory && (document.mimeType.startsWith("text/") || document.name.endsWith(".md", true) || document.name.endsWith(".json", true) || document.name.endsWith(".kt", true) || document.name.endsWith(".java", true) || document.name.endsWith(".xml", true) || document.name.endsWith(".yml", true) || document.name.endsWith(".yaml", true) || document.name.endsWith(".txt", true))

    /** Returns null when the selected text file exceeds the local editing limit or cannot be read. */
    fun readText(document: LocalDocument, maxBytes: Int = 1_000_000): String? {
        if (!isEditable(document)) return null
        return context.contentResolver.openInputStream(document.uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                if (output.size() + count > maxBytes) return@use null
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    fun writeText(document: LocalDocument, content: String): Boolean {
        if (!isEditable(document)) return false
        return runCatching { context.contentResolver.openOutputStream(document.uri, "wt")?.bufferedWriter()?.use { it.write(content) } ?: error("The document provider did not allow writing this file.") }.isSuccess
    }

    fun readBytes(document: LocalDocument): ByteArray? = if (document.isDirectory) null else context.contentResolver.openInputStream(document.uri)?.use { it.readBytes() }

    /** Creates a local copy in another owner-selected document tree. The original remains untouched. */
    fun exportCopy(document: LocalDocument, destinationTree: Uri): LocalDocument? {
        val destination = DocumentFile.fromTreeUri(context, destinationTree)?.takeIf { it.canWrite() } ?: return null
        val source = DocumentFile.fromSingleUri(context, document.uri) ?: return null
        return copyInto(source, destination)?.let(::asLocal)
    }

    /** Archives a selected file or workspace inside the same owner-selected tree; it never sends the archive off-device. */
    fun archive(document: LocalDocument): LocalDocument? = runCatching {
        val source = findInSelectedTree(document.uri) ?: return@runCatching null
        val baseName = safeName((source.name ?: "Autonova export").substringBeforeLast('.', source.name ?: "Autonova export"), 100)
        val archive = root()?.createFile("application/zip", "$baseName.zip") ?: return@runCatching null
        context.contentResolver.openOutputStream(archive.uri, "wt")?.use { raw ->
            ZipOutputStream(raw).use { zip -> addToArchive(source, source.name ?: baseName, zip) }
        } ?: return@runCatching null
        asLocal(archive)
    }.getOrNull()

    private fun findInSelectedTree(uri: Uri): DocumentFile? {
        fun visit(current: DocumentFile): DocumentFile? {
            if (current.uri == uri) return current
            for (child in current.listFiles()) {
                if (child.uri == uri) return child
                if (child.isDirectory) visit(child)?.let { return it }
            }
            return null
        }
        return root()?.let(::visit)
    }

    private fun addToArchive(source: DocumentFile, path: String, zip: ZipOutputStream) {
        if (source.isDirectory) {
            val children = source.listFiles()
            if (children.isEmpty()) { zip.putNextEntry(ZipEntry("$path/")); zip.closeEntry() }
            else children.forEach { child -> addToArchive(child, "$path/${child.name ?: "untitled"}", zip) }
            return
        }
        zip.putNextEntry(ZipEntry(path))
        context.contentResolver.openInputStream(source.uri)?.use { input -> input.copyTo(zip) } ?: error("Could not read ${source.name ?: "a selected file"}.")
        zip.closeEntry()
    }

    private fun copyInto(source: DocumentFile, destination: DocumentFile): DocumentFile? {
        val sourceName = safeName(source.name ?: "Autonova export")
        if (source.isDirectory) {
            val created = destination.createDirectory(sourceName) ?: return null
            for (child in source.listFiles()) if (copyInto(child, created) == null) return null
            return created
        }
        val copied = destination.createFile(source.type ?: "application/octet-stream", sourceName) ?: return null
        val input = context.contentResolver.openInputStream(source.uri) ?: return null
        val output = context.contentResolver.openOutputStream(copied.uri, "wt") ?: return null
        input.use { from -> output.use { to -> from.copyTo(to) } }
        return copied
    }

    fun open(document: LocalDocument) { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(document.uri, document.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
    fun share(document: LocalDocument) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(document.mimeType).putExtra(Intent.EXTRA_STREAM, document.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share ${document.name}")) }
    fun delete(document: LocalDocument): Boolean = DocumentFile.fromSingleUri(context, document.uri)?.delete() == true
}
