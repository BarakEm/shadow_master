# Beep Audio Fix - Implementation Summary

## Problem
Playback of segmented playlists did not have audible beeps during practice, but exported audio files did have beeps.

## Root Cause
The issue was caused by different audio streams being used:
- **ToneGenerator** (used by AudioFeedbackSystem) plays through `STREAM_NOTIFICATION`
- **AudioTrack** (used for speech playback) plays through `USAGE_MEDIA`

When media is actively playing, Android's audio focus system can reduce or mute notification sounds, making beeps inaudible.

## Solution
Created a unified beep generation system that plays beeps through the same audio stream as the speech content:

### 1. BeepGenerator Utility (`audio/BeepGenerator.kt`)
- Generates PCM audio data for beeps programmatically using sine wave synthesis
- Applies fade in/out envelope to avoid clicks
- Provides constants for consistent beep frequencies across playback and export
- Includes silence generation for pauses

### 2. PracticeViewModel Integration
- Added `playBeep()` and `playDoubleBeep()` methods that use AudioTrack
- Updated all beep calls during practice to use the new methods:
  - Playback start beep (880 Hz - A5)
  - "Your turn" double beep (1047 Hz - C6)
  - Segment end beep (660 Hz - E5)
- Beeps are now played through the same AudioTrack instance as speech

### 3. PlaylistExporter Refactoring
- Refactored to use BeepGenerator instead of duplicating beep synthesis code
- Eliminated ~40 lines of duplicate code
- Ensures consistent beep characteristics between playback and export

## Benefits
1. **Audible beeps during practice** - Beeps are now clearly audible during segment playback
2. **Consistent audio quality** - Same beep generation for both playback and export
3. **Code reusability** - Single source of truth for beep generation
4. **Maintainability** - Beep parameters only need to be changed in one place

## Technical Details
- Sample rate: 16000 Hz (mono)
- Format: 16-bit PCM (little-endian)
- Beep frequencies match musical notes for pleasant sound
- Envelope applied to prevent audio clicks
- Duration configurable via ShadowingConfig.beepDurationMs

## Testing Notes
- Beeps should be clearly audible during practice playback
- Export functionality should continue to work as before
- Beep timing should match the exported audio behavior
- AudioFeedbackSystem still used for end-of-session celebration tone (not during active playback)
