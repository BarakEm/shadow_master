package com.shadowmaster.transcription

import android.content.Context
import android.util.Log
import com.shadowmaster.library.AudioFileUtility
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing transcription providers and routing transcription requests.
 * Wraps raw PCM files in WAV headers before sending to API-based providers.
 */
@Singleton
class TranscriptionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioFileUtility: AudioFileUtility
) {
    companion object {
        private const val TAG = "TranscriptionService"
    }

    /**
     * Create a provider instance based on configuration.
     * 
     * @param providerType Type of provider to create
     * @param config Provider-specific configuration
     * @return TranscriptionProvider instance or null if configuration is invalid
     */
    fun createProvider(
        providerType: TranscriptionProviderType,
        config: ProviderConfig
    ): TranscriptionProvider? {
        return when (providerType) {
            TranscriptionProviderType.IVRIT_AI -> {
                IvritAIProvider(config.ivritApiKey)
            }
            TranscriptionProviderType.LOCAL -> {
                // Auto-detect model path if not configured
                val modelPath = config.localModelPath 
                    ?: LocalModelProvider.autoDetectModel(context)
                
                modelPath?.let { path ->
                    LocalModelProvider(context, path)
                }
            }
            TranscriptionProviderType.ANDROID_SPEECH -> {
                AndroidSpeechProvider(context)
            }
            TranscriptionProviderType.GOOGLE -> {
                GoogleSpeechProvider(config.googleApiKey)
            }
            TranscriptionProviderType.AZURE -> {
                AzureSpeechProvider(config.azureApiKey, config.azureRegion)
            }
            TranscriptionProviderType.WHISPER -> {
                WhisperAPIProvider(config.whisperApiKey, config.whisperBaseUrl)
            }
            TranscriptionProviderType.CUSTOM -> {
                CustomEndpointProvider(
                    config.customEndpointUrl,
                    config.customEndpointApiKey,
                    config.customEndpointHeaders
                )
            }
        }
    }

    /**
     * Transcribe audio file using the specified provider.
     * 
     * @param audioFile Audio file to transcribe
     * @param language Language code (e.g., "en-US")
     * @param providerType Type of provider to use
     * @param config Provider configuration
     * @return Result containing transcribed text or error
     */
    suspend fun transcribe(
        audioFile: File,
        language: String,
        providerType: TranscriptionProviderType,
        config: ProviderConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        val provider = createProvider(providerType, config)
            ?: return@withContext Result.failure(
                TranscriptionError.ProviderError(
                    providerType.displayName,
                    "Provider not available or not properly configured"
                )
            )

        // Validate configuration before attempting transcription
        provider.validateConfiguration().getOrElse { error ->
            return@withContext Result.failure(error)
        }

        // API-based providers can't handle raw PCM — wrap in WAV header
        val needsWavWrapper = audioFile.extension.equals("pcm", ignoreCase = true)
                && providerType != TranscriptionProviderType.LOCAL
        var tempWavFile: File? = null

        try {
            val fileToTranscribe = if (needsWavWrapper) {
                tempWavFile = wrapPcmAsWav(audioFile)
                Log.d(TAG, "Wrapped PCM as WAV for ${providerType.displayName}: ${tempWavFile.length()} bytes")
                tempWavFile
            } else {
                audioFile
            }

            provider.transcribe(fileToTranscribe, language)
        } finally {
            tempWavFile?.delete()
        }
    }

    /** Wrap raw 16kHz mono PCM in a WAV header so API providers can parse it. */
    private fun wrapPcmAsWav(pcmFile: File): File {
        val pcmBytes = pcmFile.readBytes()
        val wavHeader = audioFileUtility.createWavHeader(pcmBytes.size)
        val tempWav = File(context.cacheDir, "transcribe_${System.currentTimeMillis()}.wav")
        FileOutputStream(tempWav).use { out ->
            out.write(wavHeader)
            out.write(pcmBytes)
        }
        return tempWav
    }

    /**
     * Validate provider configuration without attempting transcription.
     * 
     * @param providerType Type of provider to validate
     * @param config Provider configuration
     * @return Result indicating success or configuration error
     */
    suspend fun validateProvider(
        providerType: TranscriptionProviderType,
        config: ProviderConfig
    ): Result<Unit> {
        val provider = createProvider(providerType, config)
            ?: return Result.failure(
                TranscriptionError.ProviderError(
                    providerType.displayName,
                    "Provider not available"
                )
            )

        return provider.validateConfiguration()
    }

    /**
     * Get list of available provider types.
     * 
     * @return List of available provider types
     */
    fun getAvailableProviders(): List<TranscriptionProviderType> {
        return listOf(
            TranscriptionProviderType.IVRIT_AI,
            TranscriptionProviderType.LOCAL,
            TranscriptionProviderType.ANDROID_SPEECH,
            TranscriptionProviderType.GOOGLE,
            TranscriptionProviderType.AZURE,
            TranscriptionProviderType.WHISPER,
            TranscriptionProviderType.CUSTOM
        )
    }
}

/**
 * Configuration for transcription providers.
 * Contains all provider-specific settings needed to instantiate providers.
 */
data class ProviderConfig(
    val ivritApiKey: String? = null,
    val googleApiKey: String? = null,
    val azureApiKey: String? = null,
    val azureRegion: String? = null,
    val whisperApiKey: String? = null,
    val whisperBaseUrl: String? = null,
    val localModelPath: String? = null,
    val customEndpointUrl: String? = null,
    val customEndpointApiKey: String? = null,
    val customEndpointHeaders: Map<String, String> = emptyMap()
)
