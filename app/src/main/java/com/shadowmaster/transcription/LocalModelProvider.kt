package com.shadowmaster.transcription

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Local on-device transcription using Vosk.
 * 
 * Provides offline transcription without API keys or internet connection.
 * Uses Vosk models which are optimized for mobile devices.
 * 
 * Features:
 * - Completely offline after model download
 * - No API costs
 * - Privacy-friendly (no data leaves device)
 * - On-demand model download
 * - Multiple language support
 */
class LocalModelProvider(
    private val context: Context,
    private val modelPath: String?
) : TranscriptionProvider {

    override val name: String = "Local Model (Vosk)"
    override val id: String = TranscriptionProviderType.LOCAL.id
    override val requiresApiKey: Boolean = false

    companion object {
        private const val TAG = "LocalModelProvider"
        
        // Model URLs from Vosk Models (using small models optimized for mobile)
        // Using lightweight models for better mobile performance
        private const val TINY_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val BASE_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip"
        
        // Model file names (directory names after extraction)
        const val TINY_MODEL_NAME = "vosk-model-small-en-us-0.15"
        const val BASE_MODEL_NAME = "vosk-model-en-us-0.22"
        
        // Model sizes (approximate)
        const val TINY_MODEL_SIZE_MB = 40
        const val BASE_MODEL_SIZE_MB = 75

        /**
         * Get the model directory path.
         */
        fun getModelDir(context: Context): File {
            return File(context.filesDir, "whisper_models").apply {
                if (!exists()) mkdirs()
            }
        }

        /**
         * Get the path to a specific model file.
         */
        fun getModelPath(context: Context, modelName: String): File {
            return File(getModelDir(context), modelName)
        }

        /**
         * Check if a model is downloaded.
         */
        fun isModelDownloaded(context: Context, modelName: String): Boolean {
            val modelFile = getModelPath(context, modelName)
            // Check if model directory exists and contains required files
            return modelFile.exists() && modelFile.isDirectory && modelFile.listFiles()?.isNotEmpty() == true
        }

        /**
         * Get the URL for downloading a model.
         */
        fun getModelUrl(modelName: String): String {
            return when (modelName) {
                TINY_MODEL_NAME -> TINY_MODEL_URL
                BASE_MODEL_NAME -> BASE_MODEL_URL
                else -> throw IllegalArgumentException("Unknown model: $modelName")
            }
        }

        /**
         * Delete a model file.
         */
        fun deleteModel(context: Context, modelName: String): Boolean {
            val modelFile = getModelPath(context, modelName)
            return if (modelFile.exists()) {
                modelFile.deleteRecursively()
            } else {
                false
            }
        }
    }

    override suspend fun validateConfiguration(): Result<Unit> {
        return if (modelPath.isNullOrBlank()) {
            Result.failure(TranscriptionError.ProviderError(name, "Model path not configured"))
        } else {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Result.failure(
                    TranscriptionError.ProviderError(
                        name,
                        "Model file not found at: $modelPath. Please download a model first."
                    )
                )
            } else if (!modelFile.isDirectory || modelFile.listFiles()?.isEmpty() == true) {
                Result.failure(
                    TranscriptionError.ProviderError(
                        name,
                        "Model directory is invalid at: $modelPath. Please re-download the model."
                    )
                )
            } else {
                Result.success(Unit)
            }
        }
    }

    override suspend fun transcribe(audioFile: File, language: String): Result<String> = withContext(Dispatchers.IO) {
        // Validate configuration first
        validateConfiguration().getOrElse { return@withContext Result.failure(it) }

        try {
            Log.d(TAG, "Starting transcription with model: $modelPath")
            
            // Initialize Vosk model
            val model = Model(modelPath!!)
            
            // Create recognizer for 16kHz audio (our standard format)
            val recognizer = Recognizer(model, 16000.0f)
            
            // Read audio file
            val audioData = readAudioFile(audioFile)
            
            if (audioData.isEmpty()) {
                model.delete()
                return@withContext Result.failure(
                    TranscriptionError.ProviderError(name, "Failed to read audio file")
                )
            }
            
            // Process audio data
            recognizer.acceptWaveForm(audioData, audioData.size)
            val resultJson = recognizer.finalResult()
            
            // Parse JSON result
            val jsonObject = JSONObject(resultJson)
            val transcribedText = jsonObject.optString("text", "")
            
            // Cleanup
            recognizer.delete()
            model.delete()
            
            if (transcribedText.isBlank()) {
                Result.failure(
                    TranscriptionError.ProviderError(
                        name,
                        "No speech detected in audio"
                    )
                )
            } else {
                Log.d(TAG, "Transcription successful: $transcribedText")
                Result.success(transcribedText.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(TranscriptionError.UnknownError(name, e))
        }
    }

    /**
     * Read audio file and convert to byte array of samples.
     * Expected format: 16kHz mono PCM.
     */
    private fun readAudioFile(audioFile: File): ByteArray {
        return try {
            val bytes = audioFile.readBytes()
            
            // Check if it's a WAV file (starts with "RIFF")
            val startOffset = if (bytes.size > 44 && 
                bytes[0] == 'R'.code.toByte() && 
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && 
                bytes[3] == 'F'.code.toByte()) {
                // Skip WAV header (typically 44 bytes)
                44
            } else {
                // Raw PCM, no header
                0
            }

            // Return PCM data without header
            bytes.copyOfRange(startOffset, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read audio file", e)
            ByteArray(0)
        }
    }

    /**
     * Download a Vosk model.
     * 
     * Note: Vosk models are distributed as ZIP files that need to be extracted.
     * This implementation downloads the ZIP and extracts it.
     * 
     * @param modelName Name of the model (e.g., TINY_MODEL_NAME, BASE_MODEL_NAME)
     * @param progressCallback Optional callback for download progress (0.0 to 1.0)
     * @return Result containing the downloaded model directory or error
     */
    suspend fun downloadModel(
        modelName: String,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val url = getModelUrl(modelName)
            val destDir = getModelPath(context, modelName)
            val zipFile = File(getModelDir(context), "$modelName.zip")
            
            Log.d(TAG, "Downloading model from: $url")
            Log.d(TAG, "Destination: ${destDir.absolutePath}")
            
            // Create parent directory if needed
            destDir.parentFile?.mkdirs()
            
            // Delete existing files if present
            if (destDir.exists()) {
                destDir.deleteRecursively()
            }
            if (zipFile.exists()) {
                zipFile.delete()
            }
            
            // Download the ZIP file
            val connection = URL(url).openConnection()
            connection.connect()
            
            val fileLength = connection.contentLength
            Log.d(TAG, "Model size: ${fileLength / 1024 / 1024} MB")
            
            connection.getInputStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Report progress (0.0 to 0.8 for download, 0.8 to 1.0 for extraction)
                        if (fileLength > 0) {
                            val progress = (totalBytesRead.toFloat() / fileLength.toFloat()) * 0.8f
                            progressCallback?.invoke(progress)
                        }
                    }
                }
            }
            
            // Extract ZIP file
            Log.d(TAG, "Extracting model...")
            progressCallback?.invoke(0.85f)
            
            extractZipFile(zipFile, getModelDir(context))
            
            progressCallback?.invoke(0.95f)
            
            // Delete ZIP file after extraction
            zipFile.delete()
            
            progressCallback?.invoke(1.0f)
            
            Log.d(TAG, "Model downloaded and extracted successfully: ${destDir.absolutePath}")
            Result.success(destDir)
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            Result.failure(
                TranscriptionError.NetworkError(name, e)
            )
        }
    }

    /**
     * Extract a ZIP file to a destination directory.
     */
    private fun extractZipFile(zipFile: File, destDir: File) {
        val zip = java.util.zip.ZipFile(zipFile)
        try {
            zip.entries().asSequence().forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    val filePath = File(destDir, entry.name)
                    
                    if (entry.isDirectory) {
                        filePath.mkdirs()
                    } else {
                        filePath.parentFile?.mkdirs()
                        FileOutputStream(filePath).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } finally {
            zip.close()
        }
    }

    /**
     * Enum for available Vosk models.
     */
    enum class WhisperModel(val fileName: String, val sizeMB: Int, val displayName: String) {
        TINY(TINY_MODEL_NAME, TINY_MODEL_SIZE_MB, "Tiny (~40MB, fastest)"),
        BASE(BASE_MODEL_NAME, BASE_MODEL_SIZE_MB, "Base (~75MB, more accurate)")
    }
}
