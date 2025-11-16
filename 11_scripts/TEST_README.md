# Test Infrastructure

Automated test suite for the ai-tools repository.

## Overview

This test infrastructure provides comprehensive coverage of the codebase with automated tests for:

- Manifest validation (YAML parsing, schema validation)
- Build system (adapter generation for multiple tools)
- Documentation generation
- Project management
- Evaluation framework
- Utilities and helpers

## Running Tests

### Quick Start

```bash
# Run all tests
npm test

# Run tests in watch mode
npm run test:watch

# Generate coverage report
npm run test:coverage
```

### Test Output

- Tests run via Vitest test runner
- Output generated to `.output-test/` directory (auto-cleaned, gitignored)
- Coverage reports generated to `coverage/` directory

## Test Structure

### Test Files

| Test File                    | Source File             | Coverage                             |
| ---------------------------- | ----------------------- | ------------------------------------ |
| `test-utils.test.ts`         | `test-utils.ts`         | Test utilities and fixtures          |
| `validate.test.ts`           | `validate.ts`           | Manifest validation, schema checks   |
| `build.test.ts`              | `build.ts`              | Adapter generation, model resolution |
| `gen-docs.test.ts`           | `gen-docs.ts`           | Documentation generation             |
| `gen-prompt-library.test.ts` | `gen-prompt-library.ts` | Prompt library generation            |
| `gen-skills.test.ts`         | `gen-skills.ts`         | Skill manifest processing            |
| `gen-project.test.ts`        | `gen-project.ts`        | Recipe script generation             |
| `create-project.test.ts`     | `create-project.ts`     | Project creation                     |
| `deploy-project.test.ts`     | `deploy-project.ts`     | Deployment operations                |
| `eval.test.ts`               | `eval.ts`               | Evaluation framework                 |

### Test Utilities

**File**: `11_scripts/test-utils.ts`

Provides shared utilities for all tests:

```typescript
// Setup and cleanup
setupTestOutput(): Promise<string>
cleanupTestOutput(): Promise<void>

// File operations
createTestFixture(path, content): Promise<string>
readTestOutput(path): Promise<string>
testOutputExists(path): boolean
listTestOutput(path?): Promise<string[]>

// Validation
validateAdapterOutput(tool): Promise<{valid, errors}>

// Test data
FIXTURES - Pre-defined YAML manifests for testing
TEST_DIRS - Directory paths for test data
ADAPTER_DIRS - Expected adapter output directories
```

### Test Fixtures

Pre-defined YAML manifests available in `FIXTURES`:

- `validPrompt` - Valid prompt manifest
- `validAgent` - Valid agent manifest
- `validRulepack` - Valid rulepack manifest
- `validSkill` - Valid skill manifest
- `validProject` - Valid project manifest
- `invalidYaml` - Invalid YAML for error testing
- `missingRequiredField` - Incomplete manifest

## Test Categories

### 1. Validation Tests (`validate.test.ts`)

Tests for the validation system:

```typescript
// YAML parsing
- Parse valid manifests
- Reject invalid YAML
- Handle multiline content

// Schema validation
- Validate against JSON schemas
- Detect missing required fields
- Check field types and formats

// ID validation
- Accept kebab-case IDs
- Reject invalid formats

// Version validation
- Accept semantic versioning (semver)
- Reject invalid versions

// Security validation
- Detect potential secrets (API keys, passwords)
- Allow placeholders and examples

// Regression tests
- Handle backticks in content
- Preserve date formats
- Support special characters
```

### 2. Build Tests (`build.test.ts`)

Tests for the build system:

```typescript
// Manifest loading
- Load and parse all manifest types

// Model resolution hierarchy
- Feature > Project > Agent > Prompt

// Adapter generation
- GitHub Copilot (.github/)
- Windsurf (.windsurf/)
- Claude Code (.claude/)
- Cursor (.cursor/)

// Include resolution
- Resolve @include() directives
- Handle nested includes

// Rulepack inheritance
- Support extends property
- Merge parent rules
```

### 3. Generator Tests

**Documentation** (`gen-docs.test.ts`):

- Agent documentation generation
- Markdown formatting

**Prompt Library** (`gen-prompt-library.test.ts`):

- Category-based organization
- Alphabetical sorting
- Table of contents generation

**Skills** (`gen-skills.test.ts`):

- Command-based skills
- MCP tool skills
- Skill documentation

### 4. Project Management Tests

**Creation** (`create-project.test.ts`):

- Project manifest generation
- ID validation
- Directory structure

**Deployment** (`deploy-project.test.ts`):

- File copying
- Backup creation
- Rollback operations
- Validation

### 5. Evaluation Tests (`eval.test.ts`)

- Suite loading
- Test case execution
- Output comparison
- Report generation

## CI/CD Integration

Tests run automatically in GitHub Actions on:

- Push to `main` branch
- Pull requests

### Workflow Steps

```yaml
test:
  - Run tests (npm test)
  - Generate coverage report
  - Upload coverage artifacts

validate:
  - Run validation (after tests pass)

build:
  - Build adapters (after validation)
```

## Configuration

**File**: `vitest.config.ts`

```typescript
{
  test: {
    globals: true,
    environment: 'node',
    include: ['11_scripts/**/*.test.ts'],
    pool: 'forks',
    hookTimeout: 30000,
    testTimeout: 10000,
  },
  coverage: {
    provider: 'v8',
    reporter: ['text', 'json', 'html', 'lcov'],
    thresholds: {
      lines: 60,
      functions: 60,
      branches: 60,
      statements: 60,
    },
  },
}
```

## Writing Tests

### Test Template

```typescript
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { setupTestOutput, cleanupTestOutput, createTestFixture } from './test-utils.js';

describe('MyModule', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('Feature', () => {
    it('should do something', async () => {
      // Arrange
      const fixture = await createTestFixture('test.yml', 'content');

      // Act
      const result = doSomething(fixture);

      // Assert
      expect(result).toBeDefined();
    });
  });
});
```

### Best Practices

1. **Use descriptive test names**: "should validate prompt schema" not "test1"
2. **Follow AAA pattern**: Arrange, Act, Assert
3. **Test one thing**: Each test should verify one behavior
4. **Use fixtures**: Leverage pre-defined test data from `FIXTURES`
5. **Clean up**: Use `beforeEach`/`afterEach` for setup/cleanup
6. **Async tests**: Mark async tests with `async () =>`
7. **Edge cases**: Include tests for error conditions and edge cases

### Regression Tests

Add regression tests for known issues:

```typescript
describe('Regression tests', () => {
  it('should handle backticks in content', async () => {
    const content = 'Use `code` blocks';
    // Test that backticks are preserved
  });

  it('should preserve date formats', () => {
    const yaml = 'created: 2024-01-15';
    // Test that dates parse correctly
  });
});
```

## Troubleshooting

### Common Issues

**1. Test output directory conflicts**

- Solution: Tests now run sequentially to avoid conflicts
- Cleanup happens between test files

**2. Schema validation failures**

- Check that fixtures match current schema definitions
- Update fixtures when schemas change

**3. Timeouts**

- Increase `testTimeout` in vitest.config.ts if needed
- Default: 10000ms (10 seconds)

**4. Coverage thresholds**

- Current target: 60% (adjustable in vitest.config.ts)
- Focus on critical paths first

### Debug Mode

```bash
# Run specific test file
npx vitest run 11_scripts/validate.test.ts

# Run with verbose output
npx vitest run --reporter=verbose

# Run single test
npx vitest run -t "should validate prompt schema"
```

## Test Coverage Goals

### Phase 1: Core (Complete ✓)

- Validation system
- Build system
- Test utilities

### Phase 2: Generators (Complete ✓)

- Documentation generation
- Prompt library generation
- Skills generation

### Phase 3: Project Management (Complete ✓)

- Project creation
- Deployment operations
- Recipe generation

### Phase 4: Additional Coverage (Next)

- Remaining utility scripts
- Integration tests
- Performance tests

## Maintenance

### Adding New Tests

1. Create test file: `{module-name}.test.ts`
2. Import test utilities
3. Write tests using template
4. Run tests: `npm test`
5. Verify coverage: `npm run test:coverage`

### Updating Fixtures

When schemas or manifests change:

1. Update fixtures in `test-utils.ts`
2. Run tests to identify failures
3. Update assertions as needed
4. Commit changes with clear description

### CI/CD Updates

When adding new test jobs:

1. Edit `.github/workflows/ci.yml`
2. Add test step to workflow
3. Configure artifacts if needed
4. Test in PR before merging

## Additional Resources

- [Vitest Documentation](https://vitest.dev/)
- [Project Documentation](../README.md)
- [Contributing Guidelines](../docs/CONTRIBUTING.md)
- [Analysis Document](.recipe-docs/analysis.md)
- [Implementation Plan](.recipe-docs/plan.md)

## Stats

- **Total test files**: 10
- **Total tests**: 113
- **Passing tests**: 103+
- **Coverage target**: 60%
- **Average test duration**: <300ms

---

Last updated: 2024-11-16
