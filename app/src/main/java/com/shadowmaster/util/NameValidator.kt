package com.shadowmaster.util

/**
 * Utility for validating playlist and segment names.
 * Ensures names are safe for file systems and meet length constraints.
 */
object NameValidator {
    
    /**
     * Maximum allowed length for names
     */
    const val MAX_NAME_LENGTH = 100
    
    /**
     * Characters that are forbidden in file names on various file systems:
     * - Windows: < > : " / \ | ? *
     * - Unix: /
     * - Also disallowing control characters (0x00-0x1F)
     */
    private val FORBIDDEN_CHARS_REGEX = Regex("[<>:\"/\\\\|?*\\x00-\\x1F]")
    
    /**
     * Result of name validation.
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
    
    /**
     * Validates a playlist name.
     * 
     * @param name The name to validate
     * @return ValidationResult indicating if the name is valid or why it's invalid
     */
    fun validatePlaylistName(name: String): ValidationResult {
        return validateName(name, "Playlist name")
    }
    
    /**
     * Validates a segment name.
     * 
     * @param name The name to validate
     * @return ValidationResult indicating if the name is valid or why it's invalid
     */
    fun validateSegmentName(name: String): ValidationResult {
        return validateName(name, "Segment name")
    }
    
    /**
     * Internal validation logic shared by both playlist and segment names.
     */
    private fun validateName(name: String, nameType: String): ValidationResult {
        // Trim the name
        val trimmed = name.trim()
        
        // Check if empty or blank
        if (trimmed.isEmpty() || trimmed.isBlank()) {
            return ValidationResult.Invalid("$nameType cannot be empty")
        }
        
        // Check length
        if (trimmed.length > MAX_NAME_LENGTH) {
            return ValidationResult.Invalid("$nameType must be $MAX_NAME_LENGTH characters or less")
        }
        
        // Check for forbidden characters
        if (FORBIDDEN_CHARS_REGEX.containsMatchIn(trimmed)) {
            return ValidationResult.Invalid("$nameType contains invalid characters (< > : \" / \\ | ? * or control characters)")
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Sanitizes a name by removing forbidden characters and trimming/truncating as needed.
     * This can be used to automatically fix invalid names.
     * 
     * @param name The name to sanitize
     * @return A sanitized version of the name, or null if the name cannot be salvaged
     */
    fun sanitizeName(name: String): String? {
        val trimmed = name.trim()
        
        if (trimmed.isEmpty()) {
            return null
        }
        
        // Replace forbidden characters with underscores
        val sanitized = FORBIDDEN_CHARS_REGEX.replace(trimmed, "_")
        
        // Truncate if too long
        val truncated = if (sanitized.length > MAX_NAME_LENGTH) {
            sanitized.substring(0, MAX_NAME_LENGTH)
        } else {
            sanitized
        }
        
        return truncated.takeIf { it.isNotBlank() }
    }
}
