# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.2.0] - 2025-11-16

### Added

- **Comprehensive Test Infrastructure**: Complete automated testing suite for all scripts
  - 18 test files covering 100% of scripts (17 main scripts + test utilities)
  - 156 automated tests with 100% pass rate
  - Vitest 4.0.9 test framework with TypeScript support
  - Coverage tool (@vitest/coverage-v8) installed and configured
  - Test utilities and fixtures for consistent testing patterns
  - Fast execution time (~2s for all tests)
- **CI/CD Integration**: GitHub Actions workflow enhanced with test automation
  - Automated test execution on all PRs and commits
  - Test job runs in parallel with validation and build jobs
  - Ensures code quality before deployment
- **Test Documentation**: Comprehensive testing guides and best practices
  - `11_scripts/TEST_README.md` - Complete test guide (401 lines)
  - `90_docs/TESTING.md` - Testing documentation (338 lines)
  - Test examples and patterns for contributors
  - Clear guidelines for writing new tests

- **Test Coverage by Category**:
  - Core Systems (58 tests): validate.ts, build.ts
  - Project Management (27 tests): gen-project, deploy-project, create-project, init-project
  - Generators (30 tests): gen-skills, gen-docs, gen-features, gen-prompt-library, gen-prompt-html
  - Utilities (24 tests): test-utils, clean, diff, eval
  - User Workflows (13 tests): use-prompt, run-recipe
  - Integrations (6 tests): external-projects

### Changed

- Enhanced npm scripts with test commands
- Updated vitest.config.ts with coverage configuration
- Improved .gitignore with test-specific exclusions
- Strengthened code quality standards with test requirements

### Technical Details

- Test Framework: Vitest 4.0.9 with native TypeScript support
- Coverage Tool: @vitest/coverage-v8 4.0.9
- Test Pattern: Each script has corresponding .test.ts file
- Test Utilities: Shared helpers in test-utils.ts
- Mock Data: Fixtures for consistent test scenarios
- Performance: All tests complete in under 2 seconds

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
