package com.shadowmaster.audio.processing

import android.util.Log
import com.shadowmaster.audio.capture.AudioCaptureManager
import com.shadowmaster.audio.vad.SileroVadDetector
import com.shadowmaster.data.model.AudioSegment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioProcessingPipeline @Inject constructor(
    private val audioCaptureManager: AudioCaptureManager,
    private val vadDetector: SileroVadDetector
) {
    companion object {
        private const val TAG = "AudioProcessingPipeline"
        private const val MIN_SPEECH_DURATION_MS = 500L
        private const val MAX_SPEECH_DURATION_MS = 15000L
        private const val PRE_SPEECH_BUFFER_MS = 200L
    }

    private val audioBuffer = CircularAudioBuffer(maxDurationMs = 30000)
    private val frameBuffer = mutableListOf<Short>()
    private val frameSize = SileroVadDetector.FRAME_SIZE_SAMPLES

    private var silenceThresholdMs: Int = 700
    private var lastSpeechTimestamp: Long = 0
    private var speechStartTimestamp: Long = 0
    private var isSpeaking = false
    private var processingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _segmentFlow = MutableSharedFlow<AudioSegment>(
        replay = 0,
        extraBufferCapacity = 8
    )
    val segmentFlow: SharedFlow<AudioSegment> = _segmentFlow.asSharedFlow()

    private var isRunning = false

    fun start(silenceThresholdMs: Int = 700) {
        if (isRunning) return

        this.silenceThresholdMs = silenceThresholdMs

        if (!vadDetector.initialize()) {
            Log.e(TAG, "Failed to initialize VAD")
            return
        }

        isRunning = true
        resetState()

        processingJob = scope.launch {
            audioCaptureManager.audioDataFlow.collect { audioData ->
                if (isRunning) {
                    processAudioChunk(audioData)
                }
            }
        }

        Log.i(TAG, "Audio processing pipeline started")
    }

    fun stop() {
        isRunning = false
        processingJob?.cancel()
        processingJob = null
        resetState()
        Log.i(TAG, "Audio processing pipeline stopped")
    }

    private fun resetState() {
        audioBuffer.clear()
        frameBuffer.clear()
        lastSpeechTimestamp = 0
        speechStartTimestamp = 0
        isSpeaking = false
    }

    private suspend fun processAudioChunk(audioData: ShortArray) {
        audioBuffer.write(audioData)

        for (sample in audioData) {
            frameBuffer.add(sample)

            if (frameBuffer.size >= frameSize) {
                val frame = frameBuffer.take(frameSize).toShortArray()
                frameBuffer.clear()
                frameBuffer.addAll(audioData.drop(frameSize - (audioData.size % frameSize)))

                processFrame(frame)
            }
        }
    }

    private suspend fun processFrame(frame: ShortArray) {
        val currentTime = System.currentTimeMillis()
        val hasSpeech = vadDetector.isSpeech(frame)

        if (hasSpeech) {
            if (!isSpeaking) {
                isSpeaking = true
                speechStartTimestamp = currentTime
                Log.d(TAG, "Speech started")
            }
            lastSpeechTimestamp = currentTime
        } else if (isSpeaking) {
            val silenceDuration = currentTime - lastSpeechTimestamp

            if (silenceDuration >= silenceThresholdMs) {
                val speechDuration = lastSpeechTimestamp - speechStartTimestamp

                if (speechDuration >= MIN_SPEECH_DURATION_MS) {
                    extractAndEmitSegment(speechDuration)
                } else {
                    Log.d(TAG, "Speech too short: ${speechDuration}ms")
                }

                isSpeaking = false
                speechStartTimestamp = 0
            }
        }

        if (isSpeaking) {
            val currentSpeechDuration = currentTime - speechStartTimestamp
            if (currentSpeechDuration >= MAX_SPEECH_DURATION_MS) {
                Log.d(TAG, "Speech too long, forcing segment extraction")
                extractAndEmitSegment(currentSpeechDuration)
                isSpeaking = false
                speechStartTimestamp = 0
            }
        }
    }

    private suspend fun extractAndEmitSegment(durationMs: Long) {
        val totalDurationMs = durationMs + PRE_SPEECH_BUFFER_MS + silenceThresholdMs
        val samples = audioBuffer.getLastNMillis(totalDurationMs)

        if (samples.isNotEmpty()) {
            val segment = AudioSegment(
                samples = samples,
                sampleRate = AudioCaptureManager.SAMPLE_RATE
            )
            Log.i(TAG, "Segment detected: ${segment.durationMs}ms, ${segment.samples.size} samples")
            _segmentFlow.emit(segment)
        }
    }

    fun updateSilenceThreshold(thresholdMs: Int) {
        this.silenceThresholdMs = thresholdMs
    }
}
