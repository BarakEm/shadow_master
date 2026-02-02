package com.shadowmaster.transcription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Service for managing transcription providers and routing transcription requests.
 * 
 * This service acts as a facade for all transcription operations, handling:
 * - Provider selection and instantiation
 * - Configuration validation
 * - Error handling and retry logic
 * - Provider fallback (future enhancement)
 */
@ViewModelScoped
class TranscriptionService @Inject constructor(
    @ApplicationContext private val context: Context
) {

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
            TranscriptionProviderType.GOOGLE -> {
                GoogleSpeechProvider(config.googleApiKey)
            }
            TranscriptionProviderType.AZURE -> {
                AzureSpeechProvider(config.azureApiKey, config.azureRegion)
            }
            TranscriptionProviderType.WHISPER -> {
                WhisperAPIProvider(config.whisperApiKey)
            }
            TranscriptionProviderType.LOCAL -> {
                config.localModelPath?.let { modelPath ->
                    LocalModelProvider(context, modelPath)
                }
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

        // Perform transcription
        provider.transcribe(audioFile, language)
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
            TranscriptionProviderType.GOOGLE,
            TranscriptionProviderType.AZURE,
            TranscriptionProviderType.WHISPER,
            TranscriptionProviderType.LOCAL,
            TranscriptionProviderType.CUSTOM
        )
    }
}

/**
 * Configuration for transcription providers.
 * Contains all provider-specific settings needed to instantiate providers.
 */
data class ProviderConfig(
    val googleApiKey: String? = null,
    val azureApiKey: String? = null,
    val azureRegion: String? = null,
    val whisperApiKey: String? = null,
    val localModelPath: String? = null,
    val customEndpointUrl: String? = null,
    val customEndpointApiKey: String? = null,
    val customEndpointHeaders: Map<String, String> = emptyMap()
)
