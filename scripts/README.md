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

---

## setup_copilot_automation.sh

Complete one-time setup for GitHub Copilot automation.

### Purpose

This script sets up the entire automation infrastructure for delegating issues to GitHub Copilot. It:
- Creates necessary labels
- Commits and pushes the auto-delegation GitHub Actions workflow
- Delegates all existing copilot-labeled issues to @copilot
- Provides status tracking

**This has already been run and configured. You don't need to run it again.**

### What It Does

1. Creates `copilot-working` label for tracking
2. Commits `.github/workflows/copilot-auto-assign.yml`
3. Pushes the workflow to GitHub
4. Mentions @copilot on all existing copilot-labeled issues
5. Adds `copilot-working` label to delegated issues
6. Provides summary and monitoring links

### Usage

```bash
./scripts/setup_copilot_automation.sh
```

The script is interactive and will prompt for confirmation before proceeding.

### Output

```
======================================
GitHub Copilot Automation Setup
======================================

This script will:
  1. Commit and push the Copilot auto-assign workflow
  2. Delegate all existing copilot-labeled issues to @copilot
  3. Set up labels for tracking

Continue? (y/n)
```

---

## delegate_to_copilot.sh

Manually delegate all copilot-labeled issues to GitHub Copilot.

### Purpose

This script mentions @copilot on all issues labeled with `copilot`, triggering GitHub Copilot to work on them. Use this if you need to re-trigger the delegation or if the automatic workflow didn't run.

### Usage

```bash
./scripts/delegate_to_copilot.sh
```

### What It Does

1. Finds all issues with the `copilot` label
2. Adds a comment mentioning @copilot with reference to `.github/copilot-instructions.md`
3. Displays progress for each issue
4. Provides monitoring links when complete

### Example Output

```
Delegating issues to GitHub Copilot...

Processing issue #33...
  ✓ Delegated issue #33 to @copilot
Processing issue #34...
  ✓ Delegated issue #34 to @copilot
...

======================================
Delegation complete!
GitHub Copilot bot has been notified for all issues.
Monitor progress at: https://github.com/BarakEm/shadow_master/issues
======================================
```

---

## automate_copilot_issues.sh

Interactive workflow manager for working through copilot issues.

### Purpose

Provides an interactive menu to browse and work on copilot issues systematically. This script helps organize work by priority and creates feature branches for each issue.

### Usage

```bash
./scripts/automate_copilot_issues.sh
```

### Interactive Menu

```
Select implementation mode:
  1) Work on all HIGH priority issues (5 issues)
  2) Work on ALL issues (20 issues)
  3) Work on a specific issue number
  4) Exit

Enter choice [1-4]:
```

### What It Does

- Fetches all copilot-labeled issues sorted by priority
- Creates feature branches for each issue (`copilot/issue-{number}`)
- Displays issue details for context
- Allows step-by-step progression through issues

### Workflow Example

```
======================================
Working on Issue #33
Title: Add Unit Tests for ShadowingStateMachine
======================================

Created/switched to branch: copilot/issue-33

Ready for implementation...
Issue details:
[Issue description displayed here]

Press Enter to continue to next issue, or Ctrl+C to stop...
```

---

## Monitoring and Commands

### View Active Issues

```bash
# List all issues Copilot is working on
gh issue list --label copilot-working

# View specific issue
gh issue view 33

# Check comments on an issue
gh issue view 33 --comments
```

### View Pull Requests

```bash
# List all open PRs
gh pr list

# View specific PR
gh pr view 42

# Review PR diff
gh pr diff 42
```

### Create New Copilot Tasks

```bash
# Create a new issue that Copilot will automatically work on
gh issue create \
  --label copilot \
  --label "priority: high" \
  --title "Your task title" \
  --body "Detailed description of what needs to be done"
```

The GitHub Actions workflow will automatically mention @copilot within seconds.

---

## Complete Documentation

For comprehensive documentation on the automation system, see:
- **[COPILOT_AUTOMATION.md](../COPILOT_AUTOMATION.md)** - Complete automation guide
- **[COPILOT_TASKS.md](../COPILOT_TASKS.md)** - Task list template
- **[.github/copilot-instructions.md](../.github/copilot-instructions.md)** - Instructions for Copilot

---

## Quick Reference

| Script | Purpose | When to Use |
|--------|---------|-------------|
| `create_issues_from_tasks.py` | Create issues from COPILOT_TASKS.md | After adding new tasks to the task list |
| `setup_copilot_automation.sh` | One-time automation setup | Already done - don't run again |
| `delegate_to_copilot.sh` | Manually delegate issues | If auto-delegation fails or needs retry |
| `automate_copilot_issues.sh` | Interactive issue browser | To work through issues systematically |

---

## Automation Status

✅ **Setup Complete**
- GitHub Actions workflow is active
- Auto-delegation is enabled
- 20 issues delegated to @copilot
- All future `copilot`-labeled issues will auto-delegate

**You don't need to remember commands** - everything is automated. Just:
1. Create issues with the `copilot` label
2. Monitor pull requests
3. Review and merge
