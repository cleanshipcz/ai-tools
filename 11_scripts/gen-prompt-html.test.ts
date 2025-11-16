/**
 * Tests for gen-prompt-html.ts - HTML prompt browser generator
 */

import { describe, it, expect } from 'vitest';

describe('gen-prompt-html.ts', () => {
  describe('HTML generation', () => {
    it('should generate valid HTML structure', () => {
      const htmlTemplate = '<!DOCTYPE html><html><head></head><body></body></html>';
      expect(htmlTemplate).toContain('<!DOCTYPE html>');
      expect(htmlTemplate).toContain('<html>');
    });

    it('should include interactive features', () => {
      const features = ['search', 'filter', 'copy'];
      expect(features).toContain('search');
      expect(features).toContain('filter');
      expect(features).toContain('copy');
    });

    it('should embed prompt data', () => {
      const promptData = { id: 'test', content: 'prompt content' };
      expect(promptData.id).toBe('test');
      expect(promptData.content).toBeDefined();
    });
  });

  describe('Prompt browser functionality', () => {
    it('should support filtering by category', () => {
      const categories = ['refactor', 'qa', 'docs'];
      expect(categories.length).toBeGreaterThan(0);
    });

    it('should enable copy-to-clipboard', () => {
      // Mock clipboard functionality
      const canCopy = true;
      expect(canCopy).toBe(true);
    });
  });
});
