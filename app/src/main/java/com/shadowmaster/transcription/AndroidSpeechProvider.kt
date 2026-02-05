package com.shadowmaster.transcription

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Android SpeechRecognizer provider - Free Google speech recognition.
 * 
 * Uses Android's built-in SpeechRecognizer API which connects to Google's
 * speech service without requiring an API key. This is the Android equivalent
 * of the Web Speech API used in browser applications.
 * 
 * Features:
 * - No API key required
 * - Works out of the box on devices with Google app installed
 * - Supports multiple languages
 * - Free for consumer app usage
 * - Can work offline if offline models are installed on device
 * 
 * Requirements:
 * - RECORD_AUDIO permission (already granted in manifest)
 * - Google app or Google services installed (standard on most Android devices)
 * - Active internet connection (unless offline models are available)
 * 
 * Note: This is similar to how the hebrew-voice-game repository uses the
 * Web Speech API (webkitSpeechRecognition) - both are free Google services
 * that don't require explicit API keys.
 */
class AndroidSpeechProvider(
    private val context: Context
) : TranscriptionProvider {

    override val name: String = "Google Speech (Free)"
    override val id: String = "android_speech"
    override val requiresApiKey: Boolean = false

    companion object {
        private const val TAG = "AndroidSpeechProvider"
        
        // Audio format matching our standard: 16kHz mono PCM
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    override suspend fun validateConfiguration(): Result<Unit> {
        return if (SpeechRecognizer.isRecognitionAvailable(context)) {
            Result.success(Unit)
        } else {
            Result.failure(
                TranscriptionError.ProviderError(
                    name,
                    "Speech recognition not available on this device. " +
                            "Please ensure Google app or Google services are installed."
                )
            )
        }
    }

    override suspend fun transcribe(audioFile: File, language: String): Result<String> = withContext(Dispatchers.Main) {
        // Validate configuration first
        validateConfiguration().getOrElse { return@withContext Result.failure(it) }

        try {
            Log.d(TAG, "Starting transcription with Android SpeechRecognizer")
            
            // Android SpeechRecognizer works with live audio from microphone
            // Since we have a pre-recorded file, we need to:
            // 1. Play the audio through a virtual audio source, OR
            // 2. Use AudioRecord to capture it while playing, OR
            // 3. Note the limitation in the result
            
            // For now, we'll document this as a limitation and return an error
            // explaining that this provider works best with live recording
            // A full implementation would require playing the audio and capturing it
            // simultaneously, which adds complexity
            
            Result.failure(
                TranscriptionError.ProviderError(
                    name,
                    "Android SpeechRecognizer works with live microphone audio. " +
                            "To use this provider, consider implementing live transcription " +
                            "or use one of the file-based providers (Local Model, Ivrit.ai, etc.)"
                )
            )
            
            // TODO: Full implementation would:
            // 1. Create a SpeechRecognizer instance
            // 2. Set up RecognitionListener to capture results
            // 3. Play the audio file while recording with microphone
            // 4. Or implement custom audio source feeding
            
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(TranscriptionError.UnknownError(name, e))
        }
    }

    /**
     * Transcribe audio directly from microphone (live transcription).
     * This is the primary use case for Android SpeechRecognizer.
     * 
     * @param language Language code (e.g., "en-US", "he-IL")
     * @param maxDurationMs Maximum recording duration in milliseconds
     * @return Result containing transcribed text or error
     */
    suspend fun transcribeLive(
        language: String,
        maxDurationMs: Long = 10000
    ): Result<String> = withContext(Dispatchers.Main) {
        // Validate configuration first
        validateConfiguration().getOrElse { return@withContext Result.failure(it) }

        suspendCancellableCoroutine { continuation ->
            var recognizer: SpeechRecognizer? = null
            
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech started")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Volume level changed
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        // Partial audio buffer received
                    }

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech ended")
                    }

                    override fun onError(error: Int) {
                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            else -> "Unknown error: $error"
                        }
                        Log.e(TAG, "Recognition error: $errorMessage")
                        
                        recognizer?.destroy()
                        
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(
                                    TranscriptionError.ProviderError(name, errorMessage)
                                )
                            )
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val transcription = matches?.firstOrNull() ?: ""
                        
                        Log.d(TAG, "Recognition results: $transcription")
                        
                        recognizer?.destroy()
                        
                        if (continuation.isActive) {
                            if (transcription.isBlank()) {
                                continuation.resume(
                                    Result.failure(
                                        TranscriptionError.ProviderError(
                                            name,
                                            "No speech detected"
                                        )
                                    )
                                )
                            } else {
                                continuation.resume(Result.success(transcription))
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        Log.d(TAG, "Partial results: ${matches?.firstOrNull()}")
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                        // Custom event
                    }
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // Use speech timeout to limit recording duration
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, maxDurationMs)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                }

                recognizer.startListening(intent)
                
                // Set up cancellation
                continuation.invokeOnCancellation {
                    recognizer?.stopListening()
                    recognizer?.destroy()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recognition", e)
                recognizer?.destroy()
                if (continuation.isActive) {
                    continuation.resume(Result.failure(TranscriptionError.UnknownError(name, e)))
                }
            }
        }
    }
}
