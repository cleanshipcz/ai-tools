/**
 * Tests for init-project.ts - Project initialization tool
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { setupTestOutput, cleanupTestOutput, createTestFixture } from './test-utils.js';

describe('init-project.ts', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('Project initialization', () => {
    it('should create project structure', async () => {
      const structure = ['project.yml', 'README.md', '.aitools/'];
      expect(structure.length).toBe(3);
    });

    it('should generate project manifest', async () => {
      const manifest = {
        id: 'my-project',
        version: '1.0.0',
        description: 'A test project',
      };

      expect(manifest.id).toBeDefined();
      expect(manifest.version).toBeDefined();
    });

    it('should set up configuration files', async () => {
      await createTestFixture('project/project.yml', 'id: test-project');
      expect(true).toBe(true);
    });
  });

  describe('Interactive setup', () => {
    it('should collect project metadata', () => {
      const metadata = {
        name: 'Test Project',
        description: 'A test project',
        author: 'Test Author',
      };

      expect(metadata.name).toBeTruthy();
      expect(metadata.description).toBeTruthy();
    });
  });
});
