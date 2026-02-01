package com.shadowmaster.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaControlManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MediaControlManager"
    }

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    // Callbacks for audio focus changes
    private var onFocusLostCallback: (() -> Unit)? = null
    private var onFocusGainedCallback: (() -> Unit)? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasAudioFocus = true
                onFocusGainedCallback?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                hasAudioFocus = false
                onFocusLostCallback?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transiently")
                hasAudioFocus = false
                onFocusLostCallback?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost - can duck")
                // Don't pause for ducking, just lower volume (not implemented yet)
            }
        }
    }

    fun pauseOtherApps(): Boolean {
        if (hasAudioFocus) {
            Log.d(TAG, "Already have audio focus")
            return true
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)

        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                Log.i(TAG, "Audio focus granted - other apps paused")
                hasAudioFocus = true
                true
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                Log.i(TAG, "Audio focus request delayed")
                false
            }
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                Log.w(TAG, "Audio focus request failed")
                false
            }
            else -> false
        }
    }

    fun resumeOtherApps() {
        audioFocusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
            hasAudioFocus = false
            Log.i(TAG, "Audio focus abandoned - other apps can resume")
        }
        audioFocusRequest = null
    }

    fun hasAudioFocus(): Boolean = hasAudioFocus

    /**
     * Set a callback to be invoked when audio focus is lost.
     * This allows playback components to pause when another app takes audio focus.
     */
    fun setOnFocusLostCallback(callback: (() -> Unit)?) {
        onFocusLostCallback = callback
    }

    /**
     * Set a callback to be invoked when audio focus is gained.
     * This allows playback components to resume when audio focus returns.
     */
    fun setOnFocusGainedCallback(callback: (() -> Unit)?) {
        onFocusGainedCallback = callback
    }
}
