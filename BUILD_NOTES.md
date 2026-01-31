# Build Notes

## Machine Limitations

**⚠️ DO NOT run Gradle builds on this machine**

This is a weak machine. Gradle builds take too long and may crash.

### Alternative Build Methods

1. **Android Studio**: Use Android Studio's built-in build system
2. **Skip Verification**: Trust that code compiles based on IDE linting
3. **Remote Build**: If needed, build on a more powerful machine

### Blocked Commands

- `./gradlew assembleDebug`
- `./gradlew build`
- `./gradlew test`
- Any `gradlew` command with `--no-daemon`

### Code Verification Instead

Use these lightweight alternatives:
- Read code carefully for syntax errors
- Check imports and dependencies
- Verify method signatures match
- Use Grep/Read tools to cross-reference

## Critical Issues

### Issue #75: Audio Import Creates Empty Playlist

After merging all 20 Copilot PRs, audio import is broken:
- Import appears to succeed
- Playlist is created
- But playlist contains no items
- App is currently unusable

**Suspected causes:**
- VAD initialization failing
- Segment detection returning empty
- Database insertion not committing
- AudioFileUtility injection issue (from PR #62)
- Error handling changes (from PR #67) swallowing errors

**Delegated to:** GitHub Copilot (Issue #75)
**Priority:** CRITICAL - blocks all app functionality
