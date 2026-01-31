#!/bin/bash
# Delegate all copilot issues to GitHub Copilot bot

set -e

echo "Delegating issues to GitHub Copilot..."
echo ""

# Get all copilot issues
ISSUES=$(gh issue list --label copilot --limit 100 --json number --jq '.[].number' | sort -n)

for issue in $ISSUES; do
    echo "Processing issue #$issue..."
    
    # Add a comment mentioning @copilot to trigger the bot
    gh issue comment $issue --body "@copilot please implement this issue following the specifications in the description and the guidelines in .github/copilot-instructions.md"
    
    echo "  ✓ Delegated issue #$issue to @copilot"
    
    # Small delay to avoid rate limiting
    sleep 1
done

echo ""
echo "======================================"
echo "Delegation complete!"
echo "GitHub Copilot bot has been notified for all issues."
echo "Monitor progress at: https://github.com/BarakEm/shadow_master/issues"
echo "======================================"
