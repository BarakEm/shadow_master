package com.shadowmaster.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shadowmaster.R
import com.shadowmaster.data.model.BeepToneType
import com.shadowmaster.data.model.PracticeMode
import com.shadowmaster.data.model.SegmentMode
import com.shadowmaster.data.model.ShadowingConfig
import com.shadowmaster.data.model.SupportedLanguage
import com.shadowmaster.ui.theme.ShadowMasterTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Language Selection
            LanguageSelector(
                selectedLanguage = config.language,
                onLanguageSelected = { viewModel.updateLanguage(it) }
            )

            HorizontalDivider()

            // Segment Mode Selection
            SegmentModeSelector(
                selectedMode = config.segmentMode,
                onModeSelected = { viewModel.updateSegmentMode(it) }
            )

            HorizontalDivider()

            // Playback Speed
            SliderSetting(
                title = stringResource(R.string.playback_speed),
                value = config.playbackSpeed,
                valueRange = ShadowingConfig.MIN_PLAYBACK_SPEED..ShadowingConfig.MAX_PLAYBACK_SPEED,
                steps = 15,
                valueLabel = { "${String.format("%.1f", it)}x" },
                onValueChange = { viewModel.updatePlaybackSpeed(it) }
            )

            // Playback Repeats
            IntSliderSetting(
                title = stringResource(R.string.playback_repeats),
                value = config.playbackRepeats,
                valueRange = ShadowingConfig.MIN_PLAYBACK_REPEATS..ShadowingConfig.MAX_PLAYBACK_REPEATS,
                onValueChange = { viewModel.updatePlaybackRepeats(it) }
            )

            // User Repeats (hidden in bus mode)
            if (!config.busMode) {
                IntSliderSetting(
                    title = stringResource(R.string.user_repeats),
                    value = config.userRepeats,
                    valueRange = ShadowingConfig.MIN_USER_REPEATS..ShadowingConfig.MAX_USER_REPEATS,
                    onValueChange = { viewModel.updateUserRepeats(it) }
                )
            }

            // Silence Threshold
            IntSliderSetting(
                title = stringResource(R.string.silence_threshold),
                value = config.silenceThresholdMs,
                valueRange = ShadowingConfig.MIN_SILENCE_THRESHOLD_MS..ShadowingConfig.MAX_SILENCE_THRESHOLD_MS,
                steps = 12,
                valueLabel = { "${it}ms" },
                onValueChange = { viewModel.updateSilenceThreshold(it) }
            )

            HorizontalDivider()

            // Bus Mode Toggle (most prominent - at the top of modes)
            SwitchSetting(
                title = stringResource(R.string.bus_mode),
                subtitle = stringResource(R.string.bus_mode_desc),
                checked = config.busMode,
                onCheckedChange = { viewModel.updateBusMode(it) }
            )

            // Practice Mode (hidden in bus mode)
            if (!config.busMode) {
                PracticeModeSelector(
                    selectedMode = config.practiceMode,
                    onModeSelected = { viewModel.updatePracticeMode(it) }
                )

                // Buildup chunk size (only visible in buildup mode)
                if (config.practiceMode == PracticeMode.BUILDUP) {
                    IntSliderSetting(
                        title = "Buildup Chunk Size",
                        value = config.buildupChunkMs,
                        valueRange = 500..3000,
                        steps = 9,
                        valueLabel = { "${it}ms" },
                        onValueChange = { viewModel.updateBuildupChunkMs(it) }
                    )
                }
            }

            // Assessment Toggle (disabled if bus mode is on)
            if (!config.busMode) {
                SwitchSetting(
                    title = stringResource(R.string.assessment_enabled),
                    subtitle = "Evaluate pronunciation after recording",
                    checked = config.assessmentEnabled,
                    onCheckedChange = { viewModel.updateAssessmentEnabled(it) }
                )
            }

            HorizontalDivider()

            // Audio Feedback Toggle
            SwitchSetting(
                title = stringResource(R.string.audio_feedback),
                subtitle = stringResource(R.string.audio_feedback_desc),
                checked = config.audioFeedbackEnabled,
                onCheckedChange = { viewModel.updateAudioFeedbackEnabled(it) }
            )

            // Beep Customization (only visible when audio feedback is enabled)
            if (config.audioFeedbackEnabled) {
                // Beep Volume
                IntSliderSetting(
                    title = "Beep Volume",
                    value = config.beepVolume,
                    valueRange = ShadowingConfig.MIN_BEEP_VOLUME..ShadowingConfig.MAX_BEEP_VOLUME,
                    steps = 10,
                    valueLabel = { "$it%" },
                    onValueChange = { viewModel.updateBeepVolume(it) }
                )

                // Beep Tone Type
                BeepToneTypeSelector(
                    selectedToneType = config.beepToneType,
                    onToneTypeSelected = { viewModel.updateBeepToneType(it) }
                )

                // Beep Duration
                IntSliderSetting(
                    title = "Beep Duration",
                    value = config.beepDurationMs,
                    valueRange = ShadowingConfig.MIN_BEEP_DURATION_MS..ShadowingConfig.MAX_BEEP_DURATION_MS,
                    steps = 9,
                    valueLabel = { "${it}ms" },
                    onValueChange = { viewModel.updateBeepDurationMs(it) }
                )
            }

            // Navigation Pause Toggle
            SwitchSetting(
                title = stringResource(R.string.pause_for_navigation),
                subtitle = "Pause when navigation apps speak",
                checked = config.pauseForNavigation,
                onCheckedChange = { viewModel.updatePauseForNavigation(it) }
            )
        }
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: SupportedLanguage,
    onLanguageSelected: (SupportedLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.language),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = selectedLanguage.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SupportedLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    },
                    leadingIcon = {
                        if (language == selectedLanguage) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueLabel: (Float) -> String = { it.toString() },
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun IntSliderSetting(
    title: String,
    value: Int,
    valueRange: IntRange,
    steps: Int = valueRange.last - valueRange.first - 1,
    valueLabel: (Int) -> String = { it.toString() },
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SegmentModeSelector(
    selectedMode: SegmentMode,
    onModeSelected: (SegmentMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Segment Mode",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        SegmentMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = mode == selectedMode,
                    onClick = { onModeSelected(mode) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = when (mode) {
                            SegmentMode.WORD -> "Word"
                            SegmentMode.SENTENCE -> "Sentence"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = when (mode) {
                            SegmentMode.WORD -> "Short segments for individual words"
                            SegmentMode.SENTENCE -> "Longer segments for complete sentences"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeModeSelector(
    selectedMode: PracticeMode,
    onModeSelected: (PracticeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Practice Mode",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        PracticeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = mode == selectedMode,
                    onClick = { onModeSelected(mode) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = when (mode) {
                            PracticeMode.STANDARD -> "Standard"
                            PracticeMode.BUILDUP -> "Gradual Buildup"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = when (mode) {
                            PracticeMode.STANDARD -> "Play full segment, then repeat"
                            PracticeMode.BUILDUP -> "Build from end of phrase to full sentence"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BeepToneTypeSelector(
    selectedToneType: BeepToneType,
    onToneTypeSelected: (BeepToneType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Beep Tone",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        BeepToneType.entries.forEach { toneType ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToneTypeSelected(toneType) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = toneType == selectedToneType,
                    onClick = { onToneTypeSelected(toneType) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = toneType.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// Preview Functions

@Preview(showBackground = true)
@Composable
fun LanguageSelectorPreview() {
    ShadowMasterTheme {
        Surface {
            LanguageSelector(
                selectedLanguage = SupportedLanguage.ENGLISH_US,
                onLanguageSelected = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SliderSettingPreview() {
    ShadowMasterTheme {
        Surface {
            SliderSetting(
                title = "Playback Speed",
                value = 0.8f,
                valueRange = 0.5f..2.0f,
                steps = 15,
                valueLabel = { "${String.format("%.1f", it)}x" },
                onValueChange = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IntSliderSettingPreview() {
    ShadowMasterTheme {
        Surface {
            IntSliderSetting(
                title = "Playback Repeats",
                value = 2,
                valueRange = 1..5,
                onValueChange = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SegmentModeSelectorPreview() {
    ShadowMasterTheme {
        Surface {
            SegmentModeSelector(
                selectedMode = SegmentMode.SENTENCE,
                onModeSelected = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PracticeModeSelectorPreview() {
    ShadowMasterTheme {
        Surface {
            PracticeModeSelector(
                selectedMode = PracticeMode.STANDARD,
                onModeSelected = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SwitchSettingPreview() {
    ShadowMasterTheme {
        Surface {
            SwitchSetting(
                title = "Bus Mode",
                subtitle = "Listen-only mode without recording",
                checked = true,
                onCheckedChange = {}
            )
        }
    }
}
