#!/bin/bash
# Script to remove stale branches that have been merged into master
# Generated on: 2026-02-01
#
# These branches have been identified as merged into master and can be safely deleted.

set -e

echo "Removing stale branches that have been merged into master..."
echo ""

# List of merged branches to delete
MERGED_BRANCHES=(
    "add-claude-github-actions-1769791720970"
    "claude/add-mic-import-fallback-lcJgN"
    "claude/add-quick-features-g5lBD"
    "claude/cli-background-activities-9pm1L"
    "claude/delegate-copilot-tasks-x2KUo"
    "claude/fix-build-failure-f7eiZ"
    "claude/fix-build-failures-mpfKp"
    "claude/fix-progress-indicator-crash-cfcr9"
    "copilot/add-performance-metrics-collection"
    "copilot/add-playing-audio-capturing"
    "copilot/add-segment-mode-selector"
    "copilot/check-all-open-issues"
    "copilot/check-session-failures"
    "copilot/create-issues-from-tasks"
    "copilot/fix-audio-import-segmentation"
    "copilot/fix-audio-segmentation-issue"
    "copilot/fix-import-audio-issue"
    "copilot/fix-playback-loading-issue"
    "copilot/fix-workflow-build-error"
    "copilot/fix-youtube-audio-import-error"
    "copilot/set-up-copilot-instructions"
    "copilot/update-dependencies-and-fixes"
    "fix/null-safety-audioImporter"
)

echo "Found ${#MERGED_BRANCHES[@]} stale branches to remove:"
echo ""

for branch in "${MERGED_BRANCHES[@]}"; do
    echo "  - $branch"
done

echo ""
read -p "Do you want to delete these branches? (y/N) " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    for branch in "${MERGED_BRANCHES[@]}"; do
        echo "Deleting: $branch"
        git push origin --delete "$branch" 2>/dev/null || echo "  (already deleted or not found)"
    done
    echo ""
    echo "Done! Stale branches have been removed."
else
    echo "Aborted. No branches were deleted."
fi
