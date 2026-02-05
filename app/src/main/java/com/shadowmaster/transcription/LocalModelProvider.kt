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
        
        // Vosk model definitions for multiple languages
        // Using small models optimized for mobile devices
        
        // English models
        private const val EN_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val EN_BASE_URL = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip"
        const val EN_SMALL_NAME = "vosk-model-small-en-us-0.15"
        const val EN_BASE_NAME = "vosk-model-en-us-0.22"
        
        // German models
        private const val DE_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip"
        const val DE_SMALL_NAME = "vosk-model-small-de-0.15"
        
        // Arabic models
        private const val AR_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ar-0.22-linto.zip"
        const val AR_SMALL_NAME = "vosk-model-small-ar-0.22-linto"
        
        // French models
        private const val FR_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip"
        const val FR_SMALL_NAME = "vosk-model-small-fr-0.22"
        
        // Spanish models
        private const val ES_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        const val ES_SMALL_NAME = "vosk-model-small-es-0.42"
        
        // Chinese models
        private const val CN_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
        const val CN_SMALL_NAME = "vosk-model-small-cn-0.22"
        
        // Russian models
        private const val RU_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        const val RU_SMALL_NAME = "vosk-model-small-ru-0.22"
        
        // Italian models
        private const val IT_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip"
        const val IT_SMALL_NAME = "vosk-model-small-it-0.22"
        
        // Portuguese models
        private const val PT_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
        const val PT_SMALL_NAME = "vosk-model-small-pt-0.3"
        
        // Turkish models
        private const val TR_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip"
        const val TR_SMALL_NAME = "vosk-model-small-tr-0.3"
        
        // Hebrew models
        private const val HE_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-he-0.22.zip"
        const val HE_SMALL_NAME = "vosk-model-small-he-0.22"
        
        // Backward compatibility aliases
        const val TINY_MODEL_NAME = EN_SMALL_NAME
        const val BASE_MODEL_NAME = EN_BASE_NAME
        const val TINY_MODEL_SIZE_MB = 40
        const val BASE_MODEL_SIZE_MB = 75

        /**
         * Get the model directory path.
         */
        fun getModelDir(context: Context): File {
            return File(context.filesDir, "vosk_models").apply {
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
                // English models
                EN_SMALL_NAME -> EN_SMALL_URL
                EN_BASE_NAME -> EN_BASE_URL
                
                // German models
                DE_SMALL_NAME -> DE_SMALL_URL
                
                // Arabic models
                AR_SMALL_NAME -> AR_SMALL_URL
                
                // French models
                FR_SMALL_NAME -> FR_SMALL_URL
                
                // Spanish models
                ES_SMALL_NAME -> ES_SMALL_URL
                
                // Chinese models
                CN_SMALL_NAME -> CN_SMALL_URL
                
                // Russian models
                RU_SMALL_NAME -> RU_SMALL_URL
                
                // Italian models
                IT_SMALL_NAME -> IT_SMALL_URL
                
                // Portuguese models
                PT_SMALL_NAME -> PT_SMALL_URL
                
                // Turkish models
                TR_SMALL_NAME -> TR_SMALL_URL
                
                // Hebrew models
                HE_SMALL_NAME -> HE_SMALL_URL
                
                else -> throw IllegalArgumentException("Unknown model: $modelName. Available models: " +
                        "EN_SMALL, EN_BASE, DE_SMALL, AR_SMALL, FR_SMALL, ES_SMALL, CN_SMALL, " +
                        "RU_SMALL, IT_SMALL, PT_SMALL, TR_SMALL, HE_SMALL")
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
        
        /**
         * Get recommended model name for a language code.
         * Returns the small model for the specified language.
         * 
         * @param languageCode Language code (e.g., "en-US", "de-DE", "ar-SA")
         * @return Model name or null if language not supported
         */
        fun getModelForLanguage(languageCode: String): String? {
            // Extract base language code (e.g., "en" from "en-US")
            val baseLang = languageCode.lowercase().split("-", "_").firstOrNull() ?: return null
            
            return when (baseLang) {
                "en" -> EN_SMALL_NAME
                "de" -> DE_SMALL_NAME
                "ar" -> AR_SMALL_NAME
                "fr" -> FR_SMALL_NAME
                "es" -> ES_SMALL_NAME
                "zh", "cn" -> CN_SMALL_NAME
                "ru" -> RU_SMALL_NAME
                "it" -> IT_SMALL_NAME
                "pt" -> PT_SMALL_NAME
                "tr" -> TR_SMALL_NAME
                "he", "iw" -> HE_SMALL_NAME  // iw is old Hebrew code
                else -> null
            }
        }
        
        /**
         * Get all supported language codes.
         */
        fun getSupportedLanguages(): List<String> {
            return listOf("en", "de", "ar", "fr", "es", "zh", "ru", "it", "pt", "tr", "he")
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
                return@withContext Result.failure(
                    TranscriptionError.ProviderError(name, "Failed to read audio file")
                )
            }
            
            // Process audio data
            recognizer.acceptWaveForm(audioData, audioData.size)
            val resultJson = recognizer.finalResult
            
            // Parse JSON result
            val jsonObject = JSONObject(resultJson)
            val transcribedText = jsonObject.optString("text", "")
            
            // Note: Model and Recognizer don't have explicit cleanup methods in Vosk Android 0.3.47
            // They are JNA PointerType objects that will be cleaned up by the garbage collector
            // when they go out of scope. For long-running services, consider implementing
            // a model cache to avoid repeated allocation/deallocation.
            
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
     * Enum for available Vosk models by language.
     */
    enum class VoskModel(
        val fileName: String, 
        val sizeMB: Int, 
        val displayName: String,
        val language: String,
        val languageCode: String
    ) {
        // English models
        EN_SMALL(EN_SMALL_NAME, 40, "English (Small, ~40MB)", "English", "en"),
        EN_BASE(EN_BASE_NAME, 75, "English (Base, ~75MB)", "English", "en"),
        
        // German models
        DE_SMALL(DE_SMALL_NAME, 45, "German (Small, ~45MB)", "Deutsch", "de"),
        
        // Arabic models
        AR_SMALL(AR_SMALL_NAME, 45, "Arabic (Small, ~45MB)", "العربية", "ar"),
        
        // French models
        FR_SMALL(FR_SMALL_NAME, 39, "French (Small, ~39MB)", "Français", "fr"),
        
        // Spanish models
        ES_SMALL(ES_SMALL_NAME, 39, "Spanish (Small, ~39MB)", "Español", "es"),
        
        // Chinese models
        CN_SMALL(CN_SMALL_NAME, 42, "Chinese (Small, ~42MB)", "中文", "zh"),
        
        // Russian models
        RU_SMALL(RU_SMALL_NAME, 45, "Russian (Small, ~45MB)", "Русский", "ru"),
        
        // Italian models
        IT_SMALL(IT_SMALL_NAME, 48, "Italian (Small, ~48MB)", "Italiano", "it"),
        
        // Portuguese models
        PT_SMALL(PT_SMALL_NAME, 31, "Portuguese (Small, ~31MB)", "Português", "pt"),
        
        // Turkish models
        TR_SMALL(TR_SMALL_NAME, 35, "Turkish (Small, ~35MB)", "Türkçe", "tr"),
        
        // Hebrew models
        HE_SMALL(HE_SMALL_NAME, 38, "Hebrew (Small, ~38MB)", "עברית", "he");
        
        companion object {
            /**
             * Get available models for a specific language code.
             * @param languageCode Language code (e.g., "en", "de", "ar")
             * @return List of available models for that language
             */
            fun getModelsForLanguage(languageCode: String): List<VoskModel> {
                val normalizedCode = languageCode.lowercase().take(2)
                return values().filter { it.languageCode == normalizedCode }
            }
            
            /**
             * Get all supported language codes.
             */
            fun getSupportedLanguages(): List<String> {
                return values().map { it.languageCode }.distinct().sorted()
            }
            
            /**
             * Get the recommended (first) model for a language.
             */
            fun getRecommendedModelForLanguage(languageCode: String): VoskModel? {
                return getModelsForLanguage(languageCode).firstOrNull()
            }
        }
        
        // Backward compatibility aliases
        @Deprecated("Use EN_SMALL instead", ReplaceWith("EN_SMALL"))
        companion object {
            val TINY = EN_SMALL
            val BASE = EN_BASE
        }
    }
}
