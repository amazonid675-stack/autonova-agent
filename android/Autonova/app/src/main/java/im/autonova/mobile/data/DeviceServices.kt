package im.autonova.mobile.data

import android.Manifest
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.app.Activity
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID

class VoiceAssistant(private val context: Context) : TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var ready = false
    private var pendingSpeech: String? = null
    override fun onInit(status: Int) { ready = status == TextToSpeech.SUCCESS; if (ready) { textToSpeech?.language = Locale.getDefault(); pendingSpeech?.let { textToSpeech?.speak(it, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) }; pendingSpeech = null } }
    fun listen(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { onError("Speech recognition is unavailable on this device."); return }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) { recognizer.destroy(); onError("Voice input stopped (code $error). Try again.") }
            override fun onResults(results: android.os.Bundle?) { val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); recognizer.destroy(); if (text.isBlank()) onError("No speech was recognized.") else onFinal(text) }
            override fun onPartialResults(partialResults: android.os.Bundle?) { partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial) }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) })
    }
    fun speak(text: String): Boolean { if (text.isBlank()) return false; val output = text.take(3500); if (textToSpeech == null) { pendingSpeech = output; textToSpeech = TextToSpeech(context, this); return true }; return if (ready) textToSpeech?.speak(output, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) == TextToSpeech.SUCCESS else { pendingSpeech = output; true } }
    fun close() { textToSpeech?.stop(); textToSpeech?.shutdown(); textToSpeech = null }
}

class LocalModelEngine(private val context: Context, private val config: SecureConfig) {
    suspend fun importModel(uri: android.net.Uri): Result<String> = withContext(Dispatchers.IO) { runCatching {
        val destination = File(context.filesDir, "models/autonova-${UUID.randomUUID()}.task").also { it.parentFile?.mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } } ?: error("Unable to read the selected model.")
        require(destination.length() > 1_000_000) { "Select a supported quantized .task model, not an empty file." }
        config.saveLocalModelPath(destination.absolutePath)
        "Local model imported (${destination.length() / (1024 * 1024)} MB)."
    } }
    fun status(): String = config.localModelPath()?.let { path -> if (File(path).isFile) "Imported local model: ${File(path).name}" else "Selected local model is no longer available." } ?: "No local model imported."
    fun storageBytes(): Long = config.localModelPath()?.let { File(it).takeIf(File::isFile)?.length() } ?: 0L
    fun removeImportedModel(): Result<String> = runCatching { val path = config.localModelPath() ?: return@runCatching "No local model is selected."; val file = File(path); if (file.exists() && !file.delete()) error("The imported local model could not be removed."); config.clearLocalModel(); "Local model removed from private app storage." }
    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.Default) { runCatching {
        val path = config.localModelPath() ?: error("Import a compatible local .task model first.")
        require(File(path).isFile) { "The selected local model is no longer available." }
        val options = LlmInference.LlmInferenceOptions.builder().setModelPath(path).setMaxTokens(512).setMaxTopK(40).build()
        LlmInference.createFromOptions(context, options).use { it.generateResponse(prompt) }
    } }
}

class AgentNotifier(private val context: Context) {
    fun createChannel() { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL, "Autonova tasks", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Completion and failure updates for your Autonova tasks." }) }
    fun notifyTask(taskId: String, request: String, status: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel()
        val title = if (status == "COMPLETED") "Autonova task completed" else "Autonova task needs attention"
        NotificationManagerCompat.from(context).notify(taskId.hashCode(), NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle(title).setContentText(request.take(120)).setAutoCancel(true).build())
    }
    fun notifyBackgroundReview(success: Boolean, detail: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel()
        val title = if (success) "Autonova background review finished" else "Autonova background review needs attention"
        NotificationManagerCompat.from(context).notify("background-review".hashCode(), NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle(title).setContentText(detail.take(120)).setAutoCancel(true).build())
    }
    companion object { const val CHANNEL = "autonova-task-updates" }
}

class ScreenshotCapture(private val context: Context) {
    fun consentIntent(): Intent = (context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()
    fun capture(resultCode: Int, data: Intent?, onResult: (Result<ByteArray>) -> Unit) {
        if (resultCode != Activity.RESULT_OK || data == null) { onResult(Result.failure(IllegalStateException("Screen capture was not approved."))); return }
        val handler = Handler(Looper.getMainLooper())
        val metrics = context.resources.displayMetrics
        val reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, data)
        var display: android.hardware.display.VirtualDisplay? = null
        var finished = false
        fun finish(result: Result<ByteArray>) { if (finished) return; finished = true; runCatching { display?.release(); reader.close(); projection.stop() }; onResult(result) }
        reader.setOnImageAvailableListener({ source ->
            runCatching {
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                image.use { frame ->
                    val plane = frame.planes.first(); val padding = plane.rowStride - plane.pixelStride * frame.width
                    val padded = Bitmap.createBitmap(frame.width + padding / plane.pixelStride, frame.height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val bitmap = Bitmap.createBitmap(padded, 0, 0, frame.width, frame.height)
                    ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output); output.toByteArray() }
                }
            }.onSuccess { finish(Result.success(it)) }.onFailure { finish(Result.failure(it)) }
        }, handler)
        display = projection.createVirtualDisplay("AutonovaCapture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler)
        handler.postDelayed({ finish(Result.failure(IllegalStateException("Screen capture timed out."))) }, 5_000)
    }
}
