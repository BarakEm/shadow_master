package com.shadowmaster.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance metric data classes
 */
sealed class PerformanceMetric {
    abstract val operationId: String
    abstract val startTime: Long
    abstract val endTime: Long?
    abstract val durationMs: Long?
    
    val isComplete: Boolean get() = endTime != null
}

data class AudioImportMetric(
    override val operationId: String,
    override val startTime: Long,
    override val endTime: Long? = null,
    val fileName: String,
    val fileSizeBytes: Long,
    val audioFormat: String? = null,
    val durationSeconds: Double? = null,
    val success: Boolean = false,
    val error: String? = null
) : PerformanceMetric() {
    override val durationMs: Long? get() = endTime?.let { it - startTime }
}

data class SegmentationMetric(
    override val operationId: String,
    override val startTime: Long,
    override val endTime: Long? = null,
    val audioFileName: String,
    val segmentationMode: String,
    val segmentsDetected: Int = 0,
    val audioLengthMs: Long,
    val success: Boolean = false,
    val error: String? = null
) : PerformanceMetric() {
    override val durationMs: Long? get() = endTime?.let { it - startTime }
}

data class UIRenderMetric(
    override val operationId: String,
    override val startTime: Long,
    override val endTime: Long? = null,
    val screenName: String,
    val operation: String,
    val frameCount: Int = 0
) : PerformanceMetric() {
    override val durationMs: Long? get() = endTime?.let { it - startTime }
}

data class DatabaseQueryMetric(
    override val operationId: String,
    override val startTime: Long,
    override val endTime: Long? = null,
    val queryType: String,
    val tableName: String,
    val recordCount: Int = 0,
    val success: Boolean = false,
    val error: String? = null
) : PerformanceMetric() {
    override val durationMs: Long? get() = endTime?.let { it - startTime }
}

/**
 * Collects and exports performance metrics for analysis.
 * Tracks audio import, segmentation, UI rendering, and database operations.
 * 
 * Usage:
 * ```
 * val id = performanceTracker.startAudioImport(fileName, fileSize)
 * // ... perform import ...
 * performanceTracker.endAudioImport(id, success, duration, format)
 * 
 * // Export metrics
 * val json = performanceTracker.exportAsJson()
 * performanceTracker.saveToFile(json, "metrics.json")
 * ```
 */
@Singleton
class PerformanceTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PerformanceTracker"
        private const val MAX_METRICS_IN_MEMORY = 1000
    }

    private val metrics = mutableListOf<PerformanceMetric>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _metricsCount = MutableStateFlow(0)
    val metricsCount: StateFlow<Int> = _metricsCount.asStateFlow()
    
    @Volatile private var enabled: Boolean = true
    @Volatile private var sessionStartTime: Long = System.currentTimeMillis()

    /**
     * Enable or disable metrics collection.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        Log.d(TAG, "Performance tracking ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Start tracking an audio import operation.
     * @return Operation ID to use when ending the operation
     */
    suspend fun startAudioImport(fileName: String, fileSizeBytes: Long): String {
        if (!enabled) return ""
        
        val operationId = UUID.randomUUID().toString()
        val metric = AudioImportMetric(
            operationId = operationId,
            startTime = System.currentTimeMillis(),
            fileName = fileName,
            fileSizeBytes = fileSizeBytes
        )
        
        addMetric(metric)
        
        return operationId
    }

    /**
     * End tracking an audio import operation.
     */
    suspend fun endAudioImport(
        operationId: String,
        success: Boolean,
        durationSeconds: Double? = null,
        audioFormat: String? = null,
        error: String? = null
    ) {
        if (!enabled || operationId.isEmpty()) return
        
        mutex.withLock {
            val index = metrics.indexOfFirst { 
                it is AudioImportMetric && it.operationId == operationId 
            }
            
            if (index != -1) {
                val existing = metrics[index] as AudioImportMetric
                metrics[index] = existing.copy(
                    endTime = System.currentTimeMillis(),
                    success = success,
                    durationSeconds = durationSeconds,
                    audioFormat = audioFormat,
                    error = error
                )
            }
        }
    }

    /**
     * Start tracking a segmentation operation.
     * @return Operation ID to use when ending the operation
     */
    suspend fun startSegmentation(
        audioFileName: String,
        segmentationMode: String,
        audioLengthMs: Long
    ): String {
        if (!enabled) return ""
        
        val operationId = UUID.randomUUID().toString()
        val metric = SegmentationMetric(
            operationId = operationId,
            startTime = System.currentTimeMillis(),
            audioFileName = audioFileName,
            segmentationMode = segmentationMode,
            audioLengthMs = audioLengthMs
        )
        
        addMetric(metric)
        
        return operationId
    }

    /**
     * End tracking a segmentation operation.
     */
    suspend fun endSegmentation(
        operationId: String,
        success: Boolean,
        segmentsDetected: Int = 0,
        error: String? = null
    ) {
        if (!enabled || operationId.isEmpty()) return
        
        mutex.withLock {
            val index = metrics.indexOfFirst { 
                it is SegmentationMetric && it.operationId == operationId 
            }
            
            if (index != -1) {
                val existing = metrics[index] as SegmentationMetric
                metrics[index] = existing.copy(
                    endTime = System.currentTimeMillis(),
                    success = success,
                    segmentsDetected = segmentsDetected,
                    error = error
                )
            }
        }
    }

    /**
     * Start tracking a UI render operation.
     * @return Operation ID to use when ending the operation
     */
    suspend fun startUIRender(screenName: String, operation: String): String {
        if (!enabled) return ""
        
        val operationId = UUID.randomUUID().toString()
        val metric = UIRenderMetric(
            operationId = operationId,
            startTime = System.currentTimeMillis(),
            screenName = screenName,
            operation = operation
        )
        
        addMetric(metric)
        
        return operationId
    }

    /**
     * End tracking a UI render operation.
     */
    suspend fun endUIRender(operationId: String, frameCount: Int = 0) {
        if (!enabled || operationId.isEmpty()) return
        
        mutex.withLock {
            val index = metrics.indexOfFirst { 
                it is UIRenderMetric && it.operationId == operationId 
            }
            
            if (index != -1) {
                val existing = metrics[index] as UIRenderMetric
                metrics[index] = existing.copy(
                    endTime = System.currentTimeMillis(),
                    frameCount = frameCount
                )
            }
        }
    }

    /**
     * Start tracking a database query operation.
     * @return Operation ID to use when ending the operation
     */
    suspend fun startDatabaseQuery(queryType: String, tableName: String): String {
        if (!enabled) return ""
        
        val operationId = UUID.randomUUID().toString()
        val metric = DatabaseQueryMetric(
            operationId = operationId,
            startTime = System.currentTimeMillis(),
            queryType = queryType,
            tableName = tableName
        )
        
        addMetric(metric)
        
        return operationId
    }

    /**
     * End tracking a database query operation.
     */
    suspend fun endDatabaseQuery(
        operationId: String,
        success: Boolean,
        recordCount: Int = 0,
        error: String? = null
    ) {
        if (!enabled || operationId.isEmpty()) return
        
        mutex.withLock {
            val index = metrics.indexOfFirst { 
                it is DatabaseQueryMetric && it.operationId == operationId 
            }
            
            if (index != -1) {
                val existing = metrics[index] as DatabaseQueryMetric
                metrics[index] = existing.copy(
                    endTime = System.currentTimeMillis(),
                    success = success,
                    recordCount = recordCount,
                    error = error
                )
            }
        }
    }

    /**
     * Track a database query with automatic timing (use for quick queries).
     * Automatically extracts record count if result is a Collection.
     * 
     * @param queryType Type of query (e.g., "SELECT", "INSERT", "UPDATE")
     * @param tableName Name of the table being queried
     * @param block The query operation to track
     * @return The result of the query operation
     */
    suspend fun <T> trackDatabaseQuery(
        queryType: String,
        tableName: String,
        block: suspend () -> T
    ): T {
        val id = startDatabaseQuery(queryType, tableName)
        return try {
            val result = block()
            
            // Automatically extract record count if result is a Collection
            val recordCount = when (result) {
                is Collection<*> -> result.size
                is Array<*> -> result.size
                else -> 0
            }
            
            endDatabaseQuery(id, success = true, recordCount = recordCount)
            result
        } catch (e: Exception) {
            endDatabaseQuery(id, success = false, error = e.message)
            throw e
        }
    }

    /**
     * Add a metric to the collection.
     */
    private suspend fun addMetric(metric: PerformanceMetric) {
        mutex.withLock {
            // Limit memory usage
            if (metrics.size >= MAX_METRICS_IN_MEMORY) {
                // Remove oldest 100 metrics
                metrics.subList(0, 100).clear()
                Log.w(TAG, "Removed oldest 100 metrics to stay under limit")
            }
            
            metrics.add(metric)
            _metricsCount.value = metrics.size
        }
    }

    /**
     * Get all collected metrics.
     */
    suspend fun getMetrics(): List<PerformanceMetric> {
        return mutex.withLock {
            metrics.toList()
        }
    }

    /**
     * Get metrics filtered by type.
     */
    suspend fun getMetricsByType(type: Class<out PerformanceMetric>): List<PerformanceMetric> {
        return mutex.withLock {
            metrics.filter { type.isInstance(it) }
        }
    }

    /**
     * Get summary statistics for all metrics.
     */
    suspend fun getSummary(): PerformanceSummary {
        return mutex.withLock {
            val audioImports = metrics.filterIsInstance<AudioImportMetric>()
            val segmentations = metrics.filterIsInstance<SegmentationMetric>()
            val uiRenders = metrics.filterIsInstance<UIRenderMetric>()
            val dbQueries = metrics.filterIsInstance<DatabaseQueryMetric>()
            
            PerformanceSummary(
                sessionStartTime = sessionStartTime,
                totalMetrics = metrics.size,
                audioImportCount = audioImports.size,
                audioImportSuccessRate = calculateSuccessRate(audioImports.map { it.success }),
                audioImportAvgDurationMs = calculateAvgDuration(audioImports),
                segmentationCount = segmentations.size,
                segmentationSuccessRate = calculateSuccessRate(segmentations.map { it.success }),
                segmentationAvgDurationMs = calculateAvgDuration(segmentations),
                uiRenderCount = uiRenders.size,
                uiRenderAvgDurationMs = calculateAvgDuration(uiRenders),
                dbQueryCount = dbQueries.size,
                dbQuerySuccessRate = calculateSuccessRate(dbQueries.map { it.success }),
                dbQueryAvgDurationMs = calculateAvgDuration(dbQueries)
            )
        }
    }

    private fun calculateSuccessRate(successes: List<Boolean>): Double {
        if (successes.isEmpty()) return 0.0
        return successes.count { it }.toDouble() / successes.size * 100.0
    }

    private fun calculateAvgDuration(metrics: List<PerformanceMetric>): Double {
        val durations = metrics.mapNotNull { it.durationMs }
        if (durations.isEmpty()) return 0.0
        return durations.average()
    }

    /**
     * Export all metrics as JSON.
     */
    suspend fun exportAsJson(): String {
        return mutex.withLock {
            val json = JSONObject()
            json.put("exportTime", System.currentTimeMillis())
            json.put("sessionStartTime", sessionStartTime)
            json.put("totalMetrics", metrics.size)
            
            // Calculate summary inline to avoid deadlock (getSummary() also acquires mutex)
            val audioImports = metrics.filterIsInstance<AudioImportMetric>()
            val segmentations = metrics.filterIsInstance<SegmentationMetric>()
            val uiRenders = metrics.filterIsInstance<UIRenderMetric>()
            val dbQueries = metrics.filterIsInstance<DatabaseQueryMetric>()
            
            val summary = PerformanceSummary(
                sessionStartTime = sessionStartTime,
                totalMetrics = metrics.size,
                audioImportCount = audioImports.size,
                audioImportSuccessRate = calculateSuccessRate(audioImports.map { it.success }),
                audioImportAvgDurationMs = calculateAvgDuration(audioImports),
                segmentationCount = segmentations.size,
                segmentationSuccessRate = calculateSuccessRate(segmentations.map { it.success }),
                segmentationAvgDurationMs = calculateAvgDuration(segmentations),
                uiRenderCount = uiRenders.size,
                uiRenderAvgDurationMs = calculateAvgDuration(uiRenders),
                dbQueryCount = dbQueries.size,
                dbQuerySuccessRate = calculateSuccessRate(dbQueries.map { it.success }),
                dbQueryAvgDurationMs = calculateAvgDuration(dbQueries)
            )
            json.put("summary", summary.toJson())
            
            // Detailed metrics by type
            json.put("audioImports", metricsToJsonArray(audioImports))
            json.put("segmentations", metricsToJsonArray(segmentations))
            json.put("uiRenders", metricsToJsonArray(uiRenders))
            json.put("databaseQueries", metricsToJsonArray(dbQueries))
            
            json.toString(2) // Pretty print with 2-space indent
        }
    }

    private fun metricsToJsonArray(metrics: List<PerformanceMetric>): JSONArray {
        val array = JSONArray()
        metrics.forEach { metric ->
            array.put(metricToJson(metric))
        }
        return array
    }

    private fun metricToJson(metric: PerformanceMetric): JSONObject {
        val json = JSONObject()
        json.put("operationId", metric.operationId)
        json.put("startTime", metric.startTime)
        json.put("endTime", metric.endTime)
        json.put("durationMs", metric.durationMs)
        json.put("complete", metric.isComplete)
        
        when (metric) {
            is AudioImportMetric -> {
                json.put("type", "audioImport")
                json.put("fileName", metric.fileName)
                json.put("fileSizeBytes", metric.fileSizeBytes)
                json.put("audioFormat", metric.audioFormat)
                json.put("durationSeconds", metric.durationSeconds)
                json.put("success", metric.success)
                json.put("error", metric.error)
            }
            is SegmentationMetric -> {
                json.put("type", "segmentation")
                json.put("audioFileName", metric.audioFileName)
                json.put("segmentationMode", metric.segmentationMode)
                json.put("segmentsDetected", metric.segmentsDetected)
                json.put("audioLengthMs", metric.audioLengthMs)
                json.put("success", metric.success)
                json.put("error", metric.error)
            }
            is UIRenderMetric -> {
                json.put("type", "uiRender")
                json.put("screenName", metric.screenName)
                json.put("operation", metric.operation)
                json.put("frameCount", metric.frameCount)
            }
            is DatabaseQueryMetric -> {
                json.put("type", "databaseQuery")
                json.put("queryType", metric.queryType)
                json.put("tableName", metric.tableName)
                json.put("recordCount", metric.recordCount)
                json.put("success", metric.success)
                json.put("error", metric.error)
            }
        }
        
        return json
    }

    /**
     * Save JSON to a file.
     * @param json JSON string to save
     * @param fileName Name of the file (will be saved to app cache directory)
     * @return File path where the JSON was saved
     */
    suspend fun saveToFile(json: String, fileName: String = "performance_metrics.json"): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(Date())
        val fullFileName = "metrics_${timestamp}_$fileName"
        val file = File(context.cacheDir, fullFileName)
        
        FileWriter(file).use { writer ->
            writer.write(json)
        }
        
        Log.i(TAG, "Performance metrics saved to: ${file.absolutePath}")
        return file.absolutePath
    }

    /**
     * Clear all collected metrics.
     */
    suspend fun clearMetrics() {
        mutex.withLock {
            metrics.clear()
            _metricsCount.value = 0
            sessionStartTime = System.currentTimeMillis()
            Log.d(TAG, "All metrics cleared")
        }
    }

    /**
     * Reset the tracker (clear metrics and start a new session).
     */
    suspend fun reset() {
        clearMetrics()
        enabled = true  // @Volatile ensures thread-safe visibility
    }
    
    /**
     * Release resources used by the PerformanceTracker.
     * This is a fire-and-forget operation - ongoing operations will be cancelled.
     * Call this when the tracker is no longer needed (typically on application shutdown).
     * 
     * Note: As a singleton, this is rarely needed since the tracker lives for the app lifetime.
     */
    fun release() {
        scope.cancel()
        Log.d(TAG, "PerformanceTracker resources released")
    }
}

/**
 * Summary of performance metrics.
 */
data class PerformanceSummary(
    val sessionStartTime: Long,
    val totalMetrics: Int,
    val audioImportCount: Int,
    val audioImportSuccessRate: Double,
    val audioImportAvgDurationMs: Double,
    val segmentationCount: Int,
    val segmentationSuccessRate: Double,
    val segmentationAvgDurationMs: Double,
    val uiRenderCount: Int,
    val uiRenderAvgDurationMs: Double,
    val dbQueryCount: Int,
    val dbQuerySuccessRate: Double,
    val dbQueryAvgDurationMs: Double
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("sessionStartTime", sessionStartTime)
        json.put("totalMetrics", totalMetrics)
        
        val audioImports = JSONObject()
        audioImports.put("count", audioImportCount)
        audioImports.put("successRate", audioImportSuccessRate)
        audioImports.put("avgDurationMs", audioImportAvgDurationMs)
        json.put("audioImports", audioImports)
        
        val segmentations = JSONObject()
        segmentations.put("count", segmentationCount)
        segmentations.put("successRate", segmentationSuccessRate)
        segmentations.put("avgDurationMs", segmentationAvgDurationMs)
        json.put("segmentations", segmentations)
        
        val uiRenders = JSONObject()
        uiRenders.put("count", uiRenderCount)
        uiRenders.put("avgDurationMs", uiRenderAvgDurationMs)
        json.put("uiRenders", uiRenders)
        
        val dbQueries = JSONObject()
        dbQueries.put("count", dbQueryCount)
        dbQueries.put("successRate", dbQuerySuccessRate)
        dbQueries.put("avgDurationMs", dbQueryAvgDurationMs)
        json.put("databaseQueries", dbQueries)
        
        return json
    }
}
