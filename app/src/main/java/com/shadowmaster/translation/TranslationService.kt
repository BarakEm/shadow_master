package com.shadowmaster.translation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing translation providers and routing translation requests.
 * Provides a unified interface for translating text using configured providers.
 */
@Singleton
class TranslationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Available provider types.
     */
    enum class ProviderType {
        MOCK,
        GOOGLE,
        DEEPL,
        CUSTOM
    }
    
    /**
     * Create a translation provider instance based on configuration.
     * 
     * @param providerType Type of provider to create
     * @param apiKey API key for the provider (if required)
     * @param customUrl Custom endpoint URL (for CUSTOM provider)
     * @param customHeaders Custom headers (for CUSTOM provider)
     * @return TranslationProvider instance
     */
    fun createProvider(
        providerType: ProviderType,
        apiKey: String? = null,
        customUrl: String? = null,
        customHeaders: Map<String, String> = emptyMap()
    ): TranslationProvider {
        return when (providerType) {
            ProviderType.MOCK -> MockTranslationProvider()
            
            ProviderType.GOOGLE -> GoogleTranslateProvider(
                apiKey = apiKey ?: ""
            )
            
            ProviderType.DEEPL -> DeepLProvider(
                apiKey = apiKey ?: ""
            )
            
            ProviderType.CUSTOM -> CustomEndpointProvider(
                url = customUrl ?: "",
                apiKey = apiKey,
                headers = customHeaders
            )
        }
    }
    
    /**
     * Translate text using the specified provider.
     * 
     * @param text Text to translate
     * @param sourceLanguage Source language ISO code
     * @param targetLanguage Target language ISO code
     * @param provider Provider instance to use
     * @return Result with translated text or error
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        provider: TranslationProvider
    ): Result<String> {
        // Validate input
        if (text.isBlank()) {
            return Result.failure(
                TranslationError.InvalidResponse(provider.name, "Empty text")
            )
        }
        
        // Check language pair support
        if (!provider.supportsLanguagePair(sourceLanguage, targetLanguage)) {
            return Result.failure(
                TranslationError.UnsupportedLanguagePair(
                    sourceLanguage,
                    targetLanguage,
                    provider.name
                )
            )
        }
        
        // Perform translation
        return provider.translate(text, sourceLanguage, targetLanguage)
    }
    
    /**
     * Batch translate multiple texts using the same provider.
     * 
     * @param texts List of texts to translate
     * @param sourceLanguage Source language ISO code
     * @param targetLanguage Target language ISO code
     * @param provider Provider instance to use
     * @param onProgress Callback for progress updates (current, total)
     * @return List of results (success or failure for each text)
     */
    suspend fun batchTranslate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        provider: TranslationProvider,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<Result<String>> {
        val results = mutableListOf<Result<String>>()
        
        texts.forEachIndexed { index, text ->
            val result = translate(text, sourceLanguage, targetLanguage, provider)
            results.add(result)
            onProgress?.invoke(index + 1, texts.size)
        }
        
        return results
    }
    
    /**
     * Get list of supported target languages for a provider type.
     */
    fun getSupportedLanguages(providerType: ProviderType): List<String> {
        val provider = createProvider(providerType)
        return provider.supportedTargetLanguages
    }
}
