# Visual Guide: Export Dialog Changes

## What the User Sees

### Before This Fix ❌

When clicking "Export" on a playlist, user saw:

```
┌──────────────────────────────────────┐
│       Export Playlist                │
├──────────────────────────────────────┤
│ Export "Japanese Phrases" as a      │
│ practice audio file                  │
│                                      │
│ Format:                              │
│ ┌───────────────┐ ┌───────────────┐ │
│ │ MP3 (Smaller) │ │ WAV (Quality) │ │
│ └───────────────┘ └───────────────┘ │
│                                      │
│ The exported file will include:      │
│ • Beeps between segments             │
│ • Playback repeats (from settings)   │
│ • Your current speed settings        │
│                                      │
│ ☑ Include silence for practice       │
│   Adds silent gaps for you to shadow │
│                                      │
│           [Cancel]  [Export]         │
└──────────────────────────────────────┘
```

**Problems:**
- ❌ No information about where file will be saved
- ❌ No indication of what the filename will be
- ❌ Label says "MP3" but actually creates AAC
- ❌ If export fails, no error details in logs

---

### After This Fix ✅

When clicking "Export" on a playlist, user now sees:

```
┌──────────────────────────────────────────────┐
│           Export Playlist                    │
├──────────────────────────────────────────────┤
│ Export "Japanese Phrases" as a practice      │
│ audio file                                   │
│                                              │
│ Format:                                      │
│ ┌───────────────┐ ┌───────────────┐         │
│ │ AAC (Smaller) │ │ WAV (Quality) │         │
│ └───────────────┘ └───────────────┘         │
│                                              │
│ ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ │
│ ┃ File will be saved to:                 ┃ │
│ ┃   Music/ShadowMaster/                  ┃ │
│ ┃                                        ┃ │
│ ┃ Filename pattern:                      ┃ │
│ ┃   ShadowMaster_Japanese_Phrases_       ┃ │
│ ┃   <timestamp>.aac                      ┃ │
│ ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ │
│                                              │
│ The exported file will include:              │
│ • Beeps between segments                     │
│ • Playback repeats (from settings)           │
│ • Your current speed settings                │
│                                              │
│ ☑ Include silence for practice               │
│   Adds silent gaps for you to shadow         │
│                                              │
│              [Cancel]  [Export]              │
└──────────────────────────────────────────────┘
```

**Improvements:**
- ✅ Shows exact save directory: "Music/ShadowMaster/"
- ✅ Shows filename pattern with actual playlist name
- ✅ Extension changes dynamically (.aac or .wav)
- ✅ Accurate label: "AAC (Smaller)" instead of "MP3"
- ✅ Visual prominence with colored panel
- ✅ Monospace font for technical details

---

## What Changed Behind the Scenes

### 1. File Creation ✅

**Before:**
```
Filename: ShadowMaster_Japanese_Phrases_1701234567890.mp3
MIME Type: audio/mpeg
Content: AAC audio (mismatch! ⚠️)
Logs: None
Result: Silent failure possible
```

**After:**
```
Filename: ShadowMaster_Japanese_Phrases_1701234567890.aac
MIME Type: audio/aac
Content: AAC audio (match! ✅)
Logs: Comprehensive DEBUG/INFO/ERROR
Result: Clear error messages if failure
```

### 2. Error Visibility ✅

**Before:**
```
[No logs]
User sees: "Export failed"
Developer: No way to debug
```

**After:**
```
[Mp3FileCreator] Starting AAC encoding for: Japanese Phrases
[Mp3FileCreator] AAC encoding completed, output size: 524288 bytes
[Mp3FileCreator] Saving with MediaStore (Android 10+)
[Mp3FileCreator] MediaStore entry created with URI: content://media/...
[Mp3FileCreator] Wrote 524288 bytes to MediaStore
[Mp3FileCreator] Updated IS_PENDING flag, rows affected: 1
[Mp3FileCreator] File saved to: Music/ShadowMaster/ShadowMaster_...aac
```

### 3. User Experience ✅

**Before:**
- User clicks Export
- Progress shows "Exporting..."
- Either success or "Export failed"
- User doesn't know where to look for file
- User confused about format (MP3 vs AAC)

**After:**
- User clicks Export
- Dialog shows where file will be saved
- Dialog shows what filename will be
- Progress shows "Exporting..."
- Success shows exact path
- User knows where to find file
- User understands format (AAC)

---

## Example Filenames

Given a playlist named "French 🇫🇷 Conversations #1"

**Sanitization Process:**
```
Original:  French 🇫🇷 Conversations #1
Sanitized: French___Conversations__1
Timestamp: 1701234567890
Extension: aac (if AAC selected) or wav (if WAV selected)

Final AAC:  ShadowMaster_French___Conversations__1_1701234567890.aac
Final WAV:  ShadowMaster_French___Conversations__1_1701234567890.wav
```

**Why Sanitize?**
- Prevents file system errors from special characters
- Ensures compatibility across all devices
- Keeps only: letters, numbers, dots, dashes, underscores

---

## Where to Find Exported Files

### On Android 10+ (MediaStore)
1. Open **Files** app (Google Files or device file manager)
2. Navigate to **Music** folder
3. Look for **ShadowMaster** subfolder
4. Your exported files are there!

Path: `Internal storage/Music/ShadowMaster/`

### On Android 9 and Below
1. Open **Files** app
2. Navigate to **Music** folder
3. Look for **ShadowMaster** subfolder
4. Your exported files are there!

Path: `/sdcard/Music/ShadowMaster/`

### In Music Players
Exported files automatically appear in:
- Google Play Music
- YouTube Music
- VLC for Android
- Any media player that scans Music folder

---

## Technical Details for Developers

### Code Changes Summary

**Mp3FileCreator.kt** (40 lines changed):
```kotlin
// Before
val fileName = "ShadowMaster_${sanitizedName}_${System.currentTimeMillis()}.mp3"
put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
// No logging

// After
val fileName = "ShadowMaster_${sanitizedName}_${System.currentTimeMillis()}.aac"
put(MediaStore.Audio.Media.MIME_TYPE, "audio/aac")
Log.d(TAG, "Starting AAC encoding for: $name")
Log.d(TAG, "AAC encoding completed, output size: ${tempEncodedFile.length()} bytes")
Log.i(TAG, "AAC file saved successfully: ${outputPath.path}")
```

**LibraryScreen.kt** (48 lines changed):
```kotlin
// Before
label = { Text("MP3 (Smaller)") }
// No save location info

// After  
label = { Text("AAC (Smaller)") }
// Added Surface with save location and filename preview
Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = MaterialTheme.shapes.small
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text("File will be saved to:")
        Text(saveLocation, color = MaterialTheme.colorScheme.primary)
        Text("Filename pattern:")
        Text("ShadowMaster_${sanitizedName}_<timestamp>.$fileExtension",
             fontFamily = FontFamily.Monospace)
    }
}
```

---

## User Benefits

| Issue | Before | After |
|-------|--------|-------|
| **Format Confusion** | Says "MP3" but creates AAC | Correctly says "AAC" |
| **File Location** | No information | Shows "Music/ShadowMaster/" |
| **Filename** | No information | Shows pattern with playlist name |
| **Export Failures** | Silent or generic error | Detailed logs in logcat |
| **MIME Type** | Incorrect (audio/mpeg) | Correct (audio/aac) |
| **Debugging** | Impossible | Full error visibility |

---

## Backward Compatibility

✅ **100% Maintained**
- Old "MP3" files (actually AAC) still play
- New exports create properly labeled .aac files
- No changes to API or database
- All existing features work unchanged
- No user action required

---

## Summary

This fix makes export feature:
- ✅ More reliable (correct MIME type)
- ✅ More transparent (shows save location)
- ✅ More debuggable (comprehensive logging)
- ✅ More accurate (AAC label instead of MP3)
- ✅ More user-friendly (clear information)

All while maintaining 100% backward compatibility!
