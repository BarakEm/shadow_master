package com.shadowmaster.audio.recording

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.shadowmaster.audio.vad.SileroVadDetector
import com.shadowmaster.data.model.AudioSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRecordingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vadDetector: SileroVadDetector
) {
    companion object {
        private const val TAG = "UserRecordingManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_RECORDING_DURATION_MS = 30000L
        private const val SILENCE_THRESHOLD_MS = 1500L
        private const val MIN_SPEECH_DURATION_MS = 300L
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var cleanupJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val recordedSamples = mutableListOf<Short>()
    private var isRecording = false
    private var onRecordingComplete: ((AudioSegment?) -> Unit)? = null

    private val bufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
    }

    @SuppressLint("MissingPermission")
    fun startRecording(onComplete: (AudioSegment?) -> Unit) {
        if (isRecording) {
            stopRecording()
        }

        this.onRecordingComplete = onComplete
        recordedSamples.clear()

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                onComplete(null)
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            startRecordingLoop()

            Log.i(TAG, "User recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            release()
            onComplete(null)
        }
    }

    private fun startRecordingLoop() {
        recordingJob = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            val frameBuffer = mutableListOf<Short>()
            val frameSize = SileroVadDetector.FRAME_SIZE_SAMPLES

            var lastSpeechTime = System.currentTimeMillis()
            var hasSpeechStarted = false
            val startTime = System.currentTimeMillis()

            while (isActive && isRecording) {
                val currentTime = System.currentTimeMillis()

                if (currentTime - startTime > MAX_RECORDING_DURATION_MS) {
                    Log.d(TAG, "Max recording duration reached")
                    break
                }

                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (readResult > 0) {
                    for (i in 0 until readResult) {
                        recordedSamples.add(buffer[i])
                        frameBuffer.add(buffer[i])

                        if (frameBuffer.size >= frameSize) {
                            val frame = frameBuffer.subList(0, frameSize).toShortArray()
                            // Remove processed samples, keeping any extras
                            repeat(frameSize) { frameBuffer.removeAt(0) }

                            val hasSpeech = vadDetector.isSpeech(frame)

                            if (hasSpeech) {
                                hasSpeechStarted = true
                                lastSpeechTime = currentTime
                            } else if (hasSpeechStarted) {
                                val silenceDuration = currentTime - lastSpeechTime
                                if (silenceDuration >= SILENCE_THRESHOLD_MS) {
                                    Log.d(TAG, "Silence detected, stopping recording")
                                    break
                                }
                            }
                        }
                    }
                } else if (readResult < 0) {
                    Log.e(TAG, "AudioRecord read error: $readResult")
                    break
                }
            }

            finishRecording()
        }
    }

    private suspend fun finishRecording() {
        isRecording = false

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }

        val segment = if (recordedSamples.size >= (MIN_SPEECH_DURATION_MS * SAMPLE_RATE / 1000)) {
            AudioSegment(
                samples = recordedSamples.toShortArray(),
                sampleRate = SAMPLE_RATE
            )
        } else {
            Log.d(TAG, "Recording too short")
            null
        }

        Log.i(TAG, "Recording finished: ${segment?.durationMs ?: 0}ms")

        // Atomically get and clear the callback to prevent double invocation
        val callback = synchronized(this) {
            val cb = onRecordingComplete
            onRecordingComplete = null
            cb
        }

        // Invoke callback with error handling
        try {
            withContext(Dispatchers.Main) {
                callback?.invoke(segment)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking recording completion callback", e)
        }
    }

    fun stopRecording() {
        val shouldLaunchCleanup = synchronized(this) {
            if (!isRecording) {
                return
            }
            
            // Prevent multiple cleanup attempts
            if (cleanupJob?.isActive == true) {
                return
            }

            isRecording = false
            true
        }
        
        if (shouldLaunchCleanup) {
            cleanupJob = scope.launch {
                try {
                    // Wait for the recording job to complete (with timeout)
                    withTimeoutOrNull(5000) {
                        recordingJob?.join()
                    } ?: Log.w(TAG, "Recording job did not complete within timeout")
                } catch (e: Exception) {
                    Log.e(TAG, "Error waiting for recording job", e)
                } finally {
                    recordingJob = null

                    try {
                        audioRecord?.stop()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping AudioRecord", e)
                    }

                    // Finish recording and invoke callback with the recorded audio
                    finishRecording()
                    
                    // Clear cleanup job to allow future stop operations
                    synchronized(this@UserRecordingManager) {
                        cleanupJob = null
                    }
                }
            }
        }
    }

    fun release() {
        stopRecording()

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
        recordedSamples.clear()
    }

    fun isRecording(): Boolean = isRecording
}
