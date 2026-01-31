package com.shadowmaster.ui.library

import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.shadowmaster.data.model.*
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.library.ExportProgress
import com.shadowmaster.library.ExportStatus
import com.shadowmaster.library.LibraryRepository
import com.shadowmaster.library.UrlImportProgress
import com.shadowmaster.library.UrlImportStatus
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LibraryViewModel.
 *
 * Tests cover:
 * - Playlist operations (select, delete, rename)
 * - Audio import operations (file, URL)
 * - Item operations (transcription, translation, favorite)
 * - Segment operations (split, merge)
 * - Export operations
 * - UI state management
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private lateinit var context: Context
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: LibraryViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        libraryRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)

        // Default mock behaviors
        every { libraryRepository.getAllPlaylists() } returns flowOf(emptyList())
        every { libraryRepository.getActiveImports() } returns flowOf(emptyList())
        every { libraryRepository.getRecentFailedImports() } returns flowOf(emptyList())
        every { libraryRepository.getUrlImportProgress() } returns flowOf(null)
        every { libraryRepository.getExportProgress() } returns flowOf(ExportProgress(ExportStatus.IDLE))

        viewModel = LibraryViewModel(context, libraryRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ==================== Playlist Selection Tests ====================

    @Test
    fun `selectPlaylist updates selectedPlaylist state`() = runTest {
        // Given
        val playlist = createTestPlaylist()
        every { libraryRepository.getItemsByPlaylist(playlist.id) } returns flowOf(emptyList())

        // When
        viewModel.selectPlaylist(playlist)
        advanceUntilIdle()

        // Then
        viewModel.selectedPlaylist.test {
            assertEquals(playlist, awaitItem())
        }
    }

    @Test
    fun `selectPlaylist loads items for selected playlist`() = runTest {
        // Given
        val playlist = createTestPlaylist()
        val items = listOf(
            createTestShadowItem(id = "1"),
            createTestShadowItem(id = "2")
        )
        every { libraryRepository.getItemsByPlaylist(playlist.id) } returns flowOf(items)

        // When
        viewModel.selectPlaylist(playlist)
        advanceUntilIdle()

        // Then
        viewModel.playlistItems.test {
            assertEquals(items, awaitItem())
        }
    }

    @Test
    fun `clearSelection clears selectedPlaylist and playlistItems`() = runTest {
        // Given
        val playlist = createTestPlaylist()
        every { libraryRepository.getItemsByPlaylist(playlist.id) } returns flowOf(emptyList())
        viewModel.selectPlaylist(playlist)
        advanceUntilIdle()

        // When
        viewModel.clearSelection()

        // Then
        viewModel.selectedPlaylist.test {
            assertNull(awaitItem())
        }
        viewModel.playlistItems.test {
            assertEquals(emptyList<ShadowItem>(), awaitItem())
        }
    }

    // ==================== Import Operations Tests ====================

    @Test
    fun `importAudioFile calls repository and shows success`() = runTest {
        // Given
        val uri = mockk<Uri>()
        coEvery { libraryRepository.importAudioFile(uri, "auto", false) } returns Result.success("playlist-id")

        // When
        viewModel.importAudioFile(uri)
        advanceUntilIdle()

        // Then
        viewModel.importSuccess.test {
            assertEquals("Audio import started", awaitItem())
        }
        coVerify { libraryRepository.importAudioFile(uri, "auto", false) }
    }

    @Test
    fun `importAudioFile shows error on failure`() = runTest {
        // Given
        val uri = mockk<Uri>()
        coEvery { libraryRepository.importAudioFile(uri, "auto", false) } returns
            Result.failure(Exception("Failed to decode"))

        // When
        viewModel.importAudioFile(uri)
        advanceUntilIdle()

        // Then
        viewModel.importError.test {
            assertEquals("Failed to decode", awaitItem())
        }
    }

    @Test
    fun `importFromUrl calls repository with decoded URL`() = runTest {
        // Given
        val url = "https://example.com/audio.mp3"
        coEvery { libraryRepository.importFromUrl(url, null, "auto") } returns Result.success("playlist-id")

        // When
        viewModel.importFromUrl(url)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.importFromUrl(url, null, "auto") }
    }

    @Test
    fun `importFromUrl shows success message`() = runTest {
        // Given
        val url = "https://example.com/audio.mp3"
        coEvery { libraryRepository.importFromUrl(url, null, "auto") } returns Result.success("playlist-id")

        // When
        viewModel.importFromUrl(url)
        advanceUntilIdle()

        // Then
        viewModel.importSuccess.test {
            assertEquals("Import started successfully", awaitItem())
        }
    }

    @Test
    fun `importFromUrl shows error on failure`() = runTest {
        // Given
        val url = "https://example.com/audio.mp3"
        coEvery { libraryRepository.importFromUrl(url, null, "auto") } returns
            Result.failure(Exception("Network error"))

        // When
        viewModel.importFromUrl(url)
        advanceUntilIdle()

        // Then
        viewModel.importError.test {
            assertEquals("Network error", awaitItem())
        }
    }

    // ==================== Playlist CRUD Tests ====================

    @Test
    fun `deletePlaylist calls repository`() = runTest {
        // Given
        val playlist = createTestPlaylist()
        coEvery { libraryRepository.deletePlaylist(playlist) } just Runs

        // When
        viewModel.deletePlaylist(playlist)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.deletePlaylist(playlist) }
    }

    @Test
    fun `renamePlaylist calls repository`() = runTest {
        // Given
        val playlistId = "test-id"
        val newName = "New Name"
        coEvery { libraryRepository.renamePlaylist(playlistId, newName) } just Runs

        // When
        viewModel.renamePlaylist(playlistId, newName)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.renamePlaylist(playlistId, newName) }
    }

    @Test
    fun `renamePlaylist updates selectedPlaylist if it matches`() = runTest {
        // Given
        val playlist = createTestPlaylist(id = "test-id", name = "Old Name")
        every { libraryRepository.getItemsByPlaylist(playlist.id) } returns flowOf(emptyList())
        coEvery { libraryRepository.renamePlaylist(any(), any()) } just Runs

        viewModel.selectPlaylist(playlist)
        advanceUntilIdle()

        // When
        viewModel.renamePlaylist("test-id", "New Name")
        advanceUntilIdle()

        // Then
        viewModel.selectedPlaylist.test {
            val updated = awaitItem()
            assertEquals("New Name", updated?.name)
        }
    }

    // ==================== Item Operations Tests ====================

    @Test
    fun `toggleFavorite calls repository`() = runTest {
        // Given
        val item = createTestShadowItem(isFavorite = false)
        coEvery { libraryRepository.toggleFavorite(item.id, true) } just Runs

        // When
        viewModel.toggleFavorite(item)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.toggleFavorite(item.id, true) }
    }

    @Test
    fun `updateItemTranscription calls repository`() = runTest {
        // Given
        val itemId = "item-id"
        val transcription = "Hello world"
        coEvery { libraryRepository.updateItemTranscription(itemId, transcription) } just Runs

        // When
        viewModel.updateItemTranscription(itemId, transcription)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.updateItemTranscription(itemId, transcription) }
    }

    @Test
    fun `updateItemTranslation calls repository`() = runTest {
        // Given
        val itemId = "item-id"
        val translation = "Hola mundo"
        coEvery { libraryRepository.updateItemTranslation(itemId, translation) } just Runs

        // When
        viewModel.updateItemTranslation(itemId, translation)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.updateItemTranslation(itemId, translation) }
    }

    // ==================== Segment Operations Tests ====================

    @Test
    fun `splitSegment calls repository and shows success`() = runTest {
        // Given
        val item = createTestShadowItem()
        val splitPoint = 2500L
        val playlist = createTestPlaylist()
        every { libraryRepository.getItemsByPlaylist(playlist.id) } returns flowOf(listOf(item))
        coEvery { libraryRepository.splitSegment(item, splitPoint) } returns listOf(
            createTestShadowItem(id = "split-1"),
            createTestShadowItem(id = "split-2")
        )

        viewModel.selectPlaylist(playlist)
        advanceUntilIdle()

        // When
        viewModel.splitSegment(item, splitPoint)
        advanceUntilIdle()

        // Then
        viewModel.importSuccess.test {
            assertEquals("Segment split into 2 parts", awaitItem())
        }
    }

    @Test
    fun `splitSegment shows error on failure`() = runTest {
        // Given
        val item = createTestShadowItem()
        coEvery { libraryRepository.splitSegment(item, 2500L) } returns null

        // When
        viewModel.splitSegment(item, 2500L)
        advanceUntilIdle()

        // Then
        viewModel.importError.test {
            assertEquals("Failed to split segment", awaitItem())
        }
    }

    @Test
    fun `mergeSelectedSegments requires at least 2 selections`() = runTest {
        // Given - only 1 item selected
        viewModel.toggleMergeSelection("item-1")

        // When
        viewModel.mergeSelectedSegments()
        advanceUntilIdle()

        // Then
        viewModel.importError.test {
            assertEquals("Select at least 2 segments to merge", awaitItem())
        }
    }

    @Test
    fun `mergeSelectedSegments calls repository with selected items`() = runTest {
        // Given
        val item1 = createTestShadowItem(id = "item-1")
        val item2 = createTestShadowItem(id = "item-2")
        val playlist = createTestPlaylist()
        every { libraryRepository.getItemsByPlaylist(playlist.id) } returns flowOf(listOf(item1, item2))
        coEvery { libraryRepository.mergeSegments(any()) } returns createTestShadowItem(id = "merged")

        viewModel.selectPlaylist(playlist)
        advanceUntilIdle()
        viewModel.toggleMergeSelection("item-1")
        viewModel.toggleMergeSelection("item-2")

        // When
        viewModel.mergeSelectedSegments()
        advanceUntilIdle()

        // Then
        viewModel.importSuccess.test {
            assertEquals("Merged 2 segments", awaitItem())
        }
    }

    @Test
    fun `toggleMergeSelection adds and removes items`() = runTest {
        // When - add
        viewModel.toggleMergeSelection("item-1")

        // Then
        viewModel.selectedForMerge.test {
            assertTrue(awaitItem().contains("item-1"))
        }

        // When - remove
        viewModel.toggleMergeSelection("item-1")

        // Then
        viewModel.selectedForMerge.test {
            assertFalse(awaitItem().contains("item-1"))
        }
    }

    @Test
    fun `clearMergeSelection clears all selections`() = runTest {
        // Given
        viewModel.toggleMergeSelection("item-1")
        viewModel.toggleMergeSelection("item-2")

        // When
        viewModel.clearMergeSelection()

        // Then
        viewModel.selectedForMerge.test {
            assertTrue(awaitItem().isEmpty())
        }
    }

    // ==================== Export Tests ====================

    @Test
    fun `exportPlaylist calls repository with config`() = runTest {
        // Given
        val playlist = createTestPlaylist()
        val config = ShadowingConfig()
        every { settingsRepository.configBlocking } returns config
        coEvery {
            libraryRepository.exportPlaylist(playlist.id, playlist.name, config, true)
        } returns Result.success("/path/to/export.wav")

        // When
        viewModel.exportPlaylist(playlist, true)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.exportPlaylist(playlist.id, playlist.name, config, true) }
    }

    @Test
    fun `exportPlaylist shows success with path`() = runTest {
        // Given
        val playlist = createTestPlaylist()
        val config = ShadowingConfig()
        val outputPath = "/path/to/export.wav"
        every { settingsRepository.configBlocking } returns config
        coEvery {
            libraryRepository.exportPlaylist(playlist.id, playlist.name, config, true)
        } returns Result.success(outputPath)

        // When
        viewModel.exportPlaylist(playlist, true)
        advanceUntilIdle()

        // Then
        viewModel.importSuccess.test {
            assertEquals("Exported to $outputPath", awaitItem())
        }
    }

    @Test
    fun `exportPlaylist shows error on failure`() = runTest {
        // Given
        val playlist = createTestPlaylist()
        val config = ShadowingConfig()
        every { settingsRepository.configBlocking } returns config
        coEvery {
            libraryRepository.exportPlaylist(playlist.id, playlist.name, config, true)
        } returns Result.failure(Exception("Disk full"))

        // When
        viewModel.exportPlaylist(playlist, true)
        advanceUntilIdle()

        // Then
        viewModel.importError.test {
            assertEquals("Export failed: Disk full", awaitItem())
        }
    }

    @Test
    fun `clearExportProgress calls repository`() {
        // When
        viewModel.clearExportProgress()

        // Then
        verify { libraryRepository.clearExportProgress() }
    }

    @Test
    fun `cancelExport calls repository`() {
        // When
        viewModel.cancelExport()

        // Then
        verify { libraryRepository.cancelExport() }
    }

    // ==================== Error/Success Management Tests ====================

    @Test
    fun `clearError clears importError`() = runTest {
        // Given - set an error first
        val uri = mockk<Uri>()
        coEvery { libraryRepository.importAudioFile(uri, "auto", false) } returns
            Result.failure(Exception("Error"))
        viewModel.importAudioFile(uri)
        advanceUntilIdle()

        // When
        viewModel.clearError()

        // Then
        viewModel.importError.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `clearSuccess clears importSuccess`() = runTest {
        // Given - set a success first
        val uri = mockk<Uri>()
        coEvery { libraryRepository.importAudioFile(uri, "auto", false) } returns
            Result.success("playlist-id")
        viewModel.importAudioFile(uri)
        advanceUntilIdle()

        // When
        viewModel.clearSuccess()

        // Then
        viewModel.importSuccess.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `clearUrlImportProgress calls repository`() {
        // When
        viewModel.clearUrlImportProgress()

        // Then
        verify { libraryRepository.clearUrlImportProgress() }
    }

    @Test
    fun `dismissFailedImport calls repository`() = runTest {
        // Given
        val jobId = "failed-job-id"
        coEvery { libraryRepository.deleteImportJob(jobId) } just Runs

        // When
        viewModel.dismissFailedImport(jobId)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.deleteImportJob(jobId) }
    }

    // ==================== Helper Functions ====================

    private fun createTestPlaylist(
        id: String = "test-playlist-id",
        name: String = "Test Playlist"
    ) = ShadowPlaylist(
        id = id,
        name = name,
        language = "en"
    )

    private fun createTestShadowItem(
        id: String = "test-item-id",
        isFavorite: Boolean = false
    ) = ShadowItem(
        id = id,
        sourceFileUri = "content://test/audio.mp3",
        sourceFileName = "test.mp3",
        sourceStartMs = 0L,
        sourceEndMs = 5000L,
        audioFilePath = "/data/test/$id.pcm",
        durationMs = 5000L,
        isFavorite = isFavorite,
        playlistId = "test-playlist-id"
    )
}
