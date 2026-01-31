package com.shadowmaster.util

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for NameValidator.
 */
class NameValidatorTest {
    
    @Test
    fun `valid playlist name passes validation`() {
        val result = NameValidator.validatePlaylistName("My Playlist")
        assertTrue(result is NameValidator.ValidationResult.Valid)
    }
    
    @Test
    fun `valid segment name passes validation`() {
        val result = NameValidator.validateSegmentName("Segment 1")
        assertTrue(result is NameValidator.ValidationResult.Valid)
    }
    
    @Test
    fun `empty name fails validation`() {
        val result = NameValidator.validatePlaylistName("")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertEquals("Playlist name cannot be empty", (result as NameValidator.ValidationResult.Invalid).reason)
    }
    
    @Test
    fun `blank name fails validation`() {
        val result = NameValidator.validatePlaylistName("   ")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertEquals("Playlist name cannot be empty", (result as NameValidator.ValidationResult.Invalid).reason)
    }
    
    @Test
    fun `name with leading and trailing whitespace is valid after trim`() {
        val result = NameValidator.validatePlaylistName("  My Playlist  ")
        assertTrue(result is NameValidator.ValidationResult.Valid)
    }
    
    @Test
    fun `name exceeding max length fails validation`() {
        val longName = "a".repeat(101)
        val result = NameValidator.validatePlaylistName(longName)
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("100 characters or less"))
    }
    
    @Test
    fun `name at max length passes validation`() {
        val maxLengthName = "a".repeat(100)
        val result = NameValidator.validatePlaylistName(maxLengthName)
        assertTrue(result is NameValidator.ValidationResult.Valid)
    }
    
    @Test
    fun `name with forward slash fails validation`() {
        val result = NameValidator.validatePlaylistName("My/Playlist")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("invalid characters"))
    }
    
    @Test
    fun `name with backslash fails validation`() {
        val result = NameValidator.validatePlaylistName("My\\Playlist")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("invalid characters"))
    }
    
    @Test
    fun `name with colon fails validation`() {
        val result = NameValidator.validatePlaylistName("My:Playlist")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("invalid characters"))
    }
    
    @Test
    fun `name with asterisk fails validation`() {
        val result = NameValidator.validatePlaylistName("My*Playlist")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("invalid characters"))
    }
    
    @Test
    fun `name with question mark fails validation`() {
        val result = NameValidator.validatePlaylistName("My?Playlist")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("invalid characters"))
    }
    
    @Test
    fun `name with double quote fails validation`() {
        val result = NameValidator.validatePlaylistName("My\"Playlist")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("invalid characters"))
    }
    
    @Test
    fun `name with less than sign fails validation`() {
        val result = NameValidator.validatePlaylistName("My<Playlist")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("invalid characters"))
    }
    
    @Test
    fun `name with greater than sign fails validation`() {
        val result = NameValidator.validatePlaylistName("My>Playlist")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("invalid characters"))
    }
    
    @Test
    fun `name with pipe fails validation`() {
        val result = NameValidator.validatePlaylistName("My|Playlist")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("invalid characters"))
    }
    
    @Test
    fun `name with control characters fails validation`() {
        val result = NameValidator.validatePlaylistName("My\u0001Playlist")
        assertTrue(result is NameValidator.ValidationResult.Invalid)
        assertTrue((result as NameValidator.ValidationResult.Invalid).reason.contains("invalid characters"))
    }
    
    @Test
    fun `name with special characters like hyphen and underscore is valid`() {
        val result = NameValidator.validatePlaylistName("My-Playlist_2024")
        assertTrue(result is NameValidator.ValidationResult.Valid)
    }
    
    @Test
    fun `name with parentheses and brackets is valid`() {
        val result = NameValidator.validatePlaylistName("My Playlist (2024) [Best]")
        assertTrue(result is NameValidator.ValidationResult.Valid)
    }
    
    @Test
    fun `name with unicode characters is valid`() {
        val result = NameValidator.validatePlaylistName("我的播放列表")
        assertTrue(result is NameValidator.ValidationResult.Valid)
    }
    
    @Test
    fun `name with emoji is valid`() {
        val result = NameValidator.validatePlaylistName("My Playlist 🎵")
        assertTrue(result is NameValidator.ValidationResult.Valid)
    }
    
    @Test
    fun `sanitize removes forbidden characters`() {
        val sanitized = NameValidator.sanitizeName("My/Playlist:Test")
        assertEquals("My_Playlist_Test", sanitized)
    }
    
    @Test
    fun `sanitize truncates long names`() {
        val longName = "a".repeat(105)
        val sanitized = NameValidator.sanitizeName(longName)
        assertNotNull(sanitized)
        assertEquals(100, sanitized!!.length)
    }
    
    @Test
    fun `sanitize trims whitespace`() {
        val sanitized = NameValidator.sanitizeName("  My Playlist  ")
        assertEquals("My Playlist", sanitized)
    }
    
    @Test
    fun `sanitize returns null for empty string`() {
        val sanitized = NameValidator.sanitizeName("")
        assertNull(sanitized)
    }
    
    @Test
    fun `sanitize returns null for blank string`() {
        val sanitized = NameValidator.sanitizeName("   ")
        assertNull(sanitized)
    }
    
    @Test
    fun `sanitize handles multiple forbidden characters`() {
        val sanitized = NameValidator.sanitizeName("My<Playlist>:Test|Name")
        assertEquals("My_Playlist__Test_Name", sanitized)
    }
}
