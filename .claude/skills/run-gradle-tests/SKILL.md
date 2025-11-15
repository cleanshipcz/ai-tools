---
name: run-gradle-tests
description: Run unit tests with Gradle
---

# Run Gradle Tests

## When to use this skill

Use this skill when:
- Working with gradle, testing, kotlin, java
- User asks to "run gradle tests"

## Prerequisites

- Do not run if repo has no Gradle wrapper.
- Ensure Java/Kotlin is available.

## How to use

Run the following command:

```bash
./gradlew test --info
```

**Timeout:** 1800 seconds

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
User: "Can you run gradle tests?"
Assistant: [Runs: ./gradlew test --info]
Assistant: [Interprets output and reports results]
```

## Troubleshooting

### Command not found

Ensure `./gradlew` is installed and available in the PATH.

### Timeout

If the command takes longer than 1800 seconds, it will be terminated. Consider:
- Breaking down the task into smaller steps
- Running on a subset of files
- Checking for performance issues

---

**Author:** AI Tools Team
**Version:** 1.0.0
**Created:** 2025-01-01