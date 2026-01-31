package com.shadowmaster.library

import android.content.Context
import com.shadowmaster.R

/**
 * Maps technical errors to user-friendly messages.
 * Provides localized, actionable error messages for display in the UI.
 */
class ErrorMapper(private val context: Context) {
    
    /**
     * Maps an AudioImportError to a user-friendly message.
     * @param error The error to map
     * @return A user-friendly error message with optional action suggestion
     */
    fun mapToUserMessage(error: AudioImportError): ErrorMessage {
        return when (error) {
            is AudioImportError.FileNotFound -> ErrorMessage(
                title = context.getString(R.string.error_file_not_found_title),
                message = context.getString(R.string.error_file_not_found_message, error.path),
                suggestion = context.getString(R.string.error_file_not_found_suggestion),
                errorCode = "ERR_FILE_001"
            )
            
            is AudioImportError.UnsupportedFormat -> ErrorMessage(
                title = context.getString(R.string.error_unsupported_format_title),
                message = context.getString(R.string.error_unsupported_format_message, error.format),
                suggestion = context.getString(R.string.error_unsupported_format_suggestion),
                errorCode = "ERR_FORMAT_001"
            )
            
            is AudioImportError.DecodingFailed -> ErrorMessage(
                title = context.getString(R.string.error_decoding_failed_title),
                message = context.getString(R.string.error_decoding_failed_message, error.reason),
                suggestion = context.getString(R.string.error_decoding_failed_suggestion),
                errorCode = "ERR_DECODE_001"
            )
            
            is AudioImportError.PermissionDenied -> ErrorMessage(
                title = context.getString(R.string.error_permission_denied_title),
                message = context.getString(R.string.error_permission_denied_message, error.permission),
                suggestion = context.getString(R.string.error_permission_denied_suggestion),
                errorCode = "ERR_PERM_001"
            )
            
            is AudioImportError.NetworkError -> ErrorMessage(
                title = context.getString(R.string.error_network_title),
                message = context.getString(R.string.error_network_message, error.url),
                suggestion = context.getString(R.string.error_network_suggestion),
                errorCode = "ERR_NET_001"
            )
            
            is AudioImportError.StorageError -> ErrorMessage(
                title = context.getString(R.string.error_storage_title),
                message = context.getString(R.string.error_storage_message, error.reason),
                suggestion = context.getString(R.string.error_storage_suggestion),
                errorCode = "ERR_STORAGE_001"
            )
            
            is AudioImportError.InvalidInput -> ErrorMessage(
                title = context.getString(R.string.error_invalid_input_title),
                message = context.getString(R.string.error_invalid_input_message, error.reason),
                suggestion = context.getString(R.string.error_invalid_input_suggestion),
                errorCode = "ERR_INPUT_001"
            )
        }
    }
    
    /**
     * Maps a generic Throwable to a user-friendly message.
     * Falls back to generic error message for unknown error types.
     */
    fun mapToUserMessage(error: Throwable): ErrorMessage {
        return when (error) {
            is AudioImportError -> mapToUserMessage(error)
            else -> ErrorMessage(
                title = context.getString(R.string.error_unknown_title),
                message = error.message ?: context.getString(R.string.error_unknown_message),
                suggestion = context.getString(R.string.error_unknown_suggestion),
                errorCode = "ERR_UNKNOWN_001"
            )
        }
    }
}

/**
 * Represents a user-friendly error message with all relevant information.
 */
data class ErrorMessage(
    val title: String,
    val message: String,
    val suggestion: String,
    val errorCode: String
) {
    /**
     * Formats the complete error message for display.
     */
    fun format(): String = buildString {
        append(title)
        append("\n\n")
        append(message)
        append("\n\n")
        append(suggestion)
        append("\n\n")
        append("Error code: $errorCode")
    }
    
    /**
     * Formats the error message without the error code.
     */
    fun formatWithoutCode(): String = buildString {
        append(title)
        append("\n\n")
        append(message)
        append("\n\n")
        append(suggestion)
    }
}
