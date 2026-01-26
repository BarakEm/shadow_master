package com.shadowmaster.ui.practice

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shadowmaster.data.model.ShadowItem

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
    val isLoopMode by viewModel.isLoopMode.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()

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
                actions = {
                    // Loop mode toggle
                    IconButton(onClick = { viewModel.toggleLoopMode() }) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = if (isLoopMode) "Disable loop" else "Enable loop",
                            tint = if (isLoopMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                            }
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

            Spacer(modifier = Modifier.weight(0.15f))

            // State indicator
            PracticeStateIndicator(state = state)

            Spacer(modifier = Modifier.height(24.dp))

            // Current segment info
            currentItem?.let { item ->
                SegmentInfo(
                    item = item,
                    isTranscribing = isTranscribing,
                    onTranscribe = { viewModel.transcribeCurrentSegment() }
                )
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Segment navigation controls
            SegmentNavigationControls(
                currentIndex = currentIndex,
                totalItems = items.size,
                isLoopMode = isLoopMode,
                onPrevious = { viewModel.previousSegment() },
                onNext = { viewModel.nextSegment() },
                onPlayCurrent = { viewModel.playCurrentSegment() },
                onToggleLoop = { viewModel.toggleLoopMode() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main control buttons
            PracticeControls(
                state = state,
                onStart = { viewModel.startPractice() },
                onPauseResume = { viewModel.pauseResume() },
                onSkip = { viewModel.skip() },
                onStop = {
                    viewModel.stop()
                    onNavigateBack()
                }
            )

            Spacer(modifier = Modifier.height(48.dp))
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
        is PracticeState.Transcribing -> Triple("Transcribing...", MaterialTheme.colorScheme.tertiary, true)
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
private fun SegmentInfo(
    item: ShadowItem,
    isTranscribing: Boolean,
    onTranscribe: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Duration
            Text(
                text = formatDuration(item.durationMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Transcription if available
            if (!item.transcription.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.transcription,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                // Transcribe button
                OutlinedButton(
                    onClick = onTranscribe,
                    enabled = !isTranscribing
                ) {
                    if (isTranscribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Transcribing...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Transcribe")
                    }
                }
            }

            // Translation if available
            item.translation?.let { text ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
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
private fun SegmentNavigationControls(
    currentIndex: Int,
    totalItems: Int,
    isLoopMode: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayCurrent: () -> Unit,
    onToggleLoop: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous segment
        FilledTonalIconButton(
            onClick = onPrevious,
            enabled = currentIndex > 0
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous segment"
            )
        }

        // Play current segment (single play)
        FilledIconButton(
            onClick = onPlayCurrent
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Play this segment"
            )
        }

        // Loop toggle
        FilledTonalIconButton(
            onClick = onToggleLoop,
            colors = if (isLoopMode) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            }
        ) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = if (isLoopMode) "Disable loop" else "Enable loop"
            )
        }

        // Next segment
        FilledTonalIconButton(
            onClick = onNext,
            enabled = currentIndex < totalItems - 1
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next segment"
            )
        }
    }
}

@Composable
private fun PracticeControls(
    state: PracticeState,
    onStart: () -> Unit,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit
) {
    when (state) {
        is PracticeState.Ready, is PracticeState.Finished -> {
            Button(
                onClick = onStart,
                modifier = Modifier.size(140.dp),
                shape = CircleShape
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = if (state is PracticeState.Finished) "Restart" else "Start",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        is PracticeState.Loading, is PracticeState.Transcribing -> {
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

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 60000) % 60
    return "%d:%02d".format(minutes, seconds)
}
