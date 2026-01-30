package com.shadowmaster.ui.mic

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MicCaptureScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    viewModel: MicCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val captureState by viewModel.captureState.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val savedCount by viewModel.savedCount.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.startRecording()
        }
    }

    Scaffold(
        topBar = {
            MicCaptureTopBar(onBackClick = onNavigateBack)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                // Instructions
                if (captureState == MicCaptureState.IDLE) {
                    MicInstructionsCard()
                }

                // Status indicator
                MicStatusIndicator(
                    state = captureState,
                    duration = recordingDuration
                )

                // Main action button
                MicActionButton(
                    state = captureState,
                    hasPermission = hasPermission,
                    onStartRecording = {
                        if (hasPermission) {
                            viewModel.startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopRecording = { viewModel.stopRecording() }
                )

                // Error message
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }

                // Success message with navigation
                if (captureState == MicCaptureState.SAVED) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Recording saved to library!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (savedCount > 1) {
                                Text(
                                    text = "$savedCount recordings saved this session",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.resetCapture() }
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Record More")
                                }
                                Button(
                                    onClick = onNavigateToLibrary
                                ) {
                                    Text("Go to Library")
                                }
                            }
                        }
                    }
                }

                // Processing indicator
                if (captureState == MicCaptureState.PROCESSING) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Saving recording...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MicInstructionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Record from Microphone",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Use this to record audio from your device's microphone. " +
                       "Great for recording from external speakers or practicing pronunciation.\n\n" +
                       "Recording stops automatically after 1.5 seconds of silence or 30 seconds max.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicCaptureTopBar(
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = { Text("Record from Mic") },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
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

@Composable
private fun MicStatusIndicator(
    state: MicCaptureState,
    duration: Long
) {
    val (text, color) = when (state) {
        MicCaptureState.IDLE -> "Ready to record" to MaterialTheme.colorScheme.outline
        MicCaptureState.RECORDING -> "Recording..." to MaterialTheme.colorScheme.error
        MicCaptureState.PROCESSING -> "Processing..." to MaterialTheme.colorScheme.secondary
        MicCaptureState.SAVED -> "Saved!" to MaterialTheme.colorScheme.tertiary
        MicCaptureState.ERROR -> "Error occurred" to MaterialTheme.colorScheme.error
    }

    // Pulse animation for recording state
    val shouldPulse = state == MicCaptureState.RECORDING

    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
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
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        // Animated status dot
        Box(
            modifier = Modifier
                .size(20.dp)
                .scale(if (shouldPulse) scale else 1f)
                .background(color = color, shape = CircleShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        // Show duration while recording or after
        if (duration > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MicActionButton(
    state: MicCaptureState,
    hasPermission: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    when (state) {
        MicCaptureState.IDLE, MicCaptureState.ERROR -> {
            Button(
                onClick = onStartRecording,
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (hasPermission) "Start Recording" else "Grant Permission",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        MicCaptureState.RECORDING -> {
            Button(
                onClick = onStopRecording,
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Stop",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        MicCaptureState.PROCESSING, MicCaptureState.SAVED -> {
            // Button hidden during processing and after save
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
