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
            progress = progress ?: _exportProgress.value.progress,
            currentSegment = currentSegment ?: _exportProgress.value.currentSegment,
            totalSegments = totalSegments ?: _exportProgress.value.totalSegments,
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
