package com.shadowmaster.feedback

import android.content.Context
import android.media.ToneGenerator
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides audio feedback for hands-free operation.
 * Uses different tones to indicate state transitions.
 */
@Singleton
class AudioFeedbackSystem @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioFeedbackSystem"

        // Tone durations in milliseconds
        private const val SHORT_TONE_DURATION = 100
        private const val MEDIUM_TONE_DURATION = 200
        private const val LONG_TONE_DURATION = 300
    }

    private var toneGenerator: ToneGenerator? = null
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun initialize() {
        if (isInitialized) return

        try {
            toneGenerator = ToneGenerator(
                android.media.AudioManager.STREAM_NOTIFICATION,
                80 // Volume: 0-100
            )
            isInitialized = true
            Log.i(TAG, "Audio feedback system initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * Play when a segment is detected (ready to play back)
     * Single gentle beep
     */
    fun playSegmentDetected() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, SHORT_TONE_DURATION)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing segment detected tone", e)
        }
    }

    /**
     * Play when playback starts
     * Single gentle low beep - indicates "I'm speaking now"
     */
    fun playPlaybackStart() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, SHORT_TONE_DURATION)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing playback start tone", e)
        }
    }

    /**
     * Play when starting to record user's voice
     * Gentle double beep - indicates "Your turn to speak"
     */
    fun playRecordingStart() {
        if (!isInitialized) return

        scope.launch {
            try {
                // Gentle ascending double beep
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, SHORT_TONE_DURATION)
                delay(150)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, SHORT_TONE_DURATION)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing recording start tone", e)
            }
        }
    }

    /**
     * Play a very gentle single beep for "your turn" in practice mode
     */
    fun playYourTurn() {
        if (!isInitialized) return

        try {
            // Single gentle beep
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, MEDIUM_TONE_DURATION)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing your turn tone", e)
        }
    }

    /**
     * Play when returning to listening state
     * Soft descending tone - indicates "I'm listening for audio"
     */
    fun playListening() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, SHORT_TONE_DURATION)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing listening tone", e)
        }
    }

    /**
     * Play for good pronunciation score
     * Ascending pleasant tones
     */
    fun playGoodScore() {
        if (!isInitialized) return

        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, SHORT_TONE_DURATION)
                delay(100)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, SHORT_TONE_DURATION)
                delay(100)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, MEDIUM_TONE_DURATION)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing good score tone", e)
            }
        }
    }

    /**
     * Play for poor pronunciation score
     * Single low tone
     */
    fun playBadScore() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, LONG_TONE_DURATION)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing bad score tone", e)
        }
    }

    /**
     * Play when session is paused (e.g., for navigation)
     */
    fun playPaused() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_INTERCEPT, MEDIUM_TONE_DURATION)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing paused tone", e)
        }
    }

    /**
     * Play when session is resumed
     */
    fun playResumed() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONFIRM, MEDIUM_TONE_DURATION)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing resumed tone", e)
        }
    }

    /**
     * Play error/failure tone
     */
    fun playError() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, LONG_TONE_DURATION)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing error tone", e)
        }
    }

    fun release() {
        scope.cancel()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ToneGenerator", e)
        }
        toneGenerator = null
        isInitialized = false
        Log.i(TAG, "Audio feedback system released")
    }
}
