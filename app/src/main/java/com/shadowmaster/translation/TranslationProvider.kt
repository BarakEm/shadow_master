package com.shadowmaster.translation

/**
 * Interface for translation service providers.
 * Supports multiple backends (Google Translate, DeepL, custom endpoints).
 */
interface TranslationProvider {
    /** Display name of the provider */
    val name: String
    
    /** Whether this provider requires an API key */
    val requiresApiKey: Boolean
    
    /** List of supported target language ISO codes (e.g., "en", "es", "de") */
    val supportedTargetLanguages: List<String>
    
    /**
     * Translate text from source language to target language.
     * 
     * @param text Text to translate
     * @param sourceLanguage ISO 639-1 language code (e.g., "en", "es")
     * @param targetLanguage ISO 639-1 language code
     * @return Result with translated text or error
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<String>
    
    /**
     * Check if this provider supports the given language pair.
     */
    fun supportsLanguagePair(sourceLanguage: String, targetLanguage: String): Boolean {
        // Most providers support any source language
        return supportedTargetLanguages.contains(targetLanguage) || 
               supportedTargetLanguages.contains("*")
    }
}
