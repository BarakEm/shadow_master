package com.shadowmaster.transcription

import android.content.Context
import android.util.Log
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import javax.inject.Inject

/**
 * Local on-device transcription using Whisper.cpp.
 * 
 * Provides offline transcription without API keys or internet connection.
 * Uses Whisper.cpp with tiny (~40MB) or base (~75MB) models.
 * 
 * Features:
 * - Completely offline after model download
 * - No API costs
 * - Privacy-friendly (no data leaves device)
 * - On-demand model download
 */
class LocalModelProvider(
    private val context: Context,
    private val modelPath: String?
) : TranscriptionProvider {

    override val name: String = "Local Model (Whisper.cpp)"
    override val id: String = TranscriptionProviderType.LOCAL.id
    override val requiresApiKey: Boolean = false

    private var whisperContext: WhisperContext? = null

    companion object {
        private const val TAG = "LocalModelProvider"
        
        // Model URLs from Hugging Face
        private const val TINY_MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
        private const val BASE_MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
        
        // Model file names
        const val TINY_MODEL_NAME = "ggml-tiny.bin"
        const val BASE_MODEL_NAME = "ggml-base.bin"
        
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
            return modelFile.exists() && modelFile.length() > 0
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
                modelFile.delete()
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
            } else if (modelFile.length() == 0L) {
                Result.failure(
                    TranscriptionError.ProviderError(
                        name,
                        "Model file is empty at: $modelPath. Please re-download the model."
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
            
            // Initialize WhisperContext if not already done
            if (whisperContext == null) {
                Log.d(TAG, "Loading Whisper model...")
                whisperContext = WhisperContext.createContextFromFile(modelPath!!)
                Log.d(TAG, "Whisper model loaded successfully")
            }

            // Read audio file as PCM samples
            val samples = readAudioFile(audioFile)
            
            if (samples.isEmpty()) {
                return@withContext Result.failure(
                    TranscriptionError.InvalidAudioFormat("Audio file is empty or could not be read")
                )
            }

            Log.d(TAG, "Transcribing ${samples.size} audio samples...")
            
            // Perform transcription
            // The language code needs to be converted to 2-letter ISO 639-1 format (e.g., "en-US" -> "en")
            val langCode = language.split("-").firstOrNull() ?: "en"
            
            val result = whisperContext?.transcribeData(samples, langCode, null)
            
            if (result != null && result.text.isNotBlank()) {
                Log.d(TAG, "Transcription successful: ${result.text}")
                Result.success(result.text.trim())
            } else {
                Log.w(TAG, "Transcription returned empty result")
                Result.failure(
                    TranscriptionError.ProviderError(
                        name,
                        "Transcription returned no text. The audio might be silence or speech was not detected."
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(TranscriptionError.UnknownError(name, e))
        }
    }

    /**
     * Read audio file and convert to Float array of samples.
     * Expected format: 16kHz mono PCM.
     */
    private fun readAudioFile(audioFile: File): FloatArray {
        // Whisper.cpp expects raw PCM audio at 16kHz
        // If the audio file is WAV, we need to skip the header
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

            // Convert bytes to 16-bit PCM samples, then to float
            val samples = FloatArray((bytes.size - startOffset) / 2)
            var sampleIndex = 0
            
            for (i in startOffset until bytes.size - 1 step 2) {
                // Read 16-bit little-endian sample
                val sample = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                // Convert to signed 16-bit
                val signedSample = if (sample > 32767) sample - 65536 else sample
                // Normalize to [-1.0, 1.0]
                samples[sampleIndex++] = signedSample / 32768.0f
            }
            
            Log.d(TAG, "Converted audio file to ${samples.size} samples")
            samples
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read audio file", e)
            FloatArray(0)
        }
    }

    /**
     * Download a Whisper model from Hugging Face.
     * 
     * @param modelName Name of the model file (e.g., TINY_MODEL_NAME, BASE_MODEL_NAME)
     * @param progressCallback Optional callback for download progress (0.0 to 1.0)
     * @return Result containing the downloaded file or error
     */
    suspend fun downloadModel(
        modelName: String,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val url = getModelUrl(modelName)
            val destFile = getModelPath(context, modelName)
            
            Log.d(TAG, "Downloading model from: $url")
            Log.d(TAG, "Destination: ${destFile.absolutePath}")
            
            // Create parent directory if needed
            destFile.parentFile?.mkdirs()
            
            // Delete existing file if present
            if (destFile.exists()) {
                destFile.delete()
            }
            
            // Download the file
            val connection = URL(url).openConnection()
            connection.connect()
            
            val fileLength = connection.contentLength
            Log.d(TAG, "Model size: ${fileLength / 1024 / 1024} MB")
            
            connection.getInputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Report progress
                        if (fileLength > 0) {
                            val progress = totalBytesRead.toFloat() / fileLength.toFloat()
                            progressCallback?.invoke(progress)
                        }
                    }
                }
            }
            
            Log.d(TAG, "Model downloaded successfully: ${destFile.absolutePath}")
            Result.success(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            Result.failure(
                TranscriptionError.NetworkError(name, e)
            )
        }
    }

    /**
     * Release native resources.
     * Should be called when the provider is no longer needed.
     */
    fun release() {
        whisperContext?.release()
        whisperContext = null
    }

    /**
     * Enum for available Whisper models.
     */
    enum class WhisperModel(val fileName: String, val sizeMB: Int, val displayName: String) {
        TINY(TINY_MODEL_NAME, TINY_MODEL_SIZE_MB, "Tiny (~40MB, fastest)"),
        BASE(BASE_MODEL_NAME, BASE_MODEL_SIZE_MB, "Base (~75MB, more accurate)")
    }
}
