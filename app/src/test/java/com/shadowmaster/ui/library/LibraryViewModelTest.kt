package com.shadowmaster.ui.library

import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.shadowmaster.data.model.*
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.library.ExportProgress
import com.shadowmaster.library.ExportStatus
import com.shadowmaster.library.LibraryRepository
import com.shadowmaster.library.UrlImportProgress
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for LibraryViewModel.
 * Tests playlist operations and UI state updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var context: Context
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: LibraryViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testPlaylist = ShadowPlaylist(
        id = "playlist1",
        name = "Test Playlist",
        language = "en-US"
    )

    private val testItem = ShadowItem(
        id = "item1",
        sourceFileUri = "content://test",
        sourceFileName = "test.mp3",
        sourceStartMs = 0L,
        sourceEndMs = 5000L,
        audioFilePath = "/path/to/audio.pcm",
        durationMs = 5000L,
        playlistId = "playlist1"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        libraryRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)

        // Mock default flows
        every { libraryRepository.getAllPlaylists() } returns flowOf(emptyList())
        every { libraryRepository.getActiveImports() } returns flowOf(emptyList())
        every { libraryRepository.getRecentFailedImports() } returns flowOf(emptyList())
        every { libraryRepository.getUrlImportProgress() } returns flowOf(null)
        every { libraryRepository.getExportProgress() } returns flowOf(
            ExportProgress(ExportStatus.IDLE)
        )
        every { libraryRepository.getItemsByPlaylist(any()) } returns flowOf(emptyList())
        every { settingsRepository.configBlocking } returns ShadowingConfig()

        viewModel = LibraryViewModel(context, libraryRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `playlists StateFlow emits repository data`() = runTest {
        // Given
        val expectedPlaylists = listOf(testPlaylist)
        every { libraryRepository.getAllPlaylists() } returns flowOf(expectedPlaylists)

        // When
        val newViewModel = LibraryViewModel(context, libraryRepository, settingsRepository)
        advanceUntilIdle()

        // Then
        assertEquals(expectedPlaylists, newViewModel.playlists.value)
    }

    @Test
    fun `selectPlaylist updates selected playlist and loads items`() = runTest {
        // Given
        val items = listOf(testItem)
        every { libraryRepository.getItemsByPlaylist("playlist1") } returns flowOf(items)

        // When
        viewModel.selectPlaylist(testPlaylist)
        advanceUntilIdle()

        // Then
        assertEquals(testPlaylist, viewModel.selectedPlaylist.value)
        assertEquals(items, viewModel.playlistItems.value)
        coVerify { libraryRepository.getItemsByPlaylist("playlist1") }
    }

    @Test
    fun `clearSelection resets selected playlist and items`() = runTest {
        // Given - select a playlist first
        every { libraryRepository.getItemsByPlaylist("playlist1") } returns flowOf(listOf(testItem))
        viewModel.selectPlaylist(testPlaylist)
        advanceUntilIdle()

        // When
        viewModel.clearSelection()

        // Then
        assertNull(viewModel.selectedPlaylist.value)
        assertTrue(viewModel.playlistItems.value.isEmpty())
    }

    @Test
    fun `importAudioFile success updates success message`() = runTest {
        // Given
        val uri = mockk<Uri>(relaxed = true)
        coEvery { 
            libraryRepository.importAudioFile(uri, "en-US", false) 
        } returns Result.success(Unit)

        // When
        viewModel.importAudioFile(uri, "en-US")
        advanceUntilIdle()

        // Then
        assertEquals("Audio import started", viewModel.importSuccess.value)
        assertNull(viewModel.importError.value)
        coVerify { libraryRepository.importAudioFile(uri, "en-US", false) }
    }

    @Test
    fun `importAudioFile failure updates error message`() = runTest {
        // Given
        val uri = mockk<Uri>(relaxed = true)
        val errorMessage = "Import failed due to error"
        coEvery { 
            libraryRepository.importAudioFile(uri, "auto", false) 
        } returns Result.failure(Exception(errorMessage))

        // When
        viewModel.importAudioFile(uri)
        advanceUntilIdle()

        // Then
        assertEquals(errorMessage, viewModel.importError.value)
        assertNull(viewModel.importSuccess.value)
    }

    @Test
    fun `deletePlaylist calls repository`() = runTest {
        // When
        viewModel.deletePlaylist(testPlaylist)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.deletePlaylist(testPlaylist) }
    }

    @Test
    fun `toggleFavorite calls repository with inverted favorite state`() = runTest {
        // Given
        val item = testItem.copy(isFavorite = false)

        // When
        viewModel.toggleFavorite(item)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.toggleFavorite("item1", true) }
    }

    @Test
    fun `clearError resets error message`() = runTest {
        // Given - set an error first
        val uri = mockk<Uri>(relaxed = true)
        coEvery { 
            libraryRepository.importAudioFile(uri, "auto", false) 
        } returns Result.failure(Exception("Test error"))
        viewModel.importAudioFile(uri)
        advanceUntilIdle()

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.importError.value)
    }

    @Test
    fun `clearSuccess resets success message`() = runTest {
        // Given - set a success first
        val uri = mockk<Uri>(relaxed = true)
        coEvery { 
            libraryRepository.importAudioFile(uri, "auto", false) 
        } returns Result.success(Unit)
        viewModel.importAudioFile(uri)
        advanceUntilIdle()

        // When
        viewModel.clearSuccess()

        // Then
        assertNull(viewModel.importSuccess.value)
    }

    @Test
    fun `renamePlaylist calls repository and updates selected playlist`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist("playlist1") } returns flowOf(emptyList())
        viewModel.selectPlaylist(testPlaylist)
        advanceUntilIdle()

        // When
        viewModel.renamePlaylist("playlist1", "New Name")
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.renamePlaylist("playlist1", "New Name") }
        assertEquals("New Name", viewModel.selectedPlaylist.value?.name)
    }

    @Test
    fun `updateItemTranscription calls repository and updates items`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist("playlist1") } returns flowOf(listOf(testItem))
        viewModel.selectPlaylist(testPlaylist)
        advanceUntilIdle()

        // When
        viewModel.updateItemTranscription("item1", "Hello world")
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.updateItemTranscription("item1", "Hello world") }
        assertEquals("Hello world", viewModel.playlistItems.value[0].transcription)
    }

    @Test
    fun `updateItemTranslation calls repository and updates items`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist("playlist1") } returns flowOf(listOf(testItem))
        viewModel.selectPlaylist(testPlaylist)
        advanceUntilIdle()

        // When
        viewModel.updateItemTranslation("item1", "Hola mundo")
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.updateItemTranslation("item1", "Hola mundo") }
        assertEquals("Hola mundo", viewModel.playlistItems.value[0].translation)
    }

    @Test
    fun `splitSegment success updates success message and refreshes items`() = runTest {
        // Given
        val newItem = testItem.copy(id = "item2")
        coEvery { libraryRepository.splitSegment(testItem, 2500L) } returns newItem
        every { libraryRepository.getItemsByPlaylist("playlist1") } returns flowOf(
            listOf(testItem)
        ) andThen flowOf(listOf(testItem, newItem))
        viewModel.selectPlaylist(testPlaylist)
        advanceUntilIdle()

        // When
        viewModel.splitSegment(testItem, 2500L)
        advanceUntilIdle()

        // Then
        assertEquals("Segment split into 2 parts", viewModel.importSuccess.value)
        coVerify { libraryRepository.splitSegment(testItem, 2500L) }
    }

    @Test
    fun `splitSegment failure updates error message`() = runTest {
        // Given
        coEvery { libraryRepository.splitSegment(testItem, 2500L) } returns null
        every { libraryRepository.getItemsByPlaylist("playlist1") } returns flowOf(listOf(testItem))
        viewModel.selectPlaylist(testPlaylist)
        advanceUntilIdle()

        // When
        viewModel.splitSegment(testItem, 2500L)
        advanceUntilIdle()

        // Then
        assertEquals("Failed to split segment", viewModel.importError.value)
    }

    @Test
    fun `toggleMergeSelection adds and removes items from selection`() = runTest {
        // When - add item1
        viewModel.toggleMergeSelection("item1")

        // Then
        assertTrue(viewModel.selectedForMerge.value.contains("item1"))

        // When - add item2
        viewModel.toggleMergeSelection("item2")

        // Then
        assertTrue(viewModel.selectedForMerge.value.contains("item1"))
        assertTrue(viewModel.selectedForMerge.value.contains("item2"))

        // When - remove item1
        viewModel.toggleMergeSelection("item1")

        // Then
        assertFalse(viewModel.selectedForMerge.value.contains("item1"))
        assertTrue(viewModel.selectedForMerge.value.contains("item2"))
    }

    @Test
    fun `clearMergeSelection clears all selected items`() = runTest {
        // Given
        viewModel.toggleMergeSelection("item1")
        viewModel.toggleMergeSelection("item2")

        // When
        viewModel.clearMergeSelection()

        // Then
        assertTrue(viewModel.selectedForMerge.value.isEmpty())
    }

    @Test
    fun `mergeSelectedSegments with less than 2 items shows error`() = runTest {
        // Given
        viewModel.toggleMergeSelection("item1")

        // When
        viewModel.mergeSelectedSegments()
        advanceUntilIdle()

        // Then
        assertEquals("Select at least 2 segments to merge", viewModel.importError.value)
        coVerify(exactly = 0) { libraryRepository.mergeSegments(any()) }
    }

    @Test
    fun `mergeSelectedSegments success updates success message`() = runTest {
        // Given
        val item2 = testItem.copy(id = "item2")
        val mergedItem = testItem.copy(id = "merged")
        every { libraryRepository.getItemsByPlaylist("playlist1") } returns flowOf(
            listOf(testItem, item2)
        ) andThen flowOf(listOf(mergedItem))
        coEvery { libraryRepository.mergeSegments(any()) } returns mergedItem
        viewModel.selectPlaylist(testPlaylist)
        advanceUntilIdle()
        viewModel.toggleMergeSelection("item1")
        viewModel.toggleMergeSelection("item2")

        // When
        viewModel.mergeSelectedSegments()
        advanceUntilIdle()

        // Then
        assertEquals("Merged 2 segments", viewModel.importSuccess.value)
        assertTrue(viewModel.selectedForMerge.value.isEmpty())
        coVerify { libraryRepository.mergeSegments(any()) }
    }

    @Test
    fun `exportPlaylist success updates success message`() = runTest {
        // Given
        val exportPath = "/storage/Music/ShadowMaster/test.wav"
        every { settingsRepository.configBlocking } returns ShadowingConfig()
        coEvery { 
            libraryRepository.exportPlaylist(
                playlistId = "playlist1",
                playlistName = "Test Playlist",
                config = any(),
                includeYourTurnSilence = true
            ) 
        } returns Result.success(exportPath)

        // When
        viewModel.exportPlaylist(testPlaylist, includeYourTurnSilence = true)
        advanceUntilIdle()

        // Then
        assertEquals("Exported to $exportPath", viewModel.importSuccess.value)
    }

    @Test
    fun `exportPlaylist failure updates error message`() = runTest {
        // Given
        val errorMessage = "Export failed"
        every { settingsRepository.configBlocking } returns ShadowingConfig()
        coEvery { 
            libraryRepository.exportPlaylist(
                playlistId = "playlist1",
                playlistName = "Test Playlist",
                config = any(),
                includeYourTurnSilence = true
            ) 
        } returns Result.failure(Exception(errorMessage))

        // When
        viewModel.exportPlaylist(testPlaylist)
        advanceUntilIdle()

        // Then
        assertEquals("Export failed: $errorMessage", viewModel.importError.value)
    }

    @Test
    fun `dismissFailedImport calls repository`() = runTest {
        // When
        viewModel.dismissFailedImport("job123")
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.deleteImportJob("job123") }
    }

    @Test
    fun `importFromUrl success updates success message`() = runTest {
        // Given
        val url = "https://example.com/audio.mp3"
        coEvery { 
            libraryRepository.importFromUrl(url, "auto") 
        } returns Result.success(Unit)

        // When
        viewModel.importFromUrl(url)
        advanceUntilIdle()

        // Then
        assertEquals("Import started successfully", viewModel.importSuccess.value)
        coVerify { libraryRepository.importFromUrl(url, "auto") }
    }

    @Test
    fun `importFromUri with http scheme delegates to importFromUrl`() = runTest {
        // Given
        val url = "http://example.com/audio.mp3"
        coEvery { 
            libraryRepository.importFromUrl(url, "auto") 
        } returns Result.success(Unit)

        // When
        viewModel.importFromUri(url)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.importFromUrl(url, "auto") }
        coVerify(exactly = 0) { libraryRepository.importAudioFile(any(), any(), any()) }
    }

    @Test
    fun `importFromUri with content scheme uses importAudioFile`() = runTest {
        // Given
        val uriString = "content://media/audio/123"
        coEvery { 
            libraryRepository.importAudioFile(any(), "auto", false) 
        } returns Result.success(Unit)

        // When
        viewModel.importFromUri(uriString)
        advanceUntilIdle()

        // Then
        coVerify { libraryRepository.importAudioFile(any(), "auto", false) }
        coVerify(exactly = 0) { libraryRepository.importFromUrl(any(), any()) }
    }
}
