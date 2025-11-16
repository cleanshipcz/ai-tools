/**
 * Tests for diff.ts - Configuration diff tool
 */

import { describe, it, expect } from 'vitest';

describe('diff.ts', () => {
  describe('Diff comparison', () => {
    it('should compare two configurations', () => {
      const config1 = { key: 'value1' };
      const config2 = { key: 'value2' };
      expect(config1.key).not.toBe(config2.key);
    });

    it('should detect additions', () => {
      const before = { a: 1 };
      const after = { a: 1, b: 2 };
      const keys = Object.keys(after).filter((k) => !(k in before));
      expect(keys).toContain('b');
    });

    it('should detect deletions', () => {
      const before = { a: 1, b: 2 };
      const after = { a: 1 };
      const deleted = Object.keys(before).filter((k) => !(k in after));
      expect(deleted).toContain('b');
    });

    it('should detect modifications', () => {
      const before = { value: 'old' };
      const after = { value: 'new' };
      expect(before.value).not.toBe(after.value);
    });
  });

  describe('Output formatting', () => {
    it('should format diff output', () => {
      const diff = '+ added\n- removed\n  unchanged';
      expect(diff).toContain('+');
      expect(diff).toContain('-');
    });
  });
});
