---
name: run-ktlint
description: Run ktlint for Kotlin code formatting
---

# Run Ktlint

## When to use this skill

Use this skill when:
- Working with ktlint, kotlin, formatting
- User asks to "run ktlint"

## Prerequisites

- Requires ktlint to be configured in the project.

## How to use

Run the following command:

```bash
./gradlew ktlintCheck
```

**Timeout:** 300 seconds

## Understanding the output

### Exit code

- `0`: Success
- Non-zero: Error occurred (check stderr for details)

### Standard output (stdout)

Contains the main output of the command.

### Standard error (stderr)

Contains error messages and warnings.

## Examples

### Basic usage

```
User: "Can you run ktlint?"
Assistant: [Runs: ./gradlew ktlintCheck]
Assistant: [Interprets output and reports results]
```

## Troubleshooting

### Command not found

Ensure `./gradlew` is installed and available in the PATH.

### Timeout

If the command takes longer than 300 seconds, it will be terminated. Consider:
- Breaking down the task into smaller steps
- Running on a subset of files
- Checking for performance issues

---

**Author:** AI Tools Team
**Version:** 1.0.0
**Created:** 2025-01-01