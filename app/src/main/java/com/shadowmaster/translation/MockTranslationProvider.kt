package com.shadowmaster.translation

/**
 * Mock translation provider for testing and debugging.
 * Returns fake translations without making API calls.
 */
class MockTranslationProvider : TranslationProvider {
    override val name: String = "Mock (Testing)"
    override val requiresApiKey: Boolean = false
    override val supportedTargetLanguages: List<String> = listOf("*")
    
    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<String> {
        // Simulate network delay
        kotlinx.coroutines.delay(500)
        
        // Return mock translation
        val mockTranslation = "[Mock $sourceLanguage → $targetLanguage: $text]"
        return Result.success(mockTranslation)
    }
}
