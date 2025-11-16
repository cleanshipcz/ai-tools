# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2025-11-16

### Added

- **Recipe Output Logging**: All recipe executions now automatically log to timestamped files in `.recipe-logs/`
  - Real-time console output with `tee` for simultaneous viewing and logging
  - Timestamped log files: `.recipe-logs/<recipe-id>-YYYYMMDD-HHMMSS.log`
  - Works for all tool types (copilot-cli, claude-code, cursor)
  - Works for both global and feature-bound recipes
  - Automatic `.recipe-logs/` directory creation
  - Added to `.gitignore` to keep logs local
  - Complete test coverage (11/11 tests passing)

### Changed

- Updated `gen-project.ts` to include logging setup in generated recipe scripts
- Updated `gen-features.ts` to include logging for feature-bound recipe scripts
- Enhanced `run-recipe.ts` with logging infrastructure
- Feature-specific logs are now prefixed with 'feature-' for easy identification

### Fixed

- Recipe iteration count increased from 3 to 5 in `feature-delivery.yml` for better feature delivery quality

## [1.0.0] - 2025-01-01

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
