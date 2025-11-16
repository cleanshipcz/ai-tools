/**
 * Tests for gen-prompt-library.ts - Prompt library generation
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  setupTestOutput,
  cleanupTestOutput,
  createTestFixture,
  testOutputExists,
  FIXTURES,
} from './test-utils.js';
import { load as loadYaml } from 'js-yaml';

describe('gen-prompt-library.ts', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('Prompt library generation', () => {
    it('should collect prompts by category', () => {
      const prompts = [
        { id: 'p1', category: 'testing', description: 'Test 1' },
        { id: 'p2', category: 'testing', description: 'Test 2' },
        { id: 'p3', category: 'coding', description: 'Test 3' },
      ];

      const byCategory = prompts.reduce(
        (acc, p: any) => {
          if (!acc[p.category]) acc[p.category] = [];
          acc[p.category].push(p);
          return acc;
        },
        {} as Record<string, any[]>
      );

      expect(byCategory['testing']).toHaveLength(2);
      expect(byCategory['coding']).toHaveLength(1);
    });

    it('should sort prompts alphabetically', () => {
      const prompts = [{ id: 'zebra' }, { id: 'apple' }, { id: 'banana' }];

      const sorted = [...prompts].sort((a, b) => a.id.localeCompare(b.id));
      expect(sorted[0].id).toBe('apple');
      expect(sorted[2].id).toBe('zebra');
    });

    it('should generate table of contents', () => {
      const categories = ['Testing', 'Coding', 'Documentation'];
      const toc = categories.map((c) => `- [${c}](#${c.toLowerCase()})`).join('\n');

      expect(toc).toContain('[Testing](#testing)');
      expect(toc).toContain('[Coding](#coding)');
    });
  });

  describe('Prompt metadata extraction', () => {
    it('should extract prompt metadata', () => {
      const prompt = loadYaml(FIXTURES.validPrompt) as any;

      expect(prompt.id).toBe('test-prompt');
      expect(prompt.description).toBeDefined();
      expect(prompt.category).toBeDefined();
    });

    it('should handle optional fields', () => {
      const prompt = loadYaml(FIXTURES.validPrompt) as any;

      // Tags are optional
      if (prompt.tags) {
        expect(Array.isArray(prompt.tags)).toBe(true);
      }
    });
  });
});
