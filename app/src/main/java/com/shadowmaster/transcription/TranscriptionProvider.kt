package com.shadowmaster.transcription

import java.io.File

/**
 * Interface for speech-to-text transcription providers.
 * 
 * Implementations provide transcription services from various providers
 * including Google, Azure, OpenAI Whisper, local models, and custom endpoints.
 */
interface TranscriptionProvider {
    /**
     * Display name of the provider (e.g., "Google Speech-to-Text").
     */
    val name: String

    /**
     * Unique identifier for the provider (e.g., "google", "azure", "whisper").
     */
    val id: String

    /**
     * Whether this provider requires an API key to function.
     */
    val requiresApiKey: Boolean

    /**
     * Transcribe audio file to text.
     *
     * @param audioFile Audio file to transcribe (16kHz mono PCM preferred)
     * @param language Language code (e.g., "en-US", "de-DE")
     * @return Result containing transcribed text or error
     */
    suspend fun transcribe(audioFile: File, language: String): Result<String>

    /**
     * Check if the provider is properly configured and ready to use.
     *
     * @return Result indicating success or configuration error
     */
    suspend fun validateConfiguration(): Result<Unit>
}

/**
 * Enum of supported transcription provider types.
 */
enum class TranscriptionProviderType(
    val id: String,
    val displayName: String,
    val isFree: Boolean = false,
    val isImplemented: Boolean = true
) {
    IVRIT_AI("ivrit", "ivrit.ai (Hebrew)", isFree = true),
    LOCAL("local", "Local Model", isFree = true),
    ANDROID_SPEECH("android_speech", "Google Speech (Free)", isFree = true),
    GOOGLE("google", "Google Speech-to-Text", isFree = false, isImplemented = false),
    AZURE("azure", "Azure Speech Services", isFree = false, isImplemented = false),
    WHISPER("whisper", "OpenAI Whisper", isFree = false, isImplemented = false),
    CUSTOM("custom", "Custom Endpoint", isFree = false)
}
