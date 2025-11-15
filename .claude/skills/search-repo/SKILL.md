---
name: search-repo
description: Search repository for code patterns or text
---

# Search Repo

## When to use this skill

Use this skill when:
- Working with search, filesystem
- User asks to "search repo"

## Prerequisites

- Use ripgrep or similar fast search tool if available.

## How to use

This skill uses the MCP tool: `filesystem:search`. The tool will be invoked automatically when you use this skill.

## Inputs

### `pattern` (required)

- Type: `string`
- Description: Search pattern (regex or plain text)

### `path` (optional)

- Type: `directory`
- Description: Directory to search in (defaults to repo root)

### `file_pattern` (optional)

- Type: `string`
- Description: File pattern to filter (e.g., '*.py', '*.kt')

## Examples

### Basic usage

```
User: "Can you search repo?"
Assistant: [Interprets output and reports results]
```

## Troubleshooting

### Timeout

If the command takes longer than 60 seconds, it will be terminated. Consider:
- Breaking down the task into smaller steps
- Running on a subset of files
- Checking for performance issues

---

**Author:** AI Tools Team
**Version:** 1.0.0
**Created:** 2025-01-01