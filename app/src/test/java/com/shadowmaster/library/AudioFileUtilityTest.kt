package com.shadowmaster.library

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for AudioFileUtility.
 * 
 * Note: Tests that require Android Context (like detectFormat and getFileName)
 * are not included here as they would require instrumented tests.
 * These tests focus on pure algorithms that don't need Android dependencies.
 */
class AudioFileUtilityTest {

    private val utility = AudioFileUtility()

    @Test
    fun `convertToMono with mono input returns same data`() {
        val monoData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val result = utility.convertToMono(monoData, channels = 1)
        assertArrayEquals(monoData, result)
    }

    @Test
    fun `convertToMono with stereo averages channels`() {
        // Create stereo data: 2 channels, 2 samples
        // Sample 1: L=100, R=200 => avg=150
        // Sample 2: L=300, R=400 => avg=350
        val stereoData = byteArrayOf(
            100, 0,  // L channel, sample 1 (little-endian)
            200.toByte(), 0,  // R channel, sample 1
            44, 1,  // L channel, sample 2 (300 = 0x012C)
            144.toByte(), 1  // R channel, sample 2 (400 = 0x0190)
        )
        
        val result = utility.convertToMono(stereoData, channels = 2)
        
        // Result should have 2 samples (4 bytes)
        assertEquals(4, result.size)
        
        // First sample: (100 + 200) / 2 = 150
        val sample1 = (result[1].toInt() shl 8) or (result[0].toInt() and 0xFF)
        assertEquals(150, sample1)
        
        // Second sample: (300 + 400) / 2 = 350
        val sample2 = (result[3].toInt() shl 8) or (result[2].toInt() and 0xFF)
        assertEquals(350, sample2)
    }

    @Test
    fun `resample with same rate returns same data`() {
        val pcmData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val result = utility.resample(pcmData, fromRate = 16000, toRate = 16000)
        assertArrayEquals(pcmData, result)
    }

    @Test
    fun `resample upsampling increases data size`() {
        val pcmData = byteArrayOf(0, 1, 0, 2, 0, 3, 0, 4) // 4 samples
        val result = utility.resample(pcmData, fromRate = 8000, toRate = 16000)
        
        // Upsampling from 8kHz to 16kHz should double the number of samples
        assertEquals(16, result.size) // 8 samples
    }

    @Test
    fun `resample downsampling decreases data size`() {
        val pcmData = ByteArray(16) { it.toByte() } // 8 samples
        val result = utility.resample(pcmData, fromRate = 16000, toRate = 8000)
        
        // Downsampling from 16kHz to 8kHz should halve the number of samples
        assertEquals(8, result.size) // 4 samples
    }

    @Test
    fun `createWavHeader creates correct header size`() {
        val header = utility.createWavHeader(pcmDataSize = 1000)
        assertEquals(44, header.size)
    }

    @Test
    fun `createWavHeader has correct RIFF signature`() {
        val header = utility.createWavHeader(pcmDataSize = 1000)
        assertEquals('R'.code.toByte(), header[0])
        assertEquals('I'.code.toByte(), header[1])
        assertEquals('F'.code.toByte(), header[2])
        assertEquals('F'.code.toByte(), header[3])
    }

    @Test
    fun `createWavHeader has correct WAVE signature`() {
        val header = utility.createWavHeader(pcmDataSize = 1000)
        assertEquals('W'.code.toByte(), header[8])
        assertEquals('A'.code.toByte(), header[9])
        assertEquals('V'.code.toByte(), header[10])
        assertEquals('E'.code.toByte(), header[11])
    }

    @Test
    fun `createWavHeader has correct fmt signature`() {
        val header = utility.createWavHeader(pcmDataSize = 1000)
        assertEquals('f'.code.toByte(), header[12])
        assertEquals('m'.code.toByte(), header[13])
        assertEquals('t'.code.toByte(), header[14])
        assertEquals(' '.code.toByte(), header[15])
    }

    @Test
    fun `createWavHeader has correct data signature`() {
        val header = utility.createWavHeader(pcmDataSize = 1000)
        assertEquals('d'.code.toByte(), header[36])
        assertEquals('a'.code.toByte(), header[37])
        assertEquals('t'.code.toByte(), header[38])
        assertEquals('a'.code.toByte(), header[39])
    }

    @Test
    fun `createWavHeader sets PCM audio format`() {
        val header = utility.createWavHeader(pcmDataSize = 1000)
        // AudioFormat should be 1 (PCM)
        assertEquals(1, header[20].toInt())
        assertEquals(0, header[21].toInt())
    }

    @Test
    fun `createWavHeader sets correct channels`() {
        val header = utility.createWavHeader(pcmDataSize = 1000, channels = 2)
        assertEquals(2, header[22].toInt())
        assertEquals(0, header[23].toInt())
    }

    @Test
    fun `createWavHeader sets correct sample rate`() {
        val header = utility.createWavHeader(pcmDataSize = 1000, sampleRate = 44100)
        // 44100 = 0x0000AC44 (little-endian)
        val sampleRate = (header[24].toInt() and 0xFF) or
                        ((header[25].toInt() and 0xFF) shl 8) or
                        ((header[26].toInt() and 0xFF) shl 16) or
                        ((header[27].toInt() and 0xFF) shl 24)
        assertEquals(44100, sampleRate)
    }

    @Test
    fun `createWavHeader sets correct bits per sample`() {
        val header = utility.createWavHeader(pcmDataSize = 1000, bitsPerSample = 16)
        assertEquals(16, header[34].toInt())
        assertEquals(0, header[35].toInt())
    }

    @Test
    fun `calculateDurationMs calculates correctly`() {
        // 16000 samples/sec, 1 channel, 16-bit (2 bytes per sample)
        // 16000 bytes = 0.5 seconds = 500 ms
        val duration = utility.calculateDurationMs(pcmDataSize = 16000, sampleRate = 16000, channels = 1)
        assertEquals(500L, duration)
    }

    @Test
    fun `calculateDurationMs with different sample rate`() {
        // 44100 samples/sec, 1 channel, 16-bit (2 bytes per sample)
        // 88200 bytes = 1 second = 1000 ms
        val duration = utility.calculateDurationMs(pcmDataSize = 88200, sampleRate = 44100, channels = 1)
        assertEquals(1000L, duration)
    }

    @Test
    fun `calculateDurationMs with stereo`() {
        // 16000 samples/sec, 2 channels, 16-bit (2 bytes per sample per channel)
        // 64000 bytes = 1 second = 1000 ms
        val duration = utility.calculateDurationMs(pcmDataSize = 64000, sampleRate = 16000, channels = 2)
        assertEquals(1000L, duration)
    }
}
