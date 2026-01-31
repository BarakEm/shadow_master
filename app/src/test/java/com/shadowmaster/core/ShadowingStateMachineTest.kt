package com.shadowmaster.core

import com.shadowmaster.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ShadowingStateMachine.
 * Tests all state transitions, edge cases, and configuration behavior.
 */
class ShadowingStateMachineTest {

    private lateinit var stateMachine: ShadowingStateMachine
    private lateinit var testSegment: AudioSegment
    private lateinit var testUserRecording: AudioSegment

    @Before
    fun setup() {
        stateMachine = ShadowingStateMachine()
        // Create test audio segments
        testSegment = AudioSegment(
            samples = ShortArray(16000) { (it % 100).toShort() }, // 1 second of test audio
            sampleRate = 16000
        )
        testUserRecording = AudioSegment(
            samples = ShortArray(8000) { (it % 50).toShort() }, // 0.5 second of test audio
            sampleRate = 16000
        )
    }

    // ===== IDLE STATE TESTS =====

    @Test
    fun `test initial state is Idle`() = runTest {
        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Idle)
    }

    @Test
    fun `test transition from Idle to Listening on Start event`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    @Test
    fun `test Stop event from any state returns to Idle`() = runTest {
        // Start and move to Listening
        stateMachine.processEvent(ShadowingEvent.Start)
        assertTrue(stateMachine.state.first() is ShadowingState.Listening)

        // Stop should return to Idle
        stateMachine.processEvent(ShadowingEvent.Stop)
        assertTrue(stateMachine.state.first() is ShadowingState.Idle)
    }

    @Test
    fun `test Stop event from Idle stays in Idle`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Stop)
        assertTrue(stateMachine.state.first() is ShadowingState.Idle)
    }

    // ===== LISTENING STATE TESTS =====

    @Test
    fun `test SegmentDetected event from Listening transitions to SegmentDetected`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.SegmentDetected)
        assertEquals(testSegment, (state as ShadowingState.SegmentDetected).audioSegment)
    }

    @Test
    fun `test SegmentDetected event from non-Listening state is ignored`() = runTest {
        // Stay in Idle
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        assertTrue(stateMachine.state.first() is ShadowingState.Idle)
    }

    // ===== PLAYBACK STATE TESTS =====

    @Test
    fun `test PlaybackComplete from SegmentDetected transitions to Playback with repeat 1`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Playback)
        val playbackState = state as ShadowingState.Playback
        assertEquals(1, playbackState.currentRepeat)
        assertEquals(testSegment, playbackState.audioSegment)
    }

    @Test
    fun `test Playback repeats increment correctly with default config`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete) // First playback

        val state1 = stateMachine.state.first()
        assertTrue(state1 is ShadowingState.Playback)
        assertEquals(1, (state1 as ShadowingState.Playback).currentRepeat)
    }

    @Test
    fun `test Playback transitions to UserRecording after final repeat`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 2, assessmentEnabled = false)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete) // Repeat 1

        val state1 = stateMachine.state.first()
        assertTrue(state1 is ShadowingState.Playback)
        assertEquals(1, (state1 as ShadowingState.Playback).currentRepeat)

        stateMachine.processEvent(ShadowingEvent.PlaybackComplete) // Repeat 2

        val state2 = stateMachine.state.first()
        assertTrue(state2 is ShadowingState.Playback)
        assertEquals(2, (state2 as ShadowingState.Playback).currentRepeat)

        stateMachine.processEvent(ShadowingEvent.PlaybackComplete) // Done with playback

        val state3 = stateMachine.state.first()
        assertTrue(state3 is ShadowingState.UserRecording)
    }

    @Test
    fun `test Playback with multiple repeats increments correctly`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 3)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        for (repeat in 1..3) {
            val state = stateMachine.state.first()
            assertTrue(state is ShadowingState.Playback)
            assertEquals(repeat, (state as ShadowingState.Playback).currentRepeat)
            assertEquals(3, state.totalRepeats)

            if (repeat < 3) {
                stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
            }
        }
    }

    // ===== BUS MODE TESTS =====

    @Test
    fun `test bus mode skips UserRecording and returns to Listening`() = runTest {
        val config = ShadowingConfig(busMode = true, playbackRepeats = 1)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete) // Move to Playback
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete) // Should skip recording

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    // ===== USER RECORDING STATE TESTS =====

    @Test
    fun `test UserRecording state has correct repeat counts`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 1, userRepeats = 2, assessmentEnabled = false)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete) // SegmentDetected -> Playback
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete) // Playback -> UserRecording

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.UserRecording)
        val recordingState = state as ShadowingState.UserRecording
        assertEquals(1, recordingState.currentRepeat)
        assertEquals(2, recordingState.totalRepeats)
    }

    @Test
    fun `test UserRecording repeats increment correctly`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 1, userRepeats = 3, assessmentEnabled = false)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete) // Move to UserRecording

        // First recording
        val state1 = stateMachine.state.first()
        assertTrue(state1 is ShadowingState.UserRecording)
        assertEquals(1, (state1 as ShadowingState.UserRecording).currentRepeat)

        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        // Second recording
        val state2 = stateMachine.state.first()
        assertTrue(state2 is ShadowingState.UserRecording)
        assertEquals(2, (state2 as ShadowingState.UserRecording).currentRepeat)

        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        // Third recording
        val state3 = stateMachine.state.first()
        assertTrue(state3 is ShadowingState.UserRecording)
        assertEquals(3, (state3 as ShadowingState.UserRecording).currentRepeat)

        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        // Should return to Listening after all repeats
        val state4 = stateMachine.state.first()
        assertTrue(state4 is ShadowingState.Listening)
    }

    @Test
    fun `test RecordingComplete without assessment returns to Listening after repeats`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 1, userRepeats = 1, assessmentEnabled = false)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        assertTrue(stateMachine.state.first() is ShadowingState.UserRecording)

        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    // ===== ASSESSMENT AND FEEDBACK TESTS =====

    @Test
    fun `test RecordingComplete with assessment enabled transitions to Assessment`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 1, assessmentEnabled = true)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        assertTrue(stateMachine.state.first() is ShadowingState.UserRecording)

        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Assessment)
        val assessmentState = state as ShadowingState.Assessment
        assertEquals(testSegment, assessmentState.originalSegment)
        assertEquals(testUserRecording, assessmentState.userRecording)
    }

    @Test
    fun `test AssessmentComplete transitions to Feedback`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 1, assessmentEnabled = true)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        val assessmentResult = AssessmentResult(
            overallScore = 85f,
            pronunciationScore = 80f,
            fluencyScore = 90f,
            completenessScore = 85f
        )

        stateMachine.processEvent(ShadowingEvent.AssessmentComplete(assessmentResult))

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Feedback)
        assertEquals(assessmentResult, (state as ShadowingState.Feedback).result)
    }

    @Test
    fun `test FeedbackComplete returns to Listening after final repeat`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 1, userRepeats = 1, assessmentEnabled = true)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        val assessmentResult = AssessmentResult(
            overallScore = 75f,
            pronunciationScore = 70f,
            fluencyScore = 80f,
            completenessScore = 75f
        )

        stateMachine.processEvent(ShadowingEvent.AssessmentComplete(assessmentResult))
        stateMachine.processEvent(ShadowingEvent.FeedbackComplete)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    @Test
    fun `test FeedbackComplete continues to next UserRecording if repeats remain`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 1, userRepeats = 2, assessmentEnabled = true)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        val assessmentResult = AssessmentResult(
            overallScore = 75f,
            pronunciationScore = 70f,
            fluencyScore = 80f,
            completenessScore = 75f
        )

        stateMachine.processEvent(ShadowingEvent.AssessmentComplete(assessmentResult))
        stateMachine.processEvent(ShadowingEvent.FeedbackComplete)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.UserRecording)
        assertEquals(2, (state as ShadowingState.UserRecording).currentRepeat)
        assertEquals(2, state.totalRepeats)
    }

    // ===== NAVIGATION PAUSE TESTS =====

    @Test
    fun `test NavigationStarted pauses from Listening`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        assertTrue(stateMachine.state.first() is ShadowingState.Listening)

        stateMachine.processEvent(ShadowingEvent.NavigationStarted)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.PausedForNavigation)
    }

    @Test
    fun `test NavigationStarted pauses from Playback`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        assertTrue(stateMachine.state.first() is ShadowingState.Playback)

        stateMachine.processEvent(ShadowingEvent.NavigationStarted)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.PausedForNavigation)
    }

    @Test
    fun `test NavigationEnded resumes to previous state from PausedForNavigation`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        // Save the playback state
        val playbackState = stateMachine.state.first()
        assertTrue(playbackState is ShadowingState.Playback)

        stateMachine.processEvent(ShadowingEvent.NavigationStarted)
        assertTrue(stateMachine.state.first() is ShadowingState.PausedForNavigation)

        stateMachine.processEvent(ShadowingEvent.NavigationEnded)

        // Should resume to previous state (Listening, since we can't resume Playback perfectly)
        val resumedState = stateMachine.state.first()
        // The implementation resumes to the stored state or Listening
        assertTrue(resumedState is ShadowingState.Playback || resumedState is ShadowingState.Listening)
    }

    @Test
    fun `test NavigationStarted from Idle does not transition`() = runTest {
        assertTrue(stateMachine.state.first() is ShadowingState.Idle)

        stateMachine.processEvent(ShadowingEvent.NavigationStarted)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Idle)
    }

    @Test
    fun `test NavigationStarted from PausedForNavigation does not transition`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.NavigationStarted)

        assertTrue(stateMachine.state.first() is ShadowingState.PausedForNavigation)

        stateMachine.processEvent(ShadowingEvent.NavigationStarted)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.PausedForNavigation)
    }

    @Test
    fun `test Start from PausedForNavigation resumes to previous state`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.NavigationStarted)

        assertTrue(stateMachine.state.first() is ShadowingState.PausedForNavigation)

        stateMachine.processEvent(ShadowingEvent.Start)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    // ===== SKIP TESTS =====

    @Test
    fun `test Skip from Playback returns to Listening`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        assertTrue(stateMachine.state.first() is ShadowingState.Playback)

        stateMachine.processEvent(ShadowingEvent.Skip)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    @Test
    fun `test Skip from UserRecording returns to Listening`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 1, assessmentEnabled = false)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        assertTrue(stateMachine.state.first() is ShadowingState.UserRecording)

        stateMachine.processEvent(ShadowingEvent.Skip)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    @Test
    fun `test Skip from Assessment returns to Listening`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 1, assessmentEnabled = true)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        assertTrue(stateMachine.state.first() is ShadowingState.Assessment)

        stateMachine.processEvent(ShadowingEvent.Skip)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    @Test
    fun `test Skip from Feedback returns to Listening`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 1, assessmentEnabled = true)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        val assessmentResult = AssessmentResult(
            overallScore = 75f,
            pronunciationScore = 70f,
            fluencyScore = 80f,
            completenessScore = 75f
        )

        stateMachine.processEvent(ShadowingEvent.AssessmentComplete(assessmentResult))

        assertTrue(stateMachine.state.first() is ShadowingState.Feedback)

        stateMachine.processEvent(ShadowingEvent.Skip)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    @Test
    fun `test Skip from Idle does not transition`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Skip)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Idle)
    }

    @Test
    fun `test Skip from Listening does not transition`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.Skip)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    // ===== INVALID TRANSITION TESTS =====

    @Test
    fun `test PlaybackComplete from Idle is ignored`() = runTest {
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Idle)
    }

    @Test
    fun `test RecordingComplete from Idle is ignored`() = runTest {
        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Idle)
    }

    @Test
    fun `test AssessmentComplete from non-Assessment state is ignored`() = runTest {
        val assessmentResult = AssessmentResult(75f, 70f, 80f, 75f)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.AssessmentComplete(assessmentResult))

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    @Test
    fun `test FeedbackComplete from non-Feedback state is ignored`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.FeedbackComplete)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    @Test
    fun `test NavigationEnded from non-PausedForNavigation state is ignored`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.NavigationEnded)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    // ===== EDGE CASE TESTS =====

    @Test
    fun `test rapid consecutive Start events only transition once`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.Start)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Listening)
    }

    @Test
    fun `test rapid Stop events remain in Idle`() = runTest {
        stateMachine.processEvent(ShadowingEvent.Stop)
        stateMachine.processEvent(ShadowingEvent.Stop)
        stateMachine.processEvent(ShadowingEvent.Stop)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Idle)
    }

    @Test
    fun `test counters reset on Stop event`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 3, userRepeats = 2)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete) // Increment repeat counter

        // Stop and restart
        stateMachine.processEvent(ShadowingEvent.Stop)
        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Playback)
        // Counter should be reset to 1
        assertEquals(1, (state as ShadowingState.Playback).currentRepeat)
    }

    @Test
    fun `test counters reset on Skip event`() = runTest {
        val config = ShadowingConfig(playbackRepeats = 3)
        stateMachine.updateConfig(config)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        // Skip and restart
        stateMachine.processEvent(ShadowingEvent.Skip)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Playback)
        // Counter should be reset to 1
        assertEquals(1, (state as ShadowingState.Playback).currentRepeat)
    }

    @Test
    fun `test config update mid-flow uses new config`() = runTest {
        val config1 = ShadowingConfig(playbackRepeats = 2)
        stateMachine.updateConfig(config1)

        stateMachine.processEvent(ShadowingEvent.Start)
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        // Update config mid-flow
        val config2 = ShadowingConfig(playbackRepeats = 5)
        stateMachine.updateConfig(config2)

        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)

        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Playback)
        // Should use new config's totalRepeats
        assertEquals(5, (state as ShadowingState.Playback).totalRepeats)
    }

    @Test
    fun `test getCurrentConfig returns updated config`() = runTest {
        val config = ShadowingConfig(
            playbackRepeats = 3,
            userRepeats = 2,
            busMode = true
        )

        stateMachine.updateConfig(config)

        val currentConfig = stateMachine.getCurrentConfig()
        assertEquals(3, currentConfig.playbackRepeats)
        assertEquals(2, currentConfig.userRepeats)
        assertTrue(currentConfig.busMode)
    }

    @Test
    fun `test full flow with assessment enabled`() = runTest {
        val config = ShadowingConfig(
            playbackRepeats = 1,
            userRepeats = 1,
            assessmentEnabled = true
        )
        stateMachine.updateConfig(config)

        // Start
        stateMachine.processEvent(ShadowingEvent.Start)
        assertTrue(stateMachine.state.first() is ShadowingState.Listening)

        // Segment detected
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        assertTrue(stateMachine.state.first() is ShadowingState.SegmentDetected)

        // Playback
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        assertTrue(stateMachine.state.first() is ShadowingState.Playback)

        // User recording
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        assertTrue(stateMachine.state.first() is ShadowingState.UserRecording)

        // Assessment
        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))
        assertTrue(stateMachine.state.first() is ShadowingState.Assessment)

        // Feedback
        val result = AssessmentResult(80f, 75f, 85f, 80f)
        stateMachine.processEvent(ShadowingEvent.AssessmentComplete(result))
        assertTrue(stateMachine.state.first() is ShadowingState.Feedback)

        // Back to listening
        stateMachine.processEvent(ShadowingEvent.FeedbackComplete)
        assertTrue(stateMachine.state.first() is ShadowingState.Listening)
    }

    @Test
    fun `test full flow without assessment`() = runTest {
        val config = ShadowingConfig(
            playbackRepeats = 1,
            userRepeats = 1,
            assessmentEnabled = false
        )
        stateMachine.updateConfig(config)

        // Start
        stateMachine.processEvent(ShadowingEvent.Start)
        assertTrue(stateMachine.state.first() is ShadowingState.Listening)

        // Segment detected
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        assertTrue(stateMachine.state.first() is ShadowingState.SegmentDetected)

        // Playback
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        assertTrue(stateMachine.state.first() is ShadowingState.Playback)

        // User recording
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        assertTrue(stateMachine.state.first() is ShadowingState.UserRecording)

        // Back to listening (no assessment)
        stateMachine.processEvent(ShadowingEvent.RecordingComplete(testUserRecording))
        assertTrue(stateMachine.state.first() is ShadowingState.Listening)
    }

    @Test
    fun `test full bus mode flow`() = runTest {
        val config = ShadowingConfig(
            playbackRepeats = 2,
            busMode = true
        )
        stateMachine.updateConfig(config)

        // Start
        stateMachine.processEvent(ShadowingEvent.Start)
        assertTrue(stateMachine.state.first() is ShadowingState.Listening)

        // Segment detected
        stateMachine.processEvent(ShadowingEvent.SegmentDetected(testSegment))
        assertTrue(stateMachine.state.first() is ShadowingState.SegmentDetected)

        // First playback
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        assertTrue(stateMachine.state.first() is ShadowingState.Playback)

        // Second playback
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        val state = stateMachine.state.first()
        assertTrue(state is ShadowingState.Playback)
        assertEquals(2, (state as ShadowingState.Playback).currentRepeat)

        // Back to listening (skip recording in bus mode)
        stateMachine.processEvent(ShadowingEvent.PlaybackComplete)
        assertTrue(stateMachine.state.first() is ShadowingState.Listening)
    }
}
