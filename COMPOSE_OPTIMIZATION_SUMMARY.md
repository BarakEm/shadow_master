# LibraryScreen Compose Optimization Summary

## Overview
This document summarizes the Compose recomposition optimizations applied to `LibraryScreen.kt` as part of issue #18.

## Optimizations Applied

### 1. Stable Lambda Callbacks with `remember`

**Problem**: Lambda functions created inline cause recomposition of child composables even when the logic hasn't changed.

**Solution**: Extract lambdas and wrap them in `remember` with appropriate keys.

**Examples**:
```kotlin
// Before
IconButton(onClick = {
    if (selectedPlaylist != null) {
        viewModel.clearSelection()
    } else {
        onNavigateBack()
    }
}) { /* ... */ }

// After
val onBackClick = remember(selectedPlaylist) {
    {
        if (selectedPlaylist != null) {
            viewModel.clearSelection()
        } else {
            onNavigateBack()
        }
    }
}
IconButton(onClick = onBackClick) { /* ... */ }
```

**Locations**:
- `onImportAudioFile`: Audio file import callback
- `onBackClick`: Navigation back handler
- `onMergeClick`, `onCancelMerge`, `onEnterMergeMode`: Merge mode actions
- `onStartPracticeClick`: Practice start action

### 2. Extracted Composables

**Problem**: State defined inline within parent composables causes parent recomposition when that state changes.

**Solution**: Extract to separate composable functions with stable parameters.

**Example**:
```kotlin
// Before - showMenu state inside LibraryScreen causes recomposition
var showMenu by remember { mutableStateOf(false) }
Box {
    FloatingActionButton(onClick = { showMenu = true }) { /* ... */ }
    DropdownMenu(expanded = showMenu, /* ... */) { /* ... */ }
}

// After - showMenu isolated in ImportFab composable
ImportFab(
    onImportAudioFile = { audioPickerLauncher.launch(arrayOf("audio/*")) },
    onImportFromUrl = { showUrlImportDialog = true }
)

@Composable
private fun ImportFab(
    onImportAudioFile: () -> Unit,
    onImportFromUrl: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    // ... implementation
}
```

**Location**: `ImportFab` composable

### 3. `derivedStateOf` for Computed Values

**Problem**: Calculations that depend on state but don't need to recompute on every recomposition waste resources.

**Solution**: Use `derivedStateOf` to only recompute when dependencies change.

**Examples**:
```kotlin
// Before
val hasImportedAudio = remember(playlistItems) {
    playlistItems.any { it.importedAudioId != null }
}

// After
val hasImportedAudio by remember {
    derivedStateOf {
        playlistItems.any { it.importedAudioId != null }
    }
}
```

**Locations**:
- `hasImportedAudio`: Check if playlist has imported audio
- `firstImportedAudioId`: Find first imported audio ID
- `segmentsCountText`: Formatted segment count string
- `selectedCountText`: Formatted selected items count string

### 4. Memoized Display Strings

**Problem**: Formatting functions called during every recomposition create string allocations.

**Solution**: Memoize formatted strings with `remember` and appropriate keys.

**Examples**:
```kotlin
// In ShadowItemCard
val durationText = remember(item.durationMs) { formatDuration(item.durationMs) }
val displayText = remember(item.transcription, item.orderInPlaylist) {
    item.transcription ?: "Segment ${item.orderInPlaylist + 1}"
}

// In PlaylistCard
val lastPracticedText = remember(playlist.lastPracticedAt) {
    playlist.lastPracticedAt?.let { " • Last: ${formatDate(it)}" }
}
```

**Locations**:
- `ShadowItemCard`: Duration and display text
- `PlaylistCard`: Last practiced date text

### 5. LazyColumn Key Parameters

**Status**: Already implemented correctly in the original code.

All `items()` calls in `LazyColumn` already use proper `key` parameters:
```kotlin
items(playlists, key = { it.id }) { playlist -> /* ... */ }
items(items, key = { it.id }) { item -> /* ... */ }
items(activeImports, key = { it.id }) { job -> /* ... */ }
items(failedImports, key = { it.id }) { job -> /* ... */ }
```

This ensures efficient list updates and prevents unnecessary recompositions of unchanged items.

## Compose Compiler Metrics

### Configuration Added

The build configuration now includes Compose compiler metrics:

```kotlin
kotlinOptions {
    jvmTarget = "17"
    
    // Enable Compose compiler metrics and reports for performance analysis
    freeCompilerArgs += listOf(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${layout.buildDirectory.get().asFile.absolutePath}/compose_metrics",
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${layout.buildDirectory.get().asFile.absolutePath}/compose_reports"
    )
}
```

### Using the Metrics

After building the project, Compose metrics will be generated in:
- `app/build/compose_metrics/` - Metrics JSON files
- `app/build/compose_reports/` - Human-readable reports

**Key Files to Review**:
1. `*-composables.txt` - List of all composables and their stability
2. `*-classes.txt` - Stability of data classes and parameters
3. `*-module.json` - JSON metrics for programmatic analysis

**What to Look For**:
- **Skippable composables**: Can skip recomposition when inputs haven't changed
- **Restartable composables**: Can be recomposed independently
- **Unstable parameters**: Parameters that prevent skipping (should be minimized)

### Example Output

```
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun ShadowItemCard(
  stable item: ShadowItem
  stable onToggleFavorite: Function0<Unit>
  stable onEditClick: Function0<Unit>
  stable onSplitClick: Function0<Unit>
  stable mergeMode: Boolean
  stable isSelectedForMerge: Boolean
  stable onToggleMergeSelection: Function0<Unit>
)
```

- `restartable`: Can be recomposed independently
- `skippable`: Will skip recomposition if inputs haven't changed
- `stable`: Parameters are stable (don't trigger unnecessary recompositions)

## Expected Performance Improvements

1. **Reduced Recompositions**: Lambda callbacks no longer recreate on parent recomposition
2. **Efficient Computations**: `derivedStateOf` prevents redundant calculations
3. **Memory Efficiency**: Memoized strings reduce string allocations
4. **Better Scrolling**: Optimized `LazyColumn` items with proper keys
5. **Cleaner Structure**: Extracted composables improve maintainability

## Testing Recommendations

1. **Visual Testing**: Verify UI still functions correctly
2. **Performance Testing**: 
   - Scroll through large playlists
   - Toggle merge mode
   - Open/close dialogs
   - Switch between playlists
3. **Metrics Review**: 
   - Build with metrics enabled
   - Review composable stability
   - Identify remaining unstable parameters
4. **Recomposition Counting**: 
   - Use Layout Inspector to count recompositions
   - Enable "Show recomposition counts" in developer options

## Future Optimization Opportunities

1. **ViewModel State Optimization**: Consider using `StateFlow` immutable updates
2. **List Diffing**: If playlists/items update frequently, consider custom diff callbacks
3. **Heavy Computations**: Move any remaining heavy computations to background threads
4. **Image Loading**: If images are added, use proper caching and placeholder strategies

## References

- [Jetpack Compose Performance](https://developer.android.com/jetpack/compose/performance)
- [Compose Compiler Metrics](https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md)
- [Compose Stability](https://developer.android.com/jetpack/compose/performance/stability)
- [derivedStateOf](https://developer.android.com/jetpack/compose/side-effects#derivedstateof)
