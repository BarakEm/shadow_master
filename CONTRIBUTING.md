# Contributing to Shadow Master

Thank you for your interest in contributing to Shadow Master!

## Branch Protection

The `master` branch is protected with these rules:
- **Pull requests required** - No direct pushes to master
- **Reviews required** - PRs need approval before merging
- **Conversations resolved** - All review comments must be addressed
- **Up-to-date branches** - PRs must be current with master

## Development Workflow

### For Human Contributors

1. **Fork the repository** (external contributors) or **create a branch** (team members)

2. **Branch naming convention:**
   ```
   feature/short-description
   fix/issue-number-description
   improve/what-you-improved
   ```

3. **Make your changes** following the coding guidelines in `.github/copilot-instructions.md`

4. **Create a pull request:**
   - Use a clear, descriptive title
   - Reference any related issues with "Fixes #XX" or "Relates to #XX"
   - Fill out the PR template

5. **Address review feedback** and get approval

6. **Merge** - Use squash merge for clean history

### For Claude (AI Assistant)

Claude follows an automated workflow:

1. **Branch naming:** `claude/issue-{NUMBER}-{DATE}`
2. **Auto PR creation** when branch is pushed
3. **Auto approval** by GitHub Actions
4. **Auto merge** when checks pass

This allows rapid iteration on issue fixes while maintaining history.

## Code Review Guidelines

### Automated Code Review

All pull requests automatically receive a code review from Claude as part of the build workflow. Claude reviews:
- Code quality and adherence to Kotlin best practices
- Proper resource management (MediaCodec, MediaExtractor, etc.)
- Coroutine usage and dispatcher selection
- Naming accuracy
- Avoiding unnecessary comments
- Testing coverage for new features

The automated review follows the guidelines in `CLAUDE.md` and `.github/claude-instructions.md`.

### What Human Reviewers Look For

- [ ] Code follows Kotlin style guidelines
- [ ] Compose UI patterns are used correctly
- [ ] State management follows existing patterns
- [ ] Error handling is appropriate
- [ ] No breaking changes to public APIs
- [ ] Tests are included for new functionality
- [ ] Documentation is updated if needed

### Review Turnaround

- **Critical fixes:** Same day
- **Bug fixes:** 1-2 days
- **Features:** 2-5 days

## Commit Messages

Use conventional commit format:

```
type: Brief description

Longer explanation if needed.

Fixes #123
```

Types:
- `feat:` New feature
- `fix:` Bug fix
- `refactor:` Code refactoring
- `docs:` Documentation
- `test:` Tests
- `chore:` Maintenance

## Testing

Before submitting a PR:

1. **Run lint:** `./gradlew lint`
2. **Run tests:** `./gradlew test`
3. **Test on device** if touching audio/UI code

### Continuous Integration

All PRs automatically run:
- **Build workflow** - Builds debug and release APKs
- **Claude code review** - Automated code quality review
- **Additional checks** - As configured in `.github/workflows/`

PRs must pass all checks before merging.

## Getting Help

- **Questions:** Open a Discussion
- **Bugs:** Open an Issue with reproduction steps
- **Features:** Open an Issue describing the use case

## Code of Conduct

Be respectful and constructive in all interactions.
