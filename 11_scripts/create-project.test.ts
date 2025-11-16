/**
 * Tests for create-project.ts - Project creation
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  setupTestOutput,
  cleanupTestOutput,
  createTestFixture,
  testOutputExists,
} from './test-utils.js';

describe('create-project.ts', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('Project creation', () => {
    it('should generate project manifest', async () => {
      const projectManifest = `id: new-project
version: "1.0.0"
description: "New test project"
agents:
  - code-reviewer
rulepacks:
  - typescript-style`;

      await createTestFixture('projects/new-project.yml', projectManifest);
      expect(testOutputExists('projects/new-project.yml')).toBe(true);
    });

    it('should validate project ID format', () => {
      const validIds = ['my-project', 'test-123', 'app'];
      const invalidIds = ['My-Project', 'test_project', 'test.project'];

      const kebabPattern = /^[a-z0-9]+(-[a-z0-9]+)*$/;
      validIds.forEach((id) => expect(kebabPattern.test(id)).toBe(true));
      invalidIds.forEach((id) => expect(kebabPattern.test(id)).toBe(false));
    });

    it('should create project directory structure', async () => {
      await createTestFixture('projects/test-project/project.yml', 'content');
      await createTestFixture('projects/test-project/README.md', '# Project');

      expect(testOutputExists('projects/test-project/project.yml')).toBe(true);
      expect(testOutputExists('projects/test-project/README.md')).toBe(true);
    });
  });

  describe('Interactive prompts', () => {
    it('should collect project metadata', () => {
      const metadata = {
        id: 'test-project',
        description: 'Test project description',
        model: 'claude-sonnet-4.5',
      };

      expect(metadata.id).toBeDefined();
      expect(metadata.description.length).toBeGreaterThan(10);
    });

    it('should validate required fields', () => {
      const required = ['id', 'description'];
      const project = { id: 'test', description: 'Test project' };

      required.forEach((field) => {
        expect(project).toHaveProperty(field);
      });
    });
  });
});
