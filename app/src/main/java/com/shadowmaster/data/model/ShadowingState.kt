package com.shadowmaster.data.model

/**
 * Represents all possible states in the shadowing practice flow.
 *
 * The shadowing flow follows this general pattern:
 * 1. **Idle** - App is ready but not actively practicing
 * 2. **Listening** - Monitoring audio for speech segments
 * 3. **SegmentDetected** - Speech segment found, preparing playback
 * 4. **Playback** - Playing audio segment for learner (repeated 1-5 times)
 * 5. **UserRecording** - Learner repeats the segment (recorded 1-3 times)
 * 6. **Assessment** - Evaluating learner's pronunciation
 * 7. **Feedback** - Showing assessment results
 * 8. Return to **Listening** for next segment
 *
 * Special states:
 * - **PausedForNavigation** - Temporarily paused when navigation audio is detected
 *
 * See [ShadowingStateMachine] for valid state transitions.
 * See `docs/state-machine-diagram.md` for a visual representation.
 */
sealed class ShadowingState {
    /**
     * Initial state when app is ready but not actively practicing.
     *
     * Valid transitions:
     * - **Start** event -> **Listening** state (user starts practice session)
     */
    data object Idle : ShadowingState()

    /**
     * Actively monitoring audio stream for speech segments.
     *
     * In this state, the app uses Voice Activity Detection (VAD) to identify
     * speech boundaries. When speech is detected and silence follows, a segment
     * is extracted.
     *
     * Valid transitions:
     * - **SegmentDetected** event -> **SegmentDetected** state (speech segment found)
     * - **Stop** event -> **Idle** state (user stops practice)
     * - **NavigationStarted** event -> **PausedForNavigation** state
     */
    data object Listening : ShadowingState()

    /**
     * Speech segment has been detected and is being prepared for playback.
     *
     * This is a brief transition state. The coordinator pauses other apps and
     * initiates playback immediately.
     *
     * Valid transitions:
     * - **PlaybackComplete** event -> **Playback** state (begins first playback)
     * - **Stop** event -> **Idle** state
     */
    data class SegmentDetected(val audioSegment: AudioSegment) : ShadowingState()

    /**
     * Playing the audio segment for the learner to hear.
     *
     * Plays the segment at configured speed (0.5x-2.0x) and repeats according
     * to settings (1-5 times). After all repeats, transitions to user recording
     * or returns to listening if in bus mode.
     *
     * @param audioSegment The audio segment being played
     * @param currentRepeat Current playback iteration (1-based)
     * @param totalRepeats Total number of playback repeats configured
     *
     * Valid transitions:
     * - **PlaybackComplete** event -> **Playback** state (repeat playback) OR
     *   **UserRecording** state (after all repeats, if not bus mode) OR
     *   **Listening** state (after all repeats, if bus mode enabled)
     * - **Skip** event -> **Listening** state
     * - **Stop** event -> **Idle** state
     * - **NavigationStarted** event -> **PausedForNavigation** state
     */
    data class Playback(val audioSegment: AudioSegment, val currentRepeat: Int, val totalRepeats: Int) : ShadowingState()

    /**
     * Recording the learner's attempt to repeat the segment.
     *
     * Records audio from microphone with automatic silence detection. Can repeat
     * multiple times (1-3) based on settings. If assessment is enabled, transitions
     * to assessment; otherwise, may repeat or return to listening.
     *
     * @param audioSegment The original audio segment for reference
     * @param currentRepeat Current recording iteration (1-based)
     * @param totalRepeats Total number of user repeats configured
     *
     * Valid transitions:
     * - **RecordingComplete** event -> **Assessment** state (if assessment enabled) OR
     *   **UserRecording** state (repeat recording, if more repeats needed and no assessment) OR
     *   **Listening** state (after all repeats, if assessment disabled)
     * - **Skip** event -> **Listening** state
     * - **Stop** event -> **Idle** state
     * - **NavigationStarted** event -> **PausedForNavigation** state
     */
    data class UserRecording(val audioSegment: AudioSegment, val currentRepeat: Int, val totalRepeats: Int) : ShadowingState()

    /**
     * Evaluating the learner's pronunciation against the original.
     *
     * Sends audio to pronunciation assessment service (e.g., Azure Speech Services)
     * for scoring. Currently returns mock scores pending full implementation.
     *
     * @param originalSegment The original audio segment
     * @param userRecording The learner's recorded attempt
     *
     * Valid transitions:
     * - **AssessmentComplete** event -> **Feedback** state
     * - **Skip** event -> **Listening** state
     * - **Stop** event -> **Idle** state
     */
    data class Assessment(val originalSegment: AudioSegment, val userRecording: AudioSegment) : ShadowingState()

    /**
     * Displaying assessment results to the learner.
     *
     * Shows pronunciation score, fluency score, and completeness score.
     * May play audio feedback (beep) based on score quality.
     *
     * @param result Assessment scores and feedback
     *
     * Valid transitions:
     * - **FeedbackComplete** event -> **UserRecording** state (if more repeats needed) OR
     *   **Listening** state (after all repeats)
     * - **Skip** event -> **Listening** state
     * - **Stop** event -> **Idle** state
     */
    data class Feedback(val result: AssessmentResult) : ShadowingState()

    /**
     * Temporarily paused due to navigation audio detection.
     *
     * When navigation app audio is detected (e.g., GPS directions), the practice
     * flow pauses to avoid interfering. Returns to previous state when navigation
     * ends. Only active if "pauseForNavigation" setting is enabled.
     *
     * Valid transitions:
     * - **NavigationEnded** event -> Previous state before pause
     * - **Start** event -> Previous state before pause (if saved) OR **Listening** state
     * - **Stop** event -> **Idle** state
     */
    data object PausedForNavigation : ShadowingState()

    val displayName: String
        get() = when (this) {
            is Idle -> "Ready"
            is Listening -> "Listening"
            is SegmentDetected -> "Segment Detected"
            is Playback -> "Playing (${currentRepeat}/${totalRepeats})"
            is UserRecording -> "Your Turn (${currentRepeat}/${totalRepeats})"
            is Assessment -> "Assessing"
            is Feedback -> "Feedback"
            is PausedForNavigation -> "Paused"
        }
}

/**
 * Events that trigger state transitions in the shadowing state machine.
 *
 * Events are processed by [ShadowingStateMachine.processEvent] which determines
 * the appropriate state transition based on the current state and event type.
 *
 * Event flow examples:
 * 1. Starting practice: **Start** -> **SegmentDetected** -> **PlaybackComplete** (x N) ->
 *    **RecordingComplete** -> **AssessmentComplete** -> **FeedbackComplete** -> (repeat)
 *
 * 2. Bus mode (listen-only): **Start** -> **SegmentDetected** -> **PlaybackComplete** (x N) -> (repeat)
 *
 * 3. Navigation interruption: **NavigationStarted** -> (pause) -> **NavigationEnded** -> (resume)
 */
sealed class ShadowingEvent {
    /**
     * User starts a practice session.
     *
     * Triggered when:
     * - User taps "Start" button in UI
     * - Foreground service starts
     * - Navigation audio ends and session resumes
     *
     * Effect: **Idle** -> **Listening** OR **PausedForNavigation** -> previous state
     */
    data object Start : ShadowingEvent()

    /**
     * User stops the practice session.
     *
     * Triggered when:
     * - User taps "Stop" button in UI
     * - User navigates away from practice screen
     * - Foreground service stops
     *
     * Effect: Any state -> **Idle** (resets all counters and state)
     */
    data object Stop : ShadowingEvent()

    /**
     * Speech segment has been detected in the audio stream.
     *
     * Triggered when:
     * - VAD (Voice Activity Detection) identifies a complete speech segment
     * - Speech is followed by sufficient silence (configurable threshold)
     *
     * Effect: **Listening** -> **SegmentDetected**
     *
     * @param segment The detected audio segment with samples and metadata
     */
    data class SegmentDetected(val segment: AudioSegment) : ShadowingEvent()

    /**
     * Audio playback has completed.
     *
     * Triggered when:
     * - PlaybackEngine finishes playing a segment
     *
     * Effect:
     * - **SegmentDetected** -> **Playback** (start first repeat)
     * - **Playback** -> **Playback** (next repeat if more repeats configured)
     * - **Playback** -> **UserRecording** (after all repeats, normal mode)
     * - **Playback** -> **Listening** (after all repeats, bus mode)
     */
    data object PlaybackComplete : ShadowingEvent()

    /**
     * User recording has completed.
     *
     * Triggered when:
     * - UserRecordingManager finishes capturing user's speech
     * - Silence detected after user speaks (automatic stop)
     * - Timeout reached for recording
     *
     * Effect:
     * - **UserRecording** -> **Assessment** (if assessment enabled)
     * - **UserRecording** -> **UserRecording** (next repeat if more repeats and no assessment)
     * - **UserRecording** -> **Listening** (after all repeats, assessment disabled)
     *
     * @param recordedSegment The captured audio of user's pronunciation attempt
     */
    data class RecordingComplete(val recordedSegment: AudioSegment) : ShadowingEvent()

    /**
     * Pronunciation assessment has completed.
     *
     * Triggered when:
     * - Assessment service returns pronunciation scores
     * - Currently returns mock scores (real Azure integration pending)
     *
     * Effect: **Assessment** -> **Feedback**
     *
     * @param result Scores for overall, pronunciation, fluency, and completeness
     */
    data class AssessmentComplete(val result: AssessmentResult) : ShadowingEvent()

    /**
     * Feedback display has completed.
     *
     * Triggered when:
     * - Feedback timeout expires
     * - User dismisses feedback (future feature)
     *
     * Effect:
     * - **Feedback** -> **UserRecording** (if more user repeats configured)
     * - **Feedback** -> **Listening** (after all repeats)
     */
    data object FeedbackComplete : ShadowingEvent()

    /**
     * Navigation audio has been detected.
     *
     * Triggered when:
     * - NavigationAudioDetector identifies GPS/navigation app audio
     * - Only if "pauseForNavigation" setting is enabled
     *
     * Effect: Any active state -> **PausedForNavigation** (saves previous state)
     */
    data object NavigationStarted : ShadowingEvent()

    /**
     * Navigation audio has ended.
     *
     * Triggered when:
     * - NavigationAudioDetector confirms navigation audio stopped
     * - Silence period after navigation instructions
     *
     * Effect: **PausedForNavigation** -> previous saved state
     */
    data object NavigationEnded : ShadowingEvent()

    /**
     * User manually skips the current segment.
     *
     * Triggered when:
     * - User taps "Skip" button
     * - User gestures to skip (future feature)
     *
     * Effect: **Playback**, **UserRecording**, **Assessment**, or **Feedback** -> **Listening**
     */
    data object Skip : ShadowingEvent()
}

data class AudioSegment(
    val samples: ShortArray,
    val sampleRate: Int = 16000,
    val durationMs: Long = (samples.size * 1000L) / sampleRate
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioSegment) return false
        return samples.contentEquals(other.samples) && sampleRate == other.sampleRate
    }

    override fun hashCode(): Int {
        return 31 * samples.contentHashCode() + sampleRate
    }
}

data class AssessmentResult(
    val overallScore: Float,
    val pronunciationScore: Float,
    val fluencyScore: Float,
    val completenessScore: Float,
    val isGood: Boolean = overallScore >= 70f
) {
    companion object {
        val SKIPPED = AssessmentResult(
            overallScore = 0f,
            pronunciationScore = 0f,
            fluencyScore = 0f,
            completenessScore = 0f,
            isGood = false
        )

        val DISABLED = AssessmentResult(
            overallScore = 100f,
            pronunciationScore = 100f,
            fluencyScore = 100f,
            completenessScore = 100f,
            isGood = true
        )
    }
}
