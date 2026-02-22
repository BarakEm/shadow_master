package com.shadowmaster.data.model

import androidx.room.*
import java.util.UUID

/**
 * A shadow item is a single audio segment ready for shadowing practice.
 * Contains the audio data, optional transcription, and metadata.
 */
@Entity(
    tableName = "shadow_items",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["importedAudioId"])
    ]
)
data class ShadowItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Source info
    val sourceFileUri: String,           // Original file URI
    val sourceFileName: String,          // Display name
    val sourceStartMs: Long,             // Start position in source file
    val sourceEndMs: Long,               // End position in source file

    // Audio data (stored as file path to processed segment)
    val audioFilePath: String,           // Path to extracted audio segment
    val durationMs: Long,                // Duration of this segment

    // Transcription (optional)
    val transcription: String? = null,   // Original language text
    val translation: String? = null,     // Translated text
    val language: String = "unknown",    // Detected/specified language

    // Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val lastPracticedAt: Long? = null,
    val practiceCount: Int = 0,
    val isFavorite: Boolean = false,

    // Playlist membership
    val playlistId: String? = null,
    val orderInPlaylist: Int = 0,

    // Provenance tracking (NEW)
    val importedAudioId: String? = null,        // Reference to ImportedAudio
    val segmentationConfigId: String? = null    // Which config was used
)

/**
 * A playlist/collection of shadow items for organized practice.
 */
@Entity(
    tableName = "shadow_playlists",
    indices = [
        Index(value = ["createdAt"])
    ]
)
data class ShadowPlaylist(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val name: String,
    val description: String? = null,
    val language: String = "unknown",
    val coverImagePath: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val lastPracticedAt: Long? = null,

    // Source tracking
    val sourceType: SourceType = SourceType.IMPORTED,
    val sourceUri: String? = null,       // Original file/folder URI

    // Per-playlist playback settings
    val playbackSpeed: Float = 0.8f,
    val playbackRepeats: Int = 1,
    val userRepeats: Int = 1,

    // Per-playlist practice settings
    val busMode: Boolean = false,
    val practiceMode: PracticeMode = PracticeMode.STANDARD,
    val buildupChunkMs: Int = 1500,
    val audioFeedbackEnabled: Boolean = true,
    val beepVolume: Int = 80,
    val beepToneType: BeepToneType = BeepToneType.SOFT,
    val beepDurationMs: Int = 150
)

enum class SourceType {
    IMPORTED,      // Imported from file picker
    RECORDED,      // Recorded live via MediaProjection
    DOWNLOADED     // Downloaded from online source (future)
}

/**
 * Import job tracking - for processing large files in background.
 */
@Entity(
    tableName = "import_jobs",
    indices = [
        Index(value = ["status"])
    ]
)
data class ImportJob(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val sourceUri: String,
    val fileName: String,
    val status: ImportStatus = ImportStatus.PENDING,
    val progress: Int = 0,              // 0-100
    val totalSegments: Int = 0,
    val processedSegments: Int = 0,
    val errorMessage: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,

    // Settings used for this import
    val language: String = "auto",
    val enableTranscription: Boolean = false,
    val targetPlaylistId: String? = null
)

enum class ImportStatus {
    PENDING,
    EXTRACTING_AUDIO,
    DETECTING_SEGMENTS,
    TRANSCRIBING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Practice session - tracks a shadowing session for statistics.
 */
@Entity(tableName = "practice_sessions")
data class PracticeSession(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val playlistId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,

    val itemsPracticed: Int = 0,
    val totalDurationMs: Long = 0
)

/**
 * Tracks imported raw audio before segmentation.
 * Allows re-segmentation without re-importing.
 */
@Entity(tableName = "imported_audio")
data class ImportedAudio(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Source metadata
    val sourceUri: String,              // Original file URI
    val sourceFileName: String,
    val originalFormat: String,         // "mp3", "wav", etc.

    // Processed audio storage
    val pcmFilePath: String,            // Path to processed 16kHz mono PCM
    val durationMs: Long,               // Total duration
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val fileSizeBytes: Long,

    // Metadata
    val language: String = "unknown",
    val createdAt: Long = System.currentTimeMillis(),

    // Segmentation tracking
    val segmentationCount: Int = 0,
    val lastSegmentedAt: Long? = null
)

/**
 * Configurable segmentation parameters.
 * Allows creating presets for different use cases.
 */
@Entity(tableName = "segmentation_configs")
data class SegmentationConfig(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Segmentation parameters (currently hardcoded in AudioImporter)
    val minSegmentDurationMs: Long = 500,
    val maxSegmentDurationMs: Long = 8000,
    val silenceThresholdMs: Long = 700,
    val preSpeechBufferMs: Long = 200,
    val segmentMode: SegmentMode = SegmentMode.SENTENCE,

    // User-friendly name
    val name: String = "Default",
    val createdAt: Long = System.currentTimeMillis()
)

// Room type converters
class Converters {
    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromImportStatus(value: ImportStatus): String = value.name

    @TypeConverter
    fun toImportStatus(value: String): ImportStatus = ImportStatus.valueOf(value)

    @TypeConverter
    fun fromSegmentMode(value: SegmentMode): String = value.name

    @TypeConverter
    fun toSegmentMode(value: String): SegmentMode = SegmentMode.valueOf(value)

    @TypeConverter
    fun fromPracticeMode(value: PracticeMode): String = value.name

    @TypeConverter
    fun toPracticeMode(value: String): PracticeMode = PracticeMode.valueOf(value)

    @TypeConverter
    fun fromBeepToneType(value: BeepToneType): String = value.name

    @TypeConverter
    fun toBeepToneType(value: String): BeepToneType = BeepToneType.valueOf(value)
}
