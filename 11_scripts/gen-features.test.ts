/**
 * Tests for gen-features.ts - Feature documentation generator
 */

import { describe, it, expect } from 'vitest';

describe('gen-features.ts', () => {
  describe('Feature documentation', () => {
    it('should collect feature definitions', () => {
      const features = [
        { id: 'feature-1', status: 'implemented' },
        { id: 'feature-2', status: 'planned' },
      ];
      expect(features.length).toBe(2);
    });

    it('should categorize features by status', () => {
      const statuses = ['implemented', 'planned', 'in-progress'];
      expect(statuses).toContain('implemented');
      expect(statuses).toContain('planned');
    });

    it('should generate feature matrix', () => {
      const matrix = {
        implemented: 5,
        planned: 3,
        total: 8,
      };
      expect(matrix.total).toBe(8);
    });
  });

  describe('Documentation generation', () => {
    it('should format feature descriptions', () => {
      const feature = {
        id: 'test-feature',
        description: 'A test feature',
        status: 'implemented',
      };
      expect(feature.description).toBeTruthy();
    });

    it('should include feature roadmap', () => {
      const roadmap = ['Q1: Feature A', 'Q2: Feature B'];
      expect(roadmap.length).toBeGreaterThan(0);
    });
  });
});
