package com.shadowmaster.transcription

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * ivrit.ai Hebrew speech recognition provider.
 * 
 * Free service specialized for Hebrew language transcription.
 * No API key required for basic usage.
 * 
 * API Documentation: https://ivrit.ai/
 * 
 * Features:
 * - Free tier available (no API key needed)
 * - Optimized for Hebrew language
 * - Supports various audio formats
 * - Fast and accurate Hebrew transcription
 * 
 * Note: The API endpoint and response format are based on the expected
 * ivrit.ai service design. If the API is not responding as expected,
 * check the logs for detailed error messages and verify the service is available.
 */
class IvritAIProvider(
    private val apiKey: String? = null
) : TranscriptionProvider {

    override val name: String = "ivrit.ai (Hebrew)"
    override val id: String = TranscriptionProviderType.IVRIT_AI.id
    override val requiresApiKey: Boolean = false // Free tier available

    companion object {
        private const val TAG = "IvritAIProvider"
        private const val API_BASE_URL = "https://ivrit.ai/api/v1"
        private const val TRANSCRIBE_ENDPOINT = "$API_BASE_URL/transcribe"
        private const val MAX_FILE_SIZE_MB = 25
        private const val TIMEOUT_SECONDS = 60L
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun validateConfiguration(): Result<Unit> {
        // ivrit.ai works without API key for free tier
        // API key is optional for higher limits
        return Result.success(Unit)
    }

    override suspend fun transcribe(audioFile: File, language: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validate file size
            val fileSizeMB = audioFile.length() / (1024.0 * 1024.0)
            if (fileSizeMB > MAX_FILE_SIZE_MB) {
                return@withContext Result.failure(
                    TranscriptionError.ProviderError(
                        name,
                        "Audio file too large: ${String.format("%.1f", fileSizeMB)}MB (max: ${MAX_FILE_SIZE_MB}MB)"
                    )
                )
            }

            Log.d(TAG, "Transcribing audio file: ${audioFile.name} (${String.format("%.1f", fileSizeMB)}MB)")

            // Determine audio format and prepare for upload
            val mimeType = when (audioFile.extension.lowercase()) {
                "wav" -> "audio/wav"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                "flac" -> "audio/flac"
                else -> "application/octet-stream"
            }

            // Build multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    audioFile.name,
                    audioFile.asRequestBody(mimeType.toMediaType())
                )
                .apply {
                    // Add language parameter if Hebrew-related
                    if (language.startsWith("he", ignoreCase = true) || language == "auto") {
                        addFormDataPart("language", "he")
                    }
                    
                    // Add API key if provided (for premium features)
                    if (!apiKey.isNullOrBlank()) {
                        addFormDataPart("api_key", apiKey)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(TRANSCRIBE_ENDPOINT)
                .post(requestBody)
                .apply {
                    // Add Authorization header if API key is provided
                    if (!apiKey.isNullOrBlank()) {
                        addHeader("Authorization", "Bearer $apiKey")
                    }
                }
                .build()

            Log.d(TAG, "Sending request to ivrit.ai API...")
            Log.d(TAG, "Request URL: $TRANSCRIBE_ENDPOINT")
            Log.d(TAG, "Language: $language")
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Response code: ${response.code}")
            Log.d(TAG, "Response body: $responseBody")

            if (!response.isSuccessful) {
                Log.e(TAG, "API request failed: ${response.code} - $responseBody")
                return@withContext Result.failure(
                    TranscriptionError.NetworkError(
                        name,
                        Exception("API request failed: ${response.code} - ${responseBody ?: "Unknown error"}")
                    )
                )
            }

            if (responseBody.isNullOrBlank()) {
                return@withContext Result.failure(
                    TranscriptionError.ProviderError(name, "Empty response from API")
                )
            }

            // Parse JSON response
            val jsonResponse = JSONObject(responseBody)
            
            // Handle error in response
            if (jsonResponse.has("error")) {
                val errorMessage = jsonResponse.optString("error", "Unknown error")
                Log.e(TAG, "API returned error: $errorMessage")
                return@withContext Result.failure(
                    TranscriptionError.ProviderError(name, errorMessage)
                )
            }

            // Extract transcription text
            val transcription = when {
                jsonResponse.has("transcription") -> jsonResponse.getString("transcription")
                jsonResponse.has("text") -> jsonResponse.getString("text")
                jsonResponse.has("result") -> jsonResponse.getString("result")
                else -> {
                    Log.e(TAG, "Unexpected response format: $responseBody")
                    return@withContext Result.failure(
                        TranscriptionError.ProviderError(
                            name,
                            "Unexpected response format from API"
                        )
                    )
                }
            }

            if (transcription.isBlank()) {
                return@withContext Result.failure(
                    TranscriptionError.ProviderError(name, "No speech detected in audio")
                )
            }

            Log.d(TAG, "Transcription successful: $transcription")
            Result.success(transcription.trim())

        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Network error: Cannot reach ivrit.ai", e)
            Result.failure(
                TranscriptionError.NetworkError(
                    name,
                    Exception("Cannot reach ivrit.ai. Please check your internet connection.", e)
                )
            )
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout error", e)
            Result.failure(
                TranscriptionError.NetworkError(
                    name,
                    Exception("Request timed out. The audio file might be too long.", e)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(TranscriptionError.UnknownError(name, e))
        }
    }
}
