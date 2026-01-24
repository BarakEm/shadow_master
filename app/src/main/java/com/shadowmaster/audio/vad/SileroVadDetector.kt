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

    fun initialize(): Boolean {
        if (isInitialized) return true

        return try {
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
            Log.e(TAG, "Failed to initialize Silero VAD", e)
            false
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
        try {
            vad?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VAD", e)
        }
        vad = null
        isInitialized = false
    }
}
