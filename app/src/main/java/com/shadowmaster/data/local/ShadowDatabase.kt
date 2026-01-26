package com.shadowmaster.data.local

import android.content.Context
import androidx.room.*
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

@Database(
    entities = [
        ShadowItem::class,
        ShadowPlaylist::class,
        ImportJob::class,
        PracticeSession::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ShadowDatabase : RoomDatabase() {
    abstract fun shadowItemDao(): ShadowItemDao
    abstract fun shadowPlaylistDao(): ShadowPlaylistDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun practiceSessionDao(): PracticeSessionDao

    companion object {
        @Volatile
        private var INSTANCE: ShadowDatabase? = null

        fun getDatabase(context: Context): ShadowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShadowDatabase::class.java,
                    "shadow_library_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
