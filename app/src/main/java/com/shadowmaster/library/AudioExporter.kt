package com.shadowmaster.library

import android.content.Context
import android.util.Log
import com.shadowmaster.data.local.ShadowItemDao
import com.shadowmaster.data.model.ShadowingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.io.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports playlists as audio files with beeps, repeats, and silence gaps.
 * Creates a standalone audio file for passive shadowing practice.
 *
 * This class orchestrates the export process using:
 * - [ExportProgressTracker] for progress state management
 * - [PlaylistExporter] for audio generation logic
 * - [WavFileCreator] for WAV file creation and storage
 */
@Singleton
class AudioExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowItemDao: ShadowItemDao,
    private val progressTracker: ExportProgressTracker,
    private val playlistExporter: PlaylistExporter,
    private val wavFileCreator: WavFileCreator
) {
    companion object {
        private const val TAG = "AudioExporter"
    }

    val exportProgress: StateFlow<ExportProgress> = progressTracker.exportProgress

    private var exportJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Export a playlist as a practice audio file.
     *
     * @param playlistId The playlist to export
     * @param playlistName Name for the output file
     * @param config Shadowing configuration (repeats, speed, etc.)
     * @param includeYourTurnSilence Whether to include silence gaps for user to practice
     */
    suspend fun exportPlaylist(
        playlistId: String,
        playlistName: String,
        config: ShadowingConfig,
        includeYourTurnSilence: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            progressTracker.updateProgress(ExportProgress(ExportStatus.PREPARING))

            // Load playlist items
            val items = shadowItemDao.getItemsByPlaylist(playlistId).first()
            if (items.isEmpty()) {
                progressTracker.updateProgress(
                    ExportProgress(
                        status = ExportStatus.FAILED,
                        error = "Playlist is empty"
                    )
                )
                return@withContext Result.failure(Exception("Playlist is empty"))
            }

            progressTracker.updateProgress(
                ExportProgress(
                    status = ExportStatus.EXPORTING,
                    totalSegments = items.size
                )
            )

            // Create temp file for raw PCM
            val tempPcmFile = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}.pcm")
            val outputStream = BufferedOutputStream(FileOutputStream(tempPcmFile))

            try {
                // Generate audio for each segment
                items.forEachIndexed { index, item ->
                    progressTracker.updateProgress(
                        status = ExportStatus.EXPORTING,
                        currentSegment = index + 1,
                        progress = (index * 100) / items.size
                    )

                    // Write segment with repeats and beeps
                    playlistExporter.writeSegmentAudio(outputStream, item, config, includeYourTurnSilence)

                    // Pause between segments
                    playlistExporter.writePostSegmentPause(outputStream)
                }

                outputStream.flush()
                outputStream.close()

                // Convert to WAV and save
                progressTracker.updateProgress(
                    status = ExportStatus.ENCODING,
                    progress = 90
                )

                val outputPath = wavFileCreator.saveAsWav(tempPcmFile, playlistName)

                // Clean up temp file
                tempPcmFile.delete()

                progressTracker.updateProgress(
                    ExportProgress(
                        status = ExportStatus.COMPLETED,
                        progress = 100,
                        totalSegments = items.size,
                        currentSegment = items.size,
                        outputPath = outputPath
                    )
                )

                Log.i(TAG, "Export completed: $outputPath")
                Result.success(outputPath)

            } catch (e: Exception) {
                outputStream.close()
                tempPcmFile.delete()
                throw e
            }

        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            progressTracker.updateProgress(
                ExportProgress(
                    status = ExportStatus.FAILED,
                    error = e.message ?: "Export failed"
                )
            )
            Result.failure(e)
        }
    }


    fun clearProgress() {
        progressTracker.clearProgress()
    }

    fun cancelExport() {
        exportJob?.cancel()
        progressTracker.clearProgress()
    }

    fun release() {
        scope.cancel()
    }
}
