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
 * This class coordinates the export process using specialized components:
 * - [PlaylistExporter] for audio generation
 * - [WavFileCreator] for file creation
 * - [ExportProgressTracker] for progress management
 */
@Singleton
class AudioExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowItemDao: ShadowItemDao,
    private val playlistExporter: PlaylistExporter,
    private val wavFileCreator: WavFileCreator,
    private val aacFileCreator: AacFileCreator,
    private val mp3FileCreator: Mp3FileCreator,
    private val progressTracker: ExportProgressTracker,
    private val lyricsExporter: LyricsExporter
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
     * @param format Export format (WAV or AAC)
     */
    suspend fun exportPlaylist(
        playlistId: String,
        playlistName: String,
        config: ShadowingConfig,
        includeYourTurnSilence: Boolean = true,
        format: ExportFormat = ExportFormat.AAC
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            progressTracker.startPreparing()

            // Load playlist items
            val items = shadowItemDao.getItemsByPlaylist(playlistId).first()
            if (items.isEmpty()) {
                progressTracker.fail("Playlist is empty")
                return@withContext Result.failure(Exception("Playlist is empty"))
            }

            progressTracker.startExporting(items.size)

            // Create temp file for raw PCM
            val tempPcmFile = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}.pcm")
            val outputStream = BufferedOutputStream(FileOutputStream(tempPcmFile))

            try {
                // Generate audio for each segment
                items.forEachIndexed { index, item ->
                    progressTracker.updateProgress(index + 1, items.size)

                    // Write segment with repeats and beeps
                    playlistExporter.writeSegmentAudio(outputStream, item, config, includeYourTurnSilence)

                    // Pause between segments
                    playlistExporter.writePostSegmentPause(outputStream)
                }

                outputStream.flush()
                outputStream.close()

                // Convert to selected format and save
                progressTracker.startEncoding()

                val result = when (format) {
                    ExportFormat.WAV -> wavFileCreator.saveAsWav(tempPcmFile, playlistName)
                    ExportFormat.AAC -> aacFileCreator.saveAsAac(tempPcmFile, playlistName)
                    ExportFormat.MP3 -> mp3FileCreator.saveAsMp3(tempPcmFile, playlistName)
                }

                // Clean up temp file
                tempPcmFile.delete()

                // Generate LRC + SRT lyrics files alongside audio
                try {
                    lyricsExporter.exportLyrics(items, config, includeYourTurnSilence, playlistName)
                } catch (e: Exception) {
                    Log.w(TAG, "Lyrics export failed (non-fatal)", e)
                }

                progressTracker.complete(items.size, result.path, result.uri)

                Log.i(TAG, "Export completed: ${result.path}")
                Result.success(result.path)

            } catch (e: Exception) {
                outputStream.close()
                tempPcmFile.delete()
                throw e
            }

        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            progressTracker.fail(e.message ?: "Export failed")
            Result.failure(e)
        }
    }

    fun clearProgress() {
        progressTracker.clear()
    }

    fun cancelExport() {
        exportJob?.cancel()
        progressTracker.clear()
    }

    fun release() {
        scope.cancel()
    }
}
