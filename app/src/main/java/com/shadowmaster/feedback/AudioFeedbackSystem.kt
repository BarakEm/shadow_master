package com.shadowmaster.feedback

import android.content.Context
import android.media.ToneGenerator
import android.util.Log
import com.shadowmaster.data.model.ShadowingConfig
import com.shadowmaster.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides audio feedback for hands-free operation.
 * Uses different tones to indicate state transitions.
 * Beep characteristics (volume, tone, duration) are configurable via settings.
 */
@Singleton
class AudioFeedbackSystem @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "AudioFeedbackSystem"
    }

    private var toneGenerator: ToneGenerator? = null
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Current beep configuration
    private var config: ShadowingConfig = ShadowingConfig()

    fun initialize() {
        if (isInitialized) return

        // Observe config changes and recreate ToneGenerator when volume changes
        settingsRepository.config
            .onEach { newConfig ->
                val volumeChanged = config.beepVolume != newConfig.beepVolume
                config = newConfig

                if (volumeChanged || !isInitialized) {
                    recreateToneGenerator()
                }
            }
            .launchIn(scope)

        isInitialized = true
        Log.i(TAG, "Audio feedback system initialized")
    }

    private fun recreateToneGenerator() {
        try {
            toneGenerator?.release()
            toneGenerator = ToneGenerator(
                android.media.AudioManager.STREAM_NOTIFICATION,
                config.beepVolume
            )
            Log.i(TAG, "ToneGenerator created with volume: ${config.beepVolume}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ToneGenerator", e)
        }
    }

    /**
     * Play when a segment is detected (ready to play back)
     * Single beep with user-configured tone and duration
     */
    fun playSegmentDetected() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(config.beepToneType.toneConstant, config.beepDurationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing segment detected tone", e)
        }
    }

    /**
     * Play when playback starts
     * Single beep with user-configured tone and duration - indicates "I'm speaking now"
     */
    fun playPlaybackStart() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(config.beepToneType.toneConstant, config.beepDurationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing playback start tone", e)
        }
    }

    /**
     * Play when starting to record user's voice
     * Double beep with user-configured tone - indicates "Your turn to speak"
     */
    fun playRecordingStart() {
        if (!isInitialized) return

        scope.launch {
            try {
                // Double beep using user's configured tone and duration
                toneGenerator?.startTone(config.beepToneType.toneConstant, config.beepDurationMs)
                delay(config.beepDurationMs.toLong() + 50)
                toneGenerator?.startTone(config.beepToneType.toneConstant, config.beepDurationMs)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing recording start tone", e)
            }
        }
    }

    /**
     * Play a single beep for "your turn" in practice mode
     */
    fun playYourTurn() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(config.beepToneType.toneConstant, config.beepDurationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing your turn tone", e)
        }
    }

    /**
     * Play when returning to listening state
     * Single beep with user-configured tone - indicates "I'm listening for audio"
     */
    fun playListening() {
        if (!isInitialized) return

        try {
            toneGenerator?.startTone(config.beepToneType.toneConstant, config.beepDurationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing listening tone", e)
        }
    }

    /**
     * Play for good pronunciation score
     * Triple beep sequence with user-configured tone
     */
    fun playGoodScore() {
        if (!isInitialized) return

        scope.launch {
            try {
                val shortDuration = (config.beepDurationMs * 0.6).toInt()
                val mediumDuration = config.beepDurationMs
                toneGenerator?.startTone(config.beepToneType.toneConstant, shortDuration)
                delay(shortDuration.toLong() + 50)
                toneGenerator?.startTone(config.beepToneType.toneConstant, shortDuration)
                delay(shortDuration.toLong() + 50)
                toneGenerator?.startTone(config.beepToneType.toneConstant, mediumDuration)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing good score tone", e)
            }
        }
    }

    /**
     * Play for poor pronunciation score
     * Single long beep with error tone
     */
    fun playBadScore() {
        if (!isInitialized) return

        try {
            val longDuration = (config.beepDurationMs * 1.5).toInt()
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, longDuration)
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
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_INTERCEPT, config.beepDurationMs)
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
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONFIRM, config.beepDurationMs)
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
            val longDuration = (config.beepDurationMs * 1.5).toInt()
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, longDuration)
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
