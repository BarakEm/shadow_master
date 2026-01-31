package com.shadowmaster.util

import android.content.Context
import android.util.Log
import com.shadowmaster.BuildConfig
import com.shadowmaster.crash.CrashReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Log levels for structured logging.
 */
enum class LogLevel(val priority: Int) {
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR);
    
    val label: String get() = name
}

/**
 * Structured logging utility for Shadow Master.
 * 
 * Features:
 * - Consistent log format: [timestamp] [level] [tag] message
 * - Log levels: DEBUG, INFO, WARN, ERROR
 * - Optional file logging for debugging
 * - Integration with CrashReporter
 * - Performance-conscious (no-op in release builds for DEBUG level)
 * 
 * Usage:
 * ```
 * @Inject lateinit var logger: Logger
 * 
 * logger.d("MyTag", "Debug message")
 * logger.i("MyTag", "Info message")
 * logger.w("MyTag", "Warning message", throwable)
 * logger.e("MyTag", "Error message", throwable)
 * 
 * // Enable file logging
 * logger.setFileLoggingEnabled(true)
 * 
 * // Export logs
 * val logFile = logger.exportLogs()
 * ```
 */
@Singleton
class Logger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Logger"
        private const val LOG_FILE_NAME = "app_logs.txt"
        private const val MAX_LOG_FILES = 5
        private const val MAX_LOG_ENTRIES_IN_MEMORY = 1000
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
    }

    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)
    private val logEntries = mutableListOf<String>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Volatile
    private var fileLoggingEnabled = false
    
    @Volatile
    private var minLogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO

    /**
     * Enable or disable file logging.
     * When enabled, logs are written to app's files directory.
     */
    fun setFileLoggingEnabled(enabled: Boolean) {
        fileLoggingEnabled = enabled
        Log.d(TAG, "File logging ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Set minimum log level to be logged.
     * Logs below this level will be ignored.
     */
    fun setMinLogLevel(level: LogLevel) {
        minLogLevel = level
        Log.d(TAG, "Minimum log level set to $level")
    }

    /**
     * Log a debug message.
     * No-op in release builds for performance.
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            log(LogLevel.DEBUG, tag, message, throwable)
        }
    }

    /**
     * Log an info message.
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, tag, message, throwable)
    }

    /**
     * Log a warning message.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    /**
     * Log an error message.
     * Errors are also sent to CrashReporter if a throwable is provided.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
        
        // Report to CrashReporter if throwable is provided
        if (throwable != null) {
            reportToCrashReporter(tag, message, throwable)
        }
    }

    /**
     * Core logging function.
     */
    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        // Check minimum log level
        if (level.priority < minLogLevel.priority) {
            return
        }
        
        val timestamp = dateFormat.format(Date())
        val formattedMessage = formatLogMessage(timestamp, level, tag, message)
        
        // Log to Android logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
        
        // Always store in memory, optionally write to file
        scope.launch {
            storeLogEntry(formattedMessage, throwable, fileLoggingEnabled)
        }
    }

    /**
     * Format a log message with timestamp, level, and tag.
     */
    private fun formatLogMessage(timestamp: String, level: LogLevel, tag: String, message: String): String {
        return "[$timestamp] [${level.label}] [$tag] $message"
    }

    /**
     * Store log entry in memory and optionally write to file.
     */
    private suspend fun storeLogEntry(formattedMessage: String, throwable: Throwable?, writeToFile: Boolean) {
        mutex.withLock {
            // Build complete log entry
            val logEntry = buildString {
                append(formattedMessage)
                if (throwable != null) {
                    append("\n")
                    append(Log.getStackTraceString(throwable))
                }
            }
            
            // Always add to memory
            logEntries.add(logEntry)
            
            // Limit memory usage
            if (logEntries.size > MAX_LOG_ENTRIES_IN_MEMORY) {
                logEntries.removeAt(0)
            }
            
            // Conditionally write to file
            if (writeToFile) {
                writeToFile(logEntry)
            }
        }
    }

    /**
     * Write log entry to file.
     */
    private fun writeToFile(logEntry: String) {
        try {
            val logDir = File(context.filesDir, "logs")
            logDir.mkdirs()
            
            val logFile = File(logDir, LOG_FILE_NAME)
            
            // Create new file if it doesn't exist or is too large (> 5MB)
            if (!logFile.exists() || logFile.length() > 5 * 1024 * 1024) {
                rotateLogFiles(logDir)
            }
            
            // Append to file
            FileWriter(logFile, true).use { writer ->
                writer.write(logEntry)
                writer.write("\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    /**
     * Rotate log files to prevent unlimited growth.
     */
    private fun rotateLogFiles(logDir: File) {
        try {
            val currentLog = File(logDir, LOG_FILE_NAME)
            
            if (currentLog.exists()) {
                // Rename current log with timestamp
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val archivedLog = File(logDir, "app_logs_$timestamp.txt")
                currentLog.renameTo(archivedLog)
            }
            
            // Clean up old log files
            cleanupOldLogFiles(logDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate log files", e)
        }
    }

    /**
     * Clean up old log files, keeping only the most recent ones.
     */
    private fun cleanupOldLogFiles(logDir: File) {
        val files = logDir.listFiles { file -> 
            file.name.startsWith("app_logs_") && file.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: return
        
        // Keep only the latest MAX_LOG_FILES archived logs
        files.drop(MAX_LOG_FILES).forEach { it.delete() }
    }

    /**
     * Report error to CrashReporter.
     */
    private fun reportToCrashReporter(tag: String, message: String, throwable: Throwable) {
        // CrashReporter handles uncaught exceptions, but we can still log it
        Log.e(TAG, "Error reported from $tag: $message", throwable)
        // Note: CrashReporter.initialize() should be called in Application.onCreate()
        // The actual crash reporting happens automatically via UncaughtExceptionHandler
    }

    /**
     * Export all log entries as a single string.
     * @return All log entries joined with newlines
     */
    suspend fun exportLogs(): String {
        return mutex.withLock {
            logEntries.joinToString("\n")
        }
    }

    /**
     * Export logs to a file and return the file path.
     * @return File path where logs were saved
     */
    suspend fun exportLogsToFile(fileName: String = "exported_logs.txt"): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fullFileName = "logs_${timestamp}_$fileName"
        val file = File(context.cacheDir, fullFileName)
        
        val logs = exportLogs()
        FileWriter(file).use { writer ->
            writer.write(logs)
        }
        
        Log.i(TAG, "Logs exported to: ${file.absolutePath}")
        return file.absolutePath
    }

    /**
     * Get all log entries.
     */
    suspend fun getLogEntries(): List<String> {
        return mutex.withLock {
            logEntries.toList()
        }
    }

    /**
     * Clear all log entries from memory.
     * Does not delete log files.
     */
    suspend fun clearLogEntries() {
        mutex.withLock {
            logEntries.clear()
            Log.d(TAG, "Log entries cleared from memory")
        }
    }

    /**
     * Clear all log files from disk.
     */
    fun clearLogFiles() {
        val logDir = File(context.filesDir, "logs")
        if (logDir.exists()) {
            logDir.deleteRecursively()
            Log.d(TAG, "All log files deleted")
        }
    }

    /**
     * Get the current log file if it exists.
     */
    fun getCurrentLogFile(): File? {
        val logFile = File(context.filesDir, "logs/$LOG_FILE_NAME")
        return if (logFile.exists()) logFile else null
    }

    /**
     * Get all archived log files.
     */
    fun getArchivedLogFiles(): List<File> {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) return emptyList()
        
        return logDir.listFiles { file ->
            file.name.startsWith("app_logs_") && file.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Release resources used by the Logger.
     * Call this when the logger is no longer needed (typically on app shutdown).
     * 
     * Note: As a singleton, this is rarely needed since the logger lives for the app lifetime.
     */
    fun release() {
        scope.cancel()
        Log.d(TAG, "Logger resources released")
    }
}
