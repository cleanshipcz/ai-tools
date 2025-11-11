# AI Tools Documentation

> Auto-generated documentation for agents, prompts, and skills

_Last updated: 2025-11-11_

## Table of Contents

- [Agents](#agents)
- [Prompts](#prompts)
- [Skills](#skills)

## Agents

Agents are complete AI assistants with specific purposes, configured with rulepacks, tools, and capabilities.

### `bug-fixer`

**Version:** 1.0.0

**Purpose:** Diagnose and fix bugs in code

An agent specialized in debugging and fixing issues in code.
Analyzes error messages, traces, and code to identify root causes
and propose fixes.

**Rulepacks:**

- `base`
- `coding-python`
- `coding-kotlin`

**Required Capabilities:**

- `mcp:filesystem`
- `mcp:shell`
- `mcp:git`

**Tools:**

- `read-file`
- `run-tests`
- `search-code`

**Default Settings:**

```yaml
temperature: 0.3
style: technical
```

---

### `code-reviewer`

**Version:** 2.1.0

**Purpose:** Structured code review with actionable findings

A senior code reviewer agent that analyzes code changes, identifies issues,
and provides constructive, actionable feedback.

**Rulepacks:**

- `base`
- `reviewer`
- `security`

**Required Capabilities:**

- `mcp:git`
- `mcp:filesystem`

**Tools:**

- `git-diff`
- `read-file`
- `search-code`

**Default Settings:**

```yaml
temperature: 0.2
model: claude-3-5-sonnet
style: terse
```

---

### `kotlin-style-enforcer`

**Version:** 1.0.0

**Purpose:** Enforce Kotlin coding standards and idioms

An agent that reviews Kotlin code for style violations and
suggests idiomatic improvements.

**Rulepacks:**

- `base`
- `coding-kotlin`

**Required Capabilities:**

- `mcp:filesystem`

**Tools:**

- `run-detekt`
- `run-ktlint`

**Default Settings:**

```yaml
temperature: 0.2
style: terse
```

---

### `tdd-navigator`

**Version:** 1.0.0

**Purpose:** Guide test-driven development workflow

An agent that helps with test-driven development by writing tests first,
then guiding implementation to make tests pass.

**Rulepacks:**

- `base`
- `coding-python`
- `coding-kotlin`

**Required Capabilities:**

- `mcp:filesystem`
- `mcp:shell`

**Tools:**

- `run-tests`
- `write-code`
- `refactor`

**Default Settings:**

```yaml
temperature: 0.4
style: conversational
```

---

## Prompts

Prompts are atomic, reusable templates with variables and clear specifications.

### Docs

#### `summarize-pr`

**Version:** 1.0.0  
Generate a concise summary of a pull request

**Variables:**

- `diff` (required)
  The git diff to summarize
- `context` (optional)
  Additional context about the PR

### Git

#### `summarize-pr`

**Version:** 1.0.0  
Generate a concise summary of a pull request

**Variables:**

- `diff` (required)
  The git diff to summarize
- `context` (optional)
  Additional context about the PR

### Java

#### `extract-method`

**Version:** 1.3.0  
Extract a pure function from a selected block of code

**Variables:**

- `code` (required)
  The code block to extract
- `target_name` (optional)
  Desired name for the extracted method
- `language` (required)
  Programming language of the code

### Kotlin

#### `add-null-safety`

**Version:** 1.0.0  
Add null safety checks to code that may have null pointer issues

**Variables:**

- `code` (required)
  The code to make null-safe
- `language` (required)
  Programming language

#### `extract-method`

**Version:** 1.3.0  
Extract a pure function from a selected block of code

**Variables:**

- `code` (required)
  The code block to extract
- `target_name` (optional)
  Desired name for the extracted method
- `language` (required)
  Programming language of the code

### Pr

#### `summarize-pr`

**Version:** 1.0.0  
Generate a concise summary of a pull request

**Variables:**

- `diff` (required)
  The git diff to summarize
- `context` (optional)
  Additional context about the PR

### Python

#### `extract-method`

**Version:** 1.3.0  
Extract a pure function from a selected block of code

**Variables:**

- `code` (required)
  The code block to extract
- `target_name` (optional)
  Desired name for the extracted method
- `language` (required)
  Programming language of the code

### Qa

#### `write-tests`

**Version:** 1.0.0  
Generate comprehensive unit tests for given code

**Variables:**

- `code` (required)
  The code to test
- `language` (required)
  Programming language
- `test_framework` (optional)
  Testing framework to use (e.g., pytest, junit, jest)

### Refactor

#### `add-null-safety`

**Version:** 1.0.0  
Add null safety checks to code that may have null pointer issues

**Variables:**

- `code` (required)
  The code to make null-safe
- `language` (required)
  Programming language

#### `extract-method`

**Version:** 1.3.0  
Extract a pure function from a selected block of code

**Variables:**

- `code` (required)
  The code block to extract
- `target_name` (optional)
  Desired name for the extracted method
- `language` (required)
  Programming language of the code

### Safety

#### `add-null-safety`

**Version:** 1.0.0  
Add null safety checks to code that may have null pointer issues

**Variables:**

- `code` (required)
  The code to make null-safe
- `language` (required)
  Programming language

### Tdd

#### `write-tests`

**Version:** 1.0.0  
Generate comprehensive unit tests for given code

**Variables:**

- `code` (required)
  The code to test
- `language` (required)
  Programming language
- `test_framework` (optional)
  Testing framework to use (e.g., pytest, junit, jest)

### Testing

#### `write-tests`

**Version:** 1.0.0  
Generate comprehensive unit tests for given code

**Variables:**

- `code` (required)
  The code to test
- `language` (required)
  Programming language
- `test_framework` (optional)
  Testing framework to use (e.g., pytest, junit, jest)

---

## Skills

Skills are executable tools and commands that agents can use to perform specific tasks.

### Detekt

#### `run-detekt`

**Version:** 1.0.0  
Run Detekt static analysis for Kotlin

### Filesystem

#### `search-repo`

**Version:** 1.0.0  
Search repository for code patterns or text

### Formatting

#### `run-ktlint`

**Version:** 1.0.0  
Run ktlint for Kotlin code formatting

### Gradle

#### `run-gradle-tests`

**Version:** 1.0.0  
Run unit tests with Gradle

### Java

#### `run-gradle-tests`

**Version:** 1.0.0  
Run unit tests with Gradle

### Kotlin

#### `run-detekt`

**Version:** 1.0.0  
Run Detekt static analysis for Kotlin

#### `run-gradle-tests`

**Version:** 1.0.0  
Run unit tests with Gradle

#### `run-ktlint`

**Version:** 1.0.0  
Run ktlint for Kotlin code formatting

### Ktlint

#### `run-ktlint`

**Version:** 1.0.0  
Run ktlint for Kotlin code formatting

### Lint

#### `run-detekt`

**Version:** 1.0.0  
Run Detekt static analysis for Kotlin

### Pytest

#### `run-pytest`

**Version:** 1.0.0  
Run Python tests with pytest

### Python

#### `run-pytest`

**Version:** 1.0.0  
Run Python tests with pytest

### Search

#### `search-repo`

**Version:** 1.0.0  
Search repository for code patterns or text

### Static-analysis

#### `run-detekt`

**Version:** 1.0.0  
Run Detekt static analysis for Kotlin

### Testing

#### `run-gradle-tests`

**Version:** 1.0.0  
Run unit tests with Gradle

#### `run-pytest`

**Version:** 1.0.0  
Run Python tests with pytest
