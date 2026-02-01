package com.shadowmaster.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * DeepL Translation API provider.
 * Requires API key from DeepL.
 * 
 * Language variants:
 * - English: Auto-detects EN-US vs EN-GB based on context
 * - Portuguese: Defaults to PT-BR (Brazilian Portuguese)
 * - Chinese: Uses ZH for Simplified Chinese
 * 
 * Documentation: https://www.deepl.com/docs-api
 */
class DeepLProvider(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) : TranslationProvider {
    
    override val name: String = "DeepL"
    override val requiresApiKey: Boolean = true
    
    // DeepL supports fewer languages but higher quality
    override val supportedTargetLanguages: List<String> = listOf(
        "ar", "bg", "cs", "da", "de", "el", "en", "es", "et", "fi",
        "fr", "hu", "id", "it", "ja", "ko", "lt", "lv", "nb", "nl",
        "pl", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "zh"
    )
    
    private companion object {
        // DeepL API has two endpoints - free and pro
        const val API_URL_FREE = "https://api-free.deepl.com/v2/translate"
        const val API_URL_PRO = "https://api.deepl.com/v2/translate"
        const val MAX_TEXT_LENGTH = 5000
    }
    
    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validate API key
            if (apiKey.isBlank()) {
                return@withContext Result.failure(TranslationError.ApiKeyMissing(name))
            }
            
            // Check text length
            if (text.length > MAX_TEXT_LENGTH) {
                return@withContext Result.failure(
                    TranslationError.TextTooLong(text.length, MAX_TEXT_LENGTH)
                )
            }
            
            // Check language pair support
            if (!supportsLanguagePair(sourceLanguage, targetLanguage)) {
                return@withContext Result.failure(
                    TranslationError.UnsupportedLanguagePair(sourceLanguage, targetLanguage, name)
                )
            }
            
            // Determine API endpoint based on key suffix
            val apiUrl = if (apiKey.endsWith(":fx")) {
                API_URL_FREE
            } else {
                API_URL_PRO
            }
            
            // Build form request (DeepL uses form encoding, not JSON)
            val formBody = FormBody.Builder()
                .add("text", text)
                .add("source_lang", normalizeLanguageCode(sourceLanguage).uppercase())
                .add("target_lang", normalizeLanguageCode(targetLanguage).uppercase())
                .build()
            
            val request = Request.Builder()
                .url(apiUrl)
                .header("Authorization", "DeepL-Auth-Key $apiKey")
                .post(formBody)
                .build()
            
            // Execute request
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext when (response.code) {
                    401, 403 -> Result.failure(TranslationError.AuthenticationFailed(name))
                    429 -> Result.failure(TranslationError.QuotaExceeded(name))
                    456 -> Result.failure(TranslationError.QuotaExceeded(name)) // DeepL specific
                    else -> Result.failure(
                        TranslationError.NetworkError(
                            name,
                            IOException("HTTP ${response.code}: ${response.message}")
                        )
                    )
                }
            }
            
            // Parse response
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(
                    TranslationError.InvalidResponse(name, "Empty response body")
                )
            
            val jsonResponse = JSONObject(responseBody)
            val translations = jsonResponse.getJSONArray("translations")
            
            if (translations.length() == 0) {
                return@withContext Result.failure(
                    TranslationError.InvalidResponse(name, "No translations in response")
                )
            }
            
            val translatedText = translations.getJSONObject(0).getString("text")
            Result.success(translatedText)
            
        } catch (e: IOException) {
            Result.failure(TranslationError.NetworkError(name, e))
        } catch (e: Exception) {
            Result.failure(TranslationError.InvalidResponse(name, e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Normalize language codes to DeepL's format.
     * 
     * DeepL API language code conventions:
     * - English: "EN" (auto-detects EN-US vs EN-GB)
     * - Portuguese: "PT-BR" (defaults to Brazilian Portuguese; use "PT-PT" for European)
     * - Chinese: "ZH" (Simplified Chinese)
     * - All codes are uppercase (DeepL requirement)
     */
    private fun normalizeLanguageCode(code: String): String {
        return when (code.lowercase().take(2)) {
            "en" -> "EN"  // DeepL auto-detects EN-US vs EN-GB
            "pt" -> "PT-BR"  // Default to Brazilian Portuguese
            "zh" -> "ZH"
            else -> code.uppercase().take(2)
        }
    }
}
