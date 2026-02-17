package com.shadowmaster.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shadowmaster.data.model.*
import com.shadowmaster.library.ExportStatus
import com.shadowmaster.library.InputValidator
import com.shadowmaster.library.UrlImportStatus
import com.shadowmaster.ui.components.TextInputDialog
import com.shadowmaster.ui.theme.ShadowMasterTheme
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
    val importedAudio by viewModel.importedAudio.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf<ShadowPlaylist?>(null) }
    var showRenamePlaylistDialog by remember { mutableStateOf<ShadowPlaylist?>(null) }
    var showSplitDialog by remember { mutableStateOf<ShadowItem?>(null) }
    var showExportDialog by remember { mutableStateOf<ShadowPlaylist?>(null) }
    var mergeMode by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var urlToImport by remember { mutableStateOf("") }
    var showCreatePlaylistDialog by remember { mutableStateOf<ImportedAudio?>(null) }
    var showDeleteAudioDialog by remember { mutableStateOf<ImportedAudio?>(null) }

    // Stable callbacks to prevent recomposition
    val onImportAudioFile = remember(viewModel) {
        { uri: Uri -> viewModel.importAudioFile(uri) }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImportAudioFile(it) }
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
                    val onBackClick = remember(selectedPlaylist) {
                        {
                            if (selectedPlaylist != null) {
                                viewModel.clearSelection()
                            } else {
                                onNavigateBack()
                            }
                        }
                    }
                    IconButton(onClick = onBackClick) {
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
                val playlist = selectedPlaylist!!  // Safe to !! because of null check above
                ExtendedFloatingActionButton(
                    onClick = { onStartPractice(playlist.id) },
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
                ImportFab(
                    onImportAudioFile = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                    onImportFromUrl = { showUrlImportDialog = true }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show tabs only when not viewing playlist detail
            if (selectedPlaylist == null) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Playlists") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Imported Audio") }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (selectedPlaylist != null) {
                    // Show playlist items
                    PlaylistDetailContent(
                        items = playlistItems,
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onSplitItem = { showSplitDialog = it },
                        mergeMode = mergeMode,
                        selectedForMerge = selectedForMerge,
                        onToggleMergeSelection = { viewModel.toggleMergeSelection(it.id) }
                    )
                } else {
                    // Show content based on selected tab
                    when (selectedTab) {
                        0 -> PlaylistsContent(
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
                        1 -> ImportedAudioContent(
                            importedAudio = importedAudio,
                            activeImports = activeImports,
                            failedImports = recentFailedImports,
                            onCreatePlaylist = { showCreatePlaylistDialog = it },
                            onDeleteAudio = { showDeleteAudioDialog = it },
                            onDismissFailedImport = { viewModel.dismissFailedImport(it) }
                        )
                    }
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
                
                // Success snackbar
                importSuccess?.let { success ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.clearSuccess() }) {
                                Text("Dismiss")
                            }
                        }
                    ) {
                        Text(success)
                    }
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

    // Rename playlist dialog with validation
    showRenamePlaylistDialog?.let { playlist ->
        TextInputDialog(
            title = "Rename Playlist",
            label = "Name",
            initialValue = playlist.name,
            confirmText = "Rename",
            validator = { name ->
                val result = InputValidator.validateName(name)
                result.exceptionOrNull()?.message
            },
            onConfirm = { newName ->
                viewModel.renamePlaylist(playlist.id, newName)
                showRenamePlaylistDialog = null
            },
            onDismiss = { showRenamePlaylistDialog = null }
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
        var selectedFormat by remember { mutableStateOf(com.shadowmaster.library.ExportFormat.MP3) }

        // Generate filename information to display to user
        val sanitizedName = playlist.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileExtension = when (selectedFormat) {
            com.shadowmaster.library.ExportFormat.AAC -> "aac"
            com.shadowmaster.library.ExportFormat.MP3 -> "mp3"
            com.shadowmaster.library.ExportFormat.WAV -> "wav"
        }
        val saveLocation = "Music/ShadowMaster/"
        
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
                    
                    // Format selection
                    Text(
                        text = "Format:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFormat == com.shadowmaster.library.ExportFormat.MP3,
                            onClick = { selectedFormat = com.shadowmaster.library.ExportFormat.MP3 },
                            label = { Text("MP3") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedFormat == com.shadowmaster.library.ExportFormat.AAC,
                            onClick = { selectedFormat = com.shadowmaster.library.ExportFormat.AAC },
                            label = { Text("AAC") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedFormat == com.shadowmaster.library.ExportFormat.WAV,
                            onClick = { selectedFormat = com.shadowmaster.library.ExportFormat.WAV },
                            label = { Text("WAV") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Save location information
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "File will be saved to:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = saveLocation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Filename pattern:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ShadowMaster_${sanitizedName}_<timestamp>.$fileExtension",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                    
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
                        viewModel.exportPlaylist(playlist, includeYourTurnSilence, selectedFormat)
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
        // Share launcher for exporting
        val shareLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { /* Share completed */ }
        
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
                if (exportProgress.status == ExportStatus.COMPLETED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Share button
                        if (exportProgress.outputUri != null) {
                            TextButton(
                                onClick = {
                                    val shareIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_STREAM, exportProgress.outputUri)
                                        type = "audio/*"
                                        flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    shareLauncher.launch(android.content.Intent.createChooser(shareIntent, "Share Audio"))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                        TextButton(onClick = { viewModel.clearExportProgress() }) {
                            Text("OK")
                        }
                    }
                } else if (exportProgress.status == ExportStatus.FAILED) {
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
                            com.shadowmaster.library.UrlImportStatus.SCANNING_PAGE -> "Scanning page for audio..."
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

    // Create playlist from imported audio dialog
    showCreatePlaylistDialog?.let { audio ->
        var playlistName by remember { mutableStateOf(audio.sourceFileName.substringBeforeLast(".")) }
        val presets = remember { com.shadowmaster.library.SegmentationPresets.getAllPresets() }
        var selectedPreset by remember { mutableStateOf<SegmentationConfig?>(presets.firstOrNull()) }

        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = null },
            title = { Text("Create Playlist") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Playlist Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Segmentation Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    presets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPreset = preset }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preset.id == selectedPreset?.id,
                                onClick = { selectedPreset = preset }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Mode: ${preset.segmentMode.name}, " +
                                           "Min: ${preset.minSegmentDurationMs}ms, " +
                                           "Max: ${preset.maxSegmentDurationMs}ms",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = playlistName.trim()
                        selectedPreset?.let { preset ->
                            if (name.isNotEmpty()) {
                                viewModel.createPlaylistFromImportedAudio(
                                    audio.id,
                                    name,
                                    preset
                                )
                                showCreatePlaylistDialog = null
                            }
                        }
                    },
                    enabled = playlistName.trim().isNotEmpty() && selectedPreset != null
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete imported audio confirmation dialog
    showDeleteAudioDialog?.let { audio ->
        AlertDialog(
            onDismissRequest = { showDeleteAudioDialog = null },
            title = { Text("Delete Imported Audio") },
            text = { 
                Column {
                    Text("Delete \"${audio.sourceFileName}\"?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This will delete the original imported audio. This cannot be undone!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteImportedAudio(audio)
                        showDeleteAudioDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAudioDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ImportFab(
    onImportAudioFile: () -> Unit,
    onImportFromUrl: () -> Unit
) {
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
                    onImportAudioFile()
                },
                leadingIcon = {
                    Icon(Icons.Default.AudioFile, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Import from URL") },
                onClick = {
                    showMenu = false
                    onImportFromUrl()
                },
                leadingIcon = {
                    Icon(Icons.Default.Link, contentDescription = null)
                }
            )
        }
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
private fun ImportedAudioContent(
    importedAudio: List<ImportedAudio>,
    activeImports: List<ImportJob>,
    failedImports: List<ImportJob>,
    onCreatePlaylist: (ImportedAudio) -> Unit,
    onDeleteAudio: (ImportedAudio) -> Unit,
    onDismissFailedImport: (String) -> Unit
) {
    if (importedAudio.isEmpty() && activeImports.isEmpty() && failedImports.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No imported audio files",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Import audio files to create playlists",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
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

            // Imported audio files
            items(importedAudio, key = { it.id }) { audio ->
                ImportedAudioCard(
                    audio = audio,
                    onCreatePlaylist = { onCreatePlaylist(audio) },
                    onDelete = { onDeleteAudio(audio) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportedAudioCard(
    audio: ImportedAudio,
    onCreatePlaylist: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* No single click action */ },
                onLongClick = { showMenu = true }
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = audio.sourceFileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = formatDuration(audio.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDate(audio.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (audio.segmentationCount > 0) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${audio.segmentationCount} playlist${if (audio.segmentationCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Button(onClick = onCreatePlaylist) {
                    Text(if (audio.segmentationCount > 0) "Create Another" else "Create Playlist")
                }
            }
        }
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
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
    // Memoize formatted date to avoid recalculating
    val lastPracticedText = remember(playlist.lastPracticedAt) {
        playlist.lastPracticedAt?.let { " • Last: ${formatDate(it)}" }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)  // Increased from 16.dp
                .fillMaxWidth()
        ) {
            // First row: Icon and playlist info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playlist icon - larger
                Icon(
                    imageVector = Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),  // Increased from 48.dp
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Playlist info - now has full width to expand
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,  // Changed from titleMedium
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = playlist.language.uppercase(),
                            style = MaterialTheme.typography.labelMedium,  // Changed from labelSmall
                            color = MaterialTheme.colorScheme.primary
                        )
                        lastPracticedText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,  // Changed from bodySmall
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))  // Increased from 12.dp

            // Second row: Action buttons - larger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary action - Practice button - larger
                FilledTonalButton(
                    onClick = onPlayClick,
                    modifier = Modifier.padding(end = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)  // Increased
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)  // Increased from 18.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Practice", style = MaterialTheme.typography.labelLarge)  // Changed from labelMedium
                }

                // Secondary actions - larger touch targets
                IconButton(onClick = onExportClick, modifier = Modifier.size(44.dp)) {  // Increased from 36.dp
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export",
                        modifier = Modifier.size(24.dp),  // Increased from 20.dp
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRenameClick, modifier = Modifier.size(44.dp)) {  // Increased from 36.dp
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename",
                        modifier = Modifier.size(24.dp),  // Increased from 20.dp
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(44.dp)) {  // Increased from 36.dp
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(24.dp),  // Increased from 20.dp
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    items: List<ShadowItem>,
    onToggleFavorite: (ShadowItem) -> Unit,
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
        // Memoize the segments count text
        val segmentsCountText = remember(items.size) { "${items.size} segments" }
        val selectedCountText = remember(selectedForMerge.size) { "${selectedForMerge.size} selected" }
        
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
                        text = segmentsCountText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mergeMode) {
                        Text(
                            text = selectedCountText,
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
    onSplitClick: () -> Unit,
    mergeMode: Boolean,
    isSelectedForMerge: Boolean,
    onToggleMergeSelection: () -> Unit
) {
    // Memoize computed values to avoid recalculating on each recomposition
    val durationText = remember(item.durationMs) { formatDuration(item.durationMs) }
    val displayText = remember(item.orderInPlaylist) {
        "Segment ${item.orderInPlaylist + 1}"
    }
    
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
                    text = durationText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Item info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.practiceCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

// Preview Functions

@Preview(showBackground = true)
@Composable
fun EmptyLibraryMessagePreview() {
    ShadowMasterTheme {
        Surface {
            EmptyLibraryMessage()
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun PlaylistCardPreview() {
    ShadowMasterTheme {
        Surface {
            PlaylistCard(
                playlist = ShadowPlaylist(
                    id = "1",
                    name = "Japanese Conversations",
                    description = "Daily conversation practice",
                    language = "ja-JP",
                    lastPracticedAt = System.currentTimeMillis() - 86400000 // 1 day ago
                ),
                onClick = {},
                onDeleteClick = {},
                onRenameClick = {},
                onExportClick = {},
                onPlayClick = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun PlaylistCardNoLastPracticedPreview() {
    ShadowMasterTheme {
        Surface {
            PlaylistCard(
                playlist = ShadowPlaylist(
                    id = "1",
                    name = "Spanish Phrases",
                    description = null,
                    language = "es-ES",
                    lastPracticedAt = null
                ),
                onClick = {},
                onDeleteClick = {},
                onRenameClick = {},
                onExportClick = {},
                onPlayClick = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun ShadowItemCardPreview() {
    ShadowMasterTheme {
        Surface {
            ShadowItemCard(
                item = ShadowItem(
                    id = "1",
                    sourceFileUri = "content://test",
                    sourceFileName = "conversation.mp3",
                    sourceStartMs = 0,
                    sourceEndMs = 3500,
                    audioFilePath = "/path/to/audio",
                    durationMs = 3500,
                    language = "en-US",
                    practiceCount = 12,
                    isFavorite = true,
                    orderInPlaylist = 0
                ),
                onToggleFavorite = {},
                onSplitClick = {},
                mergeMode = false,
                isSelectedForMerge = false,
                onToggleMergeSelection = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun ShadowItemCardNoTranscriptionPreview() {
    ShadowMasterTheme {
        Surface {
            ShadowItemCard(
                item = ShadowItem(
                    id = "1",
                    sourceFileUri = "content://test",
                    sourceFileName = "audio.mp3",
                    sourceStartMs = 0,
                    sourceEndMs = 2000,
                    audioFilePath = "/path/to/audio",
                    durationMs = 2000,
                    language = "en-US",
                    practiceCount = 3,
                    isFavorite = false,
                    orderInPlaylist = 5
                ),
                onToggleFavorite = {},
                onSplitClick = {},
                mergeMode = false,
                isSelectedForMerge = false,
                onToggleMergeSelection = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun ShadowItemCardMergeModePreview() {
    ShadowMasterTheme {
        Surface {
            ShadowItemCard(
                item = ShadowItem(
                    id = "1",
                    sourceFileUri = "content://test",
                    sourceFileName = "audio.mp3",
                    sourceStartMs = 0,
                    sourceEndMs = 1500,
                    audioFilePath = "/path/to/audio",
                    durationMs = 1500,
                    language = "en-US",
                    orderInPlaylist = 2
                ),
                onToggleFavorite = {},
                onSplitClick = {},
                mergeMode = true,
                isSelectedForMerge = true,
                onToggleMergeSelection = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun ImportJobCardPreview() {
    ShadowMasterTheme {
        Surface {
            ImportJobCard(
                job = ImportJob(
                    id = "1",
                    sourceUri = "content://test",
                    fileName = "podcast_episode.mp3",
                    status = ImportStatus.DETECTING_SEGMENTS,
                    progress = 65,
                    totalSegments = 20,
                    processedSegments = 13
                )
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun FailedImportCardPreview() {
    ShadowMasterTheme {
        Surface {
            FailedImportCard(
                job = ImportJob(
                    id = "1",
                    sourceUri = "content://test",
                    fileName = "broken_audio.mp3",
                    status = ImportStatus.FAILED,
                    errorMessage = "Failed to decode audio: unsupported format"
                ),
                onDismiss = {}
            )
        }
    }
}


