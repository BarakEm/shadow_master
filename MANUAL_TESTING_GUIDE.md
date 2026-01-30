# Manual Testing Guide: Segmentation Controls

## Overview
This guide covers manual testing of the newly implemented segmentation controls UI.

## Features to Test

### 1. Settings Screen - Segmentation Mode Selector

**Location:** Settings → Segmentation Mode (below Silence Threshold)

**Test Steps:**
1. Open the app
2. Navigate to Settings
3. Scroll to find "Segmentation Mode" section
4. Verify two radio button options are shown:
   - "Sentence" 
   - "Word"
5. Select "Word" and navigate away from Settings
6. Return to Settings and verify "Word" is still selected (persistence test)
7. Select "Sentence" and verify selection changes

**Expected Behavior:**
- Radio buttons should display correctly
- Only one option can be selected at a time
- Selection should persist across app restarts
- UI should follow the same style as "Practice Mode" selector

### 2. Library Screen - Re-segment Button

**Location:** Library → Playlist card → Scissors icon button

**Test Steps:**
1. Navigate to Library screen
2. Locate a playlist card
3. Look for action buttons on the right side
4. Verify a scissors icon (ContentCut) button appears between Export and Rename buttons
5. Note: Button appears for all playlists (validation happens in dialog)

**Expected Behavior:**
- Scissors icon button should be visible on all playlist cards
- Button should be the same size as other action buttons (36dp)
- Icon color should match other secondary actions (onSurfaceVariant)

### 3. Re-segment Dialog

**Location:** Library → Playlist card → Scissors icon → Dialog

**Test Steps for Playlist WITH Imported Audio:**
1. Import an audio file (File picker or URL import)
2. Wait for import to complete and playlist to be created
3. Click the scissors icon on the newly created playlist
4. Verify dialog shows:
   - Title: "Re-segment Playlist"
   - Description text about choosing preset
   - List of 4 presets with radio buttons:
     * Standard Sentences (500-8000ms segments)
     * Short Phrases (500-3000ms segments)
     * Long Sentences (1000-12000ms segments)
     * Word by Word (300-2000ms segments)
5. Select "Word by Word" preset
6. Click "Re-segment" button
7. Verify:
   - Dialog closes
   - Progress indicator shows (optional - might be too fast to see)
   - Success message appears: "Re-segmentation complete. New playlist created."
   - New playlist appears with name like "Original Name (Word by Word)"
8. Open the new playlist and verify segments are shorter/different

**Test Steps for Playlist WITHOUT Imported Audio:**
1. If you have a playlist created by live recording (not import), click scissors icon
2. Verify dialog shows:
   - Title: "Re-segment Playlist"
   - Error message: "This playlist cannot be re-segmented because it was not imported from audio."
3. Verify "Re-segment" button is not shown or disabled
4. Click "Cancel" to close dialog

**Expected Behavior:**
- Dialog should load playlist items automatically
- Radio button selection should work correctly
- Re-segment button should be disabled until a preset is selected
- Progress indicator should show during processing
- Error handling for non-imported playlists
- New playlist should have modified name with preset name appended
- Original playlist should remain unchanged

## Error Cases to Test

1. **No Internet During URL Import:** Verify error message appears
2. **Invalid Audio File:** Verify import fails gracefully
3. **Cancel During Import:** Verify cancellation works
4. **Re-segment During Active Import:** Verify button is properly disabled or shows appropriate message

## Checklist

- [ ] Settings: Segmentation Mode selector displays correctly
- [ ] Settings: Sentence/Word selection works
- [ ] Settings: Selection persists after app restart
- [ ] Library: Re-segment button appears on all playlists
- [ ] Library: Re-segment button styling matches other icons
- [ ] Dialog: Shows correct title and description
- [ ] Dialog: Lists all 4 presets with details
- [ ] Dialog: Radio button selection works
- [ ] Dialog: Error message for non-imported playlists
- [ ] Dialog: Re-segment button enabled/disabled correctly
- [ ] Re-segment: Creates new playlist with correct name
- [ ] Re-segment: Original playlist unchanged
- [ ] Re-segment: New playlist has different segments
- [ ] Re-segment: Success message displays
- [ ] Re-segment: Error handling works

## Screenshots Needed

1. Settings screen showing Segmentation Mode selector
2. Library screen showing playlist card with re-segment button
3. Re-segment dialog with preset list
4. Re-segment dialog with error message (if possible)
5. Library showing both original and new re-segmented playlist
6. Success message after re-segmentation

## Known Limitations

- Re-segment button appears on all playlists, but validation happens in dialog
- Cannot re-segment playlists created from live recording (only imported audio)
- Large audio files may take time to re-segment (progress shown via CircularProgressIndicator)
