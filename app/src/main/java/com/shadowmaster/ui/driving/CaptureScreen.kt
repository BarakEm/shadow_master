package com.shadowmaster.ui.driving

import android.Manifest
import android.app.Activity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.shadowmaster.ui.theme.ShadowMasterTheme

@Composable
fun CaptureScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val captureState by viewModel.captureState.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val capturedDuration by viewModel.capturedDuration.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startCapture(result.resultCode, result.data!!)
        }
    }

    Scaffold(
        topBar = {
            CaptureTopBar(
                onBackClick = onNavigateBack,
                onSettingsClick = onNavigateToSettings
            )
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
                if (!isCapturing && captureState == CaptureState.IDLE) {
                    InstructionsCard()
                }

                // Status indicator
                CaptureStatusIndicator(
                    state = captureState,
                    duration = capturedDuration,
                    isCapturing = isCapturing
                )

                // Main action button
                CaptureActionButton(
                    state = captureState,
                    isCapturing = isCapturing,
                    onStartCapture = {
                        mediaProjectionLauncher.launch(viewModel.getMediaProjectionIntent())
                    },
                    onStopCapture = { viewModel.stopCapture() },
                    onSaveToLibrary = { viewModel.saveToLibrary() },
                    onDiscard = { viewModel.discardCapture() }
                )

                // Secondary actions
                if (captureState == CaptureState.CAPTURED) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.discardCapture() }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Discard")
                        }
                        Button(
                            onClick = { viewModel.saveToLibrary() }
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save to Library")
                        }
                    }
                }

                // Import progress
                if (captureState == CaptureState.IMPORTING) {
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
                            Text(
                                text = "Processing audio...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { importProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Success message with navigation
                if (captureState == CaptureState.SAVED) {
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
                                text = "Audio saved to library!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.resetCapture() }
                                ) {
                                    Text("Capture More")
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
            }
        }
    }
}

@Composable
private fun InstructionsCard() {
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
                text = "How to capture audio:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1. Start playing audio in another app (YouTube, Spotify, podcast app)\n" +
                       "2. Tap the capture button below\n" +
                       "3. Grant screen recording permission\n" +
                       "4. Stop when you're done - audio will be saved to your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureTopBar(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = { Text("Capture Playing Audio") },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun CaptureStatusIndicator(
    state: CaptureState,
    duration: Long,
    isCapturing: Boolean
) {
    val (text, color) = when (state) {
        CaptureState.IDLE -> "Ready to capture" to MaterialTheme.colorScheme.outline
        CaptureState.CAPTURING -> "Recording..." to MaterialTheme.colorScheme.error
        CaptureState.CAPTURED -> "Audio captured" to MaterialTheme.colorScheme.primary
        CaptureState.IMPORTING -> "Processing..." to MaterialTheme.colorScheme.secondary
        CaptureState.SAVED -> "Saved!" to MaterialTheme.colorScheme.tertiary
        CaptureState.ERROR -> "Error occurred" to MaterialTheme.colorScheme.error
    }

    // Pulse animation for recording state
    val shouldPulse = state == CaptureState.CAPTURING

    val infiniteTransition = rememberInfiniteTransition(label = "capturePulse")
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

        // Show duration while capturing or after capture
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
private fun CaptureActionButton(
    state: CaptureState,
    isCapturing: Boolean,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onSaveToLibrary: () -> Unit,
    onDiscard: () -> Unit
) {
    when (state) {
        CaptureState.IDLE -> {
            Button(
                onClick = onStartCapture,
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
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Start Capture",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        CaptureState.CAPTURING -> {
            Button(
                onClick = onStopCapture,
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
        CaptureState.CAPTURED, CaptureState.IMPORTING, CaptureState.SAVED, CaptureState.ERROR -> {
            // Actions handled elsewhere
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

enum class CaptureState {
    IDLE,
    CAPTURING,
    CAPTURED,
    IMPORTING,
    SAVED,
    ERROR
}

// Preview Functions

@Preview(showBackground = true)
@Composable
fun InstructionsCardPreview() {
    ShadowMasterTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                InstructionsCard()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CaptureStatusIndicatorIdlePreview() {
    ShadowMasterTheme {
        Surface {
            CaptureStatusIndicator(
                state = CaptureState.IDLE,
                duration = 0,
                isCapturing = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CaptureStatusIndicatorCapturingPreview() {
    ShadowMasterTheme {
        Surface {
            CaptureStatusIndicator(
                state = CaptureState.CAPTURING,
                duration = 45000,
                isCapturing = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CaptureStatusIndicatorCapturedPreview() {
    ShadowMasterTheme {
        Surface {
            CaptureStatusIndicator(
                state = CaptureState.CAPTURED,
                duration = 120000,
                isCapturing = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CaptureStatusIndicatorSavedPreview() {
    ShadowMasterTheme {
        Surface {
            CaptureStatusIndicator(
                state = CaptureState.SAVED,
                duration = 90000,
                isCapturing = false
            )
        }
    }
}
