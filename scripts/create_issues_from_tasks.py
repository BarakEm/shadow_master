#!/usr/bin/env python3
"""
Script to create GitHub issues from COPILOT_TASKS.md

This script parses the COPILOT_TASKS.md file and creates GitHub issues for each task.
It uses the GitHub CLI (gh) to create issues.

Prerequisites:
- GitHub CLI (gh) must be installed and authenticated
- Run from the repository root directory

Usage:
    ./scripts/create_issues_from_tasks.py [--dry-run]
    OR
    python3 scripts/create_issues_from_tasks.py [--dry-run]

Options:
    --dry-run    Show what would be created without actually creating issues
"""

import re
import subprocess
import sys
from pathlib import Path
from typing import List, Dict, Optional


class Task:
    """Represents a single task from COPILOT_TASKS.md"""
    
    def __init__(self):
        self.number: Optional[int] = None
        self.title: str = ""
        self.priority: str = ""
        self.scope: str = ""
        self.description: str = ""
        self.files: List[str] = []
        self.category: str = ""
        
    def to_issue_body(self) -> str:
        """Convert task to GitHub issue body format"""
        body_parts = []
        
        # Add priority and scope
        body_parts.append(f"**Priority:** {self.priority}")
        body_parts.append(f"**Estimated Scope:** {self.scope}")
        body_parts.append("")
        
        # Add description
        body_parts.append("## Description")
        body_parts.append(self.description.strip())
        body_parts.append("")
        
        # Add files to reference
        if self.files:
            body_parts.append("## Files to Reference/Modify")
            for file in self.files:
                body_parts.append(f"- {file}")
            body_parts.append("")
        
        # Add acceptance criteria
        body_parts.append("## Acceptance Criteria")
        body_parts.append("- [ ] Implementation complete")
        body_parts.append("- [ ] Code follows project conventions")
        body_parts.append("- [ ] Changes tested")
        body_parts.append("- [ ] Documentation updated (if applicable)")
        body_parts.append("")
        
        # Add category reference
        body_parts.append(f"---")
        body_parts.append(f"*Task #{self.number} from {self.category}*")
        
        return "\n".join(body_parts)
    
    def get_labels(self) -> List[str]:
        """Get appropriate labels for this task"""
        labels = ["copilot"]
        
        # Priority-based labels
        priority_lower = self.priority.lower()
        if "high" in priority_lower:
            labels.append("priority: high")
        elif "medium" in priority_lower:
            labels.append("priority: medium")
        elif "low" in priority_lower:
            labels.append("priority: low")
        
        # Category-based labels
        category_lower = self.category.lower()
        if "testing" in category_lower or "test" in category_lower:
            labels.append("testing")
        elif "refactoring" in category_lower:
            labels.append("refactoring")
        elif "documentation" in category_lower:
            labels.append("documentation")
        elif "error handling" in category_lower:
            labels.append("error-handling")
        elif "validation" in category_lower:
            labels.append("validation")
        elif "ui" in category_lower:
            labels.append("ui")
        elif "performance" in category_lower:
            labels.append("performance")
        elif "logging" in category_lower or "monitoring" in category_lower:
            labels.append("observability")
        
        labels.append("enhancement")
        
        return labels


def parse_tasks_file(file_path: Path) -> List[Task]:
    """Parse COPILOT_TASKS.md and extract all tasks"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    tasks = []
    current_task = None
    current_category = ""
    in_description = False
    in_files = False
    
    lines = content.split('\n')
    i = 0
    
    while i < len(lines):
        line = lines[i]
        
        # Detect category headers (## Category Name)
        if line.startswith('## ') and 'Tasks' in line:
            current_category = line[3:].strip()
            in_description = False
            in_files = False
            i += 1
            continue
        
        # Detect task headers (### N. Task Title)
        task_match = re.match(r'^### (\d+)\.\s+(.+)$', line)
        if task_match:
            # Save previous task
            if current_task:
                tasks.append(current_task)
            
            # Start new task
            current_task = Task()
            current_task.number = int(task_match.group(1))
            current_task.title = task_match.group(2).strip()
            current_task.category = current_category
            in_description = False
            in_files = False
            i += 1
            continue
        
        if current_task:
            # Parse priority
            priority_match = re.match(r'^\*\*Priority:\*\*\s+(.+)$', line)
            if priority_match:
                current_task.priority = priority_match.group(1).strip()
                i += 1
                continue
            
            # Parse scope
            scope_match = re.match(r'^\*\*Estimated Scope:\*\*\s+(.+)$', line)
            if scope_match:
                current_task.scope = scope_match.group(1).strip()
                in_description = True
                i += 1
                continue
            
            # Detect files section
            if line.startswith('**Files to'):
                in_files = True
                in_description = False
                i += 1
                continue
            
            # Parse files
            if in_files and line.startswith('- '):
                file_path = line[2:].strip()
                # Remove backticks if present
                file_path = file_path.strip('`')
                current_task.files.append(file_path)
                i += 1
                continue
            
            # Parse description (everything between scope and files/separator)
            if in_description and line.strip() and not line.startswith('---') and not line.startswith('##'):
                if not line.startswith('**'):
                    current_task.description += line + '\n'
                i += 1
                continue
            
            # Stop description at separator
            if line.startswith('---') or (line.startswith('##') and 'Tasks' in line):
                in_description = False
                in_files = False
        
        i += 1
    
    # Add last task
    if current_task:
        tasks.append(current_task)
    
    return tasks


def create_github_issue(task: Task, dry_run: bool = False) -> bool:
    """Create a GitHub issue using gh CLI"""
    title = f"{task.title}"
    body = task.to_issue_body()
    labels = task.get_labels()
    
    if dry_run:
        print(f"\n{'='*80}")
        print(f"Would create issue #{task.number}:")
        print(f"Title: {title}")
        print(f"Labels: {', '.join(labels)}")
        print(f"\nBody:\n{body}")
        print(f"{'='*80}\n")
        return True
    
    # Build gh command
    cmd = [
        'gh', 'issue', 'create',
        '--title', title,
        '--body', body,
    ]
    
    # Add labels
    for label in labels:
        cmd.extend(['--label', label])
    
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            check=True
        )
        print(f"✓ Created issue #{task.number}: {title}")
        print(f"  {result.stdout.strip()}")
        return True
    except subprocess.CalledProcessError as e:
        print(f"✗ Failed to create issue #{task.number}: {title}")
        print(f"  Error: {e.stderr}")
        return False
    except FileNotFoundError:
        print("✗ GitHub CLI (gh) not found. Please install it first:")
        print("  https://cli.github.com/")
        sys.exit(1)


def main():
    """Main entry point"""
    dry_run = '--dry-run' in sys.argv
    
    # Find COPILOT_TASKS.md
    repo_root = Path(__file__).parent.parent
    tasks_file = repo_root / 'COPILOT_TASKS.md'
    
    if not tasks_file.exists():
        print(f"Error: COPILOT_TASKS.md not found at {tasks_file}")
        sys.exit(1)
    
    print(f"Parsing {tasks_file}...")
    tasks = parse_tasks_file(tasks_file)
    print(f"Found {len(tasks)} tasks\n")
    
    if dry_run:
        print("DRY RUN MODE - No issues will be created\n")
    
    # Create issues
    success_count = 0
    for task in tasks:
        if create_github_issue(task, dry_run):
            success_count += 1
    
    print(f"\n{'='*80}")
    if dry_run:
        print(f"Dry run complete: {success_count}/{len(tasks)} tasks ready to create")
        print("\nTo create issues for real, run:")
        print("  ./scripts/create_issues_from_tasks.py")
    else:
        print(f"Successfully created {success_count}/{len(tasks)} issues")
    
    return 0 if success_count == len(tasks) else 1


if __name__ == '__main__':
    sys.exit(main())
