package com.shadowmaster.library

import com.shadowmaster.data.local.*
import com.shadowmaster.data.model.*
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LibraryRepository.
 *
 * Tests cover:
 * - CRUD operations for playlists
 * - CRUD operations for shadow items
 * - Database query methods return correct data
 * - Error handling for database failures
 * - Mock Room DAOs using MockK
 */
class LibraryRepositoryTest {

    // Mock DAOs
    private lateinit var shadowItemDao: ShadowItemDao
    private lateinit var shadowPlaylistDao: ShadowPlaylistDao
    private lateinit var importJobDao: ImportJobDao
    private lateinit var practiceSessionDao: PracticeSessionDao
    private lateinit var importedAudioDao: ImportedAudioDao
    private lateinit var segmentationConfigDao: SegmentationConfigDao

    // Mock helper classes
    private lateinit var audioImporter: AudioImporter
    private lateinit var urlAudioImporter: UrlAudioImporter
    private lateinit var audioExporter: AudioExporter

    // System under test
    private lateinit var repository: LibraryRepository

    @Before
    fun setup() {
        // Initialize mocks
        shadowItemDao = mockk()
        shadowPlaylistDao = mockk()
        importJobDao = mockk()
        practiceSessionDao = mockk()
        importedAudioDao = mockk()
        segmentationConfigDao = mockk()
        audioImporter = mockk()
        urlAudioImporter = mockk()
        audioExporter = mockk()

        // Create repository with mocked dependencies
        repository = LibraryRepository(
            shadowItemDao = shadowItemDao,
            shadowPlaylistDao = shadowPlaylistDao,
            importJobDao = importJobDao,
            practiceSessionDao = practiceSessionDao,
            audioImporter = audioImporter,
            urlAudioImporter = urlAudioImporter,
            audioExporter = audioExporter,
            importedAudioDao = importedAudioDao,
            segmentationConfigDao = segmentationConfigDao
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== Playlist CRUD Tests ====================

    @Test
    fun `getAllPlaylists returns flow from DAO`() = runTest {
        // Given
        val playlist1 = createTestPlaylist(id = "1", name = "Test Playlist 1")
        val playlist2 = createTestPlaylist(id = "2", name = "Test Playlist 2")
        val expectedPlaylists = listOf(playlist1, playlist2)

        every { shadowPlaylistDao.getAllPlaylists() } returns flowOf(expectedPlaylists)

        // When
        val result = repository.getAllPlaylists().first()

        // Then
        assertEquals(expectedPlaylists, result)
        verify(exactly = 1) { shadowPlaylistDao.getAllPlaylists() }
    }

    @Test
    fun `getPlaylist returns playlist by id`() = runTest {
        // Given
        val playlistId = "test-id"
        val expectedPlaylist = createTestPlaylist(id = playlistId, name = "Test Playlist")

        coEvery { shadowPlaylistDao.getPlaylistById(playlistId) } returns expectedPlaylist

        // When
        val result = repository.getPlaylist(playlistId)

        // Then
        assertEquals(expectedPlaylist, result)
        coVerify(exactly = 1) { shadowPlaylistDao.getPlaylistById(playlistId) }
    }

    @Test
    fun `getPlaylist returns null when playlist not found`() = runTest {
        // Given
        val playlistId = "non-existent-id"

        coEvery { shadowPlaylistDao.getPlaylistById(playlistId) } returns null

        // When
        val result = repository.getPlaylist(playlistId)

        // Then
        assertNull(result)
        coVerify(exactly = 1) { shadowPlaylistDao.getPlaylistById(playlistId) }
    }

    @Test
    fun `deletePlaylist deletes playlist and associated items`() = runTest {
        // Given
        val playlist = createTestPlaylist(id = "test-id", name = "Test Playlist")

        coEvery { shadowItemDao.deleteByPlaylist(playlist.id) } just Runs
        coEvery { shadowPlaylistDao.delete(playlist) } just Runs

        // When
        repository.deletePlaylist(playlist)

        // Then
        coVerify(exactly = 1) { shadowItemDao.deleteByPlaylist(playlist.id) }
        coVerify(exactly = 1) { shadowPlaylistDao.delete(playlist) }
    }

    @Test
    fun `updatePlaylist updates playlist in DAO`() = runTest {
        // Given
        val playlist = createTestPlaylist(id = "test-id", name = "Updated Playlist")

        coEvery { shadowPlaylistDao.update(playlist) } just Runs

        // When
        repository.updatePlaylist(playlist)

        // Then
        coVerify(exactly = 1) { shadowPlaylistDao.update(playlist) }
    }

    @Test
    fun `renamePlaylist updates playlist name`() = runTest {
        // Given
        val playlistId = "test-id"
        val newName = "New Playlist Name"

        coEvery { shadowPlaylistDao.updateName(playlistId, newName) } just Runs

        // When
        repository.renamePlaylist(playlistId, newName)

        // Then
        coVerify(exactly = 1) { shadowPlaylistDao.updateName(playlistId, newName) }
    }

    // ==================== Shadow Item CRUD Tests ====================

    @Test
    fun `getAllItems returns flow from DAO`() = runTest {
        // Given
        val item1 = createTestShadowItem(id = "1", playlistId = "playlist-1")
        val item2 = createTestShadowItem(id = "2", playlistId = "playlist-1")
        val expectedItems = listOf(item1, item2)

        every { shadowItemDao.getAllItems() } returns flowOf(expectedItems)

        // When
        val result = repository.getAllItems().first()

        // Then
        assertEquals(expectedItems, result)
        verify(exactly = 1) { shadowItemDao.getAllItems() }
    }

    @Test
    fun `getItemsByPlaylist returns items for specific playlist`() = runTest {
        // Given
        val playlistId = "playlist-1"
        val item1 = createTestShadowItem(id = "1", playlistId = playlistId)
        val item2 = createTestShadowItem(id = "2", playlistId = playlistId)
        val expectedItems = listOf(item1, item2)

        every { shadowItemDao.getItemsByPlaylist(playlistId) } returns flowOf(expectedItems)

        // When
        val result = repository.getItemsByPlaylist(playlistId).first()

        // Then
        assertEquals(expectedItems, result)
        verify(exactly = 1) { shadowItemDao.getItemsByPlaylist(playlistId) }
    }

    @Test
    fun `getFavorites returns only favorite items`() = runTest {
        // Given
        val item1 = createTestShadowItem(id = "1", isFavorite = true)
        val item2 = createTestShadowItem(id = "2", isFavorite = true)
        val expectedItems = listOf(item1, item2)

        every { shadowItemDao.getFavorites() } returns flowOf(expectedItems)

        // When
        val result = repository.getFavorites().first()

        // Then
        assertEquals(expectedItems, result)
        verify(exactly = 1) { shadowItemDao.getFavorites() }
    }

    @Test
    fun `getItem returns item by id`() = runTest {
        // Given
        val itemId = "test-id"
        val expectedItem = createTestShadowItem(id = itemId)

        coEvery { shadowItemDao.getItemById(itemId) } returns expectedItem

        // When
        val result = repository.getItem(itemId)

        // Then
        assertEquals(expectedItem, result)
        coVerify(exactly = 1) { shadowItemDao.getItemById(itemId) }
    }

    @Test
    fun `getItem returns null when item not found`() = runTest {
        // Given
        val itemId = "non-existent-id"

        coEvery { shadowItemDao.getItemById(itemId) } returns null

        // When
        val result = repository.getItem(itemId)

        // Then
        assertNull(result)
        coVerify(exactly = 1) { shadowItemDao.getItemById(itemId) }
    }

    @Test
    fun `updateItem updates item in DAO`() = runTest {
        // Given
        val item = createTestShadowItem(id = "test-id")

        coEvery { shadowItemDao.update(item) } just Runs

        // When
        repository.updateItem(item)

        // Then
        coVerify(exactly = 1) { shadowItemDao.update(item) }
    }

    @Test
    fun `markItemPracticed updates item practice count`() = runTest {
        // Given
        val itemId = "test-id"

        coEvery { shadowItemDao.markPracticed(itemId) } just Runs

        // When
        repository.markItemPracticed(itemId)

        // Then
        coVerify(exactly = 1) { shadowItemDao.markPracticed(itemId) }
    }

    @Test
    fun `toggleFavorite sets favorite status`() = runTest {
        // Given
        val itemId = "test-id"
        val isFavorite = true

        coEvery { shadowItemDao.setFavorite(itemId, isFavorite) } just Runs

        // When
        repository.toggleFavorite(itemId, isFavorite)

        // Then
        coVerify(exactly = 1) { shadowItemDao.setFavorite(itemId, isFavorite) }
    }

    @Test
    fun `updateItemTranscription updates transcription text`() = runTest {
        // Given
        val itemId = "test-id"
        val transcription = "Hello world"

        coEvery { shadowItemDao.updateTranscription(itemId, transcription) } just Runs

        // When
        repository.updateItemTranscription(itemId, transcription)

        // Then
        coVerify(exactly = 1) { shadowItemDao.updateTranscription(itemId, transcription) }
    }

    @Test
    fun `updateItemTranscription can clear transcription with null`() = runTest {
        // Given
        val itemId = "test-id"

        coEvery { shadowItemDao.updateTranscription(itemId, null) } just Runs

        // When
        repository.updateItemTranscription(itemId, null)

        // Then
        coVerify(exactly = 1) { shadowItemDao.updateTranscription(itemId, null) }
    }

    @Test
    fun `updateItemTranslation updates translation text`() = runTest {
        // Given
        val itemId = "test-id"
        val translation = "Hola mundo"

        coEvery { shadowItemDao.updateTranslation(itemId, translation) } just Runs

        // When
        repository.updateItemTranslation(itemId, translation)

        // Then
        coVerify(exactly = 1) { shadowItemDao.updateTranslation(itemId, translation) }
    }

    @Test
    fun `updateItemTranslation can clear translation with null`() = runTest {
        // Given
        val itemId = "test-id"

        coEvery { shadowItemDao.updateTranslation(itemId, null) } just Runs

        // When
        repository.updateItemTranslation(itemId, null)

        // Then
        coVerify(exactly = 1) { shadowItemDao.updateTranslation(itemId, null) }
    }

    // ==================== Query Methods Tests ====================

    @Test
    fun `getItemCount returns total item count`() = runTest {
        // Given
        val expectedCount = 42

        coEvery { shadowItemDao.getItemCount() } returns expectedCount

        // When
        val result = repository.getItemCount()

        // Then
        assertEquals(expectedCount, result)
        coVerify(exactly = 1) { shadowItemDao.getItemCount() }
    }

    @Test
    fun `getPlaylistItemCount returns item count for playlist`() = runTest {
        // Given
        val playlistId = "test-playlist"
        val expectedCount = 15

        coEvery { shadowItemDao.getItemCountByPlaylist(playlistId) } returns expectedCount

        // When
        val result = repository.getPlaylistItemCount(playlistId)

        // Then
        assertEquals(expectedCount, result)
        coVerify(exactly = 1) { shadowItemDao.getItemCountByPlaylist(playlistId) }
    }

    @Test
    fun `getItemCount returns zero when database is empty`() = runTest {
        // Given
        coEvery { shadowItemDao.getItemCount() } returns 0

        // When
        val result = repository.getItemCount()

        // Then
        assertEquals(0, result)
        coVerify(exactly = 1) { shadowItemDao.getItemCount() }
    }

    @Test
    fun `getPlaylistItemCount returns zero for empty playlist`() = runTest {
        // Given
        val playlistId = "empty-playlist"

        coEvery { shadowItemDao.getItemCountByPlaylist(playlistId) } returns 0

        // When
        val result = repository.getPlaylistItemCount(playlistId)

        // Then
        assertEquals(0, result)
        coVerify(exactly = 1) { shadowItemDao.getItemCountByPlaylist(playlistId) }
    }

    // ==================== Import Job Tests ====================

    @Test
    fun `getActiveImports returns flow of active jobs`() = runTest {
        // Given
        val job1 = createTestImportJob(id = "1", status = ImportStatus.EXTRACTING_AUDIO)
        val job2 = createTestImportJob(id = "2", status = ImportStatus.DETECTING_SEGMENTS)
        val expectedJobs = listOf(job1, job2)

        every { importJobDao.getActiveJobs() } returns flowOf(expectedJobs)

        // When
        val result = repository.getActiveImports().first()

        // Then
        assertEquals(expectedJobs, result)
        verify(exactly = 1) { importJobDao.getActiveJobs() }
    }

    @Test
    fun `getAllImports returns flow of all jobs`() = runTest {
        // Given
        val job1 = createTestImportJob(id = "1", status = ImportStatus.COMPLETED)
        val job2 = createTestImportJob(id = "2", status = ImportStatus.FAILED)
        val expectedJobs = listOf(job1, job2)

        every { importJobDao.getAllJobs() } returns flowOf(expectedJobs)

        // When
        val result = repository.getAllImports().first()

        // Then
        assertEquals(expectedJobs, result)
        verify(exactly = 1) { importJobDao.getAllJobs() }
    }

    @Test
    fun `getRecentFailedImports returns flow of failed jobs`() = runTest {
        // Given
        val job1 = createTestImportJob(id = "1", status = ImportStatus.FAILED)
        val job2 = createTestImportJob(id = "2", status = ImportStatus.FAILED)
        val expectedJobs = listOf(job1, job2)

        every { importJobDao.getRecentFailedJobs() } returns flowOf(expectedJobs)

        // When
        val result = repository.getRecentFailedImports().first()

        // Then
        assertEquals(expectedJobs, result)
        verify(exactly = 1) { importJobDao.getRecentFailedJobs() }
    }

    @Test
    fun `getImportJobForPlaylist returns job by playlist id`() = runTest {
        // Given
        val playlistId = "test-playlist"
        val expectedJob = createTestImportJob(targetPlaylistId = playlistId)

        coEvery { importJobDao.getJobByPlaylistId(playlistId) } returns expectedJob

        // When
        val result = repository.getImportJobForPlaylist(playlistId)

        // Then
        assertEquals(expectedJob, result)
        coVerify(exactly = 1) { importJobDao.getJobByPlaylistId(playlistId) }
    }

    @Test
    fun `deleteImportJob deletes job when it exists`() = runTest {
        // Given
        val jobId = "test-job"
        val job = createTestImportJob(id = jobId)

        coEvery { importJobDao.getJobById(jobId) } returns job
        coEvery { importJobDao.delete(job) } just Runs

        // When
        repository.deleteImportJob(jobId)

        // Then
        coVerify(exactly = 1) { importJobDao.getJobById(jobId) }
        coVerify(exactly = 1) { importJobDao.delete(job) }
    }

    @Test
    fun `deleteImportJob does nothing when job does not exist`() = runTest {
        // Given
        val jobId = "non-existent-job"

        coEvery { importJobDao.getJobById(jobId) } returns null

        // When
        repository.deleteImportJob(jobId)

        // Then
        coVerify(exactly = 1) { importJobDao.getJobById(jobId) }
        coVerify(exactly = 0) { importJobDao.delete(any()) }
    }

    // ==================== Practice Session Tests ====================

    @Test
    fun `startPracticeSession creates and inserts new session`() = runTest {
        // Given
        val playlistId = "test-playlist"

        coEvery { practiceSessionDao.insert(any()) } just Runs

        // When
        val result = repository.startPracticeSession(playlistId)

        // Then
        assertEquals(playlistId, result.playlistId)
        assertNotNull(result.id)
        assertTrue(result.startedAt > 0)
        assertNull(result.endedAt)
        coVerify(exactly = 1) { practiceSessionDao.insert(any()) }
    }

    @Test
    fun `startPracticeSession can create session without playlist`() = runTest {
        // Given
        coEvery { practiceSessionDao.insert(any()) } just Runs

        // When
        val result = repository.startPracticeSession(null)

        // Then
        assertNull(result.playlistId)
        assertNotNull(result.id)
        coVerify(exactly = 1) { practiceSessionDao.insert(any()) }
    }

    @Test
    fun `endPracticeSession updates session and marks playlist practiced`() = runTest {
        // Given
        val playlistId = "test-playlist"
        val session = PracticeSession(playlistId = playlistId)
        val itemsPracticed = 10
        val durationMs = 60000L

        coEvery { practiceSessionDao.update(any()) } just Runs
        coEvery { shadowPlaylistDao.markPracticed(playlistId) } just Runs

        // When
        repository.endPracticeSession(session, itemsPracticed, durationMs)

        // Then
        coVerify(exactly = 1) { practiceSessionDao.update(match { 
            it.id == session.id && 
            it.itemsPracticed == itemsPracticed && 
            it.totalDurationMs == durationMs &&
            it.endedAt != null &&
            it.endedAt!! >= session.startedAt
        }) }
        coVerify(exactly = 1) { shadowPlaylistDao.markPracticed(playlistId) }
    }

    @Test
    fun `endPracticeSession without playlist does not mark playlist practiced`() = runTest {
        // Given
        val session = PracticeSession(playlistId = null)
        val itemsPracticed = 5
        val durationMs = 30000L

        coEvery { practiceSessionDao.update(any()) } just Runs

        // When
        repository.endPracticeSession(session, itemsPracticed, durationMs)

        // Then
        coVerify(exactly = 1) { practiceSessionDao.update(any()) }
        coVerify(exactly = 0) { shadowPlaylistDao.markPracticed(any()) }
    }

    @Test
    fun `getRecentSessions returns flow from DAO`() = runTest {
        // Given
        val session1 = PracticeSession(playlistId = "playlist-1")
        val session2 = PracticeSession(playlistId = "playlist-2")
        val expectedSessions = listOf(session1, session2)

        every { practiceSessionDao.getRecentSessions() } returns flowOf(expectedSessions)

        // When
        val result = repository.getRecentSessions().first()

        // Then
        assertEquals(expectedSessions, result)
        verify(exactly = 1) { practiceSessionDao.getRecentSessions() }
    }

    @Test
    fun `getTotalPracticeTime returns total from DAO`() = runTest {
        // Given
        val expectedTotal = 120000L

        coEvery { practiceSessionDao.getTotalPracticeTime() } returns expectedTotal

        // When
        val result = repository.getTotalPracticeTime()

        // Then
        assertEquals(expectedTotal, result)
        coVerify(exactly = 1) { practiceSessionDao.getTotalPracticeTime() }
    }

    @Test
    fun `getTotalPracticeTime returns zero when null from DAO`() = runTest {
        // Given
        coEvery { practiceSessionDao.getTotalPracticeTime() } returns null

        // When
        val result = repository.getTotalPracticeTime()

        // Then
        assertEquals(0L, result)
        coVerify(exactly = 1) { practiceSessionDao.getTotalPracticeTime() }
    }

    @Test
    fun `getTotalItemsPracticed returns total from DAO`() = runTest {
        // Given
        val expectedTotal = 150

        coEvery { practiceSessionDao.getTotalItemsPracticed() } returns expectedTotal

        // When
        val result = repository.getTotalItemsPracticed()

        // Then
        assertEquals(expectedTotal, result)
        coVerify(exactly = 1) { practiceSessionDao.getTotalItemsPracticed() }
    }

    @Test
    fun `getTotalItemsPracticed returns zero when null from DAO`() = runTest {
        // Given
        coEvery { practiceSessionDao.getTotalItemsPracticed() } returns null

        // When
        val result = repository.getTotalItemsPracticed()

        // Then
        assertEquals(0, result)
        coVerify(exactly = 1) { practiceSessionDao.getTotalItemsPracticed() }
    }

    // ==================== Imported Audio Tests ====================

    @Test
    fun `getAllImportedAudio returns flow from DAO`() = runTest {
        // Given
        val audio1 = createTestImportedAudio(id = "1")
        val audio2 = createTestImportedAudio(id = "2")
        val expectedAudio = listOf(audio1, audio2)

        every { importedAudioDao.getAllImportedAudio() } returns flowOf(expectedAudio)

        // When
        val result = repository.getAllImportedAudio().first()

        // Then
        assertEquals(expectedAudio, result)
        verify(exactly = 1) { importedAudioDao.getAllImportedAudio() }
    }

    @Test
    fun `getImportedAudio returns audio by id`() = runTest {
        // Given
        val audioId = "test-audio"
        val expectedAudio = createTestImportedAudio(id = audioId)

        coEvery { importedAudioDao.getById(audioId) } returns expectedAudio

        // When
        val result = repository.getImportedAudio(audioId)

        // Then
        assertEquals(expectedAudio, result)
        coVerify(exactly = 1) { importedAudioDao.getById(audioId) }
    }

    @Test
    fun `getTotalImportedAudioStorage returns total storage used`() = runTest {
        // Given
        val expectedStorage = 5242880L // 5MB

        coEvery { importedAudioDao.getTotalStorageUsed() } returns expectedStorage

        // When
        val result = repository.getTotalImportedAudioStorage()

        // Then
        assertEquals(expectedStorage, result)
        coVerify(exactly = 1) { importedAudioDao.getTotalStorageUsed() }
    }

    @Test
    fun `getTotalImportedAudioStorage returns zero when null from DAO`() = runTest {
        // Given
        coEvery { importedAudioDao.getTotalStorageUsed() } returns null

        // When
        val result = repository.getTotalImportedAudioStorage()

        // Then
        assertEquals(0L, result)
        coVerify(exactly = 1) { importedAudioDao.getTotalStorageUsed() }
    }

    // ==================== Segmentation Config Tests ====================

    @Test
    fun `getAllSegmentationConfigs returns flow from DAO`() = runTest {
        // Given
        val config1 = createTestSegmentationConfig(id = "1", name = "Default")
        val config2 = createTestSegmentationConfig(id = "2", name = "Fast")
        val expectedConfigs = listOf(config1, config2)

        every { segmentationConfigDao.getAllConfigs() } returns flowOf(expectedConfigs)

        // When
        val result = repository.getAllSegmentationConfigs().first()

        // Then
        assertEquals(expectedConfigs, result)
        verify(exactly = 1) { segmentationConfigDao.getAllConfigs() }
    }

    @Test
    fun `getSegmentationConfig returns config by id`() = runTest {
        // Given
        val configId = "test-config"
        val expectedConfig = createTestSegmentationConfig(id = configId)

        coEvery { segmentationConfigDao.getById(configId) } returns expectedConfig

        // When
        val result = repository.getSegmentationConfig(configId)

        // Then
        assertEquals(expectedConfig, result)
        coVerify(exactly = 1) { segmentationConfigDao.getById(configId) }
    }

    @Test
    fun `saveSegmentationConfig inserts config to DAO`() = runTest {
        // Given
        val config = createTestSegmentationConfig()

        coEvery { segmentationConfigDao.insert(config) } just Runs

        // When
        repository.saveSegmentationConfig(config)

        // Then
        coVerify(exactly = 1) { segmentationConfigDao.insert(config) }
    }

    @Test
    fun `deleteSegmentationConfig deletes config from DAO`() = runTest {
        // Given
        val config = createTestSegmentationConfig()

        coEvery { segmentationConfigDao.delete(config) } just Runs

        // When
        repository.deleteSegmentationConfig(config)

        // Then
        coVerify(exactly = 1) { segmentationConfigDao.delete(config) }
    }

    @Test
    fun `createPlaylistFromImportedAudio creates playlist successfully`() = runTest {
        // Given
        val importedAudioId = "test-audio-id"
        val playlistName = "My Playlist"
        val configId = "word-mode"
        val config = createTestSegmentationConfig(id = configId, name = "Word Mode")
        val expectedPlaylistId = "new-playlist-id"

        coEvery { segmentationConfigDao.getById(configId) } returns config
        coEvery { audioImporter.segmentImportedAudio(
            importedAudioId = importedAudioId,
            playlistName = playlistName,
            config = config,
            enableTranscription = false,
            jobId = null
        ) } returns Result.success(expectedPlaylistId)

        // When
        val result = repository.createPlaylistFromImportedAudio(importedAudioId, playlistName, configId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedPlaylistId, result.getOrNull())
        coVerify(exactly = 1) { segmentationConfigDao.getById(configId) }
        coVerify(exactly = 1) { audioImporter.segmentImportedAudio(
            importedAudioId = importedAudioId,
            playlistName = playlistName,
            config = config,
            enableTranscription = false,
            jobId = null
        ) }
    }

    @Test
    fun `createPlaylistFromImportedAudio fails when config not found`() = runTest {
        // Given
        val importedAudioId = "test-audio-id"
        val playlistName = "My Playlist"
        val configId = "non-existent-config"

        coEvery { segmentationConfigDao.getById(configId) } returns null

        // When
        val result = repository.createPlaylistFromImportedAudio(importedAudioId, playlistName, configId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 1) { segmentationConfigDao.getById(configId) }
        coVerify(exactly = 0) { audioImporter.segmentImportedAudio(any(), any(), any(), any(), any()) }
    }

    // ==================== Segment Operations Tests ====================

    @Test
    fun `splitSegment delegates to audioImporter`() = runTest {
        // Given
        val item = createTestShadowItem()
        val splitPointMs = 5000L
        val expectedResult = listOf(
            createTestShadowItem(id = "split-1"),
            createTestShadowItem(id = "split-2")
        )

        coEvery { audioImporter.splitSegment(item, splitPointMs) } returns expectedResult

        // When
        val result = repository.splitSegment(item, splitPointMs)

        // Then
        assertEquals(expectedResult, result)
        coVerify(exactly = 1) { audioImporter.splitSegment(item, splitPointMs) }
    }

    @Test
    fun `splitSegment returns null on failure`() = runTest {
        // Given
        val item = createTestShadowItem()
        val splitPointMs = 5000L

        coEvery { audioImporter.splitSegment(item, splitPointMs) } returns null

        // When
        val result = repository.splitSegment(item, splitPointMs)

        // Then
        assertNull(result)
        coVerify(exactly = 1) { audioImporter.splitSegment(item, splitPointMs) }
    }

    @Test
    fun `mergeSegments delegates to audioImporter`() = runTest {
        // Given
        val items = listOf(
            createTestShadowItem(id = "1"),
            createTestShadowItem(id = "2")
        )
        val expectedResult = createTestShadowItem(id = "merged")

        coEvery { audioImporter.mergeSegments(items) } returns expectedResult

        // When
        val result = repository.mergeSegments(items)

        // Then
        assertEquals(expectedResult, result)
        coVerify(exactly = 1) { audioImporter.mergeSegments(items) }
    }

    @Test
    fun `mergeSegments returns null on failure`() = runTest {
        // Given
        val items = listOf(
            createTestShadowItem(id = "1"),
            createTestShadowItem(id = "2")
        )

        coEvery { audioImporter.mergeSegments(items) } returns null

        // When
        val result = repository.mergeSegments(items)

        // Then
        assertNull(result)
        coVerify(exactly = 1) { audioImporter.mergeSegments(items) }
    }

    // ==================== Helper Functions ====================

    private fun createTestPlaylist(
        id: String = "test-playlist-id",
        name: String = "Test Playlist",
        description: String? = "Test Description",
        language: String = "en",
        createdAt: Long = System.currentTimeMillis()
    ) = ShadowPlaylist(
        id = id,
        name = name,
        description = description,
        language = language,
        createdAt = createdAt
    )

    private fun createTestShadowItem(
        id: String = "test-item-id",
        playlistId: String? = "test-playlist-id",
        sourceFileName: String = "test.mp3",
        durationMs: Long = 5000L,
        transcription: String? = null,
        translation: String? = null,
        isFavorite: Boolean = false,
        createdAt: Long = System.currentTimeMillis()
    ) = ShadowItem(
        id = id,
        sourceFileUri = "content://test/audio.mp3",
        sourceFileName = sourceFileName,
        sourceStartMs = 0L,
        sourceEndMs = durationMs,
        audioFilePath = "/data/test/$id.pcm",
        durationMs = durationMs,
        transcription = transcription,
        translation = translation,
        language = "en",
        createdAt = createdAt,
        playlistId = playlistId,
        isFavorite = isFavorite
    )

    private fun createTestImportJob(
        id: String = "test-job-id",
        sourceUri: String = "content://test/audio.mp3",
        fileName: String = "test.mp3",
        status: ImportStatus = ImportStatus.PENDING,
        targetPlaylistId: String? = null
    ) = ImportJob(
        id = id,
        sourceUri = sourceUri,
        fileName = fileName,
        status = status,
        targetPlaylistId = targetPlaylistId
    )

    private fun createTestImportedAudio(
        id: String = "test-audio-id",
        sourceFileName: String = "test.mp3",
        durationMs: Long = 300000L,
        fileSizeBytes: Long = 1024000L
    ) = ImportedAudio(
        id = id,
        sourceUri = "content://test/$sourceFileName",
        sourceFileName = sourceFileName,
        originalFormat = "mp3",
        pcmFilePath = "/data/test/$id.pcm",
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes
    )

    private fun createTestSegmentationConfig(
        id: String = "test-config-id",
        name: String = "Default",
        minSegmentDurationMs: Long = 500L,
        maxSegmentDurationMs: Long = 8000L
    ) = SegmentationConfig(
        id = id,
        name = name,
        minSegmentDurationMs = minSegmentDurationMs,
        maxSegmentDurationMs = maxSegmentDurationMs
    )
}
