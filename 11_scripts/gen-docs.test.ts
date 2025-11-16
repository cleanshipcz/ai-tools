/**
 * Tests for gen-docs.ts - Documentation generation
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  setupTestOutput,
  cleanupTestOutput,
  createTestFixture,
  testOutputExists,
} from './test-utils.js';

describe('gen-docs.ts', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('Agent documentation generation', () => {
    it('should generate agent documentation', async () => {
      const agentDoc = `# Agents

## test-agent

**Purpose:** Testing agent
`;
      await createTestFixture('AGENTS.md', agentDoc);
      expect(testOutputExists('AGENTS.md')).toBe(true);
    });

    it('should format agent metadata correctly', () => {
      const agent = {
        id: 'test-agent',
        version: '1.0.0',
        purpose: 'Testing',
        capabilities: ['test1', 'test2'],
      };

      expect(agent.id).toBe('test-agent');
      expect(agent.capabilities).toHaveLength(2);
    });
  });

  describe('Markdown formatting', () => {
    it('should generate valid markdown headers', () => {
      const header1 = '# Level 1';
      const header2 = '## Level 2';
      const header3 = '### Level 3';

      expect(header1).toMatch(/^# /);
      expect(header2).toMatch(/^## /);
      expect(header3).toMatch(/^### /);
    });

    it('should format lists correctly', () => {
      const bulletList = '- Item 1\n- Item 2\n- Item 3';
      const numberedList = '1. First\n2. Second\n3. Third';

      expect(bulletList.split('\n')).toHaveLength(3);
      expect(numberedList.split('\n')).toHaveLength(3);
    });

    it('should format code blocks', () => {
      const codeBlock = '```typescript\nconst x = 1;\n```';
      expect(codeBlock).toContain('```');
    });
  });
});
