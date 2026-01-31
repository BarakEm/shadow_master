package com.shadowmaster.util

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for PerformanceTracker data classes and JSON serialization.
 * 
 * Note: Tests that require Android Context should be placed in androidTest directory.
 * These tests focus on the data model and JSON serialization logic.
 */
class PerformanceTrackerTest {

    @Test
    fun `test AudioImportMetric data class`() {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 5000
        
        val metric = AudioImportMetric(
            operationId = "test-123",
            startTime = startTime,
            endTime = endTime,
            fileName = "test.mp3",
            fileSizeBytes = 1024000L,
            audioFormat = "MP3",
            durationSeconds = 120.0,
            success = true
        )
        
        assertEquals("test-123", metric.operationId)
        assertEquals("test.mp3", metric.fileName)
        assertEquals(1024000L, metric.fileSizeBytes)
        assertEquals("MP3", metric.audioFormat)
        assertEquals(120.0, metric.durationSeconds, 0.01)
        assertTrue(metric.success)
        assertTrue(metric.isComplete)
        assertEquals(5000L, metric.durationMs)
    }

    @Test
    fun `test SegmentationMetric data class`() {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 3000
        
        val metric = SegmentationMetric(
            operationId = "seg-456",
            startTime = startTime,
            endTime = endTime,
            audioFileName = "test.mp3",
            segmentationMode = "SENTENCE",
            audioLengthMs = 120000L,
            segmentsDetected = 15,
            success = true
        )
        
        assertEquals("seg-456", metric.operationId)
        assertEquals("test.mp3", metric.audioFileName)
        assertEquals("SENTENCE", metric.segmentationMode)
        assertEquals(120000L, metric.audioLengthMs)
        assertEquals(15, metric.segmentsDetected)
        assertTrue(metric.success)
        assertTrue(metric.isComplete)
        assertEquals(3000L, metric.durationMs)
    }

    @Test
    fun `test UIRenderMetric data class`() {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 500
        
        val metric = UIRenderMetric(
            operationId = "ui-789",
            startTime = startTime,
            endTime = endTime,
            screenName = "LibraryScreen",
            operation = "load_playlists",
            frameCount = 60
        )
        
        assertEquals("ui-789", metric.operationId)
        assertEquals("LibraryScreen", metric.screenName)
        assertEquals("load_playlists", metric.operation)
        assertEquals(60, metric.frameCount)
        assertTrue(metric.isComplete)
        assertEquals(500L, metric.durationMs)
    }

    @Test
    fun `test DatabaseQueryMetric data class`() {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 100
        
        val metric = DatabaseQueryMetric(
            operationId = "db-101",
            startTime = startTime,
            endTime = endTime,
            queryType = "SELECT",
            tableName = "shadow_items",
            recordCount = 50,
            success = true
        )
        
        assertEquals("db-101", metric.operationId)
        assertEquals("SELECT", metric.queryType)
        assertEquals("shadow_items", metric.tableName)
        assertEquals(50, metric.recordCount)
        assertTrue(metric.success)
        assertTrue(metric.isComplete)
        assertEquals(100L, metric.durationMs)
    }

    @Test
    fun `test incomplete metric`() {
        val metric = AudioImportMetric(
            operationId = "test-incomplete",
            startTime = System.currentTimeMillis(),
            endTime = null,
            fileName = "test.mp3",
            fileSizeBytes = 1024000L
        )
        
        assertFalse(metric.isComplete)
        assertNull(metric.durationMs)
    }

    @Test
    fun `test PerformanceSummary toJson`() {
        val summary = PerformanceSummary(
            sessionStartTime = 1234567890L,
            totalMetrics = 10,
            audioImportCount = 3,
            audioImportSuccessRate = 100.0,
            audioImportAvgDurationMs = 5000.0,
            segmentationCount = 2,
            segmentationSuccessRate = 100.0,
            segmentationAvgDurationMs = 3000.0,
            uiRenderCount = 4,
            uiRenderAvgDurationMs = 500.0,
            dbQueryCount = 1,
            dbQuerySuccessRate = 100.0,
            dbQueryAvgDurationMs = 100.0
        )
        
        val json = summary.toJson()
        assertNotNull(json)
        assertEquals(1234567890L, json.getLong("sessionStartTime"))
        assertEquals(10, json.getInt("totalMetrics"))
        
        val audioImports = json.getJSONObject("audioImports")
        assertEquals(3, audioImports.getInt("count"))
        assertEquals(100.0, audioImports.getDouble("successRate"), 0.01)
        assertEquals(5000.0, audioImports.getDouble("avgDurationMs"), 0.01)
        
        val segmentations = json.getJSONObject("segmentations")
        assertEquals(2, segmentations.getInt("count"))
        
        val uiRenders = json.getJSONObject("uiRenders")
        assertEquals(4, uiRenders.getInt("count"))
        
        val dbQueries = json.getJSONObject("databaseQueries")
        assertEquals(1, dbQueries.getInt("count"))
    }

    @Test
    fun `test metric with error`() {
        val metric = AudioImportMetric(
            operationId = "error-test",
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 1000,
            fileName = "corrupt.mp3",
            fileSizeBytes = 512000L,
            success = false,
            error = "File format not supported"
        )
        
        assertFalse(metric.success)
        assertEquals("File format not supported", metric.error)
        assertTrue(metric.isComplete)
    }
}
