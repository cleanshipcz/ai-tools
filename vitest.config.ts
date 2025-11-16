import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    include: ['11_scripts/**/*.test.ts'],
    pool: 'forks',
    poolOptions: {
      forks: {
        singleFork: true,
      },
    },
    fileParallelism: false,
    globalSetup: './11_scripts/vitest.setup.ts',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      include: ['11_scripts/**/*.ts'],
      exclude: [
        'node_modules/',
        '11_scripts/**/*.test.ts',
        '11_scripts/test-utils.ts',
        '11_scripts/vitest.setup.ts',
      ],
      thresholds: {
        lines: 60,
        functions: 60,
        branches: 60,
        statements: 60,
      },
    },
    hookTimeout: 30000,
    testTimeout: 10000,
  },
});
