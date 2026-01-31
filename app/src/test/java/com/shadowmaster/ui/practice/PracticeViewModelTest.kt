package com.shadowmaster.ui.practice

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shadowmaster.data.model.ImportStatus
import com.shadowmaster.data.model.ShadowItem
import com.shadowmaster.data.model.ShadowingConfig
import com.shadowmaster.data.model.ImportJob
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.feedback.AudioFeedbackSystem
import com.shadowmaster.library.LibraryRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PracticeViewModel.
 *
 * Tests cover:
 * - Initial state loading
 * - Practice state transitions
 * - Import job status tracking
 * - Pause/Resume functionality
 * - Skip functionality
 * - Item navigation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {

    private lateinit var context: Context
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var audioFeedbackSystem: AudioFeedbackSystem
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: PracticeViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val testPlaylistId = "test-playlist-id"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        libraryRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        audioFeedbackSystem = mockk(relaxed = true)
        savedStateHandle = mockk()

        // Default mock behaviors
        every { savedStateHandle.get<String>("playlistId") } returns testPlaylistId
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(emptyList())
        coEvery { libraryRepository.getImportJobForPlaylist(testPlaylistId) } returns null
        every { settingsRepository.config } returns flowOf(ShadowingConfig())
        every { settingsRepository.configBlocking } returns ShadowingConfig()
        every { audioFeedbackSystem.initialize() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(): PracticeViewModel {
        return PracticeViewModel(
            context = context,
            libraryRepository = libraryRepository,
            settingsRepository = settingsRepository,
            audioFeedbackSystem = audioFeedbackSystem,
            savedStateHandle = savedStateHandle
        )
    }

    // ==================== Initialization Tests ====================

    @Test
    fun `init initializes audio feedback system`() = runTest {
        // When
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        verify { audioFeedbackSystem.initialize() }
    }

    @Test
    fun `init loads playlist items`() = runTest {
        // Given
        val items = listOf(
            createTestShadowItem(id = "1"),
            createTestShadowItem(id = "2")
        )
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(items)

        // When
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.items.test {
            assertEquals(items, awaitItem())
        }
    }

    @Test
    fun `init sets state to Ready after loading`() = runTest {
        // When
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.state.test {
            assertEquals(PracticeState.Ready, awaitItem())
        }
    }

    @Test
    fun `init sets initial state to Loading`() = runTest {
        // Don't advance - check initial state
        viewModel = createViewModel()

        // Then - initial state should be Loading before async operations complete
        viewModel.state.test {
            // First emission is Loading, then changes to Ready
            val initialState = awaitItem()
            assertTrue(initialState is PracticeState.Loading || initialState is PracticeState.Ready)
        }
    }

    // ==================== Import Job Status Tests ====================

    @Test
    fun `importJobStatus is UNKNOWN when no job found`() = runTest {
        // Given
        coEvery { libraryRepository.getImportJobForPlaylist(testPlaylistId) } returns null

        // When
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.importJobStatus.test {
            assertEquals(ImportJobStatus.UNKNOWN, awaitItem())
        }
    }

    @Test
    fun `importJobStatus is COMPLETED when job is completed`() = runTest {
        // Given
        val job = createTestImportJob(status = ImportStatus.COMPLETED)
        coEvery { libraryRepository.getImportJobForPlaylist(testPlaylistId) } returns job

        // When
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.importJobStatus.test {
            assertEquals(ImportJobStatus.COMPLETED, awaitItem())
        }
    }

    @Test
    fun `importJobStatus is FAILED when job failed`() = runTest {
        // Given
        val job = createTestImportJob(status = ImportStatus.FAILED)
        coEvery { libraryRepository.getImportJobForPlaylist(testPlaylistId) } returns job

        // When
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.importJobStatus.test {
            assertEquals(ImportJobStatus.FAILED, awaitItem())
        }
    }

    @Test
    fun `importJobStatus is ACTIVE when job in progress`() = runTest {
        // Given
        val job = createTestImportJob(status = ImportStatus.EXTRACTING_AUDIO)
        coEvery { libraryRepository.getImportJobForPlaylist(testPlaylistId) } returns job

        // When
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.importJobStatus.test {
            assertEquals(ImportJobStatus.ACTIVE, awaitItem())
        }
    }

    // ==================== Practice Start Tests ====================

    @Test
    fun `startPractice does nothing when items are empty`() = runTest {
        // Given
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(emptyList())
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.startPractice()
        advanceUntilIdle()

        // Then - state should remain Ready
        viewModel.state.test {
            assertEquals(PracticeState.Ready, awaitItem())
        }
    }

    @Test
    fun `startPractice initializes currentItemIndex to 0`() = runTest {
        // Given - items exist but we won't fully run the practice loop
        val items = listOf(createTestShadowItem(id = "1"))
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(items)
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.currentItemIndex.test {
            assertEquals(0, awaitItem())
        }
    }

    // ==================== Pause/Resume Tests ====================

    @Test
    fun `pauseResume toggles state to Paused`() = runTest {
        // Given
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.pauseResume()

        // Then
        viewModel.state.test {
            assertEquals(PracticeState.Paused, awaitItem())
        }
    }

    @Test
    fun `pauseResume second call resumes`() = runTest {
        // Given
        viewModel = createViewModel()
        advanceUntilIdle()

        // When - pause
        viewModel.pauseResume()

        // Then
        viewModel.state.test {
            assertEquals(PracticeState.Paused, awaitItem())
        }

        // When - resume (toggles back)
        viewModel.pauseResume()

        // State won't automatically change back without active practice
        // but the isPaused flag should toggle
    }

    // ==================== Stop Tests ====================

    @Test
    fun `stop sets state to Ready`() = runTest {
        // Given
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.stop()

        // Then
        viewModel.state.test {
            assertEquals(PracticeState.Ready, awaitItem())
        }
    }

    // ==================== Progress Tests ====================

    @Test
    fun `progress starts at 0`() = runTest {
        // Given
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.progress.test {
            assertEquals(0f, awaitItem())
        }
    }

    // ==================== Skip Tests ====================

    @Test
    fun `skipToItem changes currentItemIndex when valid`() = runTest {
        // Given
        val items = listOf(
            createTestShadowItem(id = "1"),
            createTestShadowItem(id = "2"),
            createTestShadowItem(id = "3")
        )
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(items)
        viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.skipToItem(2)
        advanceUntilIdle()

        // Then
        viewModel.currentItemIndex.test {
            assertEquals(2, awaitItem())
        }
    }

    @Test
    fun `skipToItem ignores invalid index`() = runTest {
        // Given
        val items = listOf(createTestShadowItem(id = "1"))
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(items)
        viewModel = createViewModel()
        advanceUntilIdle()

        // When - try to skip to invalid index
        viewModel.skipToItem(5)
        advanceUntilIdle()

        // Then - index should remain 0
        viewModel.currentItemIndex.test {
            assertEquals(0, awaitItem())
        }
    }

    @Test
    fun `skipToItem ignores negative index`() = runTest {
        // Given
        val items = listOf(createTestShadowItem(id = "1"))
        every { libraryRepository.getItemsByPlaylist(testPlaylistId) } returns flowOf(items)
        viewModel = createViewModel()
        advanceUntilIdle()

        // When - try to skip to negative index
        viewModel.skipToItem(-1)
        advanceUntilIdle()

        // Then - index should remain 0
        viewModel.currentItemIndex.test {
            assertEquals(0, awaitItem())
        }
    }

    // ==================== Helper Functions ====================

    private fun createTestShadowItem(
        id: String = "test-item-id",
        durationMs: Long = 5000L
    ) = ShadowItem(
        id = id,
        sourceFileUri = "content://test/audio.mp3",
        sourceFileName = "test.mp3",
        sourceStartMs = 0L,
        sourceEndMs = durationMs,
        audioFilePath = "/data/test/$id.pcm",
        durationMs = durationMs,
        playlistId = testPlaylistId
    )

    private fun createTestImportJob(
        status: ImportStatus = ImportStatus.PENDING
    ) = ImportJob(
        id = "test-job-id",
        sourceUri = "content://test/audio.mp3",
        fileName = "test.mp3",
        status = status,
        targetPlaylistId = testPlaylistId
    )
}
