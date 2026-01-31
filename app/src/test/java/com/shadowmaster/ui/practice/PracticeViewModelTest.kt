package com.shadowmaster.ui.practice

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.shadowmaster.data.model.*
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.feedback.AudioFeedbackSystem
import com.shadowmaster.library.LibraryRepository
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
 * Unit tests for PracticeViewModel.
 * Tests practice session management and state transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var context: Context
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var audioFeedbackSystem: AudioFeedbackSystem
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: PracticeViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testPlaylistId = "playlist123"
    private val testConfig = ShadowingConfig(
        playbackSpeed = 1.0f,
        playbackRepeats = 2,
        busMode = false,
        audioFeedbackEnabled = false
    )

    private val testItem = ShadowItem(
        id = "item1",
        sourceFileUri = "content://test",
        sourceFileName = "test.mp3",
        sourceStartMs = 0L,
        sourceEndMs = 5000L,
        audioFilePath = "/tmp/test_audio.pcm",
        durationMs = 5000L,
        playlistId = testPlaylistId
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        libraryRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        audioFeedbackSystem = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle(mapOf("playlistId" to testPlaylistId))

        // Mock flows
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(emptyList())
        every { libraryRepository.getImportJobForPlaylist(testPlaylistId) } returns null
        every { settingsRepository.config } returns flowOf(testConfig)
        every { settingsRepository.configBlocking } returns testConfig
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        // When
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )

        // Then
        assertEquals(PracticeState.Loading, viewModel.state.value)
    }

    @Test
    fun `loadPlaylist transitions to Ready when items loaded`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(
            listOf(testItem)
        )

        // When
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // Then
        assertEquals(PracticeState.Ready, viewModel.state.value)
        assertEquals(1, viewModel.items.value.size)
    }

    @Test
    fun `loadPlaylist sets items from repository`() = runTest {
        // Given
        val items = listOf(testItem, testItem.copy(id = "item2"))
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(items)

        // When
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // Then
        assertEquals(items, viewModel.items.value)
    }

    @Test
    fun `loadPlaylist checks import job status`() = runTest {
        // Given
        val importJob = ImportJob(
            id = "job1",
            sourceUri = "content://test",
            fileName = "test.mp3",
            status = ImportStatus.COMPLETED,
            targetPlaylistId = testPlaylistId
        )
        coEvery { libraryRepository.getImportJobForPlaylist(testPlaylistId) } returns importJob
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(emptyList())

        // When
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // Then
        assertEquals(ImportJobStatus.COMPLETED, viewModel.importJobStatus.value)
    }

    @Test
    fun `pauseResume toggles pause state`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(
            listOf(testItem)
        )
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // When - first call pauses
        viewModel.pauseResume()

        // Then
        assertEquals(PracticeState.Paused, viewModel.state.value)

        // When - second call resumes (state depends on practice loop)
        viewModel.pauseResume()

        // Then - should not be Paused anymore
        assertNotEquals(PracticeState.Paused, viewModel.state.value)
    }

    @Test
    fun `stop transitions state to Ready`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(
            listOf(testItem)
        )
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // When
        viewModel.stop()

        // Then
        assertEquals(PracticeState.Ready, viewModel.state.value)
    }

    @Test
    fun `currentItemIndex starts at 0`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(
            listOf(testItem)
        )

        // When
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // Then
        assertEquals(0, viewModel.currentItemIndex.value)
    }

    @Test
    fun `progress starts at 0`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(
            listOf(testItem)
        )

        // When
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // Then
        assertEquals(0f, viewModel.progress.value, 0.001f)
    }

    @Test
    fun `importJobStatus is UNKNOWN when no job found`() = runTest {
        // Given
        coEvery { libraryRepository.getImportJobForPlaylist(testPlaylistId) } returns null
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(emptyList())

        // When
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // Then
        assertEquals(ImportJobStatus.UNKNOWN, viewModel.importJobStatus.value)
    }

    @Test
    fun `importJobStatus is FAILED when job status is FAILED`() = runTest {
        // Given
        val failedJob = ImportJob(
            id = "job1",
            sourceUri = "content://test",
            fileName = "test.mp3",
            status = ImportStatus.FAILED,
            targetPlaylistId = testPlaylistId
        )
        coEvery { libraryRepository.getImportJobForPlaylist(testPlaylistId) } returns failedJob
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(emptyList())

        // When
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // Then
        assertEquals(ImportJobStatus.FAILED, viewModel.importJobStatus.value)
    }

    @Test
    fun `importJobStatus is ACTIVE when job is in progress`() = runTest {
        // Given
        val activeJob = ImportJob(
            id = "job1",
            sourceUri = "content://test",
            fileName = "test.mp3",
            status = ImportStatus.DETECTING_SEGMENTS,
            targetPlaylistId = testPlaylistId
        )
        coEvery { libraryRepository.getImportJobForPlaylist(testPlaylistId) } returns activeJob
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(emptyList())

        // When
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // Then
        assertEquals(ImportJobStatus.ACTIVE, viewModel.importJobStatus.value)
    }

    @Test
    fun `audioFeedbackSystem is initialized on ViewModel creation`() = runTest {
        // When
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )

        // Then
        verify { audioFeedbackSystem.initialize() }
    }

    @Test
    fun `startPractice does nothing when items list is empty`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(emptyList())
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // When
        viewModel.startPractice()
        advanceUntilIdle()

        // Then
        assertEquals(PracticeState.Ready, viewModel.state.value)
        coVerify(exactly = 0) { libraryRepository.markItemPracticed(any()) }
    }

    @Test
    fun `skipToItem updates current item index`() = runTest {
        // Given
        val items = listOf(
            testItem,
            testItem.copy(id = "item2"),
            testItem.copy(id = "item3")
        )
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(items)
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()

        // When
        viewModel.skipToItem(2)
        advanceUntilIdle()

        // Then
        assertEquals(2, viewModel.currentItemIndex.value)
    }

    @Test
    fun `skipToItem with invalid index does nothing`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(
            listOf(testItem)
        )
        viewModel = PracticeViewModel(
            context,
            libraryRepository,
            settingsRepository,
            audioFeedbackSystem,
            savedStateHandle
        )
        advanceUntilIdle()
        val initialIndex = viewModel.currentItemIndex.value

        // When
        viewModel.skipToItem(10) // Invalid index
        advanceUntilIdle()

        // Then
        assertEquals(initialIndex, viewModel.currentItemIndex.value)
    }
}
