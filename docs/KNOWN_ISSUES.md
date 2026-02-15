# Known Issues

## Claude Code: Second PR in single chat session silently dropped

**Status:** Open
**Severity:** Medium
**Upstream:** https://github.com/anthropics/claude-code/issues

### Description

When asking Claude Code to create two PRs sequentially in a single chat session,
the second PR's code gets committed and pushed to the branch, but the PR itself
is never created on GitHub. No error is shown to the user.

### Steps to Reproduce

1. Start a Claude Code session (web/mobile)
2. Ask Claude to implement a change and create PR #1 → succeeds
3. In the same chat, ask for a second change and PR #2
4. Code is committed and pushed, but the PR is never opened

### Evidence

- Repo: `BarakEm/shadow_master`
- Branch: `claude/mobile-friendly-web-app-hkP70`
- PR #166 (first request) → created and merged
- Commit `49928b1` (second request) → pushed but no PR created
- Had to recover manually in a new session → PR #167

### Impact

Work is silently lost. The user has no indication the second PR was not created
since the commit exists on the branch.

### Workaround

Use a separate chat session for each PR.
