package com.shadowmaster.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shadowmaster.data.model.*
import com.shadowmaster.library.ExportStatus
import com.shadowmaster.library.UrlImportStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateBack: () -> Unit,
    onStartPractice: (playlistId: String) -> Unit,
    importUrl: String? = null,
    importUri: String? = null,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val activeImports by viewModel.activeImports.collectAsState()
    val recentFailedImports by viewModel.recentFailedImports.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val playlistItems by viewModel.playlistItems.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val importSuccess by viewModel.importSuccess.collectAsState()
    val urlImportProgress by viewModel.urlImportProgress.collectAsState()
    val selectedForMerge by viewModel.selectedForMerge.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<ShadowPlaylist?>(null) }
    var showRenamePlaylistDialog by remember { mutableStateOf<ShadowPlaylist?>(null) }
    var showEditItemDialog by remember { mutableStateOf<ShadowItem?>(null) }
    var showSplitDialog by remember { mutableStateOf<ShadowItem?>(null) }
    var showExportDialog by remember { mutableStateOf<ShadowPlaylist?>(null) }
    var mergeMode by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var urlToImport by remember { mutableStateOf("") }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importAudioFile(it) }
    }

    // Handle shared content import
    LaunchedEffect(importUrl, importUri) {
        importUrl?.let { url ->
            viewModel.importFromUrl(url)
        }
        importUri?.let { uri ->
            viewModel.importFromUri(uri)
        }
    }

    // Show error snackbar
    importError?.let { error ->
        LaunchedEffect(error) {
            // Auto-dismiss after showing
            kotlinx.coroutines.delay(5000)
            viewModel.clearError()
        }
    }

    // Show success snackbar
    importSuccess?.let { success ->
        LaunchedEffect(success) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedPlaylist != null) selectedPlaylist!!.name else "Shadow Library"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPlaylist != null) {
                            viewModel.clearSelection()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedPlaylist != null) {
                        // Merge mode toggle
                        if (mergeMode) {
                            if (selectedForMerge.size >= 2) {
                                IconButton(onClick = { viewModel.mergeSelectedSegments() }) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Merge Selected"
                                    )
                                }
                            }
                            IconButton(onClick = {
                                mergeMode = false
                                viewModel.clearMergeSelection()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Merge"
                                )
                            }
                        } else {
                            IconButton(onClick = { mergeMode = true }) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Merge Mode"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (selectedPlaylist != null && !mergeMode) {
                // Show Practice FAB when viewing playlist details
                ExtendedFloatingActionButton(
                    onClick = { onStartPractice(selectedPlaylist!!.id) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Start Practice")
                }
            } else if (selectedPlaylist == null) {
                // Show Import FAB when viewing playlist list
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    FloatingActionButton(
                        onClick = { showMenu = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Import Audio"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import Audio File") },
                            onClick = {
                                showMenu = false
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            },
                            leadingIcon = {
                                Icon(Icons.Default.AudioFile, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import from URL") },
                            onClick = {
                                showMenu = false
                                showUrlImportDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedPlaylist != null) {
                // Show playlist items
                PlaylistDetailContent(
                    items = playlistItems,
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onEditItem = { showEditItemDialog = it },
                    onSplitItem = { showSplitDialog = it },
                    mergeMode = mergeMode,
                    selectedForMerge = selectedForMerge,
                    onToggleMergeSelection = { viewModel.toggleMergeSelection(it.id) }
                )
            } else {
                // Show playlists list
                PlaylistsContent(
                    playlists = playlists,
                    activeImports = activeImports,
                    failedImports = recentFailedImports,
                    onPlaylistClick = { viewModel.selectPlaylist(it) },
                    onDeleteClick = { showDeleteDialog = it },
                    onRenameClick = { showRenamePlaylistDialog = it },
                    onExportClick = { showExportDialog = it },
                    onStartPractice = onStartPractice,
                    onDismissFailedImport = { viewModel.dismissFailedImport(it) }
                )
            }

            // Error snackbar
            importError?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { playlist ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Playlist") },
            text = { Text("Delete \"${playlist.name}\" and all its items?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(playlist)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename playlist dialog
    showRenamePlaylistDialog?.let { playlist ->
        var newName by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRenamePlaylistDialog = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renamePlaylist(playlist.id, newName.trim())
                            showRenamePlaylistDialog = null
                        }
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenamePlaylistDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit item dialog (transcription/translation)
    showEditItemDialog?.let { item ->
        var transcription by remember { mutableStateOf(item.transcription ?: "") }
        var translation by remember { mutableStateOf(item.translation ?: "") }
        AlertDialog(
            onDismissRequest = { showEditItemDialog = null },
            title = { Text("Edit Segment") },
            text = {
                Column {
                    Text(
                        text = "Segment ${item.orderInPlaylist + 1} (${formatDuration(item.durationMs)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = transcription,
                        onValueChange = { transcription = it },
                        label = { Text("Transcription (original text)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = translation,
                        onValueChange = { translation = it },
                        label = { Text("Translation") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateItemTranscription(
                            item.id,
                            transcription.ifBlank { null }
                        )
                        viewModel.updateItemTranslation(
                            item.id,
                            translation.ifBlank { null }
                        )
                        showEditItemDialog = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditItemDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Split segment dialog
    showSplitDialog?.let { item ->
        var splitPosition by remember { mutableStateOf(item.durationMs / 2f) }
        AlertDialog(
            onDismissRequest = { showSplitDialog = null },
            title = { Text("Split Segment") },
            text = {
                Column {
                    Text(
                        text = "Segment ${item.orderInPlaylist + 1} (${formatDuration(item.durationMs)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Split point: ${formatDuration(splitPosition.toLong())}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = splitPosition,
                        onValueChange = { splitPosition = it },
                        valueRange = 500f..(item.durationMs - 500f).coerceAtLeast(501f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Part 1: ${formatDuration(splitPosition.toLong())}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Part 2: ${formatDuration((item.durationMs - splitPosition.toLong()))}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.splitSegment(item, splitPosition.toLong())
                        showSplitDialog = null
                    },
                    enabled = splitPosition >= 500f && splitPosition <= item.durationMs - 500f
                ) {
                    Text("Split")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSplitDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export dialog
    showExportDialog?.let { playlist ->
        var includeYourTurnSilence by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showExportDialog = null },
            title = { Text("Export Playlist") },
            text = {
                Column {
                    Text(
                        text = "Export \"${playlist.name}\" as a practice audio file",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "The exported file will include:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Beeps between segments\n• Playback repeats (from settings)\n• Your current speed settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeYourTurnSilence,
                            onCheckedChange = { includeYourTurnSilence = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Include silence for practice",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Adds silent gaps for you to shadow",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.exportPlaylist(playlist, includeYourTurnSilence)
                        showExportDialog = null
                    }
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export progress dialog
    if (exportProgress.status != ExportStatus.IDLE) {
        AlertDialog(
            onDismissRequest = {
                if (exportProgress.status == ExportStatus.COMPLETED || exportProgress.status == ExportStatus.FAILED) {
                    viewModel.clearExportProgress()
                }
            },
            title = { Text("Exporting Audio") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = when (exportProgress.status) {
                            ExportStatus.PREPARING -> "Preparing..."
                            ExportStatus.EXPORTING -> "Exporting segment ${exportProgress.currentSegment}/${exportProgress.totalSegments}"
                            ExportStatus.ENCODING -> "Creating audio file..."
                            ExportStatus.COMPLETED -> "Export complete!"
                            ExportStatus.FAILED -> "Export failed: ${exportProgress.error}"
                            ExportStatus.IDLE -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (exportProgress.status == ExportStatus.EXPORTING || exportProgress.status == ExportStatus.ENCODING) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { exportProgress.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (exportProgress.status == ExportStatus.COMPLETED && exportProgress.outputPath != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Saved to: ${exportProgress.outputPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                if (exportProgress.status == ExportStatus.COMPLETED || exportProgress.status == ExportStatus.FAILED) {
                    TextButton(onClick = { viewModel.clearExportProgress() }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (exportProgress.status != ExportStatus.COMPLETED && exportProgress.status != ExportStatus.FAILED) {
                    TextButton(onClick = { viewModel.cancelExport() }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // URL import dialog
    if (showUrlImportDialog) {
        AlertDialog(
            onDismissRequest = { showUrlImportDialog = false },
            title = { Text("Import from URL") },
            text = {
                Column {
                    Text(
                        "Paste a YouTube, SoundCloud, or direct audio URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = urlToImport,
                        onValueChange = { urlToImport = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (urlToImport.isNotBlank()) {
                            viewModel.importFromUrl(urlToImport)
                            showUrlImportDialog = false
                            urlToImport = ""
                        }
                    },
                    enabled = urlToImport.isNotBlank()
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUrlImportDialog = false
                    urlToImport = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // URL import progress dialog
    urlImportProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { /* Don't allow dismiss during import */ },
            title = { Text("Importing from URL") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    progress.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = when (progress.status) {
                            com.shadowmaster.library.UrlImportStatus.ANALYZING -> "Analyzing URL..."
                            com.shadowmaster.library.UrlImportStatus.EXTRACTING_INFO -> "Extracting info..."
                            com.shadowmaster.library.UrlImportStatus.DOWNLOADING -> "Downloading audio..."
                            com.shadowmaster.library.UrlImportStatus.PROCESSING -> "Processing audio..."
                            com.shadowmaster.library.UrlImportStatus.COMPLETED -> "Complete!"
                            com.shadowmaster.library.UrlImportStatus.FAILED -> "Failed: ${progress.error ?: "Unknown error"}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (progress.status == com.shadowmaster.library.UrlImportStatus.DOWNLOADING && progress.progress > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${progress.progress}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    } else if (progress.status != com.shadowmaster.library.UrlImportStatus.FAILED &&
                               progress.status != com.shadowmaster.library.UrlImportStatus.COMPLETED) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (progress.status == com.shadowmaster.library.UrlImportStatus.FAILED ||
                    progress.status == com.shadowmaster.library.UrlImportStatus.COMPLETED) {
                    TextButton(onClick = { viewModel.clearUrlImportProgress() }) {
                        Text("OK")
                    }
                }
            }
        )
    }
}

@Composable
private fun PlaylistsContent(
    playlists: List<ShadowPlaylist>,
    activeImports: List<ImportJob>,
    failedImports: List<ImportJob>,
    onPlaylistClick: (ShadowPlaylist) -> Unit,
    onDeleteClick: (ShadowPlaylist) -> Unit,
    onRenameClick: (ShadowPlaylist) -> Unit,
    onExportClick: (ShadowPlaylist) -> Unit,
    onStartPractice: (String) -> Unit,
    onDismissFailedImport: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active imports section
        if (activeImports.isNotEmpty()) {
            item {
                Text(
                    text = "Importing...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(activeImports, key = { it.id }) { job ->
                ImportJobCard(job = job)
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Failed imports section
        if (failedImports.isNotEmpty()) {
            item {
                Text(
                    text = "Import Errors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(failedImports, key = { it.id }) { job ->
                FailedImportCard(
                    job = job,
                    onDismiss = { onDismissFailedImport(job.id) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Playlists section
        if (playlists.isEmpty() && activeImports.isEmpty() && failedImports.isEmpty()) {
            item {
                EmptyLibraryMessage()
            }
        } else if (playlists.isNotEmpty()) {
            item {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    onDeleteClick = { onDeleteClick(playlist) },
                    onRenameClick = { onRenameClick(playlist) },
                    onExportClick = { onExportClick(playlist) },
                    onPlayClick = { onStartPractice(playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun ImportJobCard(job: ImportJob) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when (job.status) {
                            ImportStatus.EXTRACTING_AUDIO -> "Extracting audio..."
                            ImportStatus.DETECTING_SEGMENTS -> "Detecting speech segments..."
                            ImportStatus.TRANSCRIBING -> "Transcribing..."
                            else -> "Processing..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { job.progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FailedImportCard(job: ImportJob, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = job.errorMessage ?: "Import failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: ShadowPlaylist,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit,
    onExportClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist icon
            Icon(
                imageVector = Icons.Default.PlaylistPlay,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Playlist info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playlist.language.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    playlist.lastPracticedAt?.let {
                        Text(
                            text = " • Last: ${formatDate(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Primary action - Practice button
            FilledTonalButton(
                onClick = onPlayClick,
                modifier = Modifier.padding(end = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Practice", style = MaterialTheme.typography.labelMedium)
            }

            // Secondary actions - less prominent
            IconButton(onClick = onExportClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Export",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRenameClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    items: List<ShadowItem>,
    onToggleFavorite: (ShadowItem) -> Unit,
    onEditItem: (ShadowItem) -> Unit,
    onSplitItem: (ShadowItem) -> Unit,
    mergeMode: Boolean,
    selectedForMerge: Set<String>,
    onToggleMergeSelection: (ShadowItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No items in this playlist",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${items.size} segments",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mergeMode) {
                        Text(
                            text = "${selectedForMerge.size} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            items(items, key = { it.id }) { item ->
                ShadowItemCard(
                    item = item,
                    onToggleFavorite = { onToggleFavorite(item) },
                    onEditClick = { onEditItem(item) },
                    onSplitClick = { onSplitItem(item) },
                    mergeMode = mergeMode,
                    isSelectedForMerge = item.id in selectedForMerge,
                    onToggleMergeSelection = { onToggleMergeSelection(item) }
                )
            }
        }
    }
}

@Composable
private fun ShadowItemCard(
    item: ShadowItem,
    onToggleFavorite: () -> Unit,
    onEditClick: () -> Unit,
    onSplitClick: () -> Unit,
    mergeMode: Boolean,
    isSelectedForMerge: Boolean,
    onToggleMergeSelection: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (mergeMode) {
                    Modifier.clickable(onClick = onToggleMergeSelection)
                } else {
                    Modifier
                }
            ),
        colors = if (isSelectedForMerge) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Merge checkbox
            if (mergeMode) {
                Checkbox(
                    checked = isSelectedForMerge,
                    onCheckedChange = { onToggleMergeSelection() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Duration badge
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Item info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.transcription ?: "Segment ${item.orderInPlaylist + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Show translation if available
                item.translation?.let { translation ->
                    Text(
                        text = translation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.practiceCount > 0) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " ${item.practiceCount}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Show action buttons only when not in merge mode
            if (!mergeMode) {
                // Split button (only if segment is long enough)
                if (item.durationMs > 1000) {
                    IconButton(onClick = onSplitClick) {
                        Icon(
                            imageVector = Icons.Default.Splitscreen,
                            contentDescription = "Split",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Edit button
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Favorite button
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your Shadow Library is Empty",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Import audio files to create playlists for hands-free shadowing practice",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Tap + to import your first audio file",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 60000) % 60
    return if (minutes > 0) {
        "%d:%02d".format(minutes, seconds)
    } else {
        "%ds".format(seconds)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
