package com.shadowmaster.ui.driving

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.shadowmaster.R
import com.shadowmaster.data.model.ShadowingState

@Composable
fun DrivingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DrivingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val config by viewModel.config.collectAsState()
    val isActive by viewModel.isSessionActive.collectAsState()

    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingProjectionResult by remember { mutableStateOf<Pair<Int, Intent>?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingProjectionResult?.let { (resultCode, data) ->
                viewModel.startSession(resultCode, data)
            }
        }
        pendingProjectionResult = null
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasAudioPermission) {
                viewModel.startSession(result.resultCode, result.data!!)
            } else {
                pendingProjectionResult = Pair(result.resultCode, result.data!!)
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Scaffold(
        topBar = {
            DrivingTopBar(
                languageName = config.language.displayName,
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
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Status indicator
                StatusIndicator(state = state)

                // Main action button
                MainActionButton(
                    isActive = isActive,
                    state = state,
                    onStart = {
                        mediaProjectionLauncher.launch(viewModel.getMediaProjectionIntent())
                    },
                    onStop = { viewModel.stopSession() }
                )

                // Skip button (only visible during active session)
                if (isActive && state !is ShadowingState.Listening) {
                    OutlinedButton(
                        onClick = { viewModel.skip() },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Skip")
                    }
                }
            }
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onConfirm = {
                showPermissionDialog = false
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrivingTopBar(
    languageName: String,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(languageName)
            }
        },
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
                    contentDescription = stringResource(R.string.settings)
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
private fun StatusIndicator(state: ShadowingState) {
    val (text, color) = when (state) {
        is ShadowingState.Idle -> Pair(stringResource(R.string.state_idle), MaterialTheme.colorScheme.outline)
        is ShadowingState.Listening -> Pair(stringResource(R.string.state_listening), MaterialTheme.colorScheme.primary)
        is ShadowingState.SegmentDetected -> Pair(stringResource(R.string.state_segment_detected), MaterialTheme.colorScheme.secondary)
        is ShadowingState.Playback -> Pair(
            "${stringResource(R.string.state_playback)} (${state.currentRepeat}/${state.totalRepeats})",
            MaterialTheme.colorScheme.secondary
        )
        is ShadowingState.UserRecording -> Pair(
            "${stringResource(R.string.state_user_recording)} (${state.currentRepeat}/${state.totalRepeats})",
            MaterialTheme.colorScheme.tertiary
        )
        is ShadowingState.Assessment -> Pair(stringResource(R.string.state_assessment), MaterialTheme.colorScheme.primary)
        is ShadowingState.Feedback -> Pair(stringResource(R.string.state_feedback),
            if (state.result.isGood) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
        is ShadowingState.PausedForNavigation -> Pair(stringResource(R.string.state_paused), MaterialTheme.colorScheme.outline)
    }

    // Pulse animation for active states
    val shouldPulse = state is ShadowingState.Listening ||
            state is ShadowingState.UserRecording ||
            state is ShadowingState.Assessment

    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (shouldPulse) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        // Animated status dot with pulse
        Box(
            modifier = Modifier
                .size(16.dp)
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

        // Show detailed score feedback when in Feedback state
        if (state is ShadowingState.Feedback) {
            Spacer(modifier = Modifier.height(16.dp))
            ScoreFeedbackCard(result = state.result)
        }
    }
}

@Composable
private fun ScoreFeedbackCard(result: com.shadowmaster.data.model.AssessmentResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (result.isGood)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Overall score - large display
            Text(
                text = "${result.overallScore.toInt()}%",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = if (result.isGood)
                    MaterialTheme.colorScheme.onTertiaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                text = if (result.isGood) "Good job!" else "Keep practicing!",
                style = MaterialTheme.typography.titleMedium,
                color = if (result.isGood)
                    MaterialTheme.colorScheme.onTertiaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Detail scores
            ScoreBar(label = "Pronunciation", score = result.pronunciationScore)
            ScoreBar(label = "Fluency", score = result.fluencyScore)
            ScoreBar(label = "Completeness", score = result.completenessScore)
        }
    }
}

@Composable
private fun ScoreBar(label: String, score: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(100.dp)
        )
        LinearProgressIndicator(
            progress = (score / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = when {
                score >= 80f -> MaterialTheme.colorScheme.tertiary
                score >= 60f -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
        )
        Text(
            text = "${score.toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun MainActionButton(
    isActive: Boolean,
    state: ShadowingState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val buttonColor = if (isActive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Button(
        onClick = { if (isActive) onStop() else onStart() },
        modifier = Modifier
            .size(200.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isActive) {
                    stringResource(R.string.stop_shadowing)
                } else {
                    stringResource(R.string.start_shadowing)
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_audio_title)) },
        text = { Text(stringResource(R.string.permission_audio_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.grant_permission))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
