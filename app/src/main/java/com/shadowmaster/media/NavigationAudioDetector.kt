package com.shadowmaster.media

import android.content.Context
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects when navigation audio (like Waze) is playing.
 *
 * This is a basic implementation for MVP. Phase 2 will add:
 * - More sophisticated detection using AudioPlaybackCallback
 * - Package-specific detection for Waze, Google Maps, etc.
 */
@Singleton
class NavigationAudioDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NavigationAudioDetector"
        private const val POLL_INTERVAL_MS = 500L
    }

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var detectionJob: Job? = null

    private val _isNavigationPlaying = MutableStateFlow(false)
    val isNavigationPlaying: StateFlow<Boolean> = _isNavigationPlaying.asStateFlow()

    private var onNavigationStarted: (() -> Unit)? = null
    private var onNavigationEnded: (() -> Unit)? = null

    fun startDetection(
        onStarted: () -> Unit = {},
        onEnded: () -> Unit = {}
    ) {
        this.onNavigationStarted = onStarted
        this.onNavigationEnded = onEnded

        detectionJob = scope.launch {
            var wasPlaying = false

            while (isActive) {
                // Basic detection: Check if music is active
                // In Phase 2, we'll use AudioPlaybackCallback for more precise detection
                val isPlaying = audioManager.isMusicActive

                if (isPlaying && !wasPlaying) {
                    // Potential navigation audio started
                    // For now, we don't differentiate between navigation and music
                    // This will be improved in Phase 2
                    Log.d(TAG, "External audio detected")
                } else if (!isPlaying && wasPlaying) {
                    Log.d(TAG, "External audio stopped")
                }

                wasPlaying = isPlaying
                delay(POLL_INTERVAL_MS)
            }
        }

        Log.i(TAG, "Navigation detection started")
    }

    fun stopDetection() {
        detectionJob?.cancel()
        detectionJob = null
        _isNavigationPlaying.value = false
        Log.i(TAG, "Navigation detection stopped")
    }

    /**
     * Manually trigger navigation started event.
     * Can be called when detecting specific audio patterns.
     */
    fun notifyNavigationStarted() {
        if (!_isNavigationPlaying.value) {
            _isNavigationPlaying.value = true
            onNavigationStarted?.invoke()
            Log.i(TAG, "Navigation audio started")
        }
    }

    /**
     * Manually trigger navigation ended event.
     */
    fun notifyNavigationEnded() {
        if (_isNavigationPlaying.value) {
            _isNavigationPlaying.value = false
            onNavigationEnded?.invoke()
            Log.i(TAG, "Navigation audio ended")
        }
    }
}
