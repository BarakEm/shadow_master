package com.shadowmaster.library

/**
 * Structured error types for audio import operations.
 * 
 * This sealed class hierarchy provides specific error types that can occur
 * during audio import, making error handling more precise and user-friendly.
 */
sealed class AudioImportError : Exception() {
    
    /**
     * File or resource was not found at the specified path or URI.
     * @param path The path or URI that was not found
     */
    data class FileNotFound(val path: String) : AudioImportError() {
        override val message: String = "File not found: $path"
    }
    
    /**
     * Audio format is not supported for import.
     * @param format The unsupported format identifier
     */
    data class UnsupportedFormat(val format: String) : AudioImportError() {
        override val message: String = "Unsupported audio format: $format"
    }
    
    /**
     * Failed to decode the audio file.
     * @param reason Description of why decoding failed
     */
    data class DecodingFailed(val reason: String) : AudioImportError() {
        override val message: String = "Audio decoding failed: $reason"
    }
    
    /**
     * Required permission was denied.
     * @param permission The permission that was denied
     */
    data class PermissionDenied(val permission: String) : AudioImportError() {
        override val message: String = "Permission denied: $permission"
    }
    
    /**
     * Network error occurred during URL-based import.
     * @param url The URL that failed
     * @param cause The underlying network exception
     */
    data class NetworkError(val url: String, val cause: Throwable? = null) : AudioImportError() {
        override val message: String = "Network error for URL $url: ${cause?.message ?: "Unknown error"}"
    }
    
    /**
     * Storage error occurred while reading or writing files.
     * @param reason Description of the storage error
     */
    data class StorageError(val reason: String) : AudioImportError() {
        override val message: String = "Storage error: $reason"
    }
    
    /**
     * Invalid input provided (e.g., malformed URI, invalid URL).
     * @param input The invalid input
     * @param reason Why the input is invalid
     */
    data class InvalidInput(val input: String, val reason: String) : AudioImportError() {
        override val message: String = "Invalid input '$input': $reason"
    }
}
