# Testing Guide

## Overview

This document describes the testing infrastructure for the ai-tools project. The test suite uses Vitest as the testing framework and provides comprehensive coverage for the TypeScript source code in the `src/` directory.

## Test Structure

### Test Files

All test files follow the naming convention: `<filename>.test.ts` and are located alongside the source files they test.

Example: `src/core/services/validation.service.ts` → `src/core/services/validation.service.test.ts`

### Test Organization

Tests are organized using Vitest's `describe` and `it` blocks:

```typescript
describe('ValidationService', () => {
  describe('validateManifest', () => {
    it('should return valid result for correct manifest', () => {
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
npm test <test-path>
```

Example:

```bash
npm test src/core/services/validation.service.test.ts
```

### Run Tests in Watch Mode (for development)

```bash
npm run test:watch
```

## Test Patterns

### Standard Test Setup

```typescript
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { MyService } from './my.service.js';

describe('MyService', () => {
  let service: MyService;

  beforeEach(() => {
    vi.clearAllMocks();
    service = new MyService();
  });

  it('should do something', async () => {
    // Test implementation
  });
});
```

### Mocking Dependencies

We use `vi.mock` and `vi.hoisted` for mocking dependencies.

```typescript
import { describe, it, expect, vi } from 'vitest';
import { ConfigService } from '../../core/services/config.service.js';

// Hoist mock setup
const mocks = vi.hoisted(() => ({
  getPath: vi.fn(),
}));

// Mock module
vi.mock('../../core/services/config.service.js', () => ({
  ConfigService: {
    getInstance: () => ({
      getPath: mocks.getPath,
    }),
  },
}));

describe('MyService', () => {
  it('should use config service', () => {
    // ...
    expect(mocks.getPath).toHaveBeenCalled();
  });
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
# Open coverage/index.html in your browser
```

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
- ✅ Keep tests isolated and independent
- ✅ Mock external dependencies (fs, child_process, other services)

### DON'T:

- ❌ Test implementation details
- ❌ Write tests that depend on execution order
- ❌ Use real file system operations (mock fs/promises)
- ❌ Share state between tests

## Troubleshooting

### Tests Failing Locally

1. Clean test output if any
2. Reinstall dependencies: `npm install`
3. Run single test file: `npm test <test-path>`

### Coverage Too Low

1. Check which files need coverage: `npm run test:coverage`
2. Review coverage report: `coverage/index.html`
3. Add tests for uncovered code paths

### Flaky Tests

1. Check for race conditions in async code
2. Ensure proper cleanup in `afterEach`
3. Ensure mocks are cleared/restored

## Additional Resources

- [Vitest Documentation](https://vitest.dev/)
- [Vitest API Reference](https://vitest.dev/api/)
- [Coverage Configuration](https://vitest.dev/guide/coverage.html)
- [Mocking Guide](https://vitest.dev/guide/mocking.html)
