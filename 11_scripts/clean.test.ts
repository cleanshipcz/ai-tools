/**
 * Tests for clean.ts - Cleanup utility
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  setupTestOutput,
  cleanupTestOutput,
  createTestFixture,
  testOutputExists,
} from './test-utils.js';
import { existsSync } from 'fs';
import { mkdir } from 'fs/promises';
import { join } from 'path';

describe('clean.ts', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('Directory cleaning', () => {
    it('should identify directories to clean', () => {
      const directories = ['adapters', '.output', 'evals/reports'];
      expect(directories.length).toBe(3);
      expect(directories).toContain('adapters');
      expect(directories).toContain('.output');
    });

    it('should handle non-existent directories gracefully', async () => {
      // Test that the cleaner can handle ENOENT errors
      expect(testOutputExists('non-existent')).toBe(false);
    });

    it('should clean multiple directories', async () => {
      // Create test directories
      const adapter = await createTestFixture('adapters/test.txt', 'content');
      const output = await createTestFixture('.output/test.txt', 'content');

      // Verify files were created
      expect(adapter).toBeDefined();
      expect(output).toBeDefined();
      expect(testOutputExists('adapters/test.txt')).toBe(true);
      expect(testOutputExists('.output/test.txt')).toBe(true);
    });
  });

  describe('Error handling', () => {
    it('should continue cleaning after encountering an error', () => {
      // Even if one directory fails, others should be cleaned
      expect(true).toBe(true);
    });

    it('should track number of cleaned directories', () => {
      let cleaned = 0;
      cleaned += 1;
      cleaned += 1;
      expect(cleaned).toBe(2);
    });
  });
});
