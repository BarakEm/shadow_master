package com.shadowmaster.library

import android.content.Context
import com.shadowmaster.R
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Unit tests for ErrorMapper.
 * 
 * Tests the mapping of AudioImportError types to user-friendly messages
 * and validates error message formatting.
 */
class ErrorMapperTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var errorMapper: ErrorMapper
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        errorMapper = ErrorMapper(mockContext)
        
        // Setup mock context to return expected strings
        setupMockStrings()
    }
    
    private fun setupMockStrings() {
        // File Not Found
        `when`(mockContext.getString(R.string.error_file_not_found_title))
            .thenReturn("File Not Found")
        `when`(mockContext.getString(eq(R.string.error_file_not_found_message), anyString()))
            .thenAnswer { "The file at ${it.arguments[1]} could not be found." }
        `when`(mockContext.getString(R.string.error_file_not_found_suggestion))
            .thenReturn("Please check that the file still exists and try again.")
        
        // Unsupported Format
        `when`(mockContext.getString(R.string.error_unsupported_format_title))
            .thenReturn("Unsupported Format")
        `when`(mockContext.getString(eq(R.string.error_unsupported_format_message), anyString()))
            .thenAnswer { "The audio format ${it.arguments[1]} is not supported." }
        `when`(mockContext.getString(R.string.error_unsupported_format_suggestion))
            .thenReturn("Please use MP3, M4A, WAV, OGG, AAC, FLAC, OPUS, WMA, or WEBM files.")
        
        // Decoding Failed
        `when`(mockContext.getString(R.string.error_decoding_failed_title))
            .thenReturn("Decoding Failed")
        `when`(mockContext.getString(eq(R.string.error_decoding_failed_message), anyString()))
            .thenAnswer { "Failed to decode audio: ${it.arguments[1]}" }
        `when`(mockContext.getString(R.string.error_decoding_failed_suggestion))
            .thenReturn("The audio file may be corrupted. Try a different file or re-download it.")
        
        // Permission Denied
        `when`(mockContext.getString(R.string.error_permission_denied_title))
            .thenReturn("Permission Required")
        `when`(mockContext.getString(eq(R.string.error_permission_denied_message), anyString()))
            .thenAnswer { "Permission denied: ${it.arguments[1]}" }
        `when`(mockContext.getString(R.string.error_permission_denied_suggestion))
            .thenReturn("Please grant the required permission in Settings to continue.")
        
        // Network Error
        `when`(mockContext.getString(R.string.error_network_title))
            .thenReturn("Network Error")
        `when`(mockContext.getString(eq(R.string.error_network_message), anyString()))
            .thenAnswer { "Failed to download from ${it.arguments[1]}" }
        `when`(mockContext.getString(R.string.error_network_suggestion))
            .thenReturn("Please check your internet connection and try again.")
        
        // Storage Error
        `when`(mockContext.getString(R.string.error_storage_title))
            .thenReturn("Storage Error")
        `when`(mockContext.getString(eq(R.string.error_storage_message), anyString()))
            .thenAnswer { "Storage operation failed: ${it.arguments[1]}" }
        `when`(mockContext.getString(R.string.error_storage_suggestion))
            .thenReturn("Please check that you have enough storage space and try again.")
        
        // Invalid Input
        `when`(mockContext.getString(R.string.error_invalid_input_title))
            .thenReturn("Invalid Input")
        `when`(mockContext.getString(eq(R.string.error_invalid_input_message), anyString()))
            .thenAnswer { it.arguments[1] as String }
        `when`(mockContext.getString(R.string.error_invalid_input_suggestion))
            .thenReturn("Please correct the input and try again.")
        
        // Unknown Error
        `when`(mockContext.getString(R.string.error_unknown_title))
            .thenReturn("Unexpected Error")
        `when`(mockContext.getString(R.string.error_unknown_message))
            .thenReturn("An unexpected error occurred.")
        `when`(mockContext.getString(R.string.error_unknown_suggestion))
            .thenReturn("Please try again or contact support if the problem persists.")
    }
    
    @Test
    fun `mapToUserMessage maps FileNotFound error correctly`() {
        val path = "/storage/audio/test.mp3"
        val error = AudioImportError.FileNotFound(path)
        
        val result = errorMapper.mapToUserMessage(error)
        
        assertEquals("File Not Found", result.title)
        assertEquals("The file at $path could not be found.", result.message)
        assertEquals("Please check that the file still exists and try again.", result.suggestion)
        assertEquals("ERR_FILE_001", result.errorCode)
    }
    
    @Test
    fun `mapToUserMessage maps UnsupportedFormat error correctly`() {
        val format = "AVI"
        val error = AudioImportError.UnsupportedFormat(format)
        
        val result = errorMapper.mapToUserMessage(error)
        
        assertEquals("Unsupported Format", result.title)
        assertEquals("The audio format $format is not supported.", result.message)
        assertEquals("Please use MP3, M4A, WAV, OGG, AAC, FLAC, OPUS, WMA, or WEBM files.", result.suggestion)
        assertEquals("ERR_FORMAT_001", result.errorCode)
    }
    
    @Test
    fun `mapToUserMessage maps DecodingFailed error correctly`() {
        val reason = "Invalid header"
        val error = AudioImportError.DecodingFailed(reason)
        
        val result = errorMapper.mapToUserMessage(error)
        
        assertEquals("Decoding Failed", result.title)
        assertEquals("Failed to decode audio: $reason", result.message)
        assertEquals("The audio file may be corrupted. Try a different file or re-download it.", result.suggestion)
        assertEquals("ERR_DECODE_001", result.errorCode)
    }
    
    @Test
    fun `mapToUserMessage maps PermissionDenied error correctly`() {
        val permission = "READ_EXTERNAL_STORAGE"
        val error = AudioImportError.PermissionDenied(permission)
        
        val result = errorMapper.mapToUserMessage(error)
        
        assertEquals("Permission Required", result.title)
        assertEquals("Permission denied: $permission", result.message)
        assertEquals("Please grant the required permission in Settings to continue.", result.suggestion)
        assertEquals("ERR_PERM_001", result.errorCode)
    }
    
    @Test
    fun `mapToUserMessage maps NetworkError error correctly`() {
        val url = "https://example.com/audio.mp3"
        val cause = RuntimeException("Connection timeout")
        val error = AudioImportError.NetworkError(url, cause)
        
        val result = errorMapper.mapToUserMessage(error)
        
        assertEquals("Network Error", result.title)
        assertEquals("Failed to download from $url", result.message)
        assertEquals("Please check your internet connection and try again.", result.suggestion)
        assertEquals("ERR_NET_001", result.errorCode)
    }
    
    @Test
    fun `mapToUserMessage maps StorageError error correctly`() {
        val reason = "Disk full"
        val error = AudioImportError.StorageError(reason)
        
        val result = errorMapper.mapToUserMessage(error)
        
        assertEquals("Storage Error", result.title)
        assertEquals("Storage operation failed: $reason", result.message)
        assertEquals("Please check that you have enough storage space and try again.", result.suggestion)
        assertEquals("ERR_STORAGE_001", result.errorCode)
    }
    
    @Test
    fun `mapToUserMessage maps InvalidInput error correctly`() {
        val input = "invalid://url"
        val reason = "Malformed URI"
        val error = AudioImportError.InvalidInput(input, reason)
        
        val result = errorMapper.mapToUserMessage(error)
        
        assertEquals("Invalid Input", result.title)
        assertEquals(reason, result.message)
        assertEquals("Please correct the input and try again.", result.suggestion)
        assertEquals("ERR_INPUT_001", result.errorCode)
    }
    
    @Test
    fun `mapToUserMessage handles generic Throwable with unknown error`() {
        val throwable = RuntimeException("Something went wrong")
        
        val result = errorMapper.mapToUserMessage(throwable)
        
        assertEquals("Unexpected Error", result.title)
        assertEquals("Something went wrong", result.message)
        assertEquals("Please try again or contact support if the problem persists.", result.suggestion)
        assertEquals("ERR_UNKNOWN_001", result.errorCode)
    }
    
    @Test
    fun `mapToUserMessage handles Throwable without message`() {
        val throwable = RuntimeException()
        
        val result = errorMapper.mapToUserMessage(throwable)
        
        assertEquals("Unexpected Error", result.title)
        assertEquals("An unexpected error occurred.", result.message)
        assertEquals("Please try again or contact support if the problem persists.", result.suggestion)
        assertEquals("ERR_UNKNOWN_001", result.errorCode)
    }
    
    @Test
    fun `ErrorMessage format includes all components`() {
        val errorMessage = ErrorMessage(
            title = "Test Title",
            message = "Test message",
            suggestion = "Test suggestion",
            errorCode = "TEST_001"
        )
        
        val formatted = errorMessage.format()
        
        assertTrue(formatted.contains("Test Title"))
        assertTrue(formatted.contains("Test message"))
        assertTrue(formatted.contains("Test suggestion"))
        assertTrue(formatted.contains("Error code: TEST_001"))
    }
    
    @Test
    fun `ErrorMessage formatWithoutCode excludes error code`() {
        val errorMessage = ErrorMessage(
            title = "Test Title",
            message = "Test message",
            suggestion = "Test suggestion",
            errorCode = "TEST_001"
        )
        
        val formatted = errorMessage.formatWithoutCode()
        
        assertTrue(formatted.contains("Test Title"))
        assertTrue(formatted.contains("Test message"))
        assertTrue(formatted.contains("Test suggestion"))
        assertFalse(formatted.contains("Error code"))
        assertFalse(formatted.contains("TEST_001"))
    }
    
    @Test
    fun `error codes are unique for each error type`() {
        val errorCodes = mutableSetOf<String>()
        
        // Test all AudioImportError types
        errorCodes.add(errorMapper.mapToUserMessage(AudioImportError.FileNotFound("/test")).errorCode)
        errorCodes.add(errorMapper.mapToUserMessage(AudioImportError.UnsupportedFormat("AVI")).errorCode)
        errorCodes.add(errorMapper.mapToUserMessage(AudioImportError.DecodingFailed("test")).errorCode)
        errorCodes.add(errorMapper.mapToUserMessage(AudioImportError.PermissionDenied("READ")).errorCode)
        errorCodes.add(errorMapper.mapToUserMessage(AudioImportError.NetworkError("http://test")).errorCode)
        errorCodes.add(errorMapper.mapToUserMessage(AudioImportError.StorageError("test")).errorCode)
        errorCodes.add(errorMapper.mapToUserMessage(AudioImportError.InvalidInput("test", "test")).errorCode)
        
        // All error codes should be unique
        assertEquals(7, errorCodes.size)
    }
    
    @Test
    fun `all error codes follow naming convention`() {
        val errors = listOf(
            errorMapper.mapToUserMessage(AudioImportError.FileNotFound("/test")),
            errorMapper.mapToUserMessage(AudioImportError.UnsupportedFormat("AVI")),
            errorMapper.mapToUserMessage(AudioImportError.DecodingFailed("test")),
            errorMapper.mapToUserMessage(AudioImportError.PermissionDenied("READ")),
            errorMapper.mapToUserMessage(AudioImportError.NetworkError("http://test")),
            errorMapper.mapToUserMessage(AudioImportError.StorageError("test")),
            errorMapper.mapToUserMessage(AudioImportError.InvalidInput("test", "test"))
        )
        
        // All error codes should start with "ERR_" and end with "_001"
        errors.forEach { errorMessage ->
            assertTrue("Error code should match pattern ERR_*_001", 
                errorMessage.errorCode.matches(Regex("ERR_[A-Z]+_\\d{3}")))
        }
    }
}
