<!-- This file is loaded for ALL files. Lists available tools you can reference in chat. -->

# Available AI Tools

This repository provides reusable AI agent configurations. Copy relevant sections to your project.

## Agents

**Available agent personas (invoke via `#prompt:agent-<name>`):**

- **bug-fixer**: Diagnose and fix bugs in code
- **code-reviewer**: Structured code review with actionable findings
- **kotlin-style-enforcer**: Enforce Kotlin coding standards and idioms
- **tdd-navigator**: Guide test-driven development workflow

_Example: `@workspace #prompt:agent-code-reviewer` - Review this code_

## Prompts

**Reusable prompts (attach via paperclip or `#prompt:name`):**

- **summarize-pr**: Generate a concise summary of a pull request
- **write-tests**: Generate comprehensive unit tests for given code
- **add-null-safety**: Add null safety checks to code that may have null pointer issues
- **extract-method**: Extract a pure function from a selected block of code

## Skills

**Executable commands available:**

- **run-detekt**: Run Detekt static analysis for Kotlin
- **run-gradle-tests**: Run unit tests with Gradle
- **run-ktlint**: Run ktlint for Kotlin code formatting
- **run-pytest**: Run Python tests with pytest
- **search-repo**: Search repository for code patterns or text

---

## Customize for Your Project

**Add your project specifics above (keep concise):**

1. **Project overview** - What it does (2-3 sentences)
2. **Tech stack** - Languages, frameworks
3. **Build commands** - How to run/test
4. **Key conventions** - Naming, patterns

**Example:**

```markdown
# My Project

E-commerce platform with React + Node.js

## Stack

- Frontend: React, TypeScript
- Backend: Node.js, PostgreSQL

## Commands

`npm run dev` - Start dev server
`npm test` - Run tests
```
