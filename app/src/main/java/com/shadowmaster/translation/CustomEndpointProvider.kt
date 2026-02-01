package com.shadowmaster.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Custom endpoint translation provider.
 * Allows using any HTTP translation API with configurable endpoint and headers.
 * 
 * Expected JSON request format:
 * ```json
 * {
 *   "text": "Hello",
 *   "source_language": "en",
 *   "target_language": "es"
 * }
 * ```
 * 
 * Expected JSON response format:
 * ```json
 * {
 *   "translated_text": "Hola"
 * }
 * ```
 */
class CustomEndpointProvider(
    private val url: String,
    private val apiKey: String? = null,
    private val headers: Map<String, String> = emptyMap(),
    private val httpClient: OkHttpClient = OkHttpClient()
) : TranslationProvider {
    
    override val name: String = "Custom Endpoint"
    override val requiresApiKey: Boolean = false
    
    // Assume custom endpoint supports all languages
    override val supportedTargetLanguages: List<String> = listOf("*")
    
    private companion object {
        const val MAX_TEXT_LENGTH = 10000
    }
    
    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validate URL
            if (url.isBlank()) {
                return@withContext Result.failure(
                    TranslationError.InvalidResponse(name, "URL not configured")
                )
            }
            
            // Check text length
            if (text.length > MAX_TEXT_LENGTH) {
                return@withContext Result.failure(
                    TranslationError.TextTooLong(text.length, MAX_TEXT_LENGTH)
                )
            }
            
            // Build request JSON
            val jsonBody = JSONObject().apply {
                put("text", text)
                put("source_language", sourceLanguage)
                put("target_language", targetLanguage)
            }
            
            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())
            
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
            
            // Add API key header if provided
            if (!apiKey.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            
            // Add custom headers
            headers.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }
            
            val request = requestBuilder.build()
            
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
            
            // Try common response field names
            val translatedText = when {
                jsonResponse.has("translated_text") -> 
                    jsonResponse.getString("translated_text")
                jsonResponse.has("translation") -> 
                    jsonResponse.getString("translation")
                jsonResponse.has("text") -> 
                    jsonResponse.getString("text")
                jsonResponse.has("result") -> 
                    jsonResponse.getString("result")
                else -> return@withContext Result.failure(
                    TranslationError.InvalidResponse(
                        name, 
                        "No translation field found in response. Expected: translated_text, translation, text, or result"
                    )
                )
            }
            
            Result.success(translatedText)
            
        } catch (e: IOException) {
            Result.failure(TranslationError.NetworkError(name, e))
        } catch (e: Exception) {
            Result.failure(TranslationError.InvalidResponse(name, e.message ?: "Unknown error"))
        }
    }
}
