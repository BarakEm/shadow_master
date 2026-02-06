# Claude Code Guidelines for Shadow Master

## Code Quality Rules

These rules apply to ALL code changes, whether by Claude, Copilot, Gemini, or humans.

### Comments & Documentation
- KDoc: max 1-3 lines for private/internal methods. No examples, no thread-safety notes, no step-by-step process lists.
- Only add `@param`/`@return` when the signature isn't self-documenting.
- Don't add comments to code you didn't change.

### Naming
- Name things accurately: if it produces AAC, call it AAC, not MP3.
- `ExportFormat.AAC` (not MP3). `AacFileCreator` (not Mp3FileCreator).

### Resource Management
- Always clean up `MediaCodec`, `MediaExtractor`, `ParcelFileDescriptor` in `finally` blocks.
- Cancel previous coroutine `Job` before launching a new collection on the same `StateFlow`.

### Stubs & Unimplemented Features
- Never expose unimplemented providers/features in the UI.
- Use `isImplemented = false` on enum entries for stub providers (GOOGLE, AZURE, WHISPER transcription).
- Filter UI dropdowns to only show `isImplemented == true` entries.

### Coroutines
- Store long-running collection jobs and cancel before re-launching (see `playlistItemsJob` pattern in `LibraryViewModel`).
- Use `Dispatchers.IO` for disk/network, `Dispatchers.Default` for CPU work.

### Dead Code
- Delete unused methods entirely. Don't comment them out or rename with `_` prefix.
- If a method is superseded (e.g., `detectSpeechSegments` replaced by `WithConfig`/`WithParams` variants), delete the old one.

### Architecture
- Hilt DI: prefer `@Inject constructor` on classes over explicit module bindings.
- Export format: app supports WAV and AAC only.
- Transcription providers: `TranscriptionProviderType` enum is the source of truth. Derive UI lists from it.
- Audio: always 16kHz mono PCM for VAD compatibility.

## Build & Test
```bash
./gradlew assembleDebug    # Verify compilation
./gradlew test             # Unit tests
./gradlew lint             # Lint checks
```

## Key Patterns
- State management: `ShadowingState` sealed class + `ShadowingStateMachine` + `ShadowingCoordinator`
- Settings: `SettingsRepository` with DataStore, `ShadowingConfig` data class
- Import flow: `AudioImporter.importAudioOnly()` -> `segmentImportedAudio()` (two-phase)
- Export flow: `AudioExporter` -> `PlaylistExporter` + `WavFileCreator`/`AacFileCreator`
