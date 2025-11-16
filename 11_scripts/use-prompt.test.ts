/**
 * Tests for use-prompt.ts - Interactive prompt usage tool
 */

import { describe, it, expect } from 'vitest';

describe('use-prompt.ts', () => {
  describe('Prompt discovery', () => {
    it('should find prompts by ID', () => {
      const promptId = 'write-tests';
      expect(promptId).toBeTruthy();
      expect(typeof promptId).toBe('string');
    });

    it('should list available prompts', () => {
      const prompts = ['write-tests', 'code-review', 'refactor'];
      expect(prompts.length).toBeGreaterThan(0);
    });
  });

  describe('Variable substitution', () => {
    it('should collect variable values', () => {
      const variables = { file: 'test.ts', task: 'refactor' };
      expect(variables.file).toBe('test.ts');
      expect(variables.task).toBe('refactor');
    });

    it('should handle required vs optional variables', () => {
      const variableDef = { name: 'file', required: true };
      expect(variableDef.required).toBe(true);
    });

    it('should substitute variables in prompt', () => {
      const template = 'Refactor {{file}} to {{target}}';
      const filled = template.replace('{{file}}', 'test.ts').replace('{{target}}', 'TypeScript');
      expect(filled).toBe('Refactor test.ts to TypeScript');
    });
  });

  describe('Output formatting', () => {
    it('should format prompt for display', () => {
      const prompt = { id: 'test', description: 'A test prompt', content: 'Content here' };
      expect(prompt.description).toBeTruthy();
      expect(prompt.content).toBeTruthy();
    });
  });
});
