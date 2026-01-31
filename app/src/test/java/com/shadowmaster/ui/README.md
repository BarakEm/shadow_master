# ViewModel Unit Tests

This directory contains unit tests for the ViewModels in the Shadow Master application.

## Test Files

### SettingsViewModelTest
**Location**: `app/src/test/java/com/shadowmaster/ui/settings/SettingsViewModelTest.kt`

**Test Coverage** (13 tests):
- Language update propagation
- Segment mode update
- Silence threshold configuration
- Playback speed adjustment
- Playback repeats setting
- User repeats setting
- Assessment toggle
- Pause for navigation toggle
- Bus mode toggle
- Audio feedback toggle
- Practice mode selection
- Buildup chunk size configuration
- Config StateFlow emission

**Dependencies Mocked**:
- `SettingsRepository`

### LibraryViewModelTest
**Location**: `app/src/test/java/com/shadowmaster/ui/library/LibraryViewModelTest.kt`

**Test Coverage** (24 tests):
- Playlist loading and selection
- Playlist items retrieval
- Audio file import (success and failure)
- URL import handling
- URI import routing (http/https vs content://)
- Playlist deletion
- Favorite toggling
- Error and success message management
- Playlist renaming
- Item transcription updates
- Item translation updates
- Segment splitting
- Segment merging and selection
- Playlist export
- Import job management
- URL import progress

**Dependencies Mocked**:
- `Context`
- `LibraryRepository`
- `SettingsRepository`

### PracticeViewModelTest
**Location**: `app/src/test/java/com/shadowmaster/ui/practice/PracticeViewModelTest.kt`

**Test Coverage** (15 tests):
- Initial state verification
- Playlist loading and item retrieval
- State transitions (Loading → Ready)
- Import job status tracking (UNKNOWN, ACTIVE, FAILED, COMPLETED)
- Pause/resume functionality
- Stop functionality
- Current item index tracking
- Progress tracking
- AudioFeedbackSystem initialization
- Empty items list handling
- Skip to item navigation
- Invalid index handling

**Dependencies Mocked**:
- `Context`
- `LibraryRepository`
- `SettingsRepository`
- `AudioFeedbackSystem`
- `SavedStateHandle`

## Testing Framework

### Dependencies
- **JUnit 4**: Test framework (`junit:junit:4.13.2`)
- **MockK**: Kotlin mocking library (`io.mockk:mockk:1.13.8`)
- **Coroutines Test**: Testing coroutines (`kotlinx-coroutines-test:1.7.3`)
- **AndroidX Arch Core Testing**: LiveData and ViewModel testing (`core-testing:2.2.0`)

### Key Testing Utilities

#### TestCoroutineDispatcher
Used to control coroutine execution in tests. The `StandardTestDispatcher` allows:
- Deterministic coroutine execution
- Manual time advancement with `advanceUntilIdle()`
- Proper cleanup with `Dispatchers.setMain()` and `Dispatchers.resetMain()`

#### InstantTaskExecutorRule
Ensures LiveData operations execute synchronously during tests.

#### MockK
Kotlin-first mocking library features:
- `mockk<T>()` - Create mocks
- `every { ... } returns ...` - Stub methods
- `coEvery { ... } returns ...` - Stub suspend functions
- `verify { ... }` - Verify calls
- `coVerify { ... }` - Verify suspend function calls
- `relaxed = true` - Create relaxed mocks (returns default values)

## Running Tests

### Run all tests
```bash
./gradlew test
```

### Run specific test class
```bash
./gradlew test --tests "com.shadowmaster.ui.settings.SettingsViewModelTest"
./gradlew test --tests "com.shadowmaster.ui.library.LibraryViewModelTest"
./gradlew test --tests "com.shadowmaster.ui.practice.PracticeViewModelTest"
```

### Run specific test method
```bash
./gradlew test --tests "com.shadowmaster.ui.settings.SettingsViewModelTest.updateLanguage calls repository with correct language"
```

### Generate test report
```bash
./gradlew test
# Report available at: app/build/reports/tests/testDebugUnitTest/index.html
```

## Test Patterns

### Basic Test Structure
```kotlin
@Test
fun `descriptive test name`() = runTest {
    // Given - Setup test data and mocks
    val expectedValue = "test"
    
    // When - Execute the action being tested
    viewModel.performAction(expectedValue)
    advanceUntilIdle() // Wait for coroutines
    
    // Then - Verify the results
    assertEquals(expectedValue, viewModel.state.value)
    coVerify { repository.method(expectedValue) }
}
```

### Testing StateFlows
```kotlin
@Test
fun `StateFlow emits expected values`() = runTest {
    // Given
    val expectedData = listOf(item1, item2)
    every { repository.getData() } returns flowOf(expectedData)
    
    // When
    val viewModel = MyViewModel(repository)
    advanceUntilIdle()
    
    // Then
    assertEquals(expectedData, viewModel.data.value)
}
```

### Testing Suspend Functions
```kotlin
@Test
fun `suspend function calls repository`() = runTest {
    // Given
    coEvery { repository.suspendMethod() } returns Result.success(Unit)
    
    // When
    viewModel.performAsyncAction()
    advanceUntilIdle()
    
    // Then
    coVerify { repository.suspendMethod() }
}
```

### Testing Error Handling
```kotlin
@Test
fun `error handling updates error state`() = runTest {
    // Given
    val errorMessage = "Operation failed"
    coEvery { repository.method() } returns Result.failure(Exception(errorMessage))
    
    // When
    viewModel.performAction()
    advanceUntilIdle()
    
    // Then
    assertEquals(errorMessage, viewModel.errorState.value)
}
```

## Best Practices

1. **Use Descriptive Test Names**: Use backticks for readable test names that describe the scenario
2. **Follow Given-When-Then**: Structure tests clearly with setup, action, and verification
3. **Test One Thing**: Each test should verify a single behavior
4. **Mock External Dependencies**: Use MockK to isolate the ViewModel under test
5. **Use `advanceUntilIdle()`**: Always wait for coroutines to complete before assertions
6. **Clean Up**: Use `@After` to reset Main dispatcher and clear mocks
7. **Test Error Cases**: Don't just test happy paths, verify error handling
8. **Avoid Flaky Tests**: Use deterministic test dispatchers, avoid Thread.sleep()

## Notes

- These tests focus on **ViewModel behavior**, not UI rendering or repository implementation
- Tests use **mocked repositories** to isolate the ViewModel logic
- **Coroutine testing** requires `StandardTestDispatcher` and `runTest` for deterministic execution
- Tests verify that ViewModels correctly:
  - Transform repository data into UI state
  - Handle user actions by calling repository methods
  - Propagate errors and success messages
  - Manage loading and ready states
  - Update StateFlows correctly

## Future Enhancements

Potential areas for additional test coverage:
- Integration tests with real repositories
- UI tests for Compose screens
- End-to-end tests for complete user workflows
- Performance tests for large playlists
- Error recovery scenarios
- Background job cancellation
