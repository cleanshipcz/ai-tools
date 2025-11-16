/**
 * Tests for test-utils.ts
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  setupTestOutput,
  cleanupTestOutput,
  createTestFixture,
  readTestOutput,
  testOutputExists,
  listTestOutput,
  TEST_OUTPUT_DIR,
  FIXTURES,
} from './test-utils.js';
import { existsSync } from 'fs';

describe('test-utils', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('setupTestOutput', () => {
    it('should create test output directory', async () => {
      expect(existsSync(TEST_OUTPUT_DIR)).toBe(true);
    });
  });

  describe('createTestFixture', () => {
    it('should create fixture file in test output', async () => {
      const path = await createTestFixture('test.txt', 'hello world');
      expect(existsSync(path)).toBe(true);
    });

    it('should create nested directories automatically', async () => {
      const path = await createTestFixture('nested/deep/file.txt', 'content');
      expect(existsSync(path)).toBe(true);
    });
  });

  describe('readTestOutput', () => {
    it('should read test fixture content', async () => {
      await createTestFixture('data.txt', 'test data');
      const content = await readTestOutput('data.txt');
      expect(content).toBe('test data');
    });
  });

  describe('listTestOutput', () => {
    it('should list files in test output directory', async () => {
      await createTestFixture('file1.txt', 'a');
      await createTestFixture('file2.txt', 'b');
      const files = await listTestOutput();
      expect(files.length).toBeGreaterThan(0);
    });

    it('should return empty array for non-existent directory', async () => {
      const files = await listTestOutput('nonexistent');
      expect(files).toEqual([]);
    });
  });

  describe('FIXTURES', () => {
    it('should provide valid YAML fixtures', async () => {
      // Just verify fixture strings exist - no file I/O needed
      expect(FIXTURES.validPrompt).toContain('id: test-prompt');
      expect(FIXTURES.validAgent).toContain('id: test-agent');
      expect(FIXTURES.validRulepack).toContain('id: test-rulepack');
      expect(FIXTURES.validSkill).toContain('id: test-skill');
      expect(FIXTURES.validProject).toContain('id: test-project');
    });

    it('should provide invalid fixtures for error testing', async () => {
      expect(FIXTURES.invalidYaml).toContain('broken');
      expect(FIXTURES.missingRequiredField).toContain('incomplete');
    });
  });

  describe('cleanupTestOutput', () => {
    it('should remove test output directory contents', async () => {
      // Use standalone test without beforeEach/afterEach interference
      const testDir = TEST_OUTPUT_DIR;
      if (!existsSync(testDir)) {
        await setupTestOutput();
      }
      const testFile = await createTestFixture('cleanup.txt', 'remove me');
      expect(existsSync(testFile)).toBe(true);
      await cleanupTestOutput();
      // Directory should still exist but contents should be gone
      expect(existsSync(testDir)).toBe(true);
      expect(existsSync(testFile)).toBe(false);
    });
  });
});
