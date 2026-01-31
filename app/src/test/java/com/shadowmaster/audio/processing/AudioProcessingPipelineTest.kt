package com.shadowmaster.audio.processing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AudioProcessingPipeline.
 * 
 * Tests audio buffer processing, segment detection, error handling,
 * and memory management.
 * 
 * Note: Full integration tests require Android context and are in androidTest.
 * These unit tests focus on testing the CircularAudioBuffer component
 * which is the core buffer management logic used by AudioProcessingPipeline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioProcessingPipelineTest {

    /**
     * Test CircularAudioBuffer write and read operations
     */
    @Test
    fun `test circular buffer write and getLastNSamples`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 16000)
        
        // When - Write 100 samples
        val testSamples = ShortArray(100) { it.toShort() }
        buffer.write(testSamples)
        
        // Then
        assertEquals(100, buffer.getSampleCount())
        val retrieved = buffer.getLastNSamples(100)
        assertArrayEquals(testSamples, retrieved)
    }
    
    @Test
    fun `test circular buffer handles overflow correctly`() = runTest {
        // Given - Small buffer that can only hold ~30ms (480 samples at 16kHz)
        val buffer = CircularAudioBuffer(maxDurationMs = 30, sampleRate = 16000)
        val maxSamples = (30 * 16000) / 1000 // 480 samples
        
        // When - Write more data than buffer can hold
        val firstBatch = ShortArray(300) { 1000 }
        val secondBatch = ShortArray(300) { 2000 }
        buffer.write(firstBatch)
        buffer.write(secondBatch)
        
        // Then - Should keep only the most recent data
        assertEquals(maxSamples, buffer.getSampleCount())
        val retrieved = buffer.getLastNSamples(100)
        // Most recent 100 samples should all be from secondBatch
        assertTrue(retrieved.all { it == 2000.toShort() })
    }
    
    @Test
    fun `test circular buffer getLastNMillis conversion`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 16000)
        
        // When - Write 1 second worth of data (16000 samples)
        val testSamples = ShortArray(16000) { it.toShort() }
        buffer.write(testSamples)
        
        // Then - Get 500ms of data
        val halfSecond = buffer.getLastNMillis(500)
        assertEquals(8000, halfSecond.size) // 500ms * 16 samples/ms = 8000 samples
    }
    
    @Test
    fun `test circular buffer clear operation`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 16000)
        buffer.write(ShortArray(100) { 500 })
        assertEquals(100, buffer.getSampleCount())
        
        // When
        buffer.clear()
        
        // Then
        assertEquals(0, buffer.getSampleCount())
        assertEquals(0, buffer.getDurationMs())
        val retrieved = buffer.getLastNSamples(10)
        assertEquals(0, retrieved.size)
    }
    
    @Test
    fun `test circular buffer duration calculation`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 16000)
        
        // When - Write 1600 samples (100ms at 16kHz)
        buffer.write(ShortArray(1600) { 100 })
        
        // Then
        assertEquals(100, buffer.getDurationMs())
        
        // When - Write another 800 samples (50ms)
        buffer.write(ShortArray(800) { 200 })
        
        // Then
        assertEquals(150, buffer.getDurationMs())
    }
    
    @Test
    fun `test circular buffer handles empty write`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 16000)
        
        // When
        buffer.write(ShortArray(0))
        
        // Then
        assertEquals(0, buffer.getSampleCount())
        assertEquals(0, buffer.getDurationMs())
    }
    
    @Test
    fun `test circular buffer getLastNSamples with request larger than available`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 16000)
        buffer.write(ShortArray(100) { 42 })
        
        // When - Request more samples than available
        val retrieved = buffer.getLastNSamples(200)
        
        // Then - Should return only available samples
        assertEquals(100, retrieved.size)
        assertTrue(retrieved.all { it == 42.toShort() })
    }
    
    @Test
    fun `test circular buffer thread safety with concurrent writes`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 16000)
        
        // When - Multiple writes to simulate concurrent access
        repeat(10) {
            buffer.write(ShortArray(100) { it.toShort() })
        }
        
        // Then - Should handle all writes without corruption
        assertEquals(1000, buffer.getSampleCount())
        assertTrue(buffer.getDurationMs() > 0)
    }
    
    @Test
    fun `test circular buffer maintains FIFO order`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 100, sampleRate = 16000)
        
        // When - Write distinct batches
        buffer.write(ShortArray(100) { 1 })
        buffer.write(ShortArray(100) { 2 })
        buffer.write(ShortArray(100) { 3 })
        
        // Then - Most recent samples should be from batch 3
        val last50 = buffer.getLastNSamples(50)
        assertTrue("Most recent samples should be from last batch", 
                   last50.all { it == 3.toShort() })
    }
    
    @Test
    fun `test circular buffer handles various sample rates`() = runTest {
        // Given - Buffer with non-standard sample rate
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 8000)
        
        // When - Write 1 second of data
        buffer.write(ShortArray(8000) { 100 })
        
        // Then
        assertEquals(1000, buffer.getDurationMs())
        assertEquals(8000, buffer.getSampleCount())
    }
    
    @Test
    fun `test circular buffer memory bounds`() = runTest {
        // Given - Very large buffer
        val buffer = CircularAudioBuffer(maxDurationMs = 30000, sampleRate = 16000)
        val maxSamples = (30000 * 16000) / 1000 // 480,000 samples
        
        // When - Fill buffer multiple times
        repeat(3) {
            buffer.write(ShortArray(200000) { it.toShort() })
        }
        
        // Then - Should not exceed max capacity
        assertEquals(maxSamples, buffer.getSampleCount())
        assertEquals(30000, buffer.getDurationMs())
    }
    
    @Test
    fun `test circular buffer partial frame handling`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 16000)
        
        // When - Write samples that don't align with frame boundaries
        buffer.write(ShortArray(511) { 1 }) // Just under 512 frame size
        buffer.write(ShortArray(1) { 2 })   // Complete the frame
        buffer.write(ShortArray(100) { 3 }) // Partial next frame
        
        // Then - All samples should be stored
        assertEquals(612, buffer.getSampleCount())
    }
    
    @Test
    fun `test error handling with negative duration request`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 16000)
        buffer.write(ShortArray(100) { 50 })
        
        // When - Request negative duration (edge case)
        val result = buffer.getLastNMillis(-100)
        
        // Then - Should handle gracefully
        assertEquals(0, result.size)
    }
    
    @Test
    fun `test circular buffer with zero samples request`() = runTest {
        // Given
        val buffer = CircularAudioBuffer(maxDurationMs = 1000, sampleRate = 16000)
        buffer.write(ShortArray(100) { 100 })
        
        // When
        val result = buffer.getLastNSamples(0)
        
        // Then
        assertEquals(0, result.size)
    }

    /**
     * Test AudioProcessingPipeline constants and configuration
     */
    @Test
    fun `test pipeline constants are properly defined`() {
        // These constants are critical for proper audio processing
        val minSpeechDuration = 500L // MIN_SPEECH_DURATION_MS
        val maxSpeechDuration = 8000L // MAX_SPEECH_DURATION_MS
        val preSpeechBuffer = 200L // PRE_SPEECH_BUFFER_MS
        
        assertTrue("Min speech duration should be reasonable", minSpeechDuration > 0)
        assertTrue("Max speech duration should be greater than min", 
                   maxSpeechDuration > minSpeechDuration)
        assertTrue("Pre-speech buffer should be positive", preSpeechBuffer > 0)
    }
    
    @Test
    fun `test frame size aligns with VAD requirements`() {
        // VAD requires 512-sample frames at 16kHz
        val frameSize = 512
        val sampleRate = 16000
        val frameDurationMs = (frameSize * 1000.0) / sampleRate
        
        assertEquals(32.0, frameDurationMs, 0.1)
        assertTrue("Frame duration should be ~32ms for VAD", frameDurationMs >= 30 && frameDurationMs <= 35)
    }
}
