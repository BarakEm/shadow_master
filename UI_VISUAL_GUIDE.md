# UI Changes Visual Guide

## Settings Screen - Segmentation Mode Selector

```
┌─────────────────────────────────────────────────┐
│ Settings                                    [←] │
├─────────────────────────────────────────────────┤
│                                                 │
│ Language                                        │
│ English (US)                          [▼]       │
│                                                 │
│ ─────────────────────────────────────────────── │
│                                                 │
│ Playback Speed                          0.8x    │
│ ├────────────●──────────────┤                   │
│                                                 │
│ Playback Repeats                          1     │
│ ├●────────────────────────────┤                 │
│                                                 │
│ User Repeats                              1     │
│ ├●────────────────────────────┤                 │
│                                                 │
│ Silence Threshold                       700ms   │
│ ├────────────●──────────────┤                   │
│                                                 │
│ ┌─────────────────────────────────────────────┐ │
│ │ Segmentation Mode                      NEW!│ │  <-- NEW FEATURE
│ │                                            │ │
│ │ ○ Sentence                                 │ │
│ │   Detect natural sentence boundaries       │ │
│ │                                            │ │
│ │ ● Word                                     │ │
│ │   Split longer segments into words         │ │
│ └─────────────────────────────────────────────┘ │
│                                                 │
│ ─────────────────────────────────────────────── │
│                                                 │
│ Bus Mode                            [Toggle]    │
│ Passive listening - no user recording          │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Library Screen - Playlist Card with Re-segment Button

```
┌─────────────────────────────────────────────────────────────────┐
│ Shadow Library                                          [Link]  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Playlists                                                       │
│                                                                 │
│ ┌───────────────────────────────────────────────────────────┐   │
│ │ [🎵]  Morning Podcast                                     │   │
│ │       EN-US • Last: 2024-01-15                            │   │
│ │                                    [Practice] [📤] [✂️] [✏️] [🗑️] │  <-- NEW BUTTON (scissors)
│ └───────────────────────────────────────────────────────────┘   │
│                                                                 │
│ ┌───────────────────────────────────────────────────────────┐   │
│ │ [🎵]  Spanish Lessons                                     │   │
│ │       ES-ES • Last: 2024-01-14                            │   │
│ │                                    [Practice] [📤] [✂️] [✏️] [🗑️]  │
│ └───────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

Button Legend:
[Practice] = Start practice session (FilledTonalButton)
[📤] = Export to audio file (Share icon)
[✂️] = Re-segment playlist (ContentCut icon) - NEW!
[✏️] = Rename playlist (Edit icon)
[🗑️] = Delete playlist (Delete icon)
```

## Re-segment Dialog

### Case 1: Playlist with Imported Audio

```
┌─────────────────────────────────────────────────────┐
│ Re-segment Playlist                                 │
├─────────────────────────────────────────────────────┤
│                                                     │
│ Choose a segmentation preset to create a new       │
│ playlist with different segment lengths:            │
│                                                     │
│ ○ Standard Sentences                                │
│   500-8000ms segments                               │
│                                                     │
│ ○ Short Phrases                                     │
│   500-3000ms segments                               │
│                                                     │
│ ● Word by Word                              <-- Selected
│   300-2000ms segments                               │
│                                                     │
│ ○ Long Sentences                                    │
│   1000-12000ms segments                             │
│                                                     │
├─────────────────────────────────────────────────────┤
│                          [Cancel] [Re-segment]      │
└─────────────────────────────────────────────────────┘

After clicking "Re-segment":
- Dialog closes
- Success message: "Re-segmentation complete. New playlist created."
- New playlist appears: "Morning Podcast (Word by Word)"
```

### Case 2: Playlist without Imported Audio (Error State)

```
┌─────────────────────────────────────────────────────┐
│ Re-segment Playlist                                 │
├─────────────────────────────────────────────────────┤
│                                                     │
│ ⚠️ This playlist cannot be re-segmented because    │
│    it was not imported from audio.                  │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                         [Cancel]    │
└─────────────────────────────────────────────────────┘
```

## User Flow Diagrams

### Settings Flow

```
User opens app
    ↓
Navigate to Settings
    ↓
Scroll to Segmentation Mode
    ↓
Select "Word" or "Sentence"
    ↓
Selection persisted to DataStore
    ↓
(Used for future imports)
```

### Re-segmentation Flow

```
User has imported audio playlist
    ↓
Navigate to Library
    ↓
Click scissors icon on playlist card
    ↓
Dialog loads → Check for importedAudioId
    ↓
    ├─ Has importedAudioId: Show preset options
    │   ↓
    │   Select preset (e.g., "Word by Word")
    │   ↓
    │   Click "Re-segment"
    │   ↓
    │   viewModel.resegmentImportedAudio()
    │   ↓
    │   libraryRepository.resegmentAudio()
    │   ↓
    │   Success message shown
    │   ↓
    │   New playlist created with modified name
    │
    └─ No importedAudioId: Show error message
        ↓
        Click "Cancel" to dismiss
```

## Visual Changes Summary

### Before
- Settings: No way to choose segmentation mode
- Library: No way to re-segment imported audio
- Users stuck with initial segmentation settings

### After
- Settings: Clean radio button selector for segmentation mode
- Library: Scissors button on every playlist card
- Dialog with 4 preset options
- Easy re-segmentation without re-importing audio

## Responsive Behavior

All UI elements use Material Design 3 components with proper:
- Touch targets (48dp minimum for buttons)
- Spacing (consistent 16dp padding)
- Typography (Material3 typography scale)
- Colors (Material3 color scheme)
- State handling (enabled/disabled states)
- Accessibility (content descriptions for icons)
