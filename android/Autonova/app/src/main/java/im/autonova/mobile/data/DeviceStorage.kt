package im.autonova.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class LocalDocument(val uri: Uri, val name: String, val mimeType: String, val sizeBytes: Long)

/** Scoped Storage helper. Autonova only accesses a folder deliberately selected by the device owner. */
class DeviceStorage(private val context: Context, private val config: SecureConfig) {
    private fun root(): DocumentFile? = config.storageTree()?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }

    fun selectTree(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        config.saveStorageTree(uri.toString())
    }

    fun clearTree() {
        config.storageTree()?.let { value -> runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(value), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        config.clearStorageTree()
    }

    fun hasFolder(): Boolean = root()?.canRead() == true

    fun listFiles(): List<LocalDocument> = root()?.listFiles()?.filter { it.isFile }?.map { file ->
        LocalDocument(file.uri, file.name ?: "Untitled file", file.type ?: "application/octet-stream", file.length())
    }?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun createNote(title: String, content: String): LocalDocument? {
        val safeName = title.trim().ifBlank { "Autonova note" }.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(120)
        val file = root()?.createFile("text/plain", if (safeName.endsWith(".txt")) safeName else "$safeName.txt") ?: return null
        context.contentResolver.openOutputStream(file.uri)?.bufferedWriter()?.use { it.write(content) } ?: return null
        return LocalDocument(file.uri, file.name ?: safeName, file.type ?: "text/plain", file.length())
    }

    fun readBytes(document: LocalDocument): ByteArray? = context.contentResolver.openInputStream(document.uri)?.use { it.readBytes() }

    fun open(document: LocalDocument) {
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(document.uri, document.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun share(document: LocalDocument) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(document.mimeType).putExtra(Intent.EXTRA_STREAM, document.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share ${document.name}"))
    }

    fun delete(document: LocalDocument): Boolean = DocumentFile.fromSingleUri(context, document.uri)?.delete() == true
}
