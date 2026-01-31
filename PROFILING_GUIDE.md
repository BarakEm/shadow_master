# How to Profile Compose Recompositions

This guide explains how to visualize and measure recompositions in the LibraryScreen after the optimizations.

## Method 1: Layout Inspector (Recommended)

### Setup
1. Build and run the debug app on a device or emulator
2. Open Android Studio
3. Go to **View > Tool Windows > Layout Inspector**
4. Select your app process from the dropdown

### Enable Recomposition Counts
1. In Layout Inspector, click the **"Show Recomposition Counts"** icon (looks like a counter)
2. Navigate to the Library screen in your app
3. Interact with the UI (scroll, toggle merge mode, open dialogs)
4. Watch the recomposition counts appear next to each composable

### What to Look For
- **Low counts on items**: When scrolling, items should not recompose
- **Stable FAB**: Dropdown menu state changes shouldn't recompose parent
- **Minimal TopBar recomposition**: Only when selectedPlaylist changes
- **Card stability**: Cards should only recompose when their data changes

### Expected Results After Optimization
- Scrolling: Only newly visible items should compose (count = 1)
- Merge mode toggle: Only affected UI elements recompose
- Dialog open/close: No recomposition of background content

## Method 2: Enable Recomposition Highlighting

### Add to Your App
In `MainActivity.kt` or a debug utility, add:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawLayer
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

@Composable
fun Modifier.recomposeHighlighter(): Modifier {
    val color = remember { Color(Random.nextLong()).copy(alpha = 0.2f) }
    return this.drawLayer {
        // Flash on recomposition
    }
}

// Or use this simpler version for logging
@Composable
fun RecomposeLogger(tag: String) {
    SideEffect {
        println("Recomposition: $tag")
    }
}
```

### Usage
Add to composables you want to track:

```kotlin
@Composable
fun ShadowItemCard(...) {
    RecomposeLogger("ShadowItemCard-${item.id}")
    Card(modifier = Modifier.recomposeHighlighter()) {
        // ... content
    }
}
```

### What to Look For
- Check logcat for recomposition messages
- Items should only log when they appear or their data changes
- Not when unrelated state changes

## Method 3: Compose Compiler Metrics

### Build with Metrics
```bash
./gradlew :app:assembleDebug
```

### Check Reports
```bash
# List all composables and their stability
cat app/build/compose_reports/*-composables.txt | grep "LibraryScreen\|PlaylistCard\|ShadowItemCard"

# Check for unstable parameters
cat app/build/compose_reports/*-composables.txt | grep "unstable"

# View class stability
cat app/build/compose_reports/*-classes.txt
```

### What to Look For
- All key composables should be marked as `skippable`
- Parameters should be `stable` (not `unstable`)
- Function parameters should be `stable` lambdas

### Expected Output
```
restartable skippable scheme("[...]") fun LibraryScreen(
  stable onNavigateBack: Function0<Unit>
  stable onStartPractice: Function1<String, Unit>
  unstable importUrl: String?  // OK - external parameter
  unstable importUri: String?  // OK - external parameter
  stable viewModel: LibraryViewModel
)

restartable skippable scheme("[...]") fun ShadowItemCard(
  stable item: ShadowItem
  stable onToggleFavorite: Function0<Unit>
  stable onEditClick: Function0<Unit>
  stable onSplitClick: Function0<Unit>
  stable mergeMode: Boolean
  stable isSelectedForMerge: Boolean
  stable onToggleMergeSelection: Function0<Unit>
)
```

## Method 4: Benchmark with Macrobenchmark

For more rigorous testing, create a macrobenchmark:

### Setup
Add to `build.gradle.kts`:
```kotlin
androidTestImplementation("androidx.benchmark:benchmark-macro-junit4:1.2.0")
```

### Create Benchmark Test
```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class LibraryScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun libraryScroll() = benchmarkRule.measureRepeated(
        packageName = "com.shadowmaster",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            // Navigate to library
        }
    ) {
        // Scroll through list
        device.findObject(By.res("playlist_list")).scroll(Direction.DOWN, 1f)
    }
}
```

## Interpreting Results

### Good Performance Indicators
- ✅ Scroll frame times < 16ms (60fps) or < 8ms (120fps)
- ✅ Items compose once when appearing, no recomposition during scroll
- ✅ State changes only recompose affected UI
- ✅ All key composables marked as `skippable`

### Performance Issues
- ❌ Recompositions during scroll
- ❌ Entire screen recomposes on small state changes
- ❌ High frame times (jank)
- ❌ Composables marked as `unstable` or not `skippable`

## Common Issues and Solutions

### Issue: Items recompose during scroll
**Cause**: Unstable lambda or missing key parameter
**Solution**: Ensure `key` parameter in `items()` and stable lambdas

### Issue: Cards recompose on unrelated state change
**Cause**: Inline lambda recreation or unstable parameters
**Solution**: Wrap lambdas in `remember` or extract to stable composables

### Issue: Expensive computations on every recomposition
**Cause**: Calculations not memoized
**Solution**: Use `remember` or `derivedStateOf`

### Issue: All items in list recompose together
**Cause**: Shared mutable state or missing `key` parameter
**Solution**: Isolate state and verify `key` parameters

## Before/After Comparison

### Before Optimization
```
LibraryScreen recomposition count: 25
  - Scroll event: +10 recompositions (all visible items)
  - Dialog open: +5 recompositions (background content)
  - Merge toggle: +8 recompositions (all UI elements)
```

### After Optimization (Expected)
```
LibraryScreen recomposition count: 8
  - Scroll event: +1 recomposition (new item appears)
  - Dialog open: +1 recomposition (dialog only)
  - Merge toggle: +3 recompositions (affected buttons + items)
```

## Next Steps

1. Run Layout Inspector with recomposition counts
2. Navigate through all LibraryScreen features
3. Review Compose compiler metrics
4. Compare frame timing before/after
5. Document any remaining performance issues
6. Consider additional optimizations if needed
