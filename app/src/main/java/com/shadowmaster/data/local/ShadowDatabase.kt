package com.shadowmaster.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shadowmaster.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShadowItemDao {
    @Query("SELECT * FROM shadow_items ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<ShadowItem>>

    @Query("SELECT * FROM shadow_items WHERE playlistId = :playlistId ORDER BY orderInPlaylist ASC")
    fun getItemsByPlaylist(playlistId: String): Flow<List<ShadowItem>>

    @Query("SELECT * FROM shadow_items WHERE isFavorite = 1 ORDER BY lastPracticedAt DESC")
    fun getFavorites(): Flow<List<ShadowItem>>

    @Query("SELECT * FROM shadow_items WHERE id = :id")
    suspend fun getItemById(id: String): ShadowItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ShadowItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShadowItem>)

    @Update
    suspend fun update(item: ShadowItem)

    @Delete
    suspend fun delete(item: ShadowItem)

    @Query("DELETE FROM shadow_items WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("UPDATE shadow_items SET lastPracticedAt = :timestamp, practiceCount = practiceCount + 1 WHERE id = :id")
    suspend fun markPracticed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE shadow_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE shadow_items SET transcription = :transcription WHERE id = :id")
    suspend fun updateTranscription(id: String, transcription: String?)

    @Query("UPDATE shadow_items SET translation = :translation WHERE id = :id")
    suspend fun updateTranslation(id: String, translation: String?)

    @Query("SELECT COUNT(*) FROM shadow_items")
    suspend fun getItemCount(): Int

    @Query("SELECT COUNT(*) FROM shadow_items WHERE playlistId = :playlistId")
    suspend fun getItemCountByPlaylist(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM shadow_items WHERE playlistId = :playlistId AND transcription IS NOT NULL")
    suspend fun getTranscribedItemCountByPlaylist(playlistId: String): Int
}

@Dao
interface ShadowPlaylistDao {
    @Query("SELECT * FROM shadow_playlists ORDER BY CASE WHEN lastPracticedAt IS NULL THEN 1 ELSE 0 END, lastPracticedAt DESC, createdAt DESC")
    fun getAllPlaylists(): Flow<List<ShadowPlaylist>>

    @Query("SELECT * FROM shadow_playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): ShadowPlaylist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: ShadowPlaylist): Long

    @Update
    suspend fun update(playlist: ShadowPlaylist)

    @Delete
    suspend fun delete(playlist: ShadowPlaylist)

    @Query("UPDATE shadow_playlists SET lastPracticedAt = :timestamp WHERE id = :id")
    suspend fun markPracticed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE shadow_playlists SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)
}

@Dao
interface ImportJobDao {
    @Query("SELECT * FROM import_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<ImportJob>>

    @Query("SELECT * FROM import_jobs WHERE status IN ('PENDING', 'EXTRACTING_AUDIO', 'DETECTING_SEGMENTS', 'TRANSCRIBING')")
    fun getActiveJobs(): Flow<List<ImportJob>>

    @Query("SELECT * FROM import_jobs WHERE status = 'FAILED' ORDER BY createdAt DESC LIMIT 5")
    fun getRecentFailedJobs(): Flow<List<ImportJob>>

    @Query("SELECT * FROM import_jobs WHERE targetPlaylistId = :playlistId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getJobByPlaylistId(playlistId: String): ImportJob?

    @Query("SELECT * FROM import_jobs WHERE id = :id")
    suspend fun getJobById(id: String): ImportJob?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: ImportJob)

    @Update
    suspend fun update(job: ImportJob)

    @Delete
    suspend fun delete(job: ImportJob)

    @Query("UPDATE import_jobs SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: String, status: ImportStatus, progress: Int)

    @Query("UPDATE import_jobs SET status = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("UPDATE import_jobs SET status = 'COMPLETED', completedAt = :timestamp, totalSegments = :total WHERE id = :id")
    suspend fun markCompleted(id: String, total: Int, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface PracticeSessionDao {
    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC LIMIT 50")
    fun getRecentSessions(): Flow<List<PracticeSession>>

    @Insert
    suspend fun insert(session: PracticeSession)

    @Update
    suspend fun update(session: PracticeSession)

    @Query("SELECT SUM(totalDurationMs) FROM practice_sessions")
    suspend fun getTotalPracticeTime(): Long?

    @Query("SELECT SUM(itemsPracticed) FROM practice_sessions")
    suspend fun getTotalItemsPracticed(): Int?
}

@Dao
interface ImportedAudioDao {
    @Query("SELECT * FROM imported_audio ORDER BY createdAt DESC")
    fun getAllImportedAudio(): Flow<List<ImportedAudio>>

    @Query("SELECT * FROM imported_audio WHERE id = :id")
    suspend fun getById(id: String): ImportedAudio?

    @Query("SELECT * FROM imported_audio WHERE sourceUri = :uri")
    suspend fun getBySourceUri(uri: String): ImportedAudio?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audio: ImportedAudio)

    @Update
    suspend fun update(audio: ImportedAudio)

    @Delete
    suspend fun delete(audio: ImportedAudio)

    @Query("UPDATE imported_audio SET segmentationCount = segmentationCount + 1, lastSegmentedAt = :timestamp WHERE id = :id")
    suspend fun markSegmented(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT SUM(fileSizeBytes) FROM imported_audio")
    suspend fun getTotalStorageUsed(): Long?
}

@Dao
interface SegmentationConfigDao {
    @Query("SELECT * FROM segmentation_configs ORDER BY createdAt DESC")
    fun getAllConfigs(): Flow<List<SegmentationConfig>>

    @Query("SELECT * FROM segmentation_configs WHERE id = :id")
    suspend fun getById(id: String): SegmentationConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: SegmentationConfig)

    @Update
    suspend fun update(config: SegmentationConfig)

    @Delete
    suspend fun delete(config: SegmentationConfig)
}

@Database(
    entities = [
        ShadowItem::class,
        ShadowPlaylist::class,
        ImportJob::class,
        PracticeSession::class,
        ImportedAudio::class,
        SegmentationConfig::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ShadowDatabase : RoomDatabase() {
    abstract fun shadowItemDao(): ShadowItemDao
    abstract fun shadowPlaylistDao(): ShadowPlaylistDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun importedAudioDao(): ImportedAudioDao
    abstract fun segmentationConfigDao(): SegmentationConfigDao

    companion object {
        @Volatile
        private var INSTANCE: ShadowDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create imported_audio table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS imported_audio (
                        id TEXT PRIMARY KEY NOT NULL,
                        sourceUri TEXT NOT NULL,
                        sourceFileName TEXT NOT NULL,
                        originalFormat TEXT NOT NULL,
                        pcmFilePath TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        sampleRate INTEGER NOT NULL,
                        channels INTEGER NOT NULL,
                        fileSizeBytes INTEGER NOT NULL,
                        language TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        segmentationCount INTEGER NOT NULL,
                        lastSegmentedAt INTEGER
                    )
                """.trimIndent())

                // Create segmentation_configs table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS segmentation_configs (
                        id TEXT PRIMARY KEY NOT NULL,
                        minSegmentDurationMs INTEGER NOT NULL,
                        maxSegmentDurationMs INTEGER NOT NULL,
                        silenceThresholdMs INTEGER NOT NULL,
                        preSpeechBufferMs INTEGER NOT NULL,
                        segmentMode TEXT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Add new columns to shadow_items (nullable for existing data)
                database.execSQL("ALTER TABLE shadow_items ADD COLUMN importedAudioId TEXT")
                database.execSQL("ALTER TABLE shadow_items ADD COLUMN segmentationConfigId TEXT")

                // Insert default segmentation config
                database.execSQL("""
                    INSERT INTO segmentation_configs
                    (id, minSegmentDurationMs, maxSegmentDurationMs, silenceThresholdMs,
                     preSpeechBufferMs, segmentMode, name, createdAt)
                    VALUES
                    ('default-config', 500, 8000, 700, 200, 'SENTENCE', 'Default',
                     ${System.currentTimeMillis()})
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add index on shadow_items.playlistId for playlist queries
                database.execSQL("CREATE INDEX IF NOT EXISTS index_shadow_items_playlistId ON shadow_items(playlistId)")

                // Add index on shadow_items.importedAudioId for audio lookups
                database.execSQL("CREATE INDEX IF NOT EXISTS index_shadow_items_importedAudioId ON shadow_items(importedAudioId)")

                // Add index on shadow_playlists.createdAt for sorting operations
                database.execSQL("CREATE INDEX IF NOT EXISTS index_shadow_playlists_createdAt ON shadow_playlists(createdAt)")

                // Add index on import_jobs.status for job queries
                database.execSQL("CREATE INDEX IF NOT EXISTS index_import_jobs_status ON import_jobs(status)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add predefined segmentation config presets for Word Mode and Sentence Mode
                // These are in addition to the default-config from migration 1_2
                
                // Word Mode: shorter segments for word-level practice
                database.execSQL("""
                    INSERT OR IGNORE INTO segmentation_configs
                    (id, minSegmentDurationMs, maxSegmentDurationMs, silenceThresholdMs,
                     preSpeechBufferMs, segmentMode, name, createdAt)
                    VALUES
                    ('word-mode', 500, 2000, 700, 200, 'WORD', 'Word Mode',
                     ${System.currentTimeMillis()})
                """.trimIndent())
                
                // Sentence Mode: longer segments for sentence-level practice
                database.execSQL("""
                    INSERT OR IGNORE INTO segmentation_configs
                    (id, minSegmentDurationMs, maxSegmentDurationMs, silenceThresholdMs,
                     preSpeechBufferMs, segmentMode, name, createdAt)
                    VALUES
                    ('sentence-mode', 1000, 8000, 700, 200, 'SENTENCE', 'Sentence Mode',
                     ${System.currentTimeMillis()})
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE shadow_playlists ADD COLUMN playbackSpeed REAL NOT NULL DEFAULT 0.8")
                database.execSQL("ALTER TABLE shadow_playlists ADD COLUMN playbackRepeats INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE shadow_playlists ADD COLUMN userRepeats INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getDatabase(context: Context): ShadowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShadowDatabase::class.java,
                    "shadow_library_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
