package com.shadowmaster.core

import android.util.Log
import com.shadowmaster.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State machine that manages the shadowing practice flow.
 *
 * The state machine orchestrates the listen-repeat-assess cycle for language learning.
 * It ensures valid state transitions based on events and configuration settings.
 *
 * ## Architecture
 * - **Current State**: Exposed as [StateFlow] for reactive UI updates
 * - **Event Processing**: All state changes go through [processEvent]
 * - **Configuration**: Updated via [updateConfig] to adjust behavior
 *
 * ## State Flow Variants
 *
 * ### Standard Mode (with assessment)
 * ```
 * Idle -> Listening -> SegmentDetected -> Playback (×N) ->
 * UserRecording (×M) -> Assessment -> Feedback -> Listening (loop)
 * ```
 *
 * ### Bus Mode (listen-only, no recording)
 * ```
 * Idle -> Listening -> SegmentDetected -> Playback (×N) -> Listening (loop)
 * ```
 *
 * ### Without Assessment
 * ```
 * Idle -> Listening -> SegmentDetected -> Playback (×N) ->
 * UserRecording (×M) -> Listening (loop)
 * ```
 *
 * ## Navigation Pause
 * Any active state can transition to **PausedForNavigation** when navigation audio
 * is detected. The state machine saves the current state and resumes when navigation ends.
 *
 * ## Configuration Impact
 * - **playbackRepeats** (1-5): Number of times segment is played
 * - **userRepeats** (1-3): Number of recording attempts
 * - **busMode** (true/false): Skip user recording entirely
 * - **assessmentEnabled** (true/false): Enable/disable pronunciation scoring
 * - **pauseForNavigation** (true/false): Auto-pause for navigation audio
 *
 * See [ShadowingState] for detailed state documentation.
 * See [ShadowingEvent] for detailed event documentation.
 * See `docs/state-machine-diagram.md` for visual diagram.
 *
 * @property state Current state as StateFlow for observation
 */
@Singleton
class ShadowingStateMachine @Inject constructor() {
    companion object {
        private const val TAG = "ShadowingStateMachine"
    }

    private val _state = MutableStateFlow<ShadowingState>(ShadowingState.Idle)
    val state: StateFlow<ShadowingState> = _state.asStateFlow()

    private var currentConfig: ShadowingConfig = ShadowingConfig()
    private var currentPlaybackRepeat = 0  // 0-based counter for current playback iteration
    private var currentUserRepeat = 0      // 0-based counter for current user recording iteration
    private var currentSegment: AudioSegment? = null  // The audio segment being practiced
    private var stateBeforePause: ShadowingState? = null  // Saved state for navigation pause/resume

    /**
     * Process an event and transition to the appropriate new state.
     *
     * This is the only method that should trigger state changes. It:
     * 1. Gets the current state
     * 2. Determines the new state based on current state + event
     * 3. Updates internal counters and tracking
     * 4. Emits the new state if it differs from current
     *
     * State transitions are logged for debugging.
     *
     * @param event The event to process
     */
    fun processEvent(event: ShadowingEvent) {
        val currentState = _state.value
        val newState = transition(currentState, event)

        if (newState != currentState) {
            Log.i(TAG, "State transition: ${currentState::class.simpleName} -> ${newState::class.simpleName} (event: ${event::class.simpleName})")
            _state.value = newState
        }
    }

    /**
     * Determine the next state based on current state and event.
     *
     * This is the core transition logic. It implements a comprehensive state machine
     * with the following principles:
     *
     * 1. **Stop is global**: Stop event returns to Idle from any state
     * 2. **Skip is selective**: Skip only works during active practice (not Idle/Listening)
     * 3. **Navigation pause**: Can pause from any active state, preserves state for resume
     * 4. **Counter management**: Tracks playback and recording iterations internally
     * 5. **Configuration-aware**: Behavior changes based on busMode, assessmentEnabled, etc.
     *
     * Invalid transitions are ignored (return current state unchanged).
     *
     * @param state Current state
     * @param event Event to process
     * @return New state (may be same as current if transition is invalid)
     */
    private fun transition(state: ShadowingState, event: ShadowingEvent): ShadowingState {
        return when (event) {
            is ShadowingEvent.Start -> {
                when (state) {
                    is ShadowingState.Idle -> ShadowingState.Listening
                    is ShadowingState.PausedForNavigation -> {
                        stateBeforePause ?: ShadowingState.Listening
                    }
                    else -> state
                }
            }

            is ShadowingEvent.Stop -> {
                resetCounters()
                ShadowingState.Idle
            }

            is ShadowingEvent.SegmentDetected -> {
                when (state) {
                    is ShadowingState.Listening -> {
                        currentSegment = event.segment
                        currentPlaybackRepeat = 1
                        ShadowingState.SegmentDetected(event.segment)
                    }
                    else -> state
                }
            }

            is ShadowingEvent.PlaybackComplete -> {
                when (state) {
                    is ShadowingState.SegmentDetected -> {
                        ShadowingState.Playback(
                            audioSegment = state.audioSegment,
                            currentRepeat = currentPlaybackRepeat,
                            totalRepeats = currentConfig.playbackRepeats
                        )
                    }
                    is ShadowingState.Playback -> {
                        if (currentPlaybackRepeat < currentConfig.playbackRepeats) {
                            currentPlaybackRepeat++
                            ShadowingState.Playback(
                                audioSegment = state.audioSegment,
                                currentRepeat = currentPlaybackRepeat,
                                totalRepeats = currentConfig.playbackRepeats
                            )
                        } else if (currentConfig.busMode) {
                            // Bus mode: skip user recording, go back to listening
                            resetCounters()
                            ShadowingState.Listening
                        } else {
                            currentUserRepeat = 1
                            ShadowingState.UserRecording(
                                audioSegment = state.audioSegment,
                                currentRepeat = currentUserRepeat,
                                totalRepeats = currentConfig.userRepeats
                            )
                        }
                    }
                    else -> state
                }
            }

            is ShadowingEvent.RecordingComplete -> {
                when (state) {
                    is ShadowingState.UserRecording -> {
                        if (currentConfig.assessmentEnabled) {
                            currentSegment?.let { segment ->
                                ShadowingState.Assessment(
                                    originalSegment = segment,
                                    userRecording = event.recordedSegment
                                )
                            } ?: ShadowingState.Listening
                        } else {
                            if (currentUserRepeat < currentConfig.userRepeats) {
                                currentUserRepeat++
                                ShadowingState.UserRecording(
                                    audioSegment = state.audioSegment,
                                    currentRepeat = currentUserRepeat,
                                    totalRepeats = currentConfig.userRepeats
                                )
                            } else {
                                resetCounters()
                                ShadowingState.Listening
                            }
                        }
                    }
                    else -> state
                }
            }

            is ShadowingEvent.AssessmentComplete -> {
                when (state) {
                    is ShadowingState.Assessment -> {
                        ShadowingState.Feedback(event.result)
                    }
                    else -> state
                }
            }

            is ShadowingEvent.FeedbackComplete -> {
                when (state) {
                    is ShadowingState.Feedback -> {
                        if (currentUserRepeat < currentConfig.userRepeats) {
                            currentUserRepeat++
                            currentSegment?.let { segment ->
                                ShadowingState.UserRecording(
                                    audioSegment = segment,
                                    currentRepeat = currentUserRepeat,
                                    totalRepeats = currentConfig.userRepeats
                                )
                            } ?: run {
                                resetCounters()
                                ShadowingState.Listening
                            }
                        } else {
                            resetCounters()
                            ShadowingState.Listening
                        }
                    }
                    else -> state
                }
            }

            is ShadowingEvent.NavigationStarted -> {
                if (state !is ShadowingState.Idle && state !is ShadowingState.PausedForNavigation) {
                    stateBeforePause = state
                    ShadowingState.PausedForNavigation
                } else {
                    state
                }
            }

            is ShadowingEvent.NavigationEnded -> {
                when (state) {
                    is ShadowingState.PausedForNavigation -> {
                        stateBeforePause ?: ShadowingState.Listening
                    }
                    else -> state
                }
            }

            is ShadowingEvent.Skip -> {
                when (state) {
                    is ShadowingState.Playback,
                    is ShadowingState.UserRecording,
                    is ShadowingState.Assessment,
                    is ShadowingState.Feedback -> {
                        resetCounters()
                        ShadowingState.Listening
                    }
                    else -> state
                }
            }
        }
    }

    /**
     * Reset all internal counters and tracking.
     *
     * Called when:
     * - Stopping practice session
     * - Completing a segment and returning to listening
     * - Skipping to next segment
     */
    private fun resetCounters() {
        currentPlaybackRepeat = 0
        currentUserRepeat = 0
        currentSegment = null
        stateBeforePause = null
    }

    /**
     * Update the configuration that governs state machine behavior.
     *
     * Configuration changes take effect on the next state transition.
     * Does not affect the current state or in-progress segment.
     *
     * @param config New configuration settings
     */
    fun updateConfig(config: ShadowingConfig) {
        currentConfig = config
    }

    /**
     * Get the current configuration.
     *
     * @return Current configuration settings
     */
    fun getCurrentConfig(): ShadowingConfig = currentConfig
}
