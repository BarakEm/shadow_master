# Scripts Directory

This directory contains utility scripts for the Shadow Master project.

## create_issues_from_tasks.py

A Python script that automatically creates GitHub issues from the tasks defined in `COPILOT_TASKS.md`.

### Purpose

This script parses the structured task list in `COPILOT_TASKS.md` and creates corresponding GitHub issues with proper formatting, labels, and metadata. This enables GitHub Copilot agents to automatically work on these tasks and create pull requests.

### Prerequisites

- Python 3.6 or higher
- [GitHub CLI (gh)](https://cli.github.com/) installed and authenticated
- Run from the repository root directory

### Installation

#### Install GitHub CLI

**macOS:**
```bash
brew install gh
```

**Windows:**
```bash
winget install GitHub.cli
```

**Linux:**
```bash
# Debian/Ubuntu
sudo apt install gh

# Fedora/RHEL
sudo dnf install gh
```

#### Authenticate with GitHub

```bash
gh auth login
```

Follow the prompts to authenticate with your GitHub account.

### Usage

#### Dry Run (Recommended First)

Preview what issues would be created without actually creating them:

```bash
python scripts/create_issues_from_tasks.py --dry-run
```

This will display all 20 tasks that would be converted to issues, showing:
- Issue title
- Labels that would be applied
- Full issue body with description, files, and acceptance criteria

#### Create Issues

Once you've reviewed the dry run output, create the actual issues:

```bash
python scripts/create_issues_from_tasks.py
```

The script will:
1. Parse all 20 tasks from `COPILOT_TASKS.md`
2. Create a GitHub issue for each task
3. Apply appropriate labels based on priority and category
4. Display progress and issue URLs

### What Gets Created

Each GitHub issue includes:

- **Title**: The task name (e.g., "Add Unit Tests for ShadowingStateMachine")
- **Priority**: Marked in the issue body (High/Medium/Low)
- **Scope**: Estimated scope from the original task
- **Description**: Full task description with requirements
- **Files to Reference/Modify**: List of relevant files
- **Acceptance Criteria**: Checklist for completion
- **Labels**:
  - `copilot` - Marks issue for Copilot agent processing
  - `priority: high|medium|low` - Priority level
  - Category label: `testing`, `refactoring`, `documentation`, etc.
  - `enhancement` - Issue type

### Task Categories

The script handles all task categories from COPILOT_TASKS.md:

1. **Unit Testing Tasks** (5 tasks) - Test creation for core components
2. **Code Refactoring Tasks** (3 tasks) - Code organization and cleanup
3. **Documentation Tasks** (3 tasks) - KDoc and architectural documentation
4. **Error Handling Tasks** (2 tasks) - Structured error types and user messages
5. **Input Validation Tasks** (2 tasks) - Input sanitization and validation
6. **UI Component Tasks** (2 tasks) - Reusable UI components and previews
7. **Performance Tasks** (2 tasks) - Database optimization and rendering improvements
8. **Logging & Monitoring Tasks** (2 tasks) - Logging infrastructure and metrics

### Priority Distribution

- **High Priority** (5 tasks): Tasks 1, 2, 5, 11, 13
- **Medium Priority** (11 tasks): Tasks 3, 4, 6, 7, 8, 9, 12, 15, 17, 18, 19
- **Low Priority** (4 tasks): Tasks 10, 14, 16, 20

### Examples

#### Example Issue Created

```
Title: Add Unit Tests for ShadowingStateMachine

**Priority:** High
**Estimated Scope:** New test file

## Description
Create unit tests for `core/ShadowingStateMachine.kt` covering all state transitions:
- Test transitions from `Idle` to `Playing`, `Paused`, `Stopped`
- Test transitions from `Playing` to all valid next states
- Test invalid state transitions throw appropriate errors
- Test edge cases like rapid state changes
- Use JUnit5 and MockK for mocking dependencies

## Files to Reference/Modify
- app/src/main/kotlin/com/barak/shadowmaster/core/ShadowingStateMachine.kt
- app/src/main/kotlin/com/barak/shadowmaster/data/model/ShadowingState.kt

## Acceptance Criteria
- [ ] Implementation complete
- [ ] Code follows project conventions
- [ ] Changes tested
- [ ] Documentation updated (if applicable)

---
*Task #1 from Unit Testing Tasks*

Labels: copilot, priority: high, testing, enhancement
```

### Troubleshooting

#### "GitHub CLI (gh) not found"

Install the GitHub CLI following the installation instructions above, then authenticate:
```bash
gh auth login
```

#### "Failed to create issue"

1. Ensure you're authenticated: `gh auth status`
2. Check you have write access to the repository
3. Verify the repository name is correct
4. Check if labels exist (the script will create issues even if labels don't exist, but won't fail)

#### Issues already exist

The script doesn't check for existing issues. If you run it multiple times, it will create duplicate issues. You can:
- Manually close duplicates
- Delete the `COPILOT_TASKS.md` file after running the script once
- Modify the script to check for existing issues before creating new ones

### Workflow with GitHub Copilot

After creating issues:

1. **Automatic Processing**: GitHub Copilot agents can automatically detect issues with the `copilot` label
2. **PR Creation**: Copilot will create pull requests addressing the issues
3. **Review & Merge**: Review the PRs and merge when ready
4. **Track Progress**: Use GitHub Projects or issue filters to track completion

### Customization

The script can be modified to:
- Change label names (edit `get_labels()` method)
- Modify issue body format (edit `to_issue_body()` method)
- Filter tasks by priority (add filtering in `main()`)
- Add custom metadata (extend the `Task` class)

### Files Modified

- `scripts/create_issues_from_tasks.py` - The main script
- `scripts/README.md` - This documentation
