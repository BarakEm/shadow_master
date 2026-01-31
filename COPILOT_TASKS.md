# GitHub Copilot Pro Tasks

Tasks suitable for delegation to GitHub Copilot Pro. Each task is self-contained and actionable.

## Status Summary

- ✅ **Completed:** 20 out of 20 tasks (100%) 🎉
- ⏳ **Remaining:** 0 tasks
- 🤖 **PRs Merged:** 14 PRs
- 📝 **Code Added:** ~7,500+ lines

**Last Updated:** 2026-01-31 - **ALL TASKS COMPLETE!**

---

## Unit Testing Tasks

### ✅ 1. Add Unit Tests for ShadowingStateMachine
**Status:** COMPLETED (PR #57)
**Priority:** High
**Estimated Scope:** New test file

Create unit tests for `core/ShadowingStateMachine.kt` covering all state transitions:
- Test transitions from `Idle` to `Playing`, `Paused`, `Stopped`
- Test transitions from `Playing` to all valid next states
- Test invalid state transitions throw appropriate errors
- Test edge cases like rapid state changes
- Use JUnit5 and MockK for mocking dependencies

**Files to reference:**
- `app/src/main/kotlin/com/barak/shadowmaster/core/ShadowingStateMachine.kt`
- `app/src/main/kotlin/com/barak/shadowmaster/data/model/ShadowingState.kt`

---

### ✅ 2. Add Unit Tests for LibraryRepository
**Status:** COMPLETED (PR #58)
**Priority:** High
**Estimated Scope:** New test file

Create unit tests for `data/repository/LibraryRepository.kt`:
- Test CRUD operations for playlists
- Test CRUD operations for shadow items
- Test database query methods return correct data
- Test error handling for database failures
- Mock Room DAOs using MockK

**Files to reference:**
- `app/src/main/kotlin/com/barak/shadowmaster/data/repository/LibraryRepository.kt`
- `app/src/main/kotlin/com/barak/shadowmaster/data/local/*.kt`

---

### ✅ 3. Add Unit Tests for AudioProcessingPipeline
**Status:** COMPLETED (PR #59)
**Priority:** Medium
**Estimated Scope:** New test file

Create unit tests for `audio/processing/AudioProcessingPipeline.kt`:
- Test audio buffer processing with mock data
- Test segment detection callbacks
- Test error handling for invalid audio formats
- Test memory management and buffer cleanup

**Files to reference:**
- `app/src/main/kotlin/com/barak/shadowmaster/audio/processing/AudioProcessingPipeline.kt`

---

### ❌ 4. Add ViewModel Unit Tests
**Status:** CLOSED (PR #60 had merge conflicts)
**Priority:** Medium
**Estimated Scope:** Multiple test files

Create unit tests for ViewModels:
- `LibraryViewModel` - test playlist operations and UI state updates
- `PracticeViewModel` - test practice session management
- `SettingsViewModel` - test settings changes propagation
- Use TestCoroutineDispatcher for coroutine testing

**Files to reference:**
- `app/src/main/kotlin/com/barak/shadowmaster/ui/library/LibraryViewModel.kt`
- `app/src/main/kotlin/com/barak/shadowmaster/ui/practice/PracticeViewModel.kt`

---

## Code Refactoring Tasks

### ✅ 5. Extract AudioFileUtility from AudioImporter
**Status:** COMPLETED (PR #62)
**Priority:** High
**Estimated Scope:** New utility class + refactor

Extract common audio file operations from `AudioImporter.kt` (1046 lines) into a reusable utility class:
- WAV file header creation/parsing
- Audio format detection
- Sample rate conversion helpers
- PCM buffer operations
- File I/O operations for audio data

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/library/AudioImporter.kt`
- Create: `app/src/main/kotlin/com/barak/shadowmaster/library/AudioFileUtility.kt`

---

### ✅ 6. Extract UrlTypeDetector from UrlAudioImporter
**Status:** COMPLETED (PR #61)
**Priority:** Medium
**Estimated Scope:** New class + refactor

Extract URL detection and parsing logic from `UrlAudioImporter.kt`:
- YouTube URL detection and video ID extraction
- Spotify URL detection
- Generic audio URL detection
- URL validation and normalization

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/library/UrlAudioImporter.kt`
- Create: `app/src/main/kotlin/com/barak/shadowmaster/library/UrlTypeDetector.kt`

---

### ❌ 7. Split AudioExporter into Smaller Classes
**Status:** CLOSED (PR #63 had merge conflicts)
**Priority:** Medium
**Estimated Scope:** 2-3 new classes + refactor

Break down `AudioExporter.kt` (419 lines) into:
- `WavFileCreator` - WAV header creation and file writing
- `PlaylistExporter` - Playlist-to-audio conversion logic
- `ExportProgressTracker` - Progress tracking and callbacks

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/library/AudioExporter.kt`
- Create new files in same package

---

## Documentation Tasks

### ✅ 8. Add KDoc to AudioImporter Public Methods
**Status:** COMPLETED (PR #64)
**Priority:** Medium
**Estimated Scope:** Documentation only

Add comprehensive KDoc documentation to all public methods in `AudioImporter.kt`:
- Document parameters, return values, and exceptions
- Add usage examples for complex methods
- Document thread safety considerations
- Reference related classes and methods

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/library/AudioImporter.kt`

---

### ✅ 9. Add KDoc to ShadowingCoordinator
**Status:** COMPLETED (PR #65)
**Priority:** Medium
**Estimated Scope:** Documentation only

Add KDoc documentation to `ShadowingCoordinator.kt`:
- Document the orchestration flow
- Document state transition handling
- Add sequence diagram in markdown for complex flows
- Document event handling patterns

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/core/ShadowingCoordinator.kt`

---

### ✅ 10. Document State Machine Transitions
**Status:** COMPLETED (PR #66)
**Priority:** Low
**Estimated Scope:** Documentation only

Add comprehensive documentation to state-related files:
- Document each state and its valid transitions
- Add state diagram in markdown/mermaid format
- Document event types and when they're triggered
- Add examples of common state flows

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/data/model/ShadowingState.kt`
- `app/src/main/kotlin/com/barak/shadowmaster/core/ShadowingStateMachine.kt`

---

## Error Handling Tasks

### ✅ 11. Create AudioImportError Sealed Class
**Status:** COMPLETED (PR #67)
**Priority:** High
**Estimated Scope:** New class + refactor

Create structured error types for audio import operations:
```kotlin
sealed class AudioImportError : Exception() {
    data class FileNotFound(val path: String) : AudioImportError()
    data class UnsupportedFormat(val format: String) : AudioImportError()
    data class DecodingFailed(val reason: String) : AudioImportError()
    data class PermissionDenied(val permission: String) : AudioImportError()
    data class NetworkError(val url: String, val cause: Throwable) : AudioImportError()
    data class StorageError(val reason: String) : AudioImportError()
}
```

Replace generic Exception catches with specific error types.

**Files to modify:**
- Create: `app/src/main/kotlin/com/barak/shadowmaster/library/AudioImportError.kt`
- `app/src/main/kotlin/com/barak/shadowmaster/library/AudioImporter.kt`
- `app/src/main/kotlin/com/barak/shadowmaster/library/UrlAudioImporter.kt`

---

### ✅ 12. Create ErrorMapper for User-Friendly Messages
**Status:** COMPLETED (PR #68)
**Priority:** Medium
**Estimated Scope:** New class

Create a utility class that maps technical errors to user-friendly messages:
- Map AudioImportError types to localized strings
- Provide actionable suggestions for each error type
- Support multiple languages via string resources
- Include error codes for debugging

**Files to create:**
- `app/src/main/kotlin/com/barak/shadowmaster/library/ErrorMapper.kt`

---

## Input Validation Tasks

### ✅ 13. Add Input Validation for URI/URL Imports
**Status:** COMPLETED (PR #67)
**Priority:** High
**Estimated Scope:** Validation functions

Add comprehensive input validation for audio imports:
- Validate URI scheme (content://, file://, http://, https://)
- Validate URL format and accessibility
- Sanitize file paths to prevent path traversal
- Validate audio file extensions before processing
- Add maximum file size checks

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/library/AudioImporter.kt`
- `app/src/main/kotlin/com/barak/shadowmaster/library/UrlAudioImporter.kt`

---

### ❌ 14. Add Playlist Name Validation
**Status:** CLOSED (PR #69 had merge conflicts)
**Priority:** Low
**Estimated Scope:** Validation function + UI integration

Add validation for playlist and segment names:
- Maximum length enforcement (e.g., 100 characters)
- Disallow special characters that break file systems
- Trim whitespace
- Prevent empty or blank names
- Show validation errors in UI

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/ui/library/LibraryScreen.kt`
- `app/src/main/kotlin/com/barak/shadowmaster/data/repository/LibraryRepository.kt`

---

## UI Component Tasks

### ❌ 15. Extract Reusable Dialog Components
**Status:** CLOSED (PR #70 had merge conflicts)
**Priority:** Medium
**Estimated Scope:** New composables

Extract common dialog patterns from LibraryScreen into reusable components:
- `ConfirmationDialog` - Yes/No confirmation dialogs
- `TextInputDialog` - Single text input with validation
- `SelectionDialog` - List selection dialogs
- `ProgressDialog` - Progress indicator dialogs

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/ui/library/LibraryScreen.kt`
- Create: `app/src/main/kotlin/com/barak/shadowmaster/ui/components/Dialogs.kt`

---

### ✅ 16. Add Compose Preview Functions
**Status:** COMPLETED (PR #71)
**Priority:** Low
**Estimated Scope:** Preview composables

Add @Preview composable functions for UI components:
- Preview for LibraryScreen with mock data
- Preview for PracticeScreen states
- Preview for SettingsScreen
- Preview for custom components

**Files to modify:**
- All files in `app/src/main/kotlin/com/barak/shadowmaster/ui/`

---

## Performance Tasks

### ✅ 17. Add Database Indices
**Status:** COMPLETED (PR #74 - Migration 2→3)
**Priority:** Medium
**Estimated Scope:** Entity modifications

Add database indices for frequently queried columns:
- Index on `ShadowItem.playlistId` for playlist queries
- Index on `ShadowItem.importedAudioId` for audio lookups
- Index on `ShadowPlaylist.createdAt` for sorting
- Index on `ImportJob.status` for job queries

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/data/local/ShadowItem.kt`
- `app/src/main/kotlin/com/barak/shadowmaster/data/local/ShadowPlaylist.kt`
- Update database migration version

---

### ✅ 18. Optimize Compose Recomposition in LibraryScreen
**Status:** COMPLETED (PR #72)
**Priority:** Medium
**Estimated Scope:** Compose optimization

Optimize LibraryScreen to reduce unnecessary recompositions:
- Add `remember` for expensive calculations
- Use `derivedStateOf` for filtered lists
- Add `key` parameters to LazyColumn items
- Extract stable lambda callbacks
- Profile with Compose metrics

**Files to modify:**
- `app/src/main/kotlin/com/barak/shadowmaster/ui/library/LibraryScreen.kt`

---

## Logging & Monitoring Tasks

### ✅ 19. Add Structured Logging Wrapper
**Status:** COMPLETED (PR #55)
**Priority:** Medium
**Estimated Scope:** New utility class

Create a structured logging utility:
- Consistent log format: `[timestamp] [level] [tag] message`
- Log levels: DEBUG, INFO, WARN, ERROR
- Optional file logging for debugging
- Integration with CrashReporter
- Performance-conscious (no-op in release builds)

**Files to create:**
- `app/src/main/kotlin/com/barak/shadowmaster/util/Logger.kt`

---

### ✅ 20. Add Performance Metrics Collection
**Status:** COMPLETED (PR #53)
**Priority:** Low
**Estimated Scope:** New utility class

Create utilities to collect performance metrics:
- Audio import duration tracking
- Segmentation processing time
- UI rendering metrics
- Database query performance
- Export as JSON for analysis

**Files to create:**
- `app/src/main/kotlin/com/barak/shadowmaster/util/PerformanceTracker.kt`

---

## Automated Delegation to Copilot

**This is now fully automated!** You don't need to manually create issues or mention @copilot.

### Option 1: Create Issues from This File (Batch)

```bash
# Preview what would be created
python scripts/create_issues_from_tasks.py --dry-run

# Create all issues
python scripts/create_issues_from_tasks.py
```

Issues are automatically delegated to @copilot via GitHub Actions.

### Option 2: Create Individual Issues (Recommended)

```bash
gh issue create \
  --label copilot \
  --title "Your task title" \
  --body "Detailed description"
```

GitHub Actions automatically mentions @copilot within seconds.

### What Happens Automatically

When you create an issue with the `copilot` label:
1. ✅ GitHub Actions workflow triggers
2. ✅ @copilot is automatically mentioned
3. ✅ Issue gets `copilot-working` label
4. ✅ Copilot reads `.github/copilot-instructions.md`
5. ✅ Copilot creates an implementation plan
6. ✅ Copilot submits a pull request

### Monitor Progress

```bash
# View active issues
gh issue list --label copilot-working

# View pull requests from Copilot
gh pr list
```

Or visit:
- Issues: https://github.com/BarakEm/shadow_master/issues?q=is:issue+label:copilot-working
- Pull Requests: https://github.com/BarakEm/shadow_master/pulls

### Complete Documentation

See **[COPILOT_AUTOMATION.md](COPILOT_AUTOMATION.md)** for the complete automation guide - you don't need to remember any commands!

---

## Task Priority Summary

| Priority | Tasks |
|----------|-------|
| High | 1, 2, 5, 11, 13 |
| Medium | 3, 4, 6, 7, 8, 9, 12, 15, 17, 18, 19 |
| Low | 10, 14, 16, 20 |

Start with high-priority tasks for maximum impact on code quality.
