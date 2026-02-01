package com.shadowmaster.transcription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Custom endpoint provider for transcription.
 * 
 * Allows integration with custom or self-hosted transcription services
 * that expose an HTTP API endpoint. Supports configurable headers and
 * optional API key authentication.
 * 
 * Expected API contract:
 * - POST request with multipart/form-data
 * - Audio file uploaded as "file" field
 * - Language parameter as "language" field
 * - Response: JSON with "text" field containing transcription
 * 
 * Example response format:
 * ```json
 * {
 *   "text": "transcribed text here",
 *   "language": "en-US",
 *   "confidence": 0.95
 * }
 * ```
 */
class CustomEndpointProvider(
    private val url: String?,
    private val apiKey: String?,
    private val headers: Map<String, String> = emptyMap(),
    private val client: OkHttpClient = OkHttpClient()
) : TranscriptionProvider {

    override val name: String = "Custom Endpoint"
    override val id: String = TranscriptionProviderType.CUSTOM.id
    override val requiresApiKey: Boolean = false

    override suspend fun validateConfiguration(): Result<Unit> {
        return when {
            url.isNullOrBlank() -> {
                Result.failure(
                    TranscriptionError.ProviderError(
                        name,
                        "Custom endpoint URL is required but not configured"
                    )
                )
            }
            !url.startsWith("http://") && !url.startsWith("https://") -> {
                Result.failure(
                    TranscriptionError.ProviderError(
                        name,
                        "Custom endpoint URL must start with http:// or https://"
                    )
                )
            }
            else -> Result.success(Unit)
        }
    }

    override suspend fun transcribe(audioFile: File, language: String): Result<String> {
        // Validate configuration first
        validateConfiguration().getOrElse { return Result.failure(it) }

        return withContext(Dispatchers.IO) {
            try {
                // Build multipart request
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        audioFile.asRequestBody("audio/wav".toMediaType())
                    )
                    .addFormDataPart("language", language)
                    .build()

                // Build request with headers
                val requestBuilder = Request.Builder()
                    .url(url!!)
                    .post(requestBody)

                // Add API key if provided
                if (!apiKey.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                // Add custom headers
                headers.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }

                val request = requestBuilder.build()

                // Execute request
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        TranscriptionError.NetworkError(
                            name,
                            IOException("HTTP ${response.code}: ${response.message}")
                        )
                    )
                }

                // Parse response
                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(
                        TranscriptionError.ProviderError(name, "Empty response from server")
                    )

                try {
                    val json = JSONObject(responseBody)
                    val text = json.optString("text", null)
                        ?: json.optString("transcription", null)
                        ?: return@withContext Result.failure(
                            TranscriptionError.ProviderError(
                                name,
                                "Response does not contain 'text' or 'transcription' field"
                            )
                        )

                    Result.success(text)
                } catch (e: Exception) {
                    Result.failure(
                        TranscriptionError.ProviderError(
                            name,
                            "Failed to parse response JSON: ${e.message}"
                        )
                    )
                }
            } catch (e: IOException) {
                Result.failure(TranscriptionError.NetworkError(name, e))
            } catch (e: Exception) {
                Result.failure(TranscriptionError.UnknownError(name, e))
            }
        }
    }
}
