package com.shadowmaster.translation

/**
 * Sealed class representing translation errors.
 * Provides structured error handling with user-friendly messages.
 */
sealed class TranslationError : Exception() {
    data class ApiKeyMissing(val provider: String) : TranslationError() {
        override val message: String
            get() = "API key missing for $provider"
    }
    
    data class NetworkError(
        val provider: String, 
        override val cause: Throwable
    ) : TranslationError() {
        override val message: String
            get() = "Network error with $provider: ${cause.message}"
    }
    
    data class QuotaExceeded(val provider: String) : TranslationError() {
        override val message: String
            get() = "Translation quota exceeded for $provider"
    }
    
    data class UnsupportedLanguagePair(
        val sourceLanguage: String,
        val targetLanguage: String,
        val provider: String
    ) : TranslationError() {
        override val message: String
            get() = "$provider doesn't support $sourceLanguage → $targetLanguage"
    }
    
    data class TextTooLong(
        val length: Int, 
        val maxLength: Int
    ) : TranslationError() {
        override val message: String
            get() = "Text too long ($length chars). Maximum: $maxLength"
    }
    
    data class TranscriptionMissing(val itemId: String) : TranslationError() {
        override val message: String
            get() = "Transcription missing for item $itemId"
    }
    
    data class InvalidResponse(val provider: String, val details: String) : TranslationError() {
        override val message: String
            get() = "Invalid response from $provider: $details"
    }
    
    data class AuthenticationFailed(val provider: String) : TranslationError() {
        override val message: String
            get() = "Authentication failed for $provider. Check your API key."
    }
}

/**
 * Convert TranslationError to user-friendly message for UI display.
 */
fun TranslationError.toUserMessage(): String = when (this) {
    is TranslationError.ApiKeyMissing -> 
        "Please configure $provider API key in Settings → Services"
    is TranslationError.NetworkError -> 
        "Network error with $provider: ${cause.message ?: "Unknown error"}"
    is TranslationError.QuotaExceeded -> 
        "$provider quota exceeded. Try another provider."
    is TranslationError.UnsupportedLanguagePair -> 
        "$provider doesn't support $sourceLanguage → $targetLanguage"
    is TranslationError.TextTooLong -> 
        "Text too long ($length chars). Max: $maxLength"
    is TranslationError.TranscriptionMissing -> 
        "Please transcribe this segment first before translating"
    is TranslationError.InvalidResponse -> 
        "Invalid response from $provider: $details"
    is TranslationError.AuthenticationFailed ->
        "Authentication failed for $provider. Check your API key."
}
