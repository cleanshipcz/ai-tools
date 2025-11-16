# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial repository structure
- JSON schemas for prompts, agents, rulepacks, skills, and eval suites
- Example manifests for common use cases
- Build system to generate tool-specific adapters
- Validation script with security checks
- Documentation generator
- Evaluation framework with budget tracking
- Red team test cases
- GitHub Actions CI/CD pipeline

### Rulepacks

- `base`: Foundation rules for all agents
- `coding-python`: Python-specific coding standards
- `coding-kotlin`: Kotlin-specific coding standards
- `reviewer`: Code review guidelines
- `security`: Security-focused rules
- `windsurf-defaults`: Windsurf tool defaults

### Agents

- `code-reviewer`: Structured code review agent
- `bug-fixer`: Debugging and fix proposal agent
- `tdd-navigator`: Test-driven development guide
- `kotlin-style-enforcer`: Kotlin style checker

### Prompts

- `extract-method`: Method extraction refactoring
- `add-null-safety`: Null safety improvements
- `summarize-pr`: Pull request summarization
- `write-tests`: Unit test generation

### Skills

- `run-gradle-tests`: Execute Gradle tests
- `run-detekt`: Run Detekt static analysis
- `run-ktlint`: Run ktlint formatter
- `run-pytest`: Execute Python tests
- `search-repo`: Repository code search

### MCP Servers

- `filesystem`: File system operations
- `shell`: Shell command execution (disabled by default)
- `git`: Git operations
- `http`: HTTP requests

## [1.0.0] - 2025-01-01

### Added

- Initial release
