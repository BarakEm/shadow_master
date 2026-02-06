# Manual Test Plan: Mic Recording Fixes

## Issue
1. Mic recordings don't appear in "Imported Audio" tab
2. "Record from Mic" card description text is cut off

## Test Cases

### Test Case 1: Mic Recording Appears in Imported Audio Tab

**Objective**: Verify mic recordings are saved as ImportedAudio entries and visible in the "Imported Audio" tab

**Prerequisites**:
- App installed on Android 10+ device
- Microphone permission granted

**Steps**:
1. Launch Shadow Master app
2. Tap "Record from Mic" from home screen
3. Grant microphone permission if prompted
4. Tap "Start Recording" button
5. Speak for 2-3 seconds
6. Tap "Stop" or wait for auto-stop
7. Verify success message appears: "Recording saved to library!"
8. Tap "Go to Library" button
9. Navigate to "Imported Audio" tab (second tab)

**Expected Results**:
- Mic recording appears in "Imported Audio" tab with name like "Mic Recording 2026-02-06 18:44:33"
- Recording shows duration and creation date
- Recording has "Create Playlist" button available
- Clicking "Create Playlist" allows re-segmentation with different settings

**Pass Criteria**:
✅ Recording visible in Imported Audio tab
✅ Can create new playlist from the recording
✅ New playlist contains segmented audio

---

### Test Case 2: Mic Recording Appears in Library Playlists Tab

**Objective**: Verify mic recordings still appear in "Captured Audio" playlist

**Prerequisites**:
- Test Case 1 completed successfully

**Steps**:
1. From Library screen, navigate to "Playlists" tab (first tab)
2. Find "Captured Audio" playlist
3. Tap on "Captured Audio" playlist to view contents

**Expected Results**:
- "Captured Audio" playlist exists
- Contains the mic recording from Test Case 1
- Recording is playable from the playlist

**Pass Criteria**:
✅ Recording visible in Captured Audio playlist
✅ Recording metadata matches the one in Imported Audio tab
✅ Can practice/play the recording

---

### Test Case 3: Re-segmentation from Mic Recording

**Objective**: Verify mic recordings can be re-segmented with different parameters

**Prerequisites**:
- Test Case 1 completed successfully
- At least one mic recording in Imported Audio tab

**Steps**:
1. Navigate to Library > Imported Audio tab
2. Tap "Create Playlist" button on a mic recording
3. Enter a playlist name (e.g., "Test Resegmentation")
4. Select different segmentation settings (Word vs Sentence mode)
5. Confirm creation

**Expected Results**:
- Playlist is created with newly segmented audio
- Segments respect the chosen segmentation mode
- Original "Captured Audio" playlist still exists
- Both playlists reference the same source audio via importedAudioId

**Pass Criteria**:
✅ New playlist created successfully
✅ Segments differ based on chosen mode
✅ Multiple playlists can be created from same recording

---

### Test Case 4: UI Text Display on Home Screen

**Objective**: Verify description text is not cut off on "Record from Mic" card

**Prerequisites**:
- None

**Steps**:
1. Launch Shadow Master app
2. Observe the home screen
3. Locate "Record from Mic" card
4. Read the full description text

**Expected Results**:
- Description text visible: "Record audio using your microphone. Great for capturing from external speakers or practicing pronunciation."
- Text wraps properly within card bounds
- No text is cut off or hidden
- Ellipsis (...) may appear if text still exceeds 3 lines, but this should not happen with current text

**Pass Criteria**:
✅ Full description text is readable
✅ No text overflow beyond card bounds
✅ Text wraps naturally

---

### Test Case 5: Transcription of Mic Recording

**Objective**: Verify auto-transcription works for mic recordings if enabled

**Prerequisites**:
- Transcription enabled in settings
- Transcription provider configured (if using cloud provider)
- Test Case 1 completed

**Steps**:
1. Enable auto-transcription in Settings > Transcription
2. Record new audio via "Record from Mic"
3. Wait for recording to save
4. Navigate to Library > Playlists > Captured Audio
5. View the recorded segment

**Expected Results**:
- If auto-transcription is enabled, segment should have transcription
- Transcription displayed in segment detail view

**Pass Criteria**:
✅ Transcription appears when enabled
✅ No errors in logs during transcription

---

## Regression Tests

### Regression Test 1: Normal Audio Import Still Works

**Objective**: Ensure changes didn't break regular audio import

**Steps**:
1. Navigate to Library screen
2. Tap "+" button to import audio
3. Select an audio file from device
4. Wait for import to complete

**Expected Results**:
- Audio imports successfully
- Appears in both "Imported Audio" and "Playlists" tabs
- Can be played and practiced normally

**Pass Criteria**:
✅ Normal import flow unchanged
✅ Imported audio behaves as before

---

### Regression Test 2: Other Home Screen Cards Unchanged

**Objective**: Verify other cards on home screen are not affected

**Steps**:
1. View home screen
2. Check "Shadow Library" card
3. Check "Capture Playing Audio" card

**Expected Results**:
- Both cards display correctly
- Description text is visible
- Cards are clickable and navigate to correct screens

**Pass Criteria**:
✅ Other cards unaffected
✅ All navigation works

---

## Code Review Checklist

### AudioImporter.kt Changes
- [x] PCM file saved to `importedAudioDir` instead of just `segmentsDir`
- [x] `ImportedAudio` entity created with proper metadata
- [x] `sourceFileName` includes timestamp for uniqueness
- [x] `importedAudioId` field set on `ShadowItem`
- [x] Both PCM and segment files cleaned up on error
- [x] No breaking changes to existing import flows
- [x] Added necessary imports (SimpleDateFormat, Date, Locale)

### HomeScreen.kt Changes
- [x] `maxLines = 3` added to description Text composable
- [x] `overflow = TextOverflow.Ellipsis` added to description Text composable
- [x] Change only affects description, not title or other elements
- [x] No layout changes that would affect other cards

---

## Known Limitations

1. **Storage**: Each mic recording is stored twice - once in `imported_audio` and once in `shadow_segments`. This is by design to support both re-segmentation and immediate playback.

2. **Naming**: Mic recordings use timestamp for uniqueness. Users cannot customize the name during recording (can rename after in future versions).

3. **Segmentation**: Single mic recording saved as single segment. Re-segmentation requires creating new playlist from Imported Audio tab.

---

## Test Environment

- **Android Version**: 10+ (required for AudioPlaybackCapture)
- **Device**: Physical device recommended (mic recording may not work well in emulator)
- **Permissions**: Microphone, Storage

---

## Expected Log Messages

### Successful Recording:
```
AudioImporter: Saved captured segment: <duration>ms, <sample_count> samples
```

### With Transcription:
```
AudioImporter: Auto-transcribing captured segment
AudioImporter: Transcribed captured segment: <transcription_text>
```

### Error Cases:
```
AudioImporter: Failed to save captured segment: <error_message>
```
