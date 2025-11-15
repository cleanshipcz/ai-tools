---
name: run-detekt
description: Run Detekt static analysis for Kotlin
---

# Run Detekt

## When to use this skill

Use this skill when:
- Working with detekt, kotlin, lint, static-analysis
- User asks to "run detekt"

## Prerequisites

- Requires Detekt to be configured in the project.

## How to use

Run the following command:

```bash
./gradlew detekt
```

**Timeout:** 600 seconds

## Understanding the output

### Exit code

- `0`: Success
- Non-zero: Error occurred (check stderr for details)

### Standard output (stdout)

Contains the main output of the command.

### Standard error (stderr)

Contains error messages and warnings.

### Output files

The following files will be generated:

- `build/reports/detekt/detekt.html`

## Examples

### Basic usage

```
User: "Can you run detekt?"
Assistant: [Runs: ./gradlew detekt]
Assistant: [Interprets output and reports results]
```

## Troubleshooting

### Command not found

Ensure `./gradlew` is installed and available in the PATH.

### Timeout

If the command takes longer than 600 seconds, it will be terminated. Consider:
- Breaking down the task into smaller steps
- Running on a subset of files
- Checking for performance issues

---

**Author:** AI Tools Team
**Version:** 1.0.0
**Created:** 2025-01-01