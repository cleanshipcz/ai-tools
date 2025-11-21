# Testing Guide

## Overview

This document describes the testing infrastructure for the ai-tools project. The test suite uses Vitest as the testing framework and provides comprehensive coverage for all TypeScript scripts in the `11_scripts/` directory.

## Test Structure

### Test Files

All test files follow the naming convention: `<script-name>.test.ts`

Example: `validate.ts` → `validate.test.ts`

### Test Location

All tests are located in the `11_scripts/` directory alongside their source files.

### Test Organization

Tests are organized using Vitest's `describe` and `it` blocks:

```typescript
describe('script-name', () => {
  describe('Feature category', () => {
    it('should do something specific', () => {
      // Test implementation
    });
  });
});
```

## Running Tests

### Run All Tests

```bash
npm test
```

### Run Tests with Coverage

```bash
npm run test:coverage
```

### Run Specific Test File

```bash
npm test <test-name>
```

Example:

```bash
npm test validate.test
```

### Run Tests in Watch Mode (for development)

```bash
npx vitest
```

## Test Utilities

### Location

`11_scripts/test-utils.ts`

### Available Utilities

#### setupTestOutput()

Sets up the test output directory (`.output-test/`). Called automatically in `beforeEach` hooks.

```typescript
await setupTestOutput();
```

#### cleanupTestOutput()

Cleans up test output files. Called automatically in `afterEach` hooks.

```typescript
await cleanupTestOutput();
```

#### createTestFixture(relativePath, content)

Creates a test file with specified content.

```typescript
const path = await createTestFixture('test.yml', 'id: test');
```

#### readTestOutput(relativePath)

Reads content from a test output file.

```typescript
const content = await readTestOutput('test.yml');
```

#### testOutputExists(relativePath)

Checks if a file exists in test output directory.

```typescript
const exists = testOutputExists('test.yml'); // returns boolean
```

#### listTestOutput(relativePath)

Lists files in test output directory.

```typescript
const files = await listTestOutput('prompts/');
```

#### validateAdapterOutput(tool)

Validates adapter output structure for specific tools.

```typescript
const result = await validateAdapterOutput('github-copilot');
expect(result.valid).toBe(true);
```

### Test Fixtures

Pre-defined YAML fixtures are available in `test-utils.ts`:

- `FIXTURES.validPrompt` - Valid prompt manifest
- `FIXTURES.validAgent` - Valid agent manifest
- `FIXTURES.validRulepack` - Valid rulepack manifest
- `FIXTURES.validSkill` - Valid skill manifest
- `FIXTURES.validProject` - Valid project manifest
- `FIXTURES.invalidYaml` - Invalid YAML for error testing
- `FIXTURES.missingRequiredField` - Incomplete manifest for validation testing

## Test Patterns

### Standard Test Setup

```typescript
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { setupTestOutput, cleanupTestOutput, createTestFixture } from './test-utils.js';

describe('my-script', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  it('should do something', async () => {
    // Test implementation
  });
});
```

### Testing File Operations

```typescript
it('should create a file', async () => {
  const path = await createTestFixture('test.txt', 'content');
  expect(path).toBeDefined();
  expect(testOutputExists('test.txt')).toBe(true);
});
```

### Testing YAML Parsing

```typescript
import { load as loadYaml } from 'js-yaml';

it('should parse YAML correctly', () => {
  const parsed = loadYaml(FIXTURES.validPrompt) as any;
  expect(parsed.id).toBe('test-prompt');
  expect(parsed.content).toBeDefined();
});
```

### Testing Error Handling

```typescript
it('should handle errors gracefully', () => {
  expect(() => {
    // Code that should throw
  }).toThrow();
});
```

### Testing Async Operations

```typescript
it('should handle async operations', async () => {
  const result = await someAsyncFunction();
  expect(result).toBeDefined();
});
```

## Coverage Requirements

### Current Thresholds

- Lines: 60%
- Functions: 60%
- Branches: 60%
- Statements: 60%

### Viewing Coverage Reports

HTML coverage reports are generated in `coverage/` directory:

```bash
npm run test:coverage
open coverage/index.html  # macOS
xdg-open coverage/index.html  # Linux
start coverage/index.html  # Windows
```

## Test Scripts Coverage

| Script                | Test File                  | Tests | Status |
| --------------------- | -------------------------- | ----- | ------ |
| build.ts              | build.test.ts              | 29    | ✅     |
| validate.ts           | validate.test.ts           | 29    | ✅     |
| gen-project.ts        | gen-project.test.ts        | 11    | ✅     |
| deploy-project.ts     | deploy-project.test.ts     | 7     | ✅     |
| test-utils.ts         | test-utils.test.ts         | 9     | ✅     |
| create-project.ts     | create-project.test.ts     | 5     | ✅     |
| gen-prompt-library.ts | gen-prompt-library.test.ts | 5     | ✅     |
| eval.ts               | eval.test.ts               | 8     | ✅     |
| clean.ts              | clean.test.ts              | 5     | ✅     |
| gen-docs.ts           | gen-docs.test.ts           | 5     | ✅     |
| init-project.ts       | init-project.test.ts       | 4     | ✅     |
| gen-skills.ts         | gen-skills.test.ts         | 5     | ✅     |
| diff.ts               | diff.test.ts               | 5     | ✅     |
| gen-prompt-html.ts    | gen-prompt-html.test.ts    | 5     | ✅     |
| run-recipe.ts         | run-recipe.test.ts         | 7     | ✅     |
| external-projects.ts  | external-projects.test.ts  | 6     | ✅     |
| gen-features.ts       | gen-features.test.ts       | 5     | ✅     |
| use-prompt.ts         | use-prompt.test.ts         | 6     | ✅     |

**Total: 18 test files, 156 tests**

## CI/CD Integration

### GitHub Actions

Tests are automatically run in CI/CD pipeline via `.github/workflows/ci.yml`:

1. **On every push** to main branch
2. **On every pull request**
3. **Scheduled nightly builds**

### CI Test Stages

1. Install dependencies
2. Run linter
3. Run build
4. Run tests with coverage
5. Upload coverage reports
6. Fail if coverage < 60%

## Best Practices

### DO:

- ✅ Write descriptive test names
- ✅ Test one thing per test
- ✅ Use `beforeEach` and `afterEach` for setup/cleanup
- ✅ Test edge cases and error conditions
- ✅ Use test fixtures from `test-utils.ts`
- ✅ Keep tests isolated and independent
- ✅ Mock external dependencies

### DON'T:

- ❌ Test implementation details
- ❌ Write tests that depend on execution order
- ❌ Use real file system operations (use test utilities)
- ❌ Commit test output files
- ❌ Skip cleanup in `afterEach`
- ❌ Share state between tests

## Troubleshooting

### Tests Failing Locally

1. Clean test output: `rm -rf .output-test`
2. Reinstall dependencies: `npm install`
3. Run single test file: `npm test <test-name>`

### Coverage Too Low

1. Check which files need coverage: `npm run test:coverage`
2. Review coverage report: `open coverage/index.html`
3. Add tests for uncovered code paths

### Flaky Tests

1. Check for race conditions in async code
2. Ensure proper cleanup in `afterEach`
3. Run tests multiple times: `for i in {1..10}; do npm test; done`

### Test Timeouts

1. Increase timeout in `vitest.config.ts`
2. Check for infinite loops or hanging promises
3. Add `await` to async operations

## Future Improvements

### Planned Enhancements

- [ ] Add integration tests for full build pipeline
- [ ] Add performance benchmarks
- [ ] Increase coverage to 80%+
- [ ] Add mutation testing
- [ ] Add visual regression testing for generated docs
- [ ] Add contract tests for adapter outputs

### Coverage Goals

**Phase 1 (Current):** 60% coverage - Infrastructure tests
**Phase 2 (Q2 2025):** 80% coverage - Full unit test coverage
**Phase 3 (Q3 2025):** 90% coverage - Integration + edge cases

## Additional Resources

- [Vitest Documentation](https://vitest.dev/)
- [Vitest API Reference](https://vitest.dev/api/)
- [Coverage Configuration](https://vitest.dev/guide/coverage.html)
- [Mocking Guide](https://vitest.dev/guide/mocking.html)

## Support

For questions or issues with tests:

1. Check this documentation
2. Review existing test files for patterns
3. Check Vitest documentation
4. Open an issue on GitHub
