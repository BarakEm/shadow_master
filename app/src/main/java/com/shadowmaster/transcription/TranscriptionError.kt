package com.shadowmaster.transcription

/**
 * Sealed class representing all possible transcription errors.
 * Provides structured error handling for transcription operations.
 */
sealed class TranscriptionError : Exception() {
    /**
     * API key is missing for a provider that requires it.
     */
    data class ApiKeyMissing(val provider: String) : TranscriptionError() {
        override val message: String
            get() = "API key is required for $provider but not configured"
    }

    /**
     * Network error occurred during transcription.
     */
    data class NetworkError(val provider: String, override val cause: Throwable) : TranscriptionError() {
        override val message: String
            get() = "Network error with $provider: ${cause.message}"
    }

    /**
     * API quota exceeded for the provider.
     */
    data class QuotaExceeded(val provider: String) : TranscriptionError() {
        override val message: String
            get() = "API quota exceeded for $provider"
    }

    /**
     * Unsupported language for the provider.
     */
    data class UnsupportedLanguage(val language: String, val provider: String) : TranscriptionError() {
        override val message: String
            get() = "Language '$language' is not supported by $provider"
    }

    /**
     * Audio file is too long for the provider.
     */
    data class AudioTooLong(val durationMs: Long, val maxMs: Long) : TranscriptionError() {
        override val message: String
            get() = "Audio duration ${durationMs}ms exceeds maximum ${maxMs}ms"
    }

    /**
     * Invalid audio format.
     */
    data class InvalidAudioFormat(val format: String) : TranscriptionError() {
        override val message: String
            get() = "Invalid audio format: $format"
    }

    /**
     * Provider-specific error with custom message.
     */
    data class ProviderError(val provider: String, override val message: String) : TranscriptionError()

    /**
     * Unknown error occurred during transcription.
     */
    data class UnknownError(val provider: String, override val cause: Throwable? = null) : TranscriptionError() {
        override val message: String
            get() = "Unknown error with $provider: ${cause?.message ?: "No details"}"
    }
}
