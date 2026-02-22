package com.shadowmaster.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.shadowmaster.R
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

@Composable
private fun TranscriptionServicesSection(
    config: ShadowingConfig,
    viewModel: SettingsViewModel
) {
    var showIvritKeyDialog by remember { mutableStateOf(false) }
    var showWhisperDialog by remember { mutableStateOf(false) }
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var showLocalModelDialog by remember { mutableStateOf(false) }
    var showAdvancedProviders by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Section Header with Experimental badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Transcription Services",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            ExperimentalBadge()
        }

        HorizontalDivider()

        // Default Provider Selector
        TranscriptionProviderSelector(
            selectedProvider = config.transcription.defaultProvider,
            onProviderSelected = { viewModel.updateTranscriptionDefaultProvider(it) }
        )

        HorizontalDivider()

        // ========== FREE SERVICES ==========
        Text(
            text = "Free Services",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ivrit.ai Hebrew transcription
        ProviderConfigSection(
            title = "ivrit.ai (Hebrew)",
            isConfigured = true, // Free tier always available
            onConfigureClick = { showIvritKeyDialog = true },
            additionalInfo = if (!config.transcription.ivritApiKey.isNullOrBlank())
                "Premium API Key configured" else "Free tier (no key needed)"
        )

        // Local Model (Vosk)
        LocalModelProviderSection(
            config = config,
            viewModel = viewModel,
            onConfigureClick = { showLocalModelDialog = true }
        )

        HorizontalDivider()

        // ========== PAID API SERVICES ==========
        // Collapsible section for paid services
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { showAdvancedProviders = !showAdvancedProviders },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Paid API Services",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Requires API keys and may incur costs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (showAdvancedProviders) 
                        Icons.Default.KeyboardArrowUp 
                    else 
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = if (showAdvancedProviders) "Hide" else "Show"
                )
            }
        }

        if (showAdvancedProviders) {
            // OpenAI Whisper / Faster Whisper
            ProviderConfigSection(
                title = "OpenAI Whisper",
                isConfigured = !config.transcription.whisperApiKey.isNullOrBlank()
                        || !config.transcription.whisperBaseUrl.isNullOrBlank(),
                onConfigureClick = { showWhisperDialog = true },
                additionalInfo = when {
                    !config.transcription.whisperBaseUrl.isNullOrBlank() ->
                        "Faster Whisper: ${config.transcription.whisperBaseUrl}"
                    !config.transcription.whisperApiKey.isNullOrBlank() ->
                        "OpenAI API key configured"
                    else -> null
                }
            )

            // Custom Endpoint
            ProviderConfigSection(
                title = "Custom Endpoint",
                isConfigured = !config.transcription.customEndpointUrl.isNullOrBlank(),
                onConfigureClick = { showCustomUrlDialog = true },
                additionalInfo = config.transcription.customEndpointUrl
            )
        }
    }

    // Dialogs
    if (showIvritKeyDialog) {
        ApiKeyDialog(
            title = "ivrit.ai API Key (Optional)",
            currentValue = config.transcription.ivritApiKey ?: "",
            onDismiss = { showIvritKeyDialog = false },
            onSave = { apiKey ->
                viewModel.updateTranscriptionIvritApiKey(apiKey.ifBlank { null })
                showIvritKeyDialog = false
            },
            description = "API key is optional. Free tier works without a key, but premium API key unlocks higher limits."
        )
    }

    if (showWhisperDialog) {
        WhisperConfigDialog(
            currentApiKey = config.transcription.whisperApiKey ?: "",
            currentBaseUrl = config.transcription.whisperBaseUrl ?: "",
            onDismiss = { showWhisperDialog = false },
            onSave = { apiKey, baseUrl ->
                viewModel.updateTranscriptionWhisperApiKey(apiKey.ifBlank { null })
                viewModel.updateTranscriptionWhisperBaseUrl(baseUrl.ifBlank { null })
                showWhisperDialog = false
            }
        )
    }

    if (showCustomUrlDialog) {
        CustomEndpointDialog(
            currentUrl = config.transcription.customEndpointUrl ?: "",
            currentApiKey = config.transcription.customEndpointApiKey ?: "",
            onDismiss = { showCustomUrlDialog = false },
            onSave = { url, apiKey ->
                viewModel.updateTranscriptionCustomUrl(url.ifBlank { null })
                viewModel.updateTranscriptionCustomApiKey(apiKey.ifBlank { null })
                showCustomUrlDialog = false
            }
        )
    }

    // Local Model Dialog
    if (showLocalModelDialog) {
        LocalModelDialog(
            config = config,
            viewModel = viewModel,
            onDismiss = { showLocalModelDialog = false }
        )
    }
}

@Composable
private fun TranscriptionProviderSelector(
    selectedProvider: String,
    onProviderSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val providers = com.shadowmaster.transcription.TranscriptionProviderType.entries
        .filter { it.isImplemented }
        .associate { it.id to if (it.isFree) "${it.displayName} (Free)" else it.displayName }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Default Provider",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            ExperimentalBadge()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = providers[selectedProvider] ?: "Unknown",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            providers.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onProviderSelected(id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (id == selectedProvider) {
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
private fun ProviderConfigSection(
    title: String,
    isConfigured: Boolean,
    onConfigureClick: () -> Unit,
    additionalInfo: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConfigureClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (additionalInfo != null) {
                    Text(
                        text = additionalInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = if (isConfigured) "Configured" else "Not configured",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isConfigured)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyDialog(
    title: String,
    currentValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    description: String? = null
) {
    var apiKey by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description ?: "Enter your API key:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(apiKey) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AzureConfigDialog(
    currentApiKey: String,
    currentRegion: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var region by remember { mutableStateOf(currentRegion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Azure Configuration") },
        text = {
            Column {
                Text("Enter your Azure Speech Services credentials:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("Region (e.g., eastus)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(apiKey, region) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomEndpointDialog(
    currentUrl: String,
    currentApiKey: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    var apiKey by remember { mutableStateOf(currentApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Endpoint") },
        text = {
            Column {
                Text("Configure your custom transcription endpoint:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Endpoint URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url, apiKey) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhisperConfigDialog(
    currentApiKey: String,
    currentBaseUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var baseUrl by remember { mutableStateOf(currentBaseUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OpenAI Whisper / Faster Whisper") },
        text = {
            Column {
                Text("For OpenAI Whisper, enter your API key. For Faster Whisper or other compatible servers, set the base URL and leave the API key blank.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("OpenAI API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (optional)") },
                    placeholder = { Text("e.g. http://192.168.1.100:8000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Leave blank for OpenAI. Set for Faster Whisper servers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(apiKey, baseUrl) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LocalModelProviderSection(
    config: ShadowingConfig,
    viewModel: SettingsViewModel,
    onConfigureClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isConfigured = !config.transcription.localModelPath.isNullOrBlank() &&
            com.shadowmaster.transcription.LocalModelProvider.isModelDownloaded(
                context,
                config.transcription.localModelName ?: com.shadowmaster.transcription.LocalModelProvider.TINY_MODEL_NAME
            )
    
    val statusText = when {
        config.transcription.localModelName != null && isConfigured -> 
            "Model: ${config.transcription.localModelName} (ready)"
        config.transcription.localModelName != null && !isConfigured -> 
            "Model: ${config.transcription.localModelName} (not downloaded)"
        else -> "No model selected"
    }
    
    ProviderConfigSection(
        title = "Local Model (Vosk)",
        isConfigured = isConfigured,
        onConfigureClick = onConfigureClick,
        additionalInfo = statusText
    )
}

@Composable
private fun LocalModelDialog(
    config: ShadowingConfig,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedModel by remember { 
        mutableStateOf(
            config.transcription.localModelName ?: com.shadowmaster.transcription.LocalModelProvider.TINY_MODEL_NAME
        ) 
    }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    
    val tinyDownloaded = com.shadowmaster.transcription.LocalModelProvider.isModelDownloaded(
        context,
        com.shadowmaster.transcription.LocalModelProvider.TINY_MODEL_NAME
    )
    val baseDownloaded = com.shadowmaster.transcription.LocalModelProvider.isModelDownloaded(
        context,
        com.shadowmaster.transcription.LocalModelProvider.BASE_MODEL_NAME
    )
    
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = { Text("Local Model Configuration") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select and download a Vosk model for offline transcription:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Model selection
                Column {
                    // Tiny model
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isDownloading) { selectedModel = com.shadowmaster.transcription.LocalModelProvider.TINY_MODEL_NAME }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedModel == com.shadowmaster.transcription.LocalModelProvider.TINY_MODEL_NAME,
                            onClick = { selectedModel = com.shadowmaster.transcription.LocalModelProvider.TINY_MODEL_NAME },
                            enabled = !isDownloading
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Tiny (~40MB)",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (tinyDownloaded) "✓ Downloaded" else "Not downloaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (tinyDownloaded) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Base model
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isDownloading) { selectedModel = com.shadowmaster.transcription.LocalModelProvider.BASE_MODEL_NAME }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedModel == com.shadowmaster.transcription.LocalModelProvider.BASE_MODEL_NAME,
                            onClick = { selectedModel = com.shadowmaster.transcription.LocalModelProvider.BASE_MODEL_NAME },
                            enabled = !isDownloading
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Base (~75MB)",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (baseDownloaded) "✓ Downloaded" else "Not downloaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (baseDownloaded) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Download progress
                if (isDownloading) {
                    Column {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                // Error message
                if (downloadError != null) {
                    Text(
                        text = "Error: $downloadError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val modelDownloaded = when (selectedModel) {
                com.shadowmaster.transcription.LocalModelProvider.TINY_MODEL_NAME -> tinyDownloaded
                com.shadowmaster.transcription.LocalModelProvider.BASE_MODEL_NAME -> baseDownloaded
                else -> false
            }
            
            if (modelDownloaded) {
                TextButton(
                    onClick = {
                        val modelPath = com.shadowmaster.transcription.LocalModelProvider.getModelPath(
                            context,
                            selectedModel
                        ).absolutePath
                        viewModel.updateTranscriptionLocalModelPath(modelPath)
                        viewModel.updateTranscriptionLocalModelName(selectedModel)
                        onDismiss()
                    },
                    enabled = !isDownloading
                ) {
                    Text("Use Model")
                }
            } else {
                TextButton(
                    onClick = {
                        isDownloading = true
                        downloadError = null
                        downloadProgress = 0f
                        
                        coroutineScope.launch {
                            val provider = com.shadowmaster.transcription.LocalModelProvider(context, null)
                            val result = provider.downloadModel(selectedModel) { progress ->
                                downloadProgress = progress
                            }
                            
                            result.onSuccess {
                                viewModel.updateTranscriptionLocalModelPath(it.absolutePath)
                                viewModel.updateTranscriptionLocalModelName(selectedModel)
                                isDownloading = false
                                onDismiss()
                            }.onFailure { error ->
                                downloadError = when {
                                    error.message?.contains("UnknownHost") == true -> 
                                        "Cannot reach download server. Please check your internet connection."
                                    error.message?.contains("timeout") == true -> 
                                        "Download timed out. Please try again."
                                    error.message?.contains("space") == true -> 
                                        "Not enough storage space. Please free up space and try again."
                                    else -> 
                                        "Download failed: ${error.message ?: "Unknown error"}"
                                }
                                android.util.Log.e("LocalModelDialog", "Model download failed", error)
                                isDownloading = false
                            }
                        }
                    },
                    enabled = !isDownloading
                ) {
                    Text("Download")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDownloading
            ) {
                Text("Cancel")
            }
        }
    )
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

/**
 * Experimental badge to indicate features that are still in development
 */
@Composable
private fun ExperimentalBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Text(
            text = "Experimental",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
