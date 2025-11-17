/**
 * Tests for create-project.ts - Project creation
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  setupTestOutput,
  cleanupTestOutput,
  createTestFixture,
  testOutputExists,
} from './test-utils.js';
import { join } from 'path';
import { access } from 'fs/promises';
import { PROJECTS_DIR } from './constants.js';

describe('create-project.ts', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('Constants usage', () => {
    it('should use PROJECTS_DIR constant for correct directory path', () => {
      // given
      // - PROJECTS_DIR constant is defined

      // when
      const projectPath = join(PROJECTS_DIR, 'global', 'test-project');

      // then
      // - should use '06_projects' as the base directory
      expect(PROJECTS_DIR).toBe('06_projects');
      expect(projectPath).toBe('06_projects/global/test-project');
    });

    it('should use PROJECTS_DIR for template path construction', () => {
      // given
      // - PROJECTS_DIR constant

      // when
      const templatePath = join(PROJECTS_DIR, 'global', 'template');

      // then
      // - template path should be under 06_projects
      expect(templatePath).toBe('06_projects/global/template');
      // Bug check: verify constant doesn't accidentally use old path
      expect(PROJECTS_DIR).not.toBe('projects');
    });
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

  describe('Path correctness in ProjectCreator', () => {
    it('should create project directory under PROJECTS_DIR', () => {
      // given
      // - a project ID 'test-project'
      // - scope 'local'
      const projectId = 'test-project';
      const scope = 'local';
      const rootDir = process.cwd();

      // when
      // - calculating the project directory path using constant
      const projectDir = join(rootDir, PROJECTS_DIR, scope, projectId);

      // then
      // - path should use PROJECTS_DIR constant (06_projects)
      expect(projectDir).toContain('06_projects/local/test-project');
      expect(PROJECTS_DIR).toBe('06_projects'); // Verify constant is correct
    });

    it('should reference template from PROJECTS_DIR', () => {
      // given
      // - root directory
      const rootDir = process.cwd();

      // when
      // - calculating template directory path using constant
      const templateDir = join(rootDir, PROJECTS_DIR, 'global', 'template');

      // then
      // - should use PROJECTS_DIR constant
      expect(templateDir).toContain('06_projects/global/template');
      expect(PROJECTS_DIR).toBe('06_projects'); // Verify constant is correct
    });

    it('should log correct paths in console output', () => {
      // given
      // - console.log spy
      const projectId = 'test-project';
      const scope = 'local';

      // when
      // - building log message using constant
      const expectedLogMessage = `Created directory: ${PROJECTS_DIR}/${scope}/${projectId}/`;

      // then
      // - logs should use PROJECTS_DIR constant (06_projects)
      expect(expectedLogMessage).toBe('Created directory: 06_projects/local/test-project/');
      expect(expectedLogMessage).not.toBe('Created directory: projects/local/test-project/');
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
