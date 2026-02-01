# Claude Code Instructions for Shadow Master

## Automated Issue Resolution Workflow

When fixing GitHub issues, Claude should follow this workflow for automatic PR creation and merging:

### Branch Naming Convention (IMPORTANT!)

Always use this branch naming pattern for issue fixes:
```
claude/issue-{ISSUE_NUMBER}-{DATE}
```

Example: `claude/issue-83-20260201-0715`

This naming convention triggers automatic PR creation and merging via GitHub Actions.

### Workflow Steps

1. **Read the issue** - Understand what needs to be fixed
2. **Create/checkout branch** - Use the naming convention above
3. **Make the fix** - Implement the solution
4. **Commit with issue reference** - Include "Fixes #XX" in commit message
5. **Push the branch** - The automation handles the rest

### Commit Message Format

Always include the issue reference to auto-close the issue when merged:

```
Fix: Brief description of the fix

Fixes #83

Detailed explanation of changes if needed.
```

### What Happens Automatically

When you push a branch matching `claude/issue-*`:

1. **Auto PR Creation** - GitHub Actions creates a PR automatically
2. **Issue Linking** - The issue is linked and will auto-close on merge
3. **Auto Merge** - PR is automatically merged when checks pass
4. **Branch Cleanup** - The branch is deleted after merge

### Example Workflow

```bash
# 1. Create branch with correct naming
git checkout -b claude/issue-83-20260201

# 2. Make your changes
# ... edit files ...

# 3. Commit with issue reference
git commit -m "Fix: Apply user's segment mode setting during import

Fixes #83

Added settingsRepository to read user preferences and apply them
during audio segmentation."

# 4. Push - automation takes over from here
git push -u origin claude/issue-83-20260201
```

### Permissions Available

Claude has these permissions in GitHub Actions:
- `contents: write` - Push branches
- `pull-requests: write` - Create and merge PRs
- `issues: write` - Comment on and close issues

### CLI Commands Available

Claude can use these `gh` CLI commands:
- `gh issue *` - View, comment, close issues
- `gh pr *` - Create, view, merge PRs
- `git push *` - Push branches to remote

### Tips for Faster Resolution

1. **Use simple, focused commits** - One fix per commit
2. **Include tests if quick** - But don't block on them
3. **Reference the issue** - Use "Fixes #XX" in the first commit
4. **Push early** - Let automation create the PR while you document

### Troubleshooting

If auto-merge doesn't trigger:
1. Check if branch name matches `claude/issue-*` pattern
2. Verify commit includes "Fixes #XX" or "Closes #XX"
3. Check GitHub Actions tab for workflow status
4. Manually enable auto-merge: `gh pr merge <num> --auto --squash`
