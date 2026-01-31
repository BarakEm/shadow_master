#!/bin/bash
# Complete setup for GitHub Copilot automation

set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "======================================"
echo "GitHub Copilot Automation Setup"
echo "======================================"
echo ""

echo "This script will:"
echo "  1. Commit and push the Copilot auto-assign workflow"
echo "  2. Delegate all existing copilot-labeled issues to @copilot"
echo "  3. Set up labels for tracking"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 1
fi

echo ""
echo "Step 1: Creating copilot-working label..."
gh label create "copilot-working" --description "Copilot is currently working on this issue" --color "0E8A16" 2>/dev/null || echo "  Label already exists"

echo ""
echo "Step 2: Committing GitHub Actions workflow..."
git add .github/workflows/copilot-auto-assign.yml
git commit -m "Add GitHub Actions workflow to auto-delegate copilot issues

This workflow automatically mentions @copilot when an issue is labeled with 'copilot',
triggering GitHub Copilot to work on the issue.
" 2>/dev/null || echo "  No changes to commit"

echo ""
echo "Step 3: Pushing workflow to GitHub..."
git push 2>/dev/null || echo "  Already pushed or no changes"

echo ""
echo "Step 4: Delegating existing issues to @copilot..."
ISSUES=$(gh issue list --label copilot --limit 100 --json number --jq '.[].number' | sort -n)
COUNT=$(echo "$ISSUES" | wc -w)

echo "  Found $COUNT issues to delegate"
echo ""

for issue in $ISSUES; do
    TITLE=$(gh issue view $issue --json title --jq '.title')
    echo "  Processing #$issue: $TITLE"
    
    # Check if we already commented
    HAS_COPILOT_MENTION=$(gh issue view $issue --json comments --jq '.comments[].body' | grep -c "@copilot" || true)
    
    if [ "$HAS_COPILOT_MENTION" -eq 0 ]; then
        gh issue comment $issue --body "@copilot please implement this issue following the specifications in the description and the guidelines in \`.github/copilot-instructions.md\`"
        gh issue edit $issue --add-label "copilot-working"
        echo "    ✓ Delegated to @copilot"
    else
        echo "    ⊘ Already delegated"
    fi
    
    sleep 0.5
done

echo ""
echo "======================================"
echo "Setup Complete!"
echo "======================================"
echo ""
echo "What happens next:"
echo "  • GitHub Copilot will receive notifications for all $COUNT issues"
echo "  • Copilot will analyze each issue and create implementation plans"
echo "  • When ready, Copilot will create pull requests"
echo "  • You can review and merge the PRs"
echo ""
echo "Monitor progress:"
echo "  • Issues: https://github.com/BarakEm/shadow_master/issues?q=is:issue+label:copilot-working"
echo "  • PRs: https://github.com/BarakEm/shadow_master/pulls"
echo ""
echo "Future issues labeled 'copilot' will be automatically delegated!"
echo ""
