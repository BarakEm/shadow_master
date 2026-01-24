package com.shadowmaster.data.model

sealed class ShadowingState {
    data object Idle : ShadowingState()
    data object Listening : ShadowingState()
    data class SegmentDetected(val audioSegment: AudioSegment) : ShadowingState()
    data class Playback(val audioSegment: AudioSegment, val currentRepeat: Int, val totalRepeats: Int) : ShadowingState()
    data class UserRecording(val audioSegment: AudioSegment, val currentRepeat: Int, val totalRepeats: Int) : ShadowingState()
    data class Assessment(val originalSegment: AudioSegment, val userRecording: AudioSegment) : ShadowingState()
    data class Feedback(val result: AssessmentResult) : ShadowingState()
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

sealed class ShadowingEvent {
    data object Start : ShadowingEvent()
    data object Stop : ShadowingEvent()
    data class SegmentDetected(val segment: AudioSegment) : ShadowingEvent()
    data object PlaybackComplete : ShadowingEvent()
    data class RecordingComplete(val recordedSegment: AudioSegment) : ShadowingEvent()
    data class AssessmentComplete(val result: AssessmentResult) : ShadowingEvent()
    data object FeedbackComplete : ShadowingEvent()
    data object NavigationStarted : ShadowingEvent()
    data object NavigationEnded : ShadowingEvent()
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
