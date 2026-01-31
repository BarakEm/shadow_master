package com.shadowmaster.library

import android.net.Uri
import android.webkit.URLUtil
import java.io.File

/**
 * Validates input for audio import operations.
 * Provides comprehensive validation for URIs, URLs, file paths, and other inputs.
 */
object InputValidator {
    
    // Maximum file size: 500 MB
    const val MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024
    
    // Supported URI schemes
    private val SUPPORTED_URI_SCHEMES = setOf("content", "file", "http", "https")
    
    // Supported audio file extensions
    private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "wav", "ogg", "aac", "flac", "opus", "wma", "webm"
    )
    
    /**
     * Validates a URI for audio import.
     * @param uri The URI to validate
     * @return Result.success(uri) if valid, Result.failure(AudioImportError) if invalid
     */
    fun validateUri(uri: Uri): Result<Uri> {
        // Check if URI is valid
        if (uri.toString().isEmpty()) {
            return Result.failure(
                AudioImportError.InvalidInput(uri.toString(), "URI is empty")
            )
        }
        
        // Validate scheme
        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme !in SUPPORTED_URI_SCHEMES) {
            return Result.failure(
                AudioImportError.InvalidInput(
                    uri.toString(),
                    "Unsupported URI scheme: $scheme. Supported schemes: ${SUPPORTED_URI_SCHEMES.joinToString()}"
                )
            )
        }
        
        // For file:// URIs, check for path traversal attacks
        if (scheme == "file") {
            val path = uri.path
            if (path != null && (path.contains("..") || path.contains("~"))) {
                return Result.failure(
                    AudioImportError.InvalidInput(
                        uri.toString(),
                        "Path contains potentially dangerous characters"
                    )
                )
            }
        }
        
        return Result.success(uri)
    }
    
    /**
     * Validates a URL string for audio import.
     * @param url The URL to validate
     * @return Result.success(url) if valid, Result.failure(AudioImportError) if invalid
     */
    fun validateUrl(url: String): Result<String> {
        // Check if URL is empty
        if (url.trim().isEmpty()) {
            return Result.failure(
                AudioImportError.InvalidInput(url, "URL is empty")
            )
        }
        
        // Validate URL format
        if (!URLUtil.isValidUrl(url) && !URLUtil.isNetworkUrl(url)) {
            return Result.failure(
                AudioImportError.InvalidInput(url, "Invalid URL format")
            )
        }
        
        // Validate scheme
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Result.failure(
                AudioImportError.InvalidInput(
                    url,
                    "URL must use http:// or https:// scheme"
                )
            )
        }
        
        return Result.success(url)
    }
    
    /**
     * Validates a file path for security issues.
     * @param path The file path to validate
     * @return Result.success(path) if valid, Result.failure(AudioImportError) if invalid
     */
    fun validateFilePath(path: String): Result<String> {
        // Check for path traversal
        if (path.contains("..") || path.contains("~")) {
            return Result.failure(
                AudioImportError.InvalidInput(
                    path,
                    "Path contains potentially dangerous characters"
                )
            )
        }
        
        // Check if path is absolute (security concern if not)
        val file = File(path)
        if (!file.isAbsolute) {
            return Result.failure(
                AudioImportError.InvalidInput(
                    path,
                    "Path must be absolute"
                )
            )
        }
        
        return Result.success(path)
    }
    
    /**
     * Validates audio file extension.
     * @param fileName The file name or path
     * @return Result.success(extension) if valid, Result.failure(AudioImportError) if invalid
     */
    fun validateAudioExtension(fileName: String): Result<String> {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        
        if (extension.isEmpty()) {
            return Result.failure(
                AudioImportError.UnsupportedFormat("No file extension found")
            )
        }
        
        if (extension !in SUPPORTED_AUDIO_EXTENSIONS) {
            return Result.failure(
                AudioImportError.UnsupportedFormat(
                    "$extension (supported: ${SUPPORTED_AUDIO_EXTENSIONS.joinToString()})"
                )
            )
        }
        
        return Result.success(extension)
    }
    
    /**
     * Validates file size.
     * @param sizeBytes The file size in bytes
     * @return Result.success(sizeBytes) if valid, Result.failure(AudioImportError) if too large
     */
    fun validateFileSize(sizeBytes: Long): Result<Long> {
        if (sizeBytes <= 0) {
            return Result.failure(
                AudioImportError.InvalidInput(
                    sizeBytes.toString(),
                    "File size must be positive"
                )
            )
        }
        
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            val maxSizeMb = MAX_FILE_SIZE_BYTES / (1024 * 1024)
            val actualSizeMb = sizeBytes / (1024 * 1024)
            return Result.failure(
                AudioImportError.InvalidInput(
                    sizeBytes.toString(),
                    "File size ($actualSizeMb MB) exceeds maximum allowed size ($maxSizeMb MB)"
                )
            )
        }
        
        return Result.success(sizeBytes)
    }
    
    /**
     * Validates a playlist or segment name.
     * @param name The name to validate
     * @param maxLength Maximum allowed length (default 100)
     * @return Result.success(trimmedName) if valid, Result.failure(AudioImportError) if invalid
     */
    fun validateName(name: String, maxLength: Int = 100): Result<String> {
        val trimmed = name.trim()
        
        // Check if empty
        if (trimmed.isEmpty()) {
            return Result.failure(
                AudioImportError.InvalidInput(name, "Name cannot be empty")
            )
        }
        
        // Check length
        if (trimmed.length > maxLength) {
            return Result.failure(
                AudioImportError.InvalidInput(
                    name,
                    "Name exceeds maximum length of $maxLength characters"
                )
            )
        }
        
        // Check for invalid filesystem characters
        val invalidChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
        val foundInvalidChar = invalidChars.find { trimmed.contains(it) }
        if (foundInvalidChar != null) {
            return Result.failure(
                AudioImportError.InvalidInput(
                    name,
                    "Name contains invalid character: '$foundInvalidChar'"
                )
            )
        }
        
        return Result.success(trimmed)
    }
}
