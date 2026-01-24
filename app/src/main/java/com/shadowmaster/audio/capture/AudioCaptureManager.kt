package com.shadowmaster.audio.capture

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _audioDataFlow = MutableSharedFlow<ShortArray>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val audioDataFlow: SharedFlow<ShortArray> = _audioDataFlow.asSharedFlow()

    private var isCapturing = false

    val bufferSize: Int by lazy {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        minBufferSize * BUFFER_SIZE_FACTOR
    }

    @SuppressLint("MissingPermission")
    fun startCapture(projection: MediaProjection): Boolean {
        if (isCapturing) {
            Log.w(TAG, "Already capturing")
            return true
        }

        mediaProjection = projection

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                release()
                return false
            }

            audioRecord?.startRecording()
            isCapturing = true

            startCaptureLoop()

            Log.i(TAG, "Audio capture started successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            release()
            return false
        }
    }

    private fun startCaptureLoop() {
        captureJob = scope.launch {
            val buffer = ShortArray(bufferSize / 2) // 2 bytes per short

            while (isActive && isCapturing) {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                when {
                    readResult > 0 -> {
                        val audioData = if (readResult < buffer.size) {
                            buffer.copyOf(readResult)
                        } else {
                            buffer.clone()
                        }
                        _audioDataFlow.emit(audioData)
                    }
                    readResult == AudioRecord.ERROR_INVALID_OPERATION -> {
                        Log.e(TAG, "AudioRecord invalid operation")
                        break
                    }
                    readResult == AudioRecord.ERROR_BAD_VALUE -> {
                        Log.e(TAG, "AudioRecord bad value")
                        break
                    }
                    readResult == AudioRecord.ERROR_DEAD_OBJECT -> {
                        Log.e(TAG, "AudioRecord dead object")
                        break
                    }
                }
            }
        }
    }

    fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }

        Log.i(TAG, "Audio capture stopped")
    }

    fun release() {
        stopCapture()

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaProjection", e)
        }
        mediaProjection = null

        Log.i(TAG, "AudioCaptureManager released")
    }

    fun isCapturing(): Boolean = isCapturing
}
