package com.shadowmaster.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Google Cloud Translation API provider.
 * Requires API key from Google Cloud Console.
 * Documentation: https://cloud.google.com/translate/docs/reference/rest
 */
class GoogleTranslateProvider(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) : TranslationProvider {
    
    override val name: String = "Google Translate"
    override val requiresApiKey: Boolean = true
    
    // Google Translate supports 100+ languages
    override val supportedTargetLanguages: List<String> = listOf(
        "en", "es", "de", "ru", "he", "ar", "fr", "it", "pt", "zh", 
        "ja", "ko", "hi", "bn", "pa", "te", "mr", "ta", "ur", "gu",
        "kn", "ml", "or", "af", "sq", "am", "hy", "az", "eu", "be",
        "bs", "bg", "ca", "ceb", "ny", "co", "hr", "cs", "da", "nl",
        "eo", "et", "tl", "fi", "fy", "gl", "ka", "el", "ht", "ha",
        "haw", "iw", "hmn", "hu", "is", "ig", "id", "ga", "jw", "kk",
        "km", "ku", "ky", "lo", "la", "lv", "lt", "lb", "mk", "mg",
        "ms", "mt", "mi", "mn", "my", "ne", "no", "ps", "fa", "pl",
        "ro", "sm", "gd", "sr", "st", "sn", "sd", "si", "sk", "sl",
        "so", "su", "sw", "sv", "tg", "th", "tr", "uk", "uz", "vi",
        "cy", "xh", "yi", "yo", "zu"
    )
    
    private companion object {
        const val API_URL = "https://translation.googleapis.com/language/translate/v2"
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
            
            // Build request JSON
            val jsonBody = JSONObject().apply {
                put("q", text)
                put("source", normalizeLanguageCode(sourceLanguage))
                put("target", normalizeLanguageCode(targetLanguage))
                put("format", "text")
            }
            
            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$API_URL?key=$apiKey")
                .post(requestBody)
                .build()
            
            // Execute request
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext when (response.code) {
                    401, 403 -> Result.failure(TranslationError.AuthenticationFailed(name))
                    429 -> Result.failure(TranslationError.QuotaExceeded(name))
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
            val data = jsonResponse.getJSONObject("data")
            val translations = data.getJSONArray("translations")
            
            if (translations.length() == 0) {
                return@withContext Result.failure(
                    TranslationError.InvalidResponse(name, "No translations in response")
                )
            }
            
            val translatedText = translations.getJSONObject(0).getString("translatedText")
            Result.success(translatedText)
            
        } catch (e: IOException) {
            Result.failure(TranslationError.NetworkError(name, e))
        } catch (e: Exception) {
            Result.failure(TranslationError.InvalidResponse(name, e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Normalize language codes to Google's format.
     * 
     * Google Translate API has specific language code requirements:
     * - Uses "zh-CN" for Simplified Chinese (default for "zh")
     * - Uses "iw" for Hebrew instead of ISO 639-1 "he"
     * - Most other languages use standard ISO 639-1 two-letter codes
     */
    private fun normalizeLanguageCode(code: String): String {
        return when (code.lowercase().take(2)) {
            "zh" -> "zh-CN"  // Default to Simplified Chinese
            "he" -> "iw"      // Google API uses "iw" for Hebrew (legacy ISO 639 code)
            else -> code.lowercase().take(2)
        }
    }
}
