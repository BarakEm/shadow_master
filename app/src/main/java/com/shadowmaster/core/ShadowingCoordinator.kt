package com.shadowmaster.core

import android.media.projection.MediaProjection
import android.util.Log
import com.shadowmaster.audio.capture.AudioCaptureManager
import com.shadowmaster.audio.playback.PlaybackEngine
import com.shadowmaster.audio.processing.AudioProcessingPipeline
import com.shadowmaster.audio.recording.UserRecordingManager
import com.shadowmaster.data.model.*
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.library.AudioImporter
import com.shadowmaster.media.MediaControlManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main orchestrator for the shadowing practice session.
 *
 * The ShadowingCoordinator manages the entire shadowing workflow by coordinating multiple audio
 * components and managing state transitions through the state machine. It implements the core
 * "listen-repeat-assess" loop for language learning through shadowing.
 *
 * ## Architecture Overview
 *
 * The coordinator follows an event-driven architecture:
 * 1. Components emit events (e.g., segment detected, playback complete)
 * 2. Events are processed by the [ShadowingStateMachine]
 * 3. State changes trigger appropriate component actions via [handleStateChange]
 * 4. Audio feedback is emitted through [feedbackEvents] for UI/sound notifications
 *
 * ## Shadowing Flow
 *
 * ```
 * Idle
 *   ↓ (Start)
 * Listening ←─────────────────────────┐
 *   ↓ (SegmentDetected)                │
 * SegmentDetected                      │
 *   ↓ (PlaybackComplete)               │
 * Playback [1..N repeats]              │
 *   ↓ (PlaybackComplete)               │
 * UserRecording [1..M repeats] ────────┤ (Skip)
 *   ↓ (RecordingComplete)              │
 * Assessment                           │
 *   ↓ (AssessmentComplete)             │
 * Feedback                             │
 *   ↓ (FeedbackComplete)               │
 *   └──────────────────────────────────┘
 *
 * Note: In bus mode, flow skips UserRecording/Assessment/Feedback
 * ```
 *
 * ## Sequence Diagram for Typical Flow
 *
 * ```
 * User                Coordinator         StateMachine        AudioComponents
 *  |                       |                    |                    |
 *  |--start()------------->|                    |                    |
 *  |                       |--processEvent----->|                    |
 *  |                       |   (Start)          |                    |
 *  |                       |                    |--Listening-------->|
 *  |                       |<-------------------|                    |
 *  |                       |                    |                    |
 *  |                       |<--------------segmentFlow---------------|
 *  |                       |--processEvent----->|                    |
 *  |                       |   (SegmentDetected)|                    |
 *  |                       |                    |--SegmentDetected-->|
 *  |                       |<-------------------|                    |
 *  |                       |                    |                    |
 *  |                       |--pauseOtherApps--->|                    |
 *  |                       |--play()----------->|                    |
 *  |                       |                    |                    |
 *  |                       |<---------onComplete--------------------|
 *  |                       |--processEvent----->|                    |
 *  |                       |   (PlaybackComplete)|                   |
 *  |                       |                    |--UserRecording---->|
 *  |                       |<-------------------|                    |
 *  |                       |                    |                    |
 *  |                       |--startRecording--->|                    |
 *  |                       |<------onComplete------------------------|
 *  |                       |--processEvent----->|                    |
 *  |                       |   (RecordingComplete)|                  |
 *  |                       |                    |--Assessment------->|
 *  |                       |<-------------------|                    |
 *  |                       |                    |                    |
 *  |                       |--performAssessment->                    |
 *  |                       |--processEvent----->|                    |
 *  |                       |   (AssessmentComplete)|                 |
 *  |                       |                    |--Feedback--------->|
 *  |                       |<-------------------|                    |
 *  |                       |                    |                    |
 *  |                       |--delay(1000)------>|                    |
 *  |                       |--processEvent----->|                    |
 *  |                       |   (FeedbackComplete)|                   |
 *  |                       |                    |--Listening-------->|
 *  |                       |<-------------------|                    |
 * ```
 *
 * ## Event Handling Pattern
 *
 * The coordinator uses a unidirectional data flow pattern:
 * - External events (e.g., user actions, component callbacks) → [processEvent][ShadowingStateMachine.processEvent]
 * - State changes → [handleStateChange]
 * - Component actions → trigger new events
 * - Feedback events → [feedbackEvents] for UI/audio feedback
 *
 * ## Thread Safety
 *
 * All state changes are processed on the Main dispatcher through the state machine's StateFlow.
 * Component interactions are coordinated through coroutines with appropriate dispatchers.
 *
 * @property stateMachine Manages state transitions and business logic
 * @property audioCaptureManager Captures audio from other apps (Live Shadow mode)
 * @property audioProcessingPipeline Processes captured audio and detects speech segments
 * @property playbackEngine Plays audio segments to the user
 * @property userRecordingManager Records user's shadowing attempts
 * @property mediaControlManager Controls other apps (pause/resume)
 * @property settingsRepository Provides configuration settings
 * @property audioImporter Saves captured segments to the library
 */
@Singleton
class ShadowingCoordinator @Inject constructor(
    private val stateMachine: ShadowingStateMachine,
    private val audioCaptureManager: AudioCaptureManager,
    private val audioProcessingPipeline: AudioProcessingPipeline,
    private val playbackEngine: PlaybackEngine,
    private val userRecordingManager: UserRecordingManager,
    private val mediaControlManager: MediaControlManager,
    private val settingsRepository: SettingsRepository,
    private val audioImporter: AudioImporter
) {
    companion object {
        private const val TAG = "ShadowingCoordinator"
    }

    /**
     * Coroutine scope for managing coordinator lifecycle.
     * Uses Main dispatcher for state changes and SupervisorJob for independent child failures.
     */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /** Job for collecting audio segments from the processing pipeline */
    private var segmentCollectionJob: Job? = null
    
    /** Job for observing configuration changes from settings */
    private var configCollectionJob: Job? = null

    /**
     * Current shadowing state. Exposes the state machine's state for UI observation.
     * @see ShadowingState
     */
    val state: StateFlow<ShadowingState> = stateMachine.state

    /**
     * Backing flow for audio feedback events.
     * Uses SharedFlow with buffer to ensure events aren't lost during UI state changes.
     */
    private val _feedbackEvents = MutableSharedFlow<FeedbackEvent>(
        replay = 0,
        extraBufferCapacity = 16
    )
    
    /**
     * Stream of feedback events for audio notifications (beeps) and UI updates.
     * Emitted on state transitions to provide hands-free feedback.
     * @see FeedbackEvent
     */
    val feedbackEvents: SharedFlow<FeedbackEvent> = _feedbackEvents.asSharedFlow()

    init {
        observeStateChanges()
        observeConfig()
    }

    /**
     * Observes configuration changes and updates the state machine and components.
     * Configuration changes affect:
     * - State machine behavior (playback repeats, user repeats, bus mode, etc.)
     * - Audio processing (silence threshold)
     */
    private fun observeConfig() {
        configCollectionJob = scope.launch {
            settingsRepository.config.collect { config ->
                stateMachine.updateConfig(config)
                audioProcessingPipeline.updateSilenceThreshold(config.silenceThresholdMs)
            }
        }
    }

    /**
     * Observes state changes from the state machine and handles component coordination.
     * This is the core coordination loop - each state change triggers appropriate actions.
     */
    private fun observeStateChanges() {
        scope.launch {
            stateMachine.state.collect { state ->
                handleStateChange(state)
            }
        }
    }

    /**
     * Handles state changes by coordinating appropriate component actions.
     *
     * This method is the heart of the coordinator - it responds to each state transition
     * by triggering the necessary audio operations, emitting feedback events, and
     * queuing follow-up events to continue the flow.
     *
     * State handling responsibilities:
     * - **Idle**: Emit idle feedback
     * - **Listening**: Emit listening feedback, ready for next segment
     * - **SegmentDetected**: Save segment, pause other apps, start playback
     * - **Playback**: Emit playback feedback (handled by PlaybackEngine)
     * - **UserRecording**: Start microphone recording for user's attempt
     * - **Assessment**: Evaluate user's pronunciation (currently mock)
     * - **Feedback**: Display results, delay, then continue
     * - **PausedForNavigation**: Pause session while navigation is active
     *
     * @param state The new state to handle
     */
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
                // Save the captured segment to library
                scope.launch {
                    try {
                        audioImporter.saveCapturedSegment(state.audioSegment)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save captured segment", e)
                    }
                }
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

    /**
     * Starts a shadowing session with the given MediaProjection.
     *
     * This initializes all audio components and begins listening for speech segments
     * from other apps (Live Shadow mode). The session continues until [stop] is called.
     *
     * Startup sequence:
     * 1. Start audio capture from other apps via MediaProjection
     * 2. Initialize audio processing pipeline with silence detection
     * 3. Start collecting segments from the pipeline
     * 4. Transition state machine to Listening
     *
     * @param mediaProjection The MediaProjection for capturing audio from other apps.
     *                        Must be obtained from MediaProjectionManager.
     * @return true if session started successfully, false if audio capture failed
     * @see stop
     */
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

    /**
     * Collects audio segments from the processing pipeline.
     *
     * This job continuously listens to the segmentFlow from the AudioProcessingPipeline.
     * When a segment is detected and the state is Listening, it triggers a SegmentDetected
     * event to begin the shadowing cycle.
     *
     * The job runs until cancelled (typically when [stop] is called).
     */
    private fun startSegmentCollection() {
        segmentCollectionJob = scope.launch {
            audioProcessingPipeline.segmentFlow.collect { segment ->
                if (stateMachine.state.value is ShadowingState.Listening) {
                    stateMachine.processEvent(ShadowingEvent.SegmentDetected(segment))
                }
            }
        }
    }

    /**
     * Starts playback of an audio segment.
     *
     * Applies user configuration for playback speed and repeat count.
     * When playback completes (after all repeats), triggers PlaybackComplete event.
     *
     * @param segment The audio segment to play
     */
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

    /**
     * Starts recording the user's shadowing attempt.
     *
     * The recording continues until:
     * - Silence is detected (user stopped speaking)
     * - Maximum recording duration is reached
     * - User manually skips
     *
     * When recording completes, triggers RecordingComplete event with the recorded audio,
     * or Skip event if no audio was captured.
     *
     * @param originalSegment The original audio segment being shadowed (for context)
     */
    private fun startUserRecording(originalSegment: AudioSegment) {
        userRecordingManager.startRecording { recordedSegment ->
            if (recordedSegment != null) {
                stateMachine.processEvent(ShadowingEvent.RecordingComplete(recordedSegment))
            } else {
                stateMachine.processEvent(ShadowingEvent.Skip)
            }
        }
    }

    /**
     * Performs pronunciation assessment of the user's recording.
     *
     * Compares the user's recording with the original segment to evaluate:
     * - Overall pronunciation quality
     * - Fluency (rhythm and timing)
     * - Completeness (did they say everything?)
     *
     * **Current Implementation**: Returns mock scores for MVP.
     * Real Azure Speech SDK integration is planned for Phase 2.
     *
     * If assessment is disabled in config, immediately triggers AssessmentComplete
     * with a DISABLED result.
     *
     * @param original The original audio segment
     * @param userRecording The user's recorded attempt
     */
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

    /**
     * Stops the current shadowing session.
     *
     * Shutdown sequence:
     * 1. Cancel segment collection job
     * 2. Stop all audio components (playback, recording, processing, capture)
     * 3. Resume other apps that were paused
     * 4. Transition state machine to Idle
     *
     * This method is safe to call multiple times and from any state.
     *
     * @see start
     */
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

    /**
     * Skips the current segment and returns to listening for the next one.
     *
     * Can be called during:
     * - Playback (skip hearing the segment)
     * - UserRecording (skip recording attempt)
     * - Assessment (skip pronunciation evaluation)
     * - Feedback (skip viewing results)
     *
     * Stops any active playback or recording and immediately returns to Listening state.
     */
    fun skip() {
        playbackEngine.stop()
        userRecordingManager.stopRecording()
        stateMachine.processEvent(ShadowingEvent.Skip)
    }

    /**
     * Handles navigation start event.
     *
     * When the user starts navigation (e.g., Google Maps, Waze), the session is
     * automatically paused if [ShadowingConfig.pauseForNavigation] is enabled.
     *
     * The current state is saved so it can be resumed when navigation ends.
     * Stops any active playback or recording to avoid interference.
     *
     * @see onNavigationEnded
     */
    fun onNavigationStarted() {
        if (stateMachine.getCurrentConfig().pauseForNavigation) {
            playbackEngine.stop()
            userRecordingManager.stopRecording()
            stateMachine.processEvent(ShadowingEvent.NavigationStarted)
        }
    }

    /**
     * Handles navigation end event.
     *
     * When navigation ends, the session is automatically resumed to its previous state
     * (or to Listening if no previous state was saved).
     *
     * @see onNavigationStarted
     */
    fun onNavigationEnded() {
        stateMachine.processEvent(ShadowingEvent.NavigationEnded)
    }

    /**
     * Releases all resources and stops the coordinator.
     *
     * This should be called when the coordinator is no longer needed (e.g., app shutdown).
     * Performs complete cleanup:
     * 1. Stops the session
     * 2. Cancels config observation
     * 3. Releases audio capture and recording managers
     *
     * After calling this method, the coordinator should not be used again.
     */
    fun release() {
        stop()
        configCollectionJob?.cancel()
        audioCaptureManager.release()
        userRecordingManager.release()
    }
}

/**
 * Events emitted for audio feedback (beeps) and UI notifications.
 *
 * These events are emitted through [ShadowingCoordinator.feedbackEvents] to provide:
 * - Audio feedback (beeps) for hands-free operation
 * - UI state change notifications
 * - Progress indicators during the shadowing flow
 *
 * The AudioFeedbackSystem listens to these events and generates appropriate beeps
 * when [ShadowingConfig.audioFeedbackEnabled] is true.
 *
 * @see ShadowingCoordinator.feedbackEvents
 */
sealed class FeedbackEvent {
    /** Session is idle, ready to start */
    data object Idle : FeedbackEvent()
    
    /** Listening for speech segments from other apps */
    data object Listening : FeedbackEvent()
    
    /** Speech segment detected and saved */
    data object SegmentDetected : FeedbackEvent()
    
    /** Started playing the audio segment */
    data object PlaybackStarted : FeedbackEvent()
    
    /** Started recording user's shadowing attempt */
    data object RecordingStarted : FeedbackEvent()
    
    /** Started assessing user's pronunciation */
    data object AssessmentStarted : FeedbackEvent()
    
    /** Assessment result was good (score >= 70) */
    data object GoodScore : FeedbackEvent()
    
    /** Assessment result needs improvement (score < 70) */
    data object BadScore : FeedbackEvent()
    
    /** Session paused for navigation */
    data object Paused : FeedbackEvent()
}
