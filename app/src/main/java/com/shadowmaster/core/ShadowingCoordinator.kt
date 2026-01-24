package com.shadowmaster.core

import android.media.projection.MediaProjection
import android.util.Log
import com.shadowmaster.audio.capture.AudioCaptureManager
import com.shadowmaster.audio.playback.PlaybackEngine
import com.shadowmaster.audio.processing.AudioProcessingPipeline
import com.shadowmaster.audio.recording.UserRecordingManager
import com.shadowmaster.data.model.*
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.media.MediaControlManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShadowingCoordinator @Inject constructor(
    private val stateMachine: ShadowingStateMachine,
    private val audioCaptureManager: AudioCaptureManager,
    private val audioProcessingPipeline: AudioProcessingPipeline,
    private val playbackEngine: PlaybackEngine,
    private val userRecordingManager: UserRecordingManager,
    private val mediaControlManager: MediaControlManager,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "ShadowingCoordinator"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var segmentCollectionJob: Job? = null
    private var configCollectionJob: Job? = null

    val state: StateFlow<ShadowingState> = stateMachine.state

    private val _feedbackEvents = MutableSharedFlow<FeedbackEvent>(
        replay = 0,
        extraBufferCapacity = 16
    )
    val feedbackEvents: SharedFlow<FeedbackEvent> = _feedbackEvents.asSharedFlow()

    init {
        observeStateChanges()
        observeConfig()
    }

    private fun observeConfig() {
        configCollectionJob = scope.launch {
            settingsRepository.config.collect { config ->
                stateMachine.updateConfig(config)
                audioProcessingPipeline.updateSilenceThreshold(config.silenceThresholdMs)
            }
        }
    }

    private fun observeStateChanges() {
        scope.launch {
            stateMachine.state.collect { state ->
                handleStateChange(state)
            }
        }
    }

    private suspend fun handleStateChange(state: ShadowingState) {
        when (state) {
            is ShadowingState.Idle -> {
                _feedbackEvents.emit(FeedbackEvent.Idle)
            }

            is ShadowingState.Listening -> {
                _feedbackEvents.emit(FeedbackEvent.Listening)
            }

            is ShadowingState.SegmentDetected -> {
                _feedbackEvents.emit(FeedbackEvent.SegmentDetected)
                mediaControlManager.pauseOtherApps()
                delay(200)
                startPlayback(state.audioSegment)
            }

            is ShadowingState.Playback -> {
                _feedbackEvents.emit(FeedbackEvent.PlaybackStarted)
            }

            is ShadowingState.UserRecording -> {
                _feedbackEvents.emit(FeedbackEvent.RecordingStarted)
                startUserRecording(state.audioSegment)
            }

            is ShadowingState.Assessment -> {
                _feedbackEvents.emit(FeedbackEvent.AssessmentStarted)
                performAssessment(state.originalSegment, state.userRecording)
            }

            is ShadowingState.Feedback -> {
                if (state.result.isGood) {
                    _feedbackEvents.emit(FeedbackEvent.GoodScore)
                } else {
                    _feedbackEvents.emit(FeedbackEvent.BadScore)
                }
                delay(1000)
                stateMachine.processEvent(ShadowingEvent.FeedbackComplete)
            }

            is ShadowingState.PausedForNavigation -> {
                _feedbackEvents.emit(FeedbackEvent.Paused)
            }
        }
    }

    fun start(mediaProjection: MediaProjection): Boolean {
        Log.i(TAG, "Starting shadowing session")

        if (!audioCaptureManager.startCapture(mediaProjection)) {
            Log.e(TAG, "Failed to start audio capture")
            return false
        }

        val config = stateMachine.getCurrentConfig()
        audioProcessingPipeline.start(config.silenceThresholdMs)

        startSegmentCollection()

        stateMachine.processEvent(ShadowingEvent.Start)

        return true
    }

    private fun startSegmentCollection() {
        segmentCollectionJob = scope.launch {
            audioProcessingPipeline.segmentFlow.collect { segment ->
                if (stateMachine.state.value is ShadowingState.Listening) {
                    stateMachine.processEvent(ShadowingEvent.SegmentDetected(segment))
                }
            }
        }
    }

    private fun startPlayback(segment: AudioSegment) {
        val config = stateMachine.getCurrentConfig()

        playbackEngine.play(
            segment = segment,
            speed = config.playbackSpeed,
            repeatCount = config.playbackRepeats,
            onComplete = {
                scope.launch {
                    stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
                }
            }
        )
    }

    private fun startUserRecording(originalSegment: AudioSegment) {
        userRecordingManager.startRecording { recordedSegment ->
            if (recordedSegment != null) {
                stateMachine.processEvent(ShadowingEvent.RecordingComplete)
            } else {
                stateMachine.processEvent(ShadowingEvent.Skip)
            }
        }
    }

    private suspend fun performAssessment(original: AudioSegment, userRecording: AudioSegment) {
        val config = stateMachine.getCurrentConfig()

        if (!config.assessmentEnabled) {
            stateMachine.processEvent(
                ShadowingEvent.AssessmentComplete(AssessmentResult.DISABLED)
            )
            return
        }

        // For MVP, skip actual Azure assessment - just provide a placeholder result
        // Azure assessment will be added in Phase 2
        delay(500)
        val mockResult = AssessmentResult(
            overallScore = 75f,
            pronunciationScore = 80f,
            fluencyScore = 70f,
            completenessScore = 75f,
            isGood = true
        )

        stateMachine.processEvent(ShadowingEvent.AssessmentComplete(mockResult))
    }

    fun stop() {
        Log.i(TAG, "Stopping shadowing session")

        segmentCollectionJob?.cancel()
        segmentCollectionJob = null

        playbackEngine.stop()
        userRecordingManager.stopRecording()
        audioProcessingPipeline.stop()
        audioCaptureManager.stopCapture()
        mediaControlManager.resumeOtherApps()

        stateMachine.processEvent(ShadowingEvent.Stop)
    }

    fun skip() {
        playbackEngine.stop()
        userRecordingManager.stopRecording()
        stateMachine.processEvent(ShadowingEvent.Skip)
    }

    fun onNavigationStarted() {
        if (stateMachine.getCurrentConfig().pauseForNavigation) {
            playbackEngine.stop()
            userRecordingManager.stopRecording()
            stateMachine.processEvent(ShadowingEvent.NavigationStarted)
        }
    }

    fun onNavigationEnded() {
        stateMachine.processEvent(ShadowingEvent.NavigationEnded)
    }

    fun release() {
        stop()
        configCollectionJob?.cancel()
        audioCaptureManager.release()
        userRecordingManager.release()
    }
}

sealed class FeedbackEvent {
    data object Idle : FeedbackEvent()
    data object Listening : FeedbackEvent()
    data object SegmentDetected : FeedbackEvent()
    data object PlaybackStarted : FeedbackEvent()
    data object RecordingStarted : FeedbackEvent()
    data object AssessmentStarted : FeedbackEvent()
    data object GoodScore : FeedbackEvent()
    data object BadScore : FeedbackEvent()
    data object Paused : FeedbackEvent()
}
