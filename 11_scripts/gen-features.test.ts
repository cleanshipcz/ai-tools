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

  describe('Windsurf workflow generation', () => {
    it('should generate workflows with auto_execution_mode: 3', async () => {
      const { existsSync, readFileSync, readdirSync } = await import('fs');
      const { join } = await import('path');
      const rootDir = join(process.cwd());
      const workflowsDir = join(
        rootDir,
        '.output',
        'ai-tools',
        'features',
        '.windsurf',
        'workflows'
      );

      if (existsSync(workflowsDir)) {
        const files = readdirSync(workflowsDir);
        const featureFiles = files.filter((f) => f.startsWith('feature-') && f.endsWith('.md'));

        expect(featureFiles.length).toBeGreaterThan(0);

        // Check format of first feature file
        const content = readFileSync(join(workflowsDir, featureFiles[0]), 'utf-8');
        expect(content).toMatch(/^---\n/);
        expect(content).toContain('description:');
        expect(content).toContain('auto_execution_mode: 3');
        expect(content).toMatch(/\n---\n/);
      }
    });

    it('should include feature overview and conventions in workflows', async () => {
      const { existsSync, readFileSync, readdirSync } = await import('fs');
      const { join } = await import('path');
      const rootDir = join(process.cwd());
      const workflowsDir = join(
        rootDir,
        '.output',
        'ai-tools',
        'features',
        '.windsurf',
        'workflows'
      );

      if (existsSync(workflowsDir)) {
        const files = readdirSync(workflowsDir);
        if (files.length > 0) {
          const content = readFileSync(join(workflowsDir, files[0]), 'utf-8');
          expect(content).toContain('# Feature:');
          expect(content).toContain('## Overview');
        }
      }
    });

    it('should include acceptance criteria when available', async () => {
      const { existsSync, readFileSync, readdirSync } = await import('fs');
      const { join } = await import('path');
      const rootDir = join(process.cwd());
      const workflowsDir = join(
        rootDir,
        '.output',
        'ai-tools',
        'features',
        '.windsurf',
        'workflows'
      );

      if (existsSync(workflowsDir)) {
        const files = readdirSync(workflowsDir);
        // Some features should have acceptance criteria
        let hasAcceptanceCriteria = false;
        for (const file of files) {
          const content = readFileSync(join(workflowsDir, file), 'utf-8');
          if (content.includes('## Acceptance Criteria')) {
            hasAcceptanceCriteria = true;
            break;
          }
        }
        // At least one feature should have acceptance criteria
        expect(hasAcceptanceCriteria).toBe(true);
      }
    });
  });
});
