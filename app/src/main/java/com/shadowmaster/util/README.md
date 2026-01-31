# Util Package

This package contains utility classes for the Shadow Master app.

## Logger

### Overview

`Logger` is a structured logging utility that provides consistent logging across the Shadow Master app. It features:
- **Structured Format**: `[timestamp] [level] [tag] message`
- **Log Levels**: DEBUG, INFO, WARN, ERROR
- **File Logging**: Optional file-based logging for debugging
- **CrashReporter Integration**: Automatic error reporting for critical issues
- **Performance-conscious**: DEBUG logs are no-op in release builds

### Usage Examples

#### Basic Logging

```kotlin
@Inject lateinit var logger: Logger

// Debug (no-op in release builds)
logger.d("AudioImporter", "Starting import of ${file.name}")

// Info
logger.i("AudioImporter", "Successfully imported ${file.name}")

// Warning
logger.w("AudioImporter", "File format not optimal", exception)

// Error (also reports to CrashReporter if exception provided)
logger.e("AudioImporter", "Failed to import ${file.name}", exception)
```

#### Enable File Logging

```kotlin
// Enable file logging (useful for debugging)
logger.setFileLoggingEnabled(true)

// Logs will now be written to app's files directory
logger.i("MyTag", "This will be written to file")

// Export logs to share with developers
val logFilePath = logger.exportLogsToFile("debug_session.txt")
```

#### Configure Log Level

```kotlin
// Set minimum log level (logs below this level will be ignored)
logger.setMinLogLevel(LogLevel.WARN) // Only WARN and ERROR will be logged

// Reset to default (DEBUG in debug builds, INFO in release builds)
logger.setMinLogLevel(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO)
```

#### Export and Manage Logs

```kotlin
// Get all log entries in memory
val entries = logger.getLogEntries()

// Export logs as string
val logsString = logger.exportLogs()

// Export to file
val filePath = logger.exportLogsToFile("my_logs.txt")

// Clear logs from memory (does not delete files)
logger.clearLogEntries()

// Delete all log files from disk
logger.clearLogFiles()

// Get current log file
val currentLogFile = logger.getCurrentLogFile()

// Get archived log files
val archivedFiles = logger.getArchivedLogFiles()
```

### Log Format

All logs follow this consistent format:
```
[2024-01-31 12:34:56.789] [INFO] [AudioImporter] Starting import of podcast.mp3
[2024-01-31 12:34:58.123] [ERROR] [AudioImporter] Failed to decode audio
java.io.IOException: Invalid audio format
    at com.shadowmaster.library.AudioImporter.decode(AudioImporter.kt:123)
    ...
```

### File Logging

When file logging is enabled:
- Logs are written to `app/files/logs/app_logs.txt`
- Files are automatically rotated when they exceed 5MB
- Up to 5 archived log files are kept
- Archived files are named with timestamps: `app_logs_20240131_123456.txt`

### Integration with CrashReporter

The Logger automatically integrates with CrashReporter:
- ERROR level logs with exceptions are noted in the logs
- CrashReporter handles uncaught exceptions separately
- Both systems work together to provide comprehensive debugging information

### Performance

The Logger is designed to be performant:
- **DEBUG logs**: Completely no-op in release builds (zero overhead)
- **File logging**: Asynchronous I/O on background thread
- **Memory management**: Limited to 1000 log entries in memory
- **Thread-safe**: Uses Kotlin Mutex for safe concurrent access

### Memory Management

- Maximum 1000 log entries kept in memory
- Oldest entries are automatically removed when limit is reached
- Use `clearLogEntries()` to manually free memory
- Log files are limited to 5MB each with automatic rotation
- Maximum 5 archived log files are kept

### Thread Safety

All logging operations are thread-safe:
- Uses Kotlin `Mutex` for synchronization
- File I/O runs on `Dispatchers.IO` coroutine dispatcher
- Safe to call from any thread or coroutine context

### Best Practices

1. **Use appropriate log levels**:
   - DEBUG: Verbose information for development
   - INFO: Important events and milestones
   - WARN: Recoverable errors or unexpected conditions
   - ERROR: Critical errors that may affect functionality

2. **Tag naming**: Use clear, consistent tags (usually class name)
   ```kotlin
   companion object {
       private const val TAG = "AudioImporter"
   }
   logger.i(TAG, "Import completed")
   ```

3. **Structured messages**: Include context in log messages
   ```kotlin
   logger.i(TAG, "Imported ${segments.size} segments from ${file.name} in ${duration}ms")
   ```

4. **Exception logging**: Always include throwables for warnings and errors
   ```kotlin
   try {
       importAudio()
   } catch (e: Exception) {
       logger.e(TAG, "Import failed for ${file.name}", e)
   }
   ```

5. **File logging**: Enable only when needed for debugging
   ```kotlin
   // Enable for debug builds or when troubleshooting
   if (BuildConfig.DEBUG || debugMode) {
       logger.setFileLoggingEnabled(true)
   }
   ```

---

## PerformanceTracker

### Overview

`PerformanceTracker` is a utility class for collecting performance metrics in the Shadow Master app. It tracks:
- **Audio Import**: Duration, file size, format, success/failure
- **Segmentation**: Processing time, segments detected, audio length
- **UI Rendering**: Screen render times, frame counts
- **Database Queries**: Query duration, record counts, success/failure

All metrics can be exported as JSON for analysis.

## Features

- ✅ Thread-safe metric collection using Kotlin Coroutines and Mutex
- ✅ Automatic memory management (limits to 1000 metrics)
- ✅ JSON export with summary statistics
- ✅ Enable/disable tracking on demand
- ✅ Hilt dependency injection support
- ✅ Type-safe sealed class hierarchy
- ✅ Comprehensive unit tests

## Usage Examples

### Audio Import Tracking

```kotlin
@Inject lateinit var performanceTracker: PerformanceTracker

suspend fun importAudioFile(file: File) {
    // Start tracking
    val operationId = performanceTracker.startAudioImport(
        fileName = file.name,
        fileSizeBytes = file.length()
    )
    
    try {
        // Perform import...
        val audioData = decodeAudioFile(file)
        
        // End tracking with success
        performanceTracker.endAudioImport(
            operationId = operationId,
            success = true,
            durationSeconds = audioData.durationSeconds,
            audioFormat = audioData.format
        )
    } catch (e: Exception) {
        // End tracking with error
        performanceTracker.endAudioImport(
            operationId = operationId,
            success = false,
            error = e.message
        )
    }
}
```

### Segmentation Tracking

```kotlin
suspend fun segmentAudio(audioFile: String, mode: String, audioLengthMs: Long) {
    val operationId = performanceTracker.startSegmentation(
        audioFileName = audioFile,
        segmentationMode = mode,
        audioLengthMs = audioLengthMs
    )
    
    try {
        val segments = detectSpeechSegments(audioFile)
        
        performanceTracker.endSegmentation(
            operationId = operationId,
            success = true,
            segmentsDetected = segments.size
        )
    } catch (e: Exception) {
        performanceTracker.endSegmentation(
            operationId = operationId,
            success = false,
            error = e.message
        )
    }
}
```

### UI Rendering Tracking

```kotlin
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val performanceTracker: PerformanceTracker,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    fun trackInitialRender() {
        viewModelScope.launch {
            val operationId = performanceTracker.startUIRender(
                screenName = "LibraryScreen",
                operation = "initial_load"
            )

            // Load data...
            libraryRepository.loadPlaylists()

            performanceTracker.endUIRender(operationId)
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.trackInitialRender()
    }
    
    // UI content...
}
```

### Database Query Tracking

```kotlin
// Manual tracking
suspend fun getPlaylists(): List<Playlist> {
    val operationId = performanceTracker.startDatabaseQuery("SELECT", "shadow_playlists")
    
    return try {
        val playlists = playlistDao.getAllPlaylists().first()
        performanceTracker.endDatabaseQuery(
            operationId = operationId,
            success = true,
            recordCount = playlists.size
        )
        playlists
    } catch (e: Exception) {
        performanceTracker.endDatabaseQuery(
            operationId = operationId,
            success = false,
            error = e.message
        )
        throw e
    }
}

// Automatic tracking (recommended for simple queries)
suspend fun getPlaylistsAutomatic(): List<Playlist> {
    return performanceTracker.trackDatabaseQuery("SELECT", "shadow_playlists") {
        playlistDao.getAllPlaylists().first()
    }
}
```

### Exporting Metrics

```kotlin
// Get metrics summary
val summary = performanceTracker.getSummary()
Log.d("Performance", """
    Total metrics: ${summary.totalMetrics}
    Audio imports: ${summary.audioImportCount} (${summary.audioImportSuccessRate}% success)
    Avg import duration: ${summary.audioImportAvgDurationMs}ms
    Segmentations: ${summary.segmentationCount} (${summary.segmentationSuccessRate}% success)
    Avg segmentation duration: ${summary.segmentationAvgDurationMs}ms
""".trimIndent())

// Export as JSON
val json = performanceTracker.exportAsJson()

// Save to file
val filePath = performanceTracker.saveToFile(json, "my_metrics.json")
Log.i("Performance", "Metrics saved to: $filePath")
```

### Enable/Disable Tracking

```kotlin
// Disable tracking (e.g., for production builds)
performanceTracker.setEnabled(false)

// Re-enable tracking
performanceTracker.setEnabled(true)

// Clear all collected metrics
performanceTracker.clearMetrics()

// Reset (clear + re-enable)
performanceTracker.reset()

// Release resources (call when tracker is no longer needed)
performanceTracker.release()
```

## JSON Export Format

```json
{
  "exportTime": 1706707200000,
  "sessionStartTime": 1706700000000,
  "totalMetrics": 15,
  "summary": {
    "sessionStartTime": 1706700000000,
    "totalMetrics": 15,
    "audioImports": {
      "count": 5,
      "successRate": 100.0,
      "avgDurationMs": 4523.2
    },
    "segmentations": {
      "count": 5,
      "successRate": 100.0,
      "avgDurationMs": 2314.6
    },
    "uiRenders": {
      "count": 3,
      "avgDurationMs": 456.3
    },
    "databaseQueries": {
      "count": 2,
      "successRate": 100.0,
      "avgDurationMs": 123.5
    }
  },
  "audioImports": [
    {
      "type": "audioImport",
      "operationId": "a1b2c3d4-...",
      "startTime": 1706700100000,
      "endTime": 1706700105234,
      "durationMs": 5234,
      "complete": true,
      "fileName": "podcast_episode.mp3",
      "fileSizeBytes": 12580000,
      "audioFormat": "MP3",
      "durationSeconds": 600.5,
      "success": true,
      "error": null
    }
  ],
  "segmentations": [...],
  "uiRenders": [...],
  "databaseQueries": [...]
}
```

## Memory Management

The tracker automatically limits memory usage:
- Maximum 1000 metrics in memory
- When limit is reached, oldest 100 metrics are removed
- Use `clearMetrics()` to manually free memory
- Metrics are stored in memory until exported or cleared

## Thread Safety

All metric collection operations are thread-safe:
- Uses Kotlin `Mutex` for synchronization
- Operations run on `Dispatchers.IO` coroutine dispatcher
- Safe to call from any thread or coroutine context

## Testing

Unit tests are available in `PerformanceTrackerTest.kt`:
- Data class validation
- JSON serialization
- Metric lifecycle (start/end)
- Error handling
- Summary statistics

Run tests with:
```bash
./gradlew test
```

## Best Practices

1. **Start/End Pattern**: Always pair `start*()` with `end*()` calls
2. **Error Handling**: Always catch exceptions and report errors
3. **Memory**: Clear metrics periodically in long-running sessions
4. **Production**: Consider disabling in release builds for performance
5. **Analysis**: Export metrics regularly for performance analysis

## Integration with Existing Code

The PerformanceTracker is already integrated with:
- ✅ Hilt dependency injection (ready to inject)
- ✅ Unit tests provided
- 🔄 Not yet integrated with AudioImporter (optional)
- 🔄 Not yet integrated with SegmentationPresets (optional)
- 🔄 Not yet integrated with UI screens (optional)

To integrate with existing components, inject the tracker and add start/end calls around operations you want to measure.

## Future Enhancements

Potential future improvements:
- Add visualization/charting of metrics
- Automatic upload to analytics service
- Performance alerts/thresholds
- Historical trend analysis
- Memory usage tracking
- Network request tracking
