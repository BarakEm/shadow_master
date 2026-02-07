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
 * OpenAI Whisper API provider. Also supports Faster Whisper and other
 * OpenAI-compatible transcription servers via configurable base URL.
 */
class WhisperAPIProvider(
    private val apiKey: String?,
    private val baseUrl: String? = null
) : TranscriptionProvider {

    override val name: String = "OpenAI Whisper"
    override val id: String = TranscriptionProviderType.WHISPER.id
    override val requiresApiKey: Boolean = true

    companion object {
        private const val TAG = "WhisperAPIProvider"
        private const val OPENAI_BASE_URL = "https://api.openai.com"
        private const val TRANSCRIPTIONS_PATH = "/v1/audio/transcriptions"
        private const val MAX_FILE_SIZE_MB = 25
        private const val TIMEOUT_SECONDS = 120L
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val effectiveBaseUrl: String
        get() = baseUrl?.trimEnd('/') ?: OPENAI_BASE_URL

    private val isFasterWhisper: Boolean
        get() = baseUrl != null && !baseUrl.contains("openai.com")

    override suspend fun validateConfiguration(): Result<Unit> {
        // Faster Whisper / self-hosted servers may not require an API key
        if (!isFasterWhisper && apiKey.isNullOrBlank()) {
            return Result.failure(TranscriptionError.ApiKeyMissing(name))
        }
        if (isFasterWhisper && baseUrl != null &&
            !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")
        ) {
            return Result.failure(
                TranscriptionError.ProviderError(
                    name,
                    "Base URL must start with http:// or https://"
                )
            )
        }
        return Result.success(Unit)
    }

    override suspend fun transcribe(audioFile: File, language: String): Result<String> {
        validateConfiguration().getOrElse { return Result.failure(it) }

        return withContext(Dispatchers.IO) {
            try {
                val fileSizeMB = audioFile.length() / (1024.0 * 1024.0)
                if (fileSizeMB > MAX_FILE_SIZE_MB) {
                    return@withContext Result.failure(
                        TranscriptionError.ProviderError(
                            name,
                            "Audio file too large: ${String.format("%.1f", fileSizeMB)}MB (max: ${MAX_FILE_SIZE_MB}MB)"
                        )
                    )
                }

                Log.d(TAG, "Transcribing ${audioFile.name} (${String.format("%.1f", fileSizeMB)}MB) via ${effectiveBaseUrl}")

                val mimeType = when (audioFile.extension.lowercase()) {
                    "wav" -> "audio/wav"
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "ogg" -> "audio/ogg"
                    "flac" -> "audio/flac"
                    "webm" -> "audio/webm"
                    else -> "application/octet-stream"
                }

                // Convert locale code (en-US) to ISO-639-1 (en) for OpenAI
                val isoLanguage = language.split("-", "_").firstOrNull()?.lowercase() ?: "en"

                val bodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        audioFile.asRequestBody(mimeType.toMediaType())
                    )
                    .addFormDataPart("model", if (isFasterWhisper) "large-v3" else "whisper-1")
                    .addFormDataPart("response_format", "json")

                // Only add language if not "auto" — lets the model auto-detect
                if (isoLanguage != "auto") {
                    bodyBuilder.addFormDataPart("language", isoLanguage)
                }

                val requestBody = bodyBuilder.build()

                val url = "${effectiveBaseUrl}${TRANSCRIPTIONS_PATH}"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(requestBody)

                // Add auth header — Faster Whisper may not need it
                if (!apiKey.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                Log.d(TAG, "POST $url | language=$isoLanguage | model=${if (isFasterWhisper) "large-v3" else "whisper-1"}")

                val response = httpClient.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Response ${response.code}: $responseBody")

                if (!response.isSuccessful) {
                    val errorDetail = parseErrorMessage(responseBody) ?: responseBody ?: "Unknown error"
                    return@withContext Result.failure(
                        TranscriptionError.ProviderError(
                            name,
                            "HTTP ${response.code}: $errorDetail"
                        )
                    )
                }

                if (responseBody.isNullOrBlank()) {
                    return@withContext Result.failure(
                        TranscriptionError.ProviderError(name, "Empty response from API")
                    )
                }

                val json = JSONObject(responseBody)

                // Check for error field
                if (json.has("error")) {
                    val error = json.optJSONObject("error")
                    val errorMsg = error?.optString("message") ?: json.optString("error", "Unknown error")
                    return@withContext Result.failure(
                        TranscriptionError.ProviderError(name, errorMsg)
                    )
                }

                // Extract transcription text — OpenAI returns {"text": "..."}
                val text = json.optString("text", null)
                    ?: json.optString("transcription", null)
                    ?: return@withContext Result.failure(
                        TranscriptionError.ProviderError(
                            name,
                            "Response missing 'text' field: $responseBody"
                        )
                    )

                if (text.isBlank()) {
                    return@withContext Result.failure(
                        TranscriptionError.ProviderError(name, "No speech detected in audio")
                    )
                }

                Log.d(TAG, "Transcription successful (${text.length} chars)")
                Result.success(text.trim())

            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Network error: cannot reach ${effectiveBaseUrl}", e)
                Result.failure(
                    TranscriptionError.NetworkError(
                        name,
                        Exception("Cannot reach ${effectiveBaseUrl}. Check your internet connection.", e)
                    )
                )
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Connection refused: ${effectiveBaseUrl}", e)
                Result.failure(
                    TranscriptionError.NetworkError(
                        name,
                        Exception("Connection refused at ${effectiveBaseUrl}. Is the server running?", e)
                    )
                )
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Timeout", e)
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

    private fun parseErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("error", null)
        } catch (_: Exception) {
            null
        }
    }
}
