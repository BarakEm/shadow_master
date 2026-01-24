package com.shadowmaster.core

import android.util.Log
import com.shadowmaster.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShadowingStateMachine @Inject constructor() {
    companion object {
        private const val TAG = "ShadowingStateMachine"
    }

    private val _state = MutableStateFlow<ShadowingState>(ShadowingState.Idle)
    val state: StateFlow<ShadowingState> = _state.asStateFlow()

    private var currentConfig: ShadowingConfig = ShadowingConfig()
    private var currentPlaybackRepeat = 0
    private var currentUserRepeat = 0
    private var currentSegment: AudioSegment? = null
    private var stateBeforePause: ShadowingState? = null

    fun processEvent(event: ShadowingEvent) {
        val currentState = _state.value
        val newState = transition(currentState, event)

        if (newState != currentState) {
            Log.i(TAG, "State transition: ${currentState::class.simpleName} -> ${newState::class.simpleName} (event: ${event::class.simpleName})")
            _state.value = newState
        }
    }

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
                                    userRecording = state.audioSegment
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

    private fun resetCounters() {
        currentPlaybackRepeat = 0
        currentUserRepeat = 0
        currentSegment = null
        stateBeforePause = null
    }

    fun updateConfig(config: ShadowingConfig) {
        currentConfig = config
    }

    fun getCurrentConfig(): ShadowingConfig = currentConfig
}
