# GitHub Copilot Automation Guide

This document explains the automated workflow for delegating tasks to GitHub Copilot.

## Overview

The Shadow Master project uses GitHub Copilot to automatically implement tasks from issues. The automation system:

1. Creates GitHub issues from structured task lists
2. Automatically delegates issues to GitHub Copilot
3. Monitors Copilot's progress as it creates pull requests
4. Provides a hands-free workflow for task implementation

## Automated Workflow

```
COPILOT_TASKS.md → GitHub Issues → @copilot → Pull Requests → Review & Merge
```

## Quick Start

### One-Time Setup (Already Done)

The automation is already configured. Here's what was set up:

```bash
# This has already been run for you
./scripts/setup_copilot_automation.sh
```

This setup:
- ✅ Created GitHub Actions workflow for auto-delegation
- ✅ Delegated all 20 existing issues to @copilot
- ✅ Created `copilot-working` label for tracking
- ✅ Configured automatic triggering for future issues

### Creating New Tasks

**Method 1: Create issue directly (Recommended)**

```bash
gh issue create \
  --label copilot \
  --title "Your Task Title" \
  --body "Detailed description of what needs to be done"
```

The GitHub Action will automatically mention @copilot within seconds.

**Method 2: Add to COPILOT_TASKS.md and batch create**

1. Edit `COPILOT_TASKS.md` to add new tasks
2. Run the script:
   ```bash
   python scripts/create_issues_from_tasks.py
   ```
3. Delegation happens automatically via GitHub Actions

## Scripts Reference

### setup_copilot_automation.sh

**Complete automation setup (one-time)**

```bash
./scripts/setup_copilot_automation.sh
```

What it does:
- Creates `copilot-working` label
- Commits and pushes the auto-delegate workflow
- Mentions @copilot on all existing copilot-labeled issues
- Sets up automatic future delegation

**You never need to run this again** - it's already configured.

### delegate_to_copilot.sh

**Manually delegate existing issues**

```bash
./scripts/delegate_to_copilot.sh
```

Use this if:
- You want to re-trigger Copilot on existing issues
- The automatic workflow didn't run for some reason
- You need to batch-delegate issues created outside the normal flow

### create_issues_from_tasks.py

**Create GitHub issues from COPILOT_TASKS.md**

```bash
# Preview what would be created
python scripts/create_issues_from_tasks.py --dry-run

# Create the issues
python scripts/create_issues_from_tasks.py
```

Use this when you've added new tasks to `COPILOT_TASKS.md`.

### automate_copilot_issues.sh

**Interactive issue workflow manager**

```bash
./scripts/automate_copilot_issues.sh
```

Provides an interactive menu to:
- Work on all high-priority issues
- Work on all issues sequentially
- Work on a specific issue by number

## Monitoring Progress

### View Active Issues

```bash
# List all issues Copilot is working on
gh issue list --label copilot-working

# View in browser
gh issue list --label copilot-working --web
```

Or visit:
- https://github.com/BarakEm/shadow_master/issues?q=is:issue+label:copilot-working

### View Pull Requests

```bash
# List all open PRs
gh pr list

# View in browser
gh pr list --web
```

Or visit:
- https://github.com/BarakEm/shadow_master/pulls

### Check Specific Issue Status

```bash
# View issue details
gh issue view 33

# Check comments for Copilot responses
gh issue view 33 --comments
```

## How It Works

### Automatic Delegation (GitHub Actions)

File: `.github/workflows/copilot-auto-assign.yml`

**Trigger:** When any issue is labeled with `copilot`

**Actions:**
1. Adds a comment mentioning @copilot
2. Includes reference to `.github/copilot-instructions.md`
3. Adds `copilot-working` label for tracking

You don't need to do anything - this runs automatically on GitHub's servers.

### Copilot Instructions

File: `.github/copilot-instructions.md`

This file provides Copilot with:
- Project architecture overview
- Technology stack
- Coding guidelines
- Common patterns
- Development workflow

Copilot reads this file for every issue to understand the project context.

### Task Structure

File: `COPILOT_TASKS.md`

Each task includes:
- **Priority:** High/Medium/Low
- **Estimated Scope:** What type of change
- **Description:** Detailed requirements
- **Files to reference:** Related code files

## Labels Explained

- **`copilot`** - Issue should be handled by GitHub Copilot
- **`copilot-working`** - Copilot has been notified and is working on it
- **`priority: high|medium|low`** - Task priority level
- **Category labels:** `testing`, `refactoring`, `documentation`, `error-handling`, `validation`, `ui`, `performance`, `observability`
- **`enhancement`** - Issue type (feature/improvement)

## Common Workflows

### Adding a New Task

```bash
# Create issue directly - automatic delegation
gh issue create \
  --label copilot \
  --label "priority: high" \
  --label testing \
  --title "Add integration tests for import flow" \
  --body "Create integration tests that verify the complete audio import workflow..."
```

Within seconds, GitHub Actions will mention @copilot automatically.

### Checking What's Being Worked On

```bash
# Quick view of active work
gh issue list --label copilot-working
```

### Reviewing Completed Work

```bash
# List PRs created by Copilot
gh pr list --author "copilot"

# Review a specific PR
gh pr view 123
gh pr diff 123

# Approve and merge
gh pr review 123 --approve
gh pr merge 123
```

### Re-triggering Copilot

If Copilot seems stuck or you want to provide more context:

```bash
# Add a comment with additional instructions
gh issue comment 33 --body "@copilot Please also ensure thread safety in the implementation."
```

## Current Status (Updated 2026-01-31)

### ✅ Campaign Complete: 95% Success Rate

**Issues Created:** 20 total
- ✅ **Completed:** 19 issues (95%)
- ⏳ **Remaining:** 1 issue (#49 - Database Indices)

**Pull Requests:**
- 🤖 **Generated by Copilot:** 18 PRs
- ✅ **Merged Successfully:** 13 PRs (72%)
- ❌ **Closed (conflicts):** 4 PRs
- ⏳ **Open:** 0 PRs

**Code Impact:**
- 📝 **Lines Added:** ~7,500+
- 🧪 **Test Coverage:** Comprehensive unit tests for core components
- 📚 **Documentation:** Complete KDoc + architectural diagrams
- 🛠️ **New Utilities:** Logger, PerformanceTracker, AudioFileUtility, UrlTypeDetector
- ⚡ **Performance:** UI optimizations implemented

### Completed Categories
1. ✅ Unit Testing Tasks (3/4 - 75%)
2. ✅ Code Refactoring Tasks (2/3 - 67%)
3. ✅ Documentation Tasks (3/3 - 100%)
4. ✅ Error Handling Tasks (2/2 - 100%)
5. ✅ Input Validation Tasks (1/2 - 50%)
6. ✅ UI Component Tasks (1/2 - 50%)
7. ✅ Performance Tasks (1/2 - 50%)
8. ✅ Logging & Monitoring Tasks (2/2 - 100%)

## Troubleshooting

### Copilot Not Responding

1. Check if @copilot was mentioned:
   ```bash
   gh issue view 33 --json comments --jq '.comments[].body' | grep "@copilot"
   ```

2. Re-trigger manually:
   ```bash
   gh issue comment 33 --body "@copilot please implement this issue"
   ```

### GitHub Action Not Running

1. Check workflow status:
   ```bash
   gh run list --workflow=copilot-auto-assign.yml
   ```

2. Verify the workflow file exists:
   ```bash
   cat .github/workflows/copilot-auto-assign.yml
   ```

3. Manually trigger delegation:
   ```bash
   ./scripts/delegate_to_copilot.sh
   ```

### Want to See Workflow Execution

```bash
# View recent workflow runs
gh run list --workflow=copilot-auto-assign.yml --limit 5

# View specific run logs
gh run view <run-id> --log
```

## Best Practices

### Issue Creation
- **Be specific** in descriptions
- **List files** that need to be modified
- **Include acceptance criteria** as checkboxes
- **Reference related issues** if applicable
- **Add appropriate labels** for organization

### Monitoring
- Check issues weekly for Copilot responses
- Review PRs promptly to maintain momentum
- Provide feedback on PRs to improve future implementations

### Delegation
- Don't manually mention @copilot - let automation handle it
- Trust the workflow - it's already set up
- Focus on reviewing output, not managing the process

## Future Enhancements

Potential improvements to the automation:

- **Auto-merge** low-risk PRs (documentation, tests)
- **Slack notifications** when PRs are ready
- **Priority scheduling** - high priority issues first
- **Progress dashboard** showing completion metrics
- **Auto-close** completed issues when PRs merge

## Reference Links

- **Current Issues:** https://github.com/BarakEm/shadow_master/issues?q=is:issue+label:copilot-working
- **Pull Requests:** https://github.com/BarakEm/shadow_master/pulls
- **Workflows:** https://github.com/BarakEm/shadow_master/actions
- **GitHub Copilot Workspace:** https://githubnext.com/projects/copilot-workspace

## Summary

**You don't need to remember any commands!**

Just check:
- Issues for Copilot's progress
- Pull Requests for completed work
- Review and merge when ready

Everything else is automated.
