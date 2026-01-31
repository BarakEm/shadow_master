package com.shadowmaster.ui.settings

import app.cash.turbine.test
import com.shadowmaster.data.model.PracticeMode
import com.shadowmaster.data.model.SegmentMode
import com.shadowmaster.data.model.ShadowingConfig
import com.shadowmaster.data.model.SupportedLanguage
import com.shadowmaster.data.repository.SettingsRepository
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
 * Unit tests for SettingsViewModel.
 *
 * Tests cover:
 * - Config state management
 * - Language updates
 * - Segment mode updates
 * - Playback settings (speed, repeats)
 * - Practice mode settings
 * - Feature toggles (assessment, pause for navigation, bus mode, audio feedback)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: SettingsViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)

        // Default mock behavior
        every { settingsRepository.config } returns flowOf(ShadowingConfig())

        viewModel = SettingsViewModel(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ==================== Config State Tests ====================

    @Test
    fun `config state flows from repository`() = runTest {
        // Given
        val expectedConfig = ShadowingConfig(
            playbackSpeed = 0.75f,
            playbackRepeats = 3
        )
        every { settingsRepository.config } returns flowOf(expectedConfig)

        // When
        val viewModel = SettingsViewModel(settingsRepository)
        advanceUntilIdle()

        // Then
        viewModel.config.test {
            val config = awaitItem()
            assertEquals(0.75f, config.playbackSpeed)
            assertEquals(3, config.playbackRepeats)
        }
    }

    @Test
    fun `config defaults to empty ShadowingConfig`() = runTest {
        // Given - use default mock which returns empty config

        // When
        advanceUntilIdle()

        // Then
        viewModel.config.test {
            val config = awaitItem()
            assertEquals(ShadowingConfig(), config)
        }
    }

    // ==================== Language Update Tests ====================

    @Test
    fun `updateLanguage calls repository`() = runTest {
        // Given
        val language = SupportedLanguage.JAPANESE

        // When
        viewModel.updateLanguage(language)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateLanguage(language) }
    }

    @Test
    fun `updateLanguage handles all supported languages`() = runTest {
        // Test each supported language
        SupportedLanguage.entries.forEach { language ->
            // When
            viewModel.updateLanguage(language)
            advanceUntilIdle()

            // Then
            coVerify { settingsRepository.updateLanguage(language) }
        }
    }

    // ==================== Segment Mode Tests ====================

    @Test
    fun `updateSegmentMode calls repository with WORD mode`() = runTest {
        // When
        viewModel.updateSegmentMode(SegmentMode.WORD)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateSegmentMode(SegmentMode.WORD) }
    }

    @Test
    fun `updateSegmentMode calls repository with SENTENCE mode`() = runTest {
        // When
        viewModel.updateSegmentMode(SegmentMode.SENTENCE)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateSegmentMode(SegmentMode.SENTENCE) }
    }

    // ==================== Silence Threshold Tests ====================

    @Test
    fun `updateSilenceThreshold calls repository`() = runTest {
        // Given
        val thresholdMs = 500

        // When
        viewModel.updateSilenceThreshold(thresholdMs)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateSilenceThreshold(thresholdMs) }
    }

    @Test
    fun `updateSilenceThreshold handles various values`() = runTest {
        // Test various threshold values
        listOf(300, 500, 700, 1000).forEach { threshold ->
            // When
            viewModel.updateSilenceThreshold(threshold)
            advanceUntilIdle()

            // Then
            coVerify { settingsRepository.updateSilenceThreshold(threshold) }
        }
    }

    // ==================== Playback Speed Tests ====================

    @Test
    fun `updatePlaybackSpeed calls repository`() = runTest {
        // Given
        val speed = 0.75f

        // When
        viewModel.updatePlaybackSpeed(speed)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePlaybackSpeed(speed) }
    }

    @Test
    fun `updatePlaybackSpeed handles various speeds`() = runTest {
        // Test various speed values
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
            // When
            viewModel.updatePlaybackSpeed(speed)
            advanceUntilIdle()

            // Then
            coVerify { settingsRepository.updatePlaybackSpeed(speed) }
        }
    }

    // ==================== Repeat Settings Tests ====================

    @Test
    fun `updatePlaybackRepeats calls repository`() = runTest {
        // Given
        val repeats = 3

        // When
        viewModel.updatePlaybackRepeats(repeats)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePlaybackRepeats(repeats) }
    }

    @Test
    fun `updateUserRepeats calls repository`() = runTest {
        // Given
        val repeats = 2

        // When
        viewModel.updateUserRepeats(repeats)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateUserRepeats(repeats) }
    }

    @Test
    fun `updatePlaybackRepeats handles edge cases`() = runTest {
        // Test minimum and maximum values
        listOf(1, 5, 10).forEach { repeats ->
            // When
            viewModel.updatePlaybackRepeats(repeats)
            advanceUntilIdle()

            // Then
            coVerify { settingsRepository.updatePlaybackRepeats(repeats) }
        }
    }

    // ==================== Feature Toggle Tests ====================

    @Test
    fun `updateAssessmentEnabled calls repository with true`() = runTest {
        // When
        viewModel.updateAssessmentEnabled(true)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateAssessmentEnabled(true) }
    }

    @Test
    fun `updateAssessmentEnabled calls repository with false`() = runTest {
        // When
        viewModel.updateAssessmentEnabled(false)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateAssessmentEnabled(false) }
    }

    @Test
    fun `updatePauseForNavigation calls repository with true`() = runTest {
        // When
        viewModel.updatePauseForNavigation(true)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePauseForNavigation(true) }
    }

    @Test
    fun `updatePauseForNavigation calls repository with false`() = runTest {
        // When
        viewModel.updatePauseForNavigation(false)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePauseForNavigation(false) }
    }

    @Test
    fun `updateBusMode calls repository with true`() = runTest {
        // When
        viewModel.updateBusMode(true)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateBusMode(true) }
    }

    @Test
    fun `updateBusMode calls repository with false`() = runTest {
        // When
        viewModel.updateBusMode(false)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateBusMode(false) }
    }

    @Test
    fun `updateAudioFeedbackEnabled calls repository with true`() = runTest {
        // When
        viewModel.updateAudioFeedbackEnabled(true)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateAudioFeedbackEnabled(true) }
    }

    @Test
    fun `updateAudioFeedbackEnabled calls repository with false`() = runTest {
        // When
        viewModel.updateAudioFeedbackEnabled(false)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateAudioFeedbackEnabled(false) }
    }

    // ==================== Practice Mode Tests ====================

    @Test
    fun `updatePracticeMode calls repository with STANDARD mode`() = runTest {
        // When
        viewModel.updatePracticeMode(PracticeMode.STANDARD)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePracticeMode(PracticeMode.STANDARD) }
    }

    @Test
    fun `updatePracticeMode calls repository with BUILDUP mode`() = runTest {
        // When
        viewModel.updatePracticeMode(PracticeMode.BUILDUP)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePracticeMode(PracticeMode.BUILDUP) }
    }

    // ==================== Buildup Settings Tests ====================

    @Test
    fun `updateBuildupChunkMs calls repository`() = runTest {
        // Given
        val chunkMs = 1500

        // When
        viewModel.updateBuildupChunkMs(chunkMs)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateBuildupChunkMs(chunkMs) }
    }

    @Test
    fun `updateBuildupChunkMs handles various chunk sizes`() = runTest {
        // Test various chunk sizes
        listOf(500, 1000, 1500, 2000, 3000).forEach { chunkMs ->
            // When
            viewModel.updateBuildupChunkMs(chunkMs)
            advanceUntilIdle()

            // Then
            coVerify { settingsRepository.updateBuildupChunkMs(chunkMs) }
        }
    }
}
