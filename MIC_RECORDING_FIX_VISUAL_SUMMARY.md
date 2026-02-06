# Visual Summary: Mic Recording Fixes

## Problem 1: Missing from Imported Audio Tab

### Before (Broken)
```
User Records from Mic
        ↓
saveCapturedSegment()
        ↓
    ┌───────────────────────────┐
    │ Creates ShadowItem ONLY   │
    │ - Saves to segmentsDir    │
    │ - No ImportedAudio entry  │
    │ - No importedAudioId link │
    └───────────────────────────┘
        ↓
┌──────────────────────────────────────┐
│ Library Screen                       │
│                                      │
│ [Playlists]  [Imported Audio]       │
│                                      │
│ Captured Audio   │  (empty)         │
│ └─ Recording 1   │                  │
│ └─ Recording 2   │                  │
│                                      │
│ ❌ Cannot re-segment recordings     │
│ ❌ Cannot create new playlists      │
│ ❌ Cannot transcribe separately     │
└──────────────────────────────────────┘
```

### After (Fixed)
```
User Records from Mic
        ↓
saveCapturedSegment()
        ↓
    ┌────────────────────────────────────┐
    │ 1. Saves PCM to importedAudioDir  │
    │ 2. Creates ImportedAudio entry    │
    │ 3. Copies PCM to segmentsDir      │
    │ 4. Creates ShadowItem with link   │
    │    importedAudioId = audio.id     │
    └────────────────────────────────────┘
        ↓
┌──────────────────────────────────────────────────────┐
│ Library Screen                                       │
│                                                      │
│ [Playlists]          [Imported Audio]               │
│                                                      │
│ Captured Audio       │  Mic Recording 2026-02-06... │
│ └─ Recording 1       │  [Create Playlist]           │
│ └─ Recording 2       │  Duration: 3.5s              │
│    ↑                 │                               │
│    │                 │  Mic Recording 2026-02-06... │
│    │                 │  [Create Playlist]           │
│    │                 │  Duration: 5.2s              │
│    │                 │      ↓                        │
│    └─────────────────┼──────┘                       │
│    (Same audio data, linked via importedAudioId)    │
│                                                      │
│ ✅ Can re-segment recordings                        │
│ ✅ Can create multiple playlists                    │
│ ✅ Can transcribe again with different settings     │
└──────────────────────────────────────────────────────┘
```

## Data Model Relationship

### Before
```
ShadowItem
  ├─ audioFilePath: "/data/.../segments/xyz.pcm"
  ├─ sourceFileUri: "captured://audio"
  ├─ sourceFileName: "Captured Audio"
  ├─ playlistId: "captured-audio-playlist-id"
  └─ importedAudioId: null  ❌ NOT LINKED
```

### After
```
ImportedAudio
  ├─ id: "audio-123"
  ├─ pcmFilePath: "/data/.../imported_audio/abc.pcm"
  ├─ sourceFileName: "Mic Recording 2026-02-06 18:44:33"
  └─ sourceUri: "mic://recording"
       ↓ (referenced by)
ShadowItem
  ├─ audioFilePath: "/data/.../segments/xyz.pcm"  (copy)
  ├─ sourceFileUri: "mic://recording"
  ├─ sourceFileName: "Mic Recording 2026-02-06 18:44:33"
  ├─ playlistId: "captured-audio-playlist-id"
  └─ importedAudioId: "audio-123"  ✅ LINKED

User can now:
  1. View in "Imported Audio" tab
  2. Create new playlist from ImportedAudio
  3. Re-segment with different settings
  4. Original "Captured Audio" playlist still works
```

## File System Changes

### Before
```
filesDir/
  └─ shadow_segments/
      ├─ recording1.pcm  (3.5s, directly from mic)
      ├─ recording2.pcm  (5.2s, directly from mic)
      └─ ...

❌ No copy in imported_audio dir
❌ Cannot re-segment without re-recording
```

### After
```
filesDir/
  ├─ imported_audio/
  │   ├─ abc-123.pcm  (3.5s, full recording for re-segmentation)
  │   ├─ def-456.pcm  (5.2s, full recording for re-segmentation)
  │   └─ ...
  └─ shadow_segments/
      ├─ xyz-789.pcm  (3.5s, copy for immediate playback)
      ├─ uvw-012.pcm  (5.2s, copy for immediate playback)
      └─ ...

✅ Full recording saved for re-segmentation
✅ Segment copy available for immediate playback
✅ ImportedAudio table links to full recording
✅ ShadowItem links to both via importedAudioId
```

---

## Problem 2: UI Text Cut Off

### Before (Broken)
```
┌─────────────────────────────────────────┐
│  🎤  Record from Mic                    │
│                                         │
│  Record audio using your microph...    │  ❌ Text cut off!
│                                    →    │
└─────────────────────────────────────────┘
```

### After (Fixed)
```
┌─────────────────────────────────────────┐
│  🎤  Record from Mic                    │
│                                         │
│  Record audio using your microphone.   │  ✅ Full text visible
│  Great for capturing from external     │     (wraps properly)
│  speakers or practicing pronunciation. │
│                                    →    │
└─────────────────────────────────────────┘
```

### Code Change
```kotlin
// Before
Text(
    text = description,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

// After
Text(
    text = description,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 3,                      // ← Added
    overflow = TextOverflow.Ellipsis   // ← Added
)
```

---

## User Flow Comparison

### OLD: Limited Functionality
```
1. Record from Mic
2. Audio saved to "Captured Audio" playlist
3. Can practice immediately
4. ❌ CANNOT re-segment
5. ❌ CANNOT create variations
6. ❌ NOT visible in "Imported Audio"
```

### NEW: Full Functionality
```
1. Record from Mic
2. Audio saved BOTH:
   - To "Captured Audio" playlist (for immediate use)
   - To "Imported Audio" (for re-segmentation)
3. Can practice immediately from playlist
4. ✅ CAN re-segment from "Imported Audio" tab
5. ✅ CAN create multiple playlists with different settings
6. ✅ VISIBLE in both "Playlists" and "Imported Audio" tabs
7. ✅ SAME workflow as imported files
```

---

## Technical Details

### saveCapturedSegment() Changes

#### Key Code Changes
```kotlin
// NEW: Save to importedAudioDir
pcmFile = File(importedAudioDir, "${UUID.randomUUID()}.pcm")
FileOutputStream(pcmFile).use { output ->
    // Write PCM samples
}

// NEW: Create ImportedAudio entry
val importedAudio = ImportedAudio(
    sourceUri = "mic://recording",
    sourceFileName = "Mic Recording ${timestamp}",  // Descriptive name
    pcmFilePath = pcmFile.absolutePath,
    durationMs = segment.durationMs,
    // ... other fields
)
importedAudioDao.insert(importedAudio)

// NEW: Copy PCM for segment
segmentFile = File(segmentsDir, "${UUID.randomUUID()}.pcm")
pcmFile.copyTo(segmentFile, overwrite = false)

// UPDATED: Link ShadowItem to ImportedAudio
val item = ShadowItem(
    sourceFileUri = importedAudio.sourceUri,      // Updated
    sourceFileName = importedAudio.sourceFileName, // Updated
    importedAudioId = importedAudio.id,           // NEW: Link
    // ... other fields
)
```

#### Error Handling
```kotlin
catch (e: Exception) {
    Log.e(TAG, "Failed to save captured segment", e)
    // Clean up BOTH files on failure
    segmentFile?.delete()  // OLD
    pcmFile?.delete()      // NEW
    null
}
```

---

## Benefits

### For Users
1. ✅ Mic recordings now have same capabilities as imported files
2. ✅ Can experiment with different segmentation settings
3. ✅ Can create multiple practice playlists from one recording
4. ✅ Text on home screen is fully readable
5. ✅ Consistent user experience across all audio sources

### For Developers
1. ✅ Consistent data model (all audio has ImportedAudio entry)
2. ✅ Code reuse (re-segmentation works same for all sources)
3. ✅ Better traceability (importedAudioId links everything)
4. ✅ Easier debugging (all audio has full source PCM)

---

## Storage Impact

### Additional Storage per Recording
- **Before**: 1 file (segment only)
- **After**: 2 files (full PCM + segment copy)
- **Impact**: ~2x storage per mic recording
- **Justification**: Enables re-segmentation, worth the cost

### Example
```
5-second recording at 16kHz mono 16-bit:
- PCM size: 5s × 16000 samples/s × 2 bytes = 160 KB
- Before: 160 KB total
- After: 320 KB total (160 KB × 2)
```

For typical usage (few recordings, each a few seconds), this is acceptable overhead for the added functionality.
