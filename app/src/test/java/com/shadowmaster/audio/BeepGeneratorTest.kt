package com.shadowmaster.audio

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BeepGenerator.
 */
class BeepGeneratorTest {

    @Test
    fun `generateBeep creates correct sized data`() {
        // 16kHz sample rate, 150ms duration
        // Expected: (16000 * 150 / 1000) samples * 2 bytes = 4800 bytes
        val beep = BeepGenerator.generateBeep(880.0, 150)
        assertEquals(4800, beep.size)
    }

    @Test
    fun `generateBeep creates non-zero audio data`() {
        val beep = BeepGenerator.generateBeep(880.0, 100)
        
        // Should contain non-zero samples (not complete silence)
        val hasNonZero = beep.any { it != 0.toByte() }
        assertTrue("Beep should contain non-zero audio data", hasNonZero)
    }

    @Test
    fun `generateSilence creates correct sized zero-filled data`() {
        // 16kHz sample rate, 500ms duration
        // Expected: (16000 * 500 / 1000) samples * 2 bytes = 16000 bytes
        val silence = BeepGenerator.generateSilence(500)
        assertEquals(16000, silence.size)
    }

    @Test
    fun `generateSilence creates all zeros`() {
        val silence = BeepGenerator.generateSilence(100)
        
        // Should be all zeros
        val allZeros = silence.all { it == 0.toByte() }
        assertTrue("Silence should be all zeros", allZeros)
    }

    @Test
    fun `beep frequencies match expected constants`() {
        assertEquals(880.0, BeepGenerator.PLAYBACK_BEEP_FREQ, 0.0)
        assertEquals(1047.0, BeepGenerator.YOUR_TURN_BEEP_FREQ, 0.0)
        assertEquals(660.0, BeepGenerator.SEGMENT_END_BEEP_FREQ, 0.0)
    }

    @Test
    fun `generateBeep with zero duration creates empty array`() {
        val beep = BeepGenerator.generateBeep(880.0, 0)
        assertEquals(0, beep.size)
    }

    @Test
    fun `generateSilence with zero duration creates empty array`() {
        val silence = BeepGenerator.generateSilence(0)
        assertEquals(0, silence.size)
    }

    @Test
    fun `generateBeep produces 16-bit samples`() {
        val beep = BeepGenerator.generateBeep(880.0, 100)
        
        // Size should be multiple of 2 (16-bit = 2 bytes per sample)
        assertEquals(0, beep.size % 2)
    }
}
