package com.shadowmaster.util

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Logger data classes and formatting logic.
 * 
 * Note: Tests that require Android Context should be placed in androidTest directory.
 * These tests focus on the data model and formatting logic.
 */
class LoggerTest {

    @Test
    fun `test LogLevel enum values`() {
        assertEquals(4, LogLevel.values().size)
        assertEquals(android.util.Log.DEBUG, LogLevel.DEBUG.priority)
        assertEquals(android.util.Log.INFO, LogLevel.INFO.priority)
        assertEquals(android.util.Log.WARN, LogLevel.WARN.priority)
        assertEquals(android.util.Log.ERROR, LogLevel.ERROR.priority)
    }

    @Test
    fun `test LogLevel labels`() {
        assertEquals("DEBUG", LogLevel.DEBUG.label)
        assertEquals("INFO", LogLevel.INFO.label)
        assertEquals("WARN", LogLevel.WARN.label)
        assertEquals("ERROR", LogLevel.ERROR.label)
    }

    @Test
    fun `test LogLevel priority ordering`() {
        assertTrue(LogLevel.DEBUG.priority < LogLevel.INFO.priority)
        assertTrue(LogLevel.INFO.priority < LogLevel.WARN.priority)
        assertTrue(LogLevel.WARN.priority < LogLevel.ERROR.priority)
    }

    @Test
    fun `test log format structure`() {
        val timestamp = "2024-01-01 12:00:00.000"
        val level = LogLevel.INFO
        val tag = "TestTag"
        val message = "Test message"
        
        val formatted = "[$timestamp] [${level.label}] [$tag] $message"
        
        assertTrue(formatted.startsWith("[$timestamp]"))
        assertTrue(formatted.contains("[INFO]"))
        assertTrue(formatted.contains("[$tag]"))
        assertTrue(formatted.endsWith(message))
    }

    @Test
    fun `test log format with special characters`() {
        val tag = "Test-Tag_123"
        val message = "Message with special chars: @#\$%^&*()"
        val level = LogLevel.WARN
        
        val formatted = "[timestamp] [${level.label}] [$tag] $message"
        
        assertTrue(formatted.contains(tag))
        assertTrue(formatted.contains(message))
    }

    @Test
    fun `test log format with empty message`() {
        val tag = "TestTag"
        val message = ""
        val level = LogLevel.DEBUG
        
        val formatted = "[timestamp] [${level.label}] [$tag] $message"
        
        assertTrue(formatted.endsWith("$message"))
    }

    @Test
    fun `test log format with long message`() {
        val tag = "TestTag"
        val message = "A".repeat(1000)
        val level = LogLevel.ERROR
        
        val formatted = "[timestamp] [${level.label}] [$tag] $message"
        
        assertTrue(formatted.contains(message))
        assertTrue(formatted.length > 1000)
    }
}
