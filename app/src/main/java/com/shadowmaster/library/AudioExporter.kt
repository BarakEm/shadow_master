package com.shadowmaster.library

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.shadowmaster.data.local.ShadowItemDao
import com.shadowmaster.data.model.ShadowItem
import com.shadowmaster.data.model.ShadowingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

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
 * Exports playlists as audio files with beeps, repeats, and silence gaps.
 * Creates a standalone audio file for passive shadowing practice.
 */
@Singleton
class AudioExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowItemDao: ShadowItemDao,
    private val audioFileUtility: AudioFileUtility
) {
    companion object {
        private const val TAG = "AudioExporter"
        private const val SAMPLE_RATE = 16000
        private const val BYTES_PER_SAMPLE = 2 // 16-bit audio

        // Beep frequencies (Hz)
        private const val PLAYBACK_BEEP_FREQ = 880.0  // A5 - playback start
        private const val YOUR_TURN_BEEP_FREQ = 1047.0 // C6 - your turn
        private const val SEGMENT_END_BEEP_FREQ = 660.0 // E5 - segment done

        // Durations (ms)
        private const val BEEP_DURATION_MS = 150
        private const val DOUBLE_BEEP_GAP_MS = 100
        private const val PRE_SEGMENT_PAUSE_MS = 300
        private const val POST_SEGMENT_PAUSE_MS = 500
        private const val BETWEEN_REPEATS_PAUSE_MS = 300
    }

    private val _exportProgress = MutableStateFlow(ExportProgress(ExportStatus.IDLE))
    val exportProgress: StateFlow<ExportProgress> = _exportProgress.asStateFlow()

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
            _exportProgress.value = ExportProgress(ExportStatus.PREPARING)

            // Load playlist items
            val items = shadowItemDao.getItemsByPlaylist(playlistId).first()
            if (items.isEmpty()) {
                _exportProgress.value = ExportProgress(
                    status = ExportStatus.FAILED,
                    error = "Playlist is empty"
                )
                return@withContext Result.failure(Exception("Playlist is empty"))
            }

            _exportProgress.value = ExportProgress(
                status = ExportStatus.EXPORTING,
                totalSegments = items.size
            )

            // Create temp file for raw PCM
            val tempPcmFile = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}.pcm")
            val outputStream = BufferedOutputStream(FileOutputStream(tempPcmFile))

            try {
                // Generate audio for each segment
                items.forEachIndexed { index, item ->
                    _exportProgress.value = _exportProgress.value.copy(
                        currentSegment = index + 1,
                        progress = (index * 100) / items.size
                    )

                    // Write segment with repeats and beeps
                    writeSegmentAudio(outputStream, item, config, includeYourTurnSilence)

                    // Pause between segments
                    writeSilence(outputStream, POST_SEGMENT_PAUSE_MS.toLong())
                }

                outputStream.flush()
                outputStream.close()

                // Convert to WAV and save
                _exportProgress.value = _exportProgress.value.copy(
                    status = ExportStatus.ENCODING,
                    progress = 90
                )

                val outputPath = saveAsWav(tempPcmFile, playlistName)

                // Clean up temp file
                tempPcmFile.delete()

                _exportProgress.value = ExportProgress(
                    status = ExportStatus.COMPLETED,
                    progress = 100,
                    totalSegments = items.size,
                    currentSegment = items.size,
                    outputPath = outputPath
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
            _exportProgress.value = ExportProgress(
                status = ExportStatus.FAILED,
                error = e.message ?: "Export failed"
            )
            Result.failure(e)
        }
    }

    /**
     * Write a single segment's audio with all beeps and pauses.
     */
    private fun writeSegmentAudio(
        output: OutputStream,
        item: ShadowItem,
        config: ShadowingConfig,
        includeYourTurnSilence: Boolean
    ) {
        val segmentAudio = loadSegmentAudio(item.audioFilePath) ?: return

        // For each playback repeat
        for (repeat in 1..config.playbackRepeats) {
            // Playback start beep (single tone)
            writeBeep(output, PLAYBACK_BEEP_FREQ, BEEP_DURATION_MS)
            writeSilence(output, PRE_SEGMENT_PAUSE_MS.toLong())

            // Play the segment
            output.write(segmentAudio)

            // Pause after playback
            writeSilence(output, BETWEEN_REPEATS_PAUSE_MS.toLong())
        }

        // "Your turn" section (if not bus mode and silence included)
        if (!config.busMode && includeYourTurnSilence) {
            for (userRepeat in 1..config.userRepeats) {
                // Double beep for "your turn"
                writeBeep(output, YOUR_TURN_BEEP_FREQ, BEEP_DURATION_MS)
                writeSilence(output, DOUBLE_BEEP_GAP_MS.toLong())
                writeBeep(output, YOUR_TURN_BEEP_FREQ, BEEP_DURATION_MS)
                writeSilence(output, PRE_SEGMENT_PAUSE_MS.toLong())

                // Silence for user to shadow (same duration as segment)
                writeSilence(output, item.durationMs)

                // Small buffer after shadowing
                writeSilence(output, BETWEEN_REPEATS_PAUSE_MS.toLong())
            }

            // Segment complete beep (descending tone)
            writeBeep(output, SEGMENT_END_BEEP_FREQ, BEEP_DURATION_MS)
        }
    }

    /**
     * Load segment audio from file.
     */
    private fun loadSegmentAudio(filePath: String): ByteArray? {
        return try {
            val file = File(filePath)
            if (file.exists()) file.readBytes() else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load segment: $filePath", e)
            null
        }
    }

    /**
     * Generate and write a beep tone.
     */
    private fun writeBeep(output: OutputStream, frequency: Double, durationMs: Int) {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ByteArray(numSamples * BYTES_PER_SAMPLE)

        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * i * frequency / SAMPLE_RATE
            // Apply envelope to avoid clicks (fade in/out)
            val envelope = when {
                i < numSamples / 10 -> i.toDouble() / (numSamples / 10)
                i > numSamples * 9 / 10 -> (numSamples - i).toDouble() / (numSamples / 10)
                else -> 1.0
            }
            val sample = (sin(angle) * 16000 * envelope).toInt().toShort()

            // Little-endian 16-bit
            samples[i * 2] = (sample.toInt() and 0xFF).toByte()
            samples[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        output.write(samples)
    }

    /**
     * Write silence (zeros) for specified duration.
     */
    private fun writeSilence(output: OutputStream, durationMs: Long) {
        val numSamples = ((SAMPLE_RATE * durationMs) / 1000).toInt()
        val silence = ByteArray(numSamples * BYTES_PER_SAMPLE) // Initialized to zeros
        output.write(silence)
    }

    /**
     * Convert raw PCM to WAV and save to Music folder.
     */
    private fun saveAsWav(pcmFile: File, name: String): String {
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "ShadowMaster_${sanitizedName}_${System.currentTimeMillis()}.wav"

        val pcmData = pcmFile.readBytes()
        val wavData = audioFileUtility.createWavHeader(pcmData.size) + pcmData

        // Save to Music folder using MediaStore (works on all Android versions)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(fileName, wavData)
        } else {
            saveToExternalStorage(fileName, wavData)
        }
    }

    /**
     * Save using MediaStore API (Android 10+)
     */
    private fun saveWithMediaStore(fileName: String, data: ByteArray): String {
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/ShadowMaster")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Failed to create media store entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(data)
        } ?: throw IOException("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        return "Music/ShadowMaster/$fileName"
    }

    /**
     * Save to external storage (Android 9 and below)
     */
    @Suppress("DEPRECATION")
    private fun saveToExternalStorage(fileName: String, data: ByteArray): String {
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "ShadowMaster"
        )
        musicDir.mkdirs()

        val outputFile = File(musicDir, fileName)
        FileOutputStream(outputFile).use { it.write(data) }

        return outputFile.absolutePath
    }

    fun clearProgress() {
        _exportProgress.value = ExportProgress(ExportStatus.IDLE)
    }

    fun cancelExport() {
        exportJob?.cancel()
        _exportProgress.value = ExportProgress(ExportStatus.IDLE)
    }

    fun release() {
        scope.cancel()
    }
}
