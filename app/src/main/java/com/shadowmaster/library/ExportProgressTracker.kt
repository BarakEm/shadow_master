package com.shadowmaster.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress state for audio export operations.
 */
data class ExportProgress(
    val status: ExportStatus,
    val progress: Int = 0,
    val currentSegment: Int = 0,
    val totalSegments: Int = 0,
    val outputPath: String? = null,
    val outputUri: android.net.Uri? = null,
    val error: String? = null
)

/**
 * Export operation status.
 */
enum class ExportStatus {
    IDLE,
    PREPARING,
    EXPORTING,
    ENCODING,
    COMPLETED,
    FAILED
}

/**
 * Audio export format.
 */
enum class ExportFormat {
    WAV,
    MP3
}

/**
 * Tracks and manages export progress state.
 * Extracted from AudioExporter for single responsibility.
 */
@Singleton
class ExportProgressTracker @Inject constructor() {

    private val _exportProgress = MutableStateFlow(ExportProgress(ExportStatus.IDLE))
    val exportProgress: StateFlow<ExportProgress> = _exportProgress.asStateFlow()

    /**
     * Set status to preparing for export.
     */
    fun startPreparing() {
        _exportProgress.value = ExportProgress(ExportStatus.PREPARING)
    }

    /**
     * Set status to exporting with total segments count.
     */
    fun startExporting(totalSegments: Int) {
        _exportProgress.value = ExportProgress(
            status = ExportStatus.EXPORTING,
            totalSegments = totalSegments
        )
    }

    /**
     * Update progress for current segment.
     */
    fun updateProgress(currentSegment: Int, totalSegments: Int) {
        _exportProgress.value = _exportProgress.value.copy(
            currentSegment = currentSegment,
            progress = (currentSegment * 100) / totalSegments
        )
    }

    /**
     * Set status to encoding.
     */
    fun startEncoding() {
        _exportProgress.value = _exportProgress.value.copy(
            status = ExportStatus.ENCODING,
            progress = 90
        )
    }

    /**
     * Mark export as completed.
     */
    fun complete(totalSegments: Int, outputPath: String, outputUri: android.net.Uri? = null) {
        _exportProgress.value = ExportProgress(
            status = ExportStatus.COMPLETED,
            progress = 100,
            totalSegments = totalSegments,
            currentSegment = totalSegments,
            outputPath = outputPath,
            outputUri = outputUri
        )
    }

    /**
     * Mark export as failed with error message.
     */
    fun fail(error: String) {
        _exportProgress.value = ExportProgress(
            status = ExportStatus.FAILED,
            error = error
        )
    }

    /**
     * Reset progress to idle state.
     */
    fun clear() {
        _exportProgress.value = ExportProgress(ExportStatus.IDLE)
    }
}
