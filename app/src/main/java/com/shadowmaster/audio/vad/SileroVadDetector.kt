package com.shadowmaster.audio.vad

import android.content.Context
import android.util.Log
import com.konovalov.vad.silero.Vad
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SileroVadDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SileroVadDetector"
        const val FRAME_SIZE_SAMPLES = 512 // ~32ms at 16kHz
    }

    private var vad: VadSilero? = null
    private var isInitialized = false
    private val lock = Any()

    fun initialize(): Boolean {
        synchronized(lock) {
            if (isInitialized && vad != null) return true

            return try {
                // Close any existing instance first
                try {
                    vad?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing existing VAD", e)
                }
                vad = null
                isInitialized = false

                vad = Vad.builder()
                    .setContext(context)
                    .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                    .setFrameSize(FrameSize.FRAME_SIZE_512)
                    .setMode(Mode.NORMAL)
                    .setSilenceDurationMs(300)
                    .setSpeechDurationMs(50)
                    .build()

                isInitialized = true
                Log.i(TAG, "Silero VAD initialized successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Silero VAD: ${e.message}", e)
                vad = null
                isInitialized = false
                false
            } catch (e: Error) {
                // Catch errors like UnsatisfiedLinkError for native library issues
                Log.e(TAG, "Error loading Silero VAD native library: ${e.message}", e)
                vad = null
                isInitialized = false
                false
            }
        }
    }

    fun isSpeech(audioFrame: ShortArray): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "VAD not initialized")
            return false
        }

        return try {
            vad?.isSpeech(audioFrame) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting speech", e)
            false
        }
    }

    fun close() {
        synchronized(lock) {
            try {
                vad?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing VAD", e)
            }
            vad = null
            isInitialized = false
        }
    }
}
