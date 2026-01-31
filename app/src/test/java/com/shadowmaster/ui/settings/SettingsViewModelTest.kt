package com.shadowmaster.ui.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.shadowmaster.data.model.*
import com.shadowmaster.data.repository.SettingsRepository
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
 * Unit tests for SettingsViewModel.
 * Tests settings changes propagation to repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        
        // Mock config flow
        every { settingsRepository.config } returns flowOf(ShadowingConfig())
        
        viewModel = SettingsViewModel(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `updateLanguage calls repository with correct language`() = runTest {
        // Given
        val language = SupportedLanguage.GERMAN

        // When
        viewModel.updateLanguage(language)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateLanguage(language) }
    }

    @Test
    fun `updateSegmentMode calls repository with correct mode`() = runTest {
        // Given
        val mode = SegmentMode.WORD

        // When
        viewModel.updateSegmentMode(mode)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateSegmentMode(mode) }
    }

    @Test
    fun `updateSilenceThreshold calls repository with correct threshold`() = runTest {
        // Given
        val thresholdMs = 500

        // When
        viewModel.updateSilenceThreshold(thresholdMs)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateSilenceThreshold(thresholdMs) }
    }

    @Test
    fun `updatePlaybackSpeed calls repository with correct speed`() = runTest {
        // Given
        val speed = 1.2f

        // When
        viewModel.updatePlaybackSpeed(speed)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePlaybackSpeed(speed) }
    }

    @Test
    fun `updatePlaybackRepeats calls repository with correct repeats`() = runTest {
        // Given
        val repeats = 3

        // When
        viewModel.updatePlaybackRepeats(repeats)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePlaybackRepeats(repeats) }
    }

    @Test
    fun `updateUserRepeats calls repository with correct repeats`() = runTest {
        // Given
        val repeats = 2

        // When
        viewModel.updateUserRepeats(repeats)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateUserRepeats(repeats) }
    }

    @Test
    fun `updateAssessmentEnabled calls repository with correct enabled state`() = runTest {
        // Given
        val enabled = false

        // When
        viewModel.updateAssessmentEnabled(enabled)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateAssessmentEnabled(enabled) }
    }

    @Test
    fun `updatePauseForNavigation calls repository with correct enabled state`() = runTest {
        // Given
        val enabled = true

        // When
        viewModel.updatePauseForNavigation(enabled)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePauseForNavigation(enabled) }
    }

    @Test
    fun `updateBusMode calls repository with correct enabled state`() = runTest {
        // Given
        val enabled = true

        // When
        viewModel.updateBusMode(enabled)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateBusMode(enabled) }
    }

    @Test
    fun `updateAudioFeedbackEnabled calls repository with correct enabled state`() = runTest {
        // Given
        val enabled = false

        // When
        viewModel.updateAudioFeedbackEnabled(enabled)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateAudioFeedbackEnabled(enabled) }
    }

    @Test
    fun `updatePracticeMode calls repository with correct mode`() = runTest {
        // Given
        val mode = PracticeMode.BUILDUP

        // When
        viewModel.updatePracticeMode(mode)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePracticeMode(mode) }
    }

    @Test
    fun `updateBuildupChunkMs calls repository with correct value`() = runTest {
        // Given
        val chunkMs = 2000

        // When
        viewModel.updateBuildupChunkMs(chunkMs)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateBuildupChunkMs(chunkMs) }
    }

    @Test
    fun `config StateFlow emits repository config`() = runTest {
        // Given
        val expectedConfig = ShadowingConfig(
            language = SupportedLanguage.SPANISH,
            playbackSpeed = 0.9f
        )
        every { settingsRepository.config } returns flowOf(expectedConfig)

        // When
        val newViewModel = SettingsViewModel(settingsRepository)
        advanceUntilIdle()

        // Then
        assertEquals(expectedConfig, newViewModel.config.value)
    }
}
