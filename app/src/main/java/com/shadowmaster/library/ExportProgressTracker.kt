package com.shadowmaster.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress state for audio export
 */
data class ExportProgress(
    val status: ExportStatus,
    val progress: Int = 0,
    val currentSegment: Int = 0,
    val totalSegments: Int = 0,
    val outputPath: String? = null,
    val error: String? = null
)

enum class ExportStatus {
    IDLE,
    PREPARING,
    EXPORTING,
    ENCODING,
    COMPLETED,
    FAILED
}

/**
 * Tracks and manages export progress state.
 * Provides a StateFlow for observing progress updates.
 */
@Singleton
class ExportProgressTracker @Inject constructor() {
    private val _exportProgress = MutableStateFlow(ExportProgress(ExportStatus.IDLE))
    val exportProgress: StateFlow<ExportProgress> = _exportProgress.asStateFlow()

    /**
     * Update the export progress state.
     */
    fun updateProgress(progress: ExportProgress) {
        _exportProgress.value = progress
    }

    /**
     * Update only specific fields of the current progress.
     * Pass null to keep existing values, pass explicit values (including 0 or "") to update.
     * 
     * Note: For nullable String fields (outputPath, error), passing null will preserve their
     * current values (which may themselves be null). To reset all fields to their defaults,
     * use clearProgress().
     */
    fun updateProgress(
        status: ExportStatus? = null,
        progress: Int? = null,
        currentSegment: Int? = null,
        totalSegments: Int? = null,
        outputPath: String? = null,
        error: String? = null
    ) {
        _exportProgress.value = _exportProgress.value.copy(
            status = status ?: _exportProgress.value.status,
            progress = if (progress != null) progress else _exportProgress.value.progress,
            currentSegment = if (currentSegment != null) currentSegment else _exportProgress.value.currentSegment,
            totalSegments = if (totalSegments != null) totalSegments else _exportProgress.value.totalSegments,
            outputPath = outputPath ?: _exportProgress.value.outputPath,
            error = error ?: _exportProgress.value.error
        )
    }

    /**
     * Reset progress to idle state.
     */
    fun clearProgress() {
        _exportProgress.value = ExportProgress(ExportStatus.IDLE)
    }

    /**
     * Get the current progress value.
     */
    fun getCurrentProgress(): ExportProgress = _exportProgress.value
}
