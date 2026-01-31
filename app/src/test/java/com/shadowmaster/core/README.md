# ShadowingStateMachine Tests

## Overview
Comprehensive unit tests for the `ShadowingStateMachine` class, covering all state transitions, configuration behaviors, and edge cases.

## Test Coverage

### Total Test Cases: 44

### Test Categories

#### 1. Idle State Tests (4 tests)
- Initial state verification
- Start event transitions
- Stop event handling
- State persistence

#### 2. Listening State Tests (2 tests)
- SegmentDetected event handling
- Invalid event rejection

#### 3. Playback State Tests (4 tests)
- Transition from SegmentDetected
- Repeat counter incrementation (1-5 repeats)
- Transition to UserRecording after final repeat
- Default configuration behavior

#### 4. Bus Mode Tests (1 test)
- Skip UserRecording in passive listening mode
- Direct return to Listening after playback

#### 5. UserRecording State Tests (3 tests)
- Correct repeat counts display
- Repeat counter incrementation (1-3 repeats)
- Return to Listening without assessment

#### 6. Assessment & Feedback Tests (4 tests)
- Transition to Assessment when enabled
- AssessmentComplete to Feedback flow
- FeedbackComplete to Listening after final repeat
- FeedbackComplete continuation for additional repeats

#### 7. Navigation Pause Tests (6 tests)
- Pause from Listening state
- Pause from Playback state
- Resume to previous state
- No transition from Idle
- No transition when already paused
- Resume via Start event

#### 8. Skip Functionality Tests (6 tests)
- Skip from Playback
- Skip from UserRecording
- Skip from Assessment
- Skip from Feedback
- No-op from Idle
- No-op from Listening

#### 9. Invalid Transition Tests (5 tests)
- PlaybackComplete from invalid states
- RecordingComplete from invalid states
- AssessmentComplete from invalid states
- FeedbackComplete from invalid states
- NavigationEnded from invalid states

#### 10. Edge Case Tests (6 tests)
- Rapid consecutive Start events
- Rapid Stop events
- Counter reset on Stop
- Counter reset on Skip
- Config updates mid-flow
- Config retrieval verification

#### 11. Complete Flow Tests (3 tests)
- Full flow with assessment enabled (all 8 states)
- Full flow without assessment (6 states)
- Full bus mode flow (passive listening)

## Test Design Patterns

### Using JUnit4
Tests use JUnit4 (matching existing project patterns) with:
- `@Before` for test setup
- `@Test` for test methods
- `Assert.*` for assertions

### Using Kotlin Coroutines Test
- `runTest` for coroutine test contexts
- `StateFlow.first()` for state verification
- Async state machine testing

### Test Data
- `testSegment`: 1-second audio sample (16000 samples @ 16kHz)
- `testUserRecording`: 0.5-second audio sample (8000 samples @ 16kHz)

## Running the Tests

```bash
# Run all ShadowingStateMachine tests
./gradlew test --tests "com.shadowmaster.core.ShadowingStateMachineTest"

# Run specific test
./gradlew test --tests "com.shadowmaster.core.ShadowingStateMachineTest.test initial state is Idle"

# Run with verbose output
./gradlew test --tests "com.shadowmaster.core.ShadowingStateMachineTest" --info
```

## State Transition Coverage

### Verified Transitions
- `Idle` → `Listening` (Start)
- `Listening` → `SegmentDetected` (SegmentDetected)
- `SegmentDetected` → `Playback` (PlaybackComplete)
- `Playback` → `Playback` (repeat increment)
- `Playback` → `UserRecording` (final PlaybackComplete, normal mode)
- `Playback` → `Listening` (final PlaybackComplete, bus mode)
- `UserRecording` → `UserRecording` (repeat increment)
- `UserRecording` → `Assessment` (RecordingComplete, assessment enabled)
- `UserRecording` → `Listening` (RecordingComplete, assessment disabled or final repeat)
- `Assessment` → `Feedback` (AssessmentComplete)
- `Feedback` → `UserRecording` (FeedbackComplete, more repeats)
- `Feedback` → `Listening` (FeedbackComplete, final repeat)
- `Any (except Idle/Paused)` → `PausedForNavigation` (NavigationStarted)
- `PausedForNavigation` → `Previous State` (NavigationEnded or Start)
- `Playback/UserRecording/Assessment/Feedback` → `Listening` (Skip)
- `Any` → `Idle` (Stop)

### Invalid Transitions Verified
All invalid event/state combinations are tested to ensure no-op behavior.

## Configuration Testing

Tests verify behavior with various `ShadowingConfig` settings:
- `playbackRepeats`: 1-5
- `userRepeats`: 1-3
- `assessmentEnabled`: true/false
- `busMode`: true/false

## Edge Cases Covered

1. **Rapid Events**: Multiple identical events in sequence
2. **Counter Management**: Reset on Stop and Skip
3. **Dynamic Config**: Config changes mid-flow
4. **Null Safety**: Handling of null currentSegment
5. **State Persistence**: stateBeforePause management

## Future Enhancements

Potential areas for additional testing:
- Performance testing with high-frequency events
- Concurrent event processing
- State history tracking
- Error recovery scenarios
