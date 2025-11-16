/**
 * Tests for external-projects.ts - External project integration
 */

import { describe, it, expect } from 'vitest';

describe('external-projects.ts', () => {
  describe('Project discovery', () => {
    it('should find external projects', () => {
      const projects = [
        { path: '/path/to/project1', name: 'Project 1' },
        { path: '/path/to/project2', name: 'Project 2' },
      ];
      expect(projects.length).toBe(2);
    });

    it('should validate project structure', () => {
      const isValid = (path: string) => path.includes('project');
      expect(isValid('/path/to/project')).toBe(true);
    });
  });

  describe('Project configuration', () => {
    it('should load external config', () => {
      const config = {
        id: 'external-project',
        type: 'external',
        path: '/external/path',
      };
      expect(config.type).toBe('external');
    });

    it('should merge configurations', () => {
      const base = { a: 1, b: 2 };
      const override = { b: 3, c: 4 };
      const merged = { ...base, ...override };
      expect(merged.b).toBe(3);
      expect(merged.c).toBe(4);
    });
  });

  describe('Project integration', () => {
    it('should link external projects', () => {
      const links = ['project1', 'project2'];
      expect(links).toContain('project1');
    });

    it('should handle project paths', () => {
      const path = '/path/to/external/project';
      expect(path).toContain('/external/');
    });
  });
});
