package com.shadowmaster.ui.practice

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shadowmaster.data.model.ShadowItem
import com.shadowmaster.ui.practice.ImportJobStatus
import com.shadowmaster.ui.theme.ShadowMasterTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    onNavigateBack: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val items by viewModel.items.collectAsState()
    val currentIndex by viewModel.currentItemIndex.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val importJobStatus by viewModel.importJobStatus.collectAsState()
    val loopModeEndless by viewModel.loopModeEndless.collectAsState()
    val currentSpeed by viewModel.currentSpeed.collectAsState()
    val playbackRepeats by viewModel.playbackRepeats.collectAsState()
    val busMode by viewModel.busMode.collectAsState()

    val currentItem = items.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Practice Mode")
                        if (items.isNotEmpty()) {
                            Text(
                                text = "${currentIndex + 1} / ${items.size}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stop()
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(0.2f))

            // State indicator
            PracticeStateIndicator(state = state)

            Spacer(modifier = Modifier.height(32.dp))

            // Show message when playlist is empty
            if (items.isEmpty() && state is PracticeState.Ready) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val emptyState = when (importJobStatus) {
                            ImportJobStatus.FAILED -> {
                                EmptyStateConfig(
                                    icon = Icons.Default.Error,
                                    iconTint = MaterialTheme.colorScheme.error,
                                    title = "Import Failed",
                                    message = "The audio import failed. Please go back to the library to see the error details and try importing again."
                                )
                            }
                            ImportJobStatus.ACTIVE -> {
                                EmptyStateConfig(
                                    icon = Icons.Default.Info,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    title = "Import in Progress",
                                    message = "The audio is still being imported and segmented. Please wait a moment, then try again."
                                )
                            }
                            else -> {
                                EmptyStateConfig(
                                    icon = Icons.Default.Info,
                                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    title = "No Items in Playlist",
                                    message = "This playlist is empty. Go back to the library to add audio segments."
                                )
                            }
                        }

                        Icon(
                            imageVector = emptyState.icon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = emptyState.iconTint
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = emptyState.title,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = emptyState.iconTint
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = emptyState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Current segment info
                currentItem?.let { item ->
                    SegmentInfo(item = item)
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // Control buttons
            PracticeControls(
                state = state,
                items = items,
                onStart = { viewModel.startPractice() },
                onPauseResume = { viewModel.pauseResume() },
                onSkip = { viewModel.skip() },
                onStop = {
                    viewModel.stop()
                    onNavigateBack()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            LoopControls(
                loopModeEndless = loopModeEndless,
                currentSpeed = currentSpeed,
                playbackRepeats = playbackRepeats,
                busMode = busMode,
                itemCount = items.size,
                currentIndex = currentIndex,
                onToggleLoopMode = { viewModel.toggleLoopMode() },
                onSpeedChange = { viewModel.setSpeed(it) },
                onPlaybackRepeatsChange = { viewModel.setPlaybackRepeats(it) },
                onSaveSettings = { viewModel.saveSettings() },
                onSkipToItem = { viewModel.skipToItem(it) },
                onToggleBusMode = { viewModel.toggleBusMode() }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PracticeStateIndicator(state: PracticeState) {
    val (text, color, shouldPulse) = when (state) {
        is PracticeState.Loading -> Triple("Loading...", MaterialTheme.colorScheme.outline, false)
        is PracticeState.Ready -> Triple("Ready", MaterialTheme.colorScheme.primary, false)
        is PracticeState.Playing -> Triple(
            "Playing (${state.repeatNumber})",
            MaterialTheme.colorScheme.secondary,
            true
        )
        is PracticeState.WaitingForUser -> Triple(
            "Listen & Prepare",
            MaterialTheme.colorScheme.tertiary,
            true
        )
        is PracticeState.UserRecording -> Triple(
            "Your Turn! (${state.repeatNumber})",
            MaterialTheme.colorScheme.error,
            true
        )
        is PracticeState.Paused -> Triple("Paused", MaterialTheme.colorScheme.outline, false)
        is PracticeState.Finished -> Triple("Complete!", MaterialTheme.colorScheme.primary, false)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (shouldPulse) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .scale(if (shouldPulse) scale else 1f)
                .background(color = color, shape = CircleShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SegmentInfo(item: ShadowItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (item.transcription != null) {
                // Karaoke layout: transcription is the dominant element
                Text(
                    text = item.transcription,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )

                // Translation below transcription
                item.translation?.let { text ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Duration as secondary info
                Text(
                    text = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // No transcription: duration-focused layout
                Text(
                    text = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Practice count
            if (item.practiceCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Practiced ${item.practiceCount}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeControls(
    state: PracticeState,
    items: List<ShadowItem>,
    onStart: () -> Unit,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit
) {
    when (state) {
        is PracticeState.Ready, is PracticeState.Finished -> {
            Button(
                onClick = onStart,
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                enabled = items.isNotEmpty()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (state is PracticeState.Finished) "Restart" else "Start",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        is PracticeState.Loading -> {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
        }

        else -> {
            // Active practice controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stop button
                FilledTonalButton(
                    onClick = onStop,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop"
                    )
                }

                // Play/Pause button
                Button(
                    onClick = onPauseResume,
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (state is PracticeState.Paused) {
                            Icons.Default.PlayArrow
                        } else {
                            Icons.Default.Pause
                        },
                        contentDescription = if (state is PracticeState.Paused) "Resume" else "Pause",
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Skip button
                FilledTonalButton(
                    onClick = onSkip,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip"
                    )
                }
            }
        }
    }
}

@Composable
private fun LoopControls(
    loopModeEndless: Boolean,
    currentSpeed: Float,
    playbackRepeats: Int,
    busMode: Boolean,
    itemCount: Int,
    currentIndex: Int,
    onToggleLoopMode: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPlaybackRepeatsChange: (Int) -> Unit,
    onSaveSettings: () -> Unit,
    onSkipToItem: (Int) -> Unit,
    onToggleBusMode: () -> Unit
) {
    // Local scrubber state to avoid fighting with live currentIndex during drag
    var scrubbing by remember { mutableStateOf(false) }
    var scrubberPos by remember { mutableFloatStateOf(currentIndex.toFloat()) }
    LaunchedEffect(currentIndex) {
        if (!scrubbing) scrubberPos = currentIndex.toFloat()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Loop mode and bus mode toggles
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onToggleLoopMode) {
                Text(if (loopModeEndless) "🔁 Endless" else "🔢 Counted")
            }
            OutlinedButton(onClick = onToggleBusMode) {
                Text(if (busMode) "🚌 Bus" else "🎤 Active")
            }
        }

        // Speed slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Speed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${"%.1f".format(currentSpeed)}x",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = currentSpeed,
            onValueChange = onSpeedChange,
            onValueChangeFinished = onSaveSettings,
            valueRange = 0.5f..2.0f,
            steps = 29,
            modifier = Modifier.fillMaxWidth()
        )

        // Playback repeats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Repeats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        onPlaybackRepeatsChange(playbackRepeats - 1)
                        onSaveSettings()
                    },
                    enabled = playbackRepeats > 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Fewer repeats", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "$playbackRepeats",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                FilledTonalIconButton(
                    onClick = {
                        onPlaybackRepeatsChange(playbackRepeats + 1)
                        onSaveSettings()
                    },
                    enabled = playbackRepeats < 5,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "More repeats", modifier = Modifier.size(16.dp))
                }
            }
        }

        // Position scrubber
        if (itemCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Segment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(scrubberPos.roundToInt() + 1)} / $itemCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = scrubberPos,
                onValueChange = { scrubbing = true; scrubberPos = it },
                onValueChangeFinished = {
                    scrubbing = false
                    onSkipToItem(scrubberPos.roundToInt())
                },
                valueRange = 0f..(itemCount - 1).toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 60000) % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Configuration for empty state display in practice screen
 */
private data class EmptyStateConfig(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconTint: androidx.compose.ui.graphics.Color,
    val title: String,
    val message: String
)

// Preview Functions

@Preview(showBackground = true)
@Composable
fun PracticeStateIndicatorReadyPreview() {
    ShadowMasterTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                PracticeStateIndicator(state = PracticeState.Ready)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PracticeStateIndicatorPlayingPreview() {
    ShadowMasterTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                PracticeStateIndicator(state = PracticeState.Playing(itemIndex = 0, repeatNumber = 2))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PracticeStateIndicatorUserRecordingPreview() {
    ShadowMasterTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                PracticeStateIndicator(state = PracticeState.UserRecording(itemIndex = 0, repeatNumber = 1))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PracticeStateIndicatorPausedPreview() {
    ShadowMasterTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                PracticeStateIndicator(state = PracticeState.Paused)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SegmentInfoPreview() {
    ShadowMasterTheme {
        Surface {
            SegmentInfo(
                item = ShadowItem(
                    id = "1",
                    sourceFileUri = "content://test",
                    sourceFileName = "example.mp3",
                    sourceStartMs = 0,
                    sourceEndMs = 3500,
                    audioFilePath = "/path/to/audio",
                    durationMs = 3500,
                    transcription = "Hello, how are you today?",
                    translation = "こんにちは、今日はお元気ですか？",
                    language = "en-US",
                    practiceCount = 5
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SegmentInfoNoTranscriptionPreview() {
    ShadowMasterTheme {
        Surface {
            SegmentInfo(
                item = ShadowItem(
                    id = "1",
                    sourceFileUri = "content://test",
                    sourceFileName = "example.mp3",
                    sourceStartMs = 0,
                    sourceEndMs = 2000,
                    audioFilePath = "/path/to/audio",
                    durationMs = 2000,
                    language = "en-US",
                    practiceCount = 0
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PracticeControlsReadyPreview() {
    ShadowMasterTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PracticeControls(
                    state = PracticeState.Ready,
                    items = listOf(
                        ShadowItem(
                            id = "1",
                            sourceFileUri = "content://test",
                            sourceFileName = "example.mp3",
                            sourceStartMs = 0,
                            sourceEndMs = 3000,
                            audioFilePath = "/path/to/audio",
                            durationMs = 3000
                        )
                    ),
                    onStart = {},
                    onPauseResume = {},
                    onSkip = {},
                    onStop = {}
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PracticeControlsPlayingPreview() {
    ShadowMasterTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PracticeControls(
                    state = PracticeState.Playing(itemIndex = 0, repeatNumber = 1),
                    items = listOf(
                        ShadowItem(
                            id = "1",
                            sourceFileUri = "content://test",
                            sourceFileName = "example.mp3",
                            sourceStartMs = 0,
                            sourceEndMs = 3000,
                            audioFilePath = "/path/to/audio",
                            durationMs = 3000
                        )
                    ),
                    onStart = {},
                    onPauseResume = {},
                    onSkip = {},
                    onStop = {}
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PracticeControlsPausedPreview() {
    ShadowMasterTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PracticeControls(
                    state = PracticeState.Paused,
                    items = listOf(
                        ShadowItem(
                            id = "1",
                            sourceFileUri = "content://test",
                            sourceFileName = "example.mp3",
                            sourceStartMs = 0,
                            sourceEndMs = 3000,
                            audioFilePath = "/path/to/audio",
                            durationMs = 3000
                        )
                    ),
                    onStart = {},
                    onPauseResume = {},
                    onSkip = {},
                    onStop = {}
                )
            }
        }
    }
}
