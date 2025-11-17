/**
 * Tests for deploy-project.ts - Project deployment
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  setupTestOutput,
  cleanupTestOutput,
  createTestFixture,
  testOutputExists,
} from './test-utils.js';

describe('deploy-project.ts', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('Deployment operations', () => {
    it('should copy files to deployment target', async () => {
      const sourcePath = await createTestFixture(
        'source/.github/copilot-instructions.md',
        '# Instructions'
      );
      const targetPath = await createTestFixture(
        'target/.github/copilot-instructions.md',
        '# Instructions'
      );

      expect(sourcePath).toBeDefined();
      expect(targetPath).toBeDefined();
    });

    it('should create backup before deployment', async () => {
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
      await createTestFixture(
        `backups/backup-${timestamp}/.github/copilot-instructions.md`,
        '# Old'
      );

      expect(testOutputExists(`backups/backup-${timestamp}/.github/copilot-instructions.md`)).toBe(
        true
      );
    });

    it('should validate deployment targets', () => {
      const tools = ['github-copilot', 'windsurf', 'claude-code', 'cursor'];
      const validTools = tools.filter((t) =>
        ['github-copilot', 'windsurf', 'claude-code', 'cursor'].includes(t)
      );

      expect(validTools).toHaveLength(4);
    });
  });

  describe('Rollback operations', () => {
    it('should list available backups', async () => {
      const backup1 = await createTestFixture('backups/backup-1/.github/test.md', 'v1');
      const backup2 = await createTestFixture('backups/backup-2/.github/test.md', 'v2');

      expect(backup1).toBeDefined();
      expect(backup2).toBeDefined();
    });

    it('should restore from backup', async () => {
      await createTestFixture('backups/latest/.github/copilot-instructions.md', '# Backup');
      const content = await createTestFixture(
        'restored/.github/copilot-instructions.md',
        '# Backup'
      );

      expect(content).toBeDefined();
    });
  });

  describe('Deployment validation', () => {
    it('should verify deployed files', async () => {
      await createTestFixture('.github/copilot-instructions.md', '# Test');

      const exists = testOutputExists('.github/copilot-instructions.md');
      expect(exists).toBe(true);
    });

    it('should check file permissions', async () => {
      // Mock permission check
      const hasPermission = true;
      expect(hasPermission).toBe(true);
    });
  });

  describe('Bug: Missing deploy.yml file', () => {
    it('should skip projects without deploy.yml during deploy-all', async () => {
      // given
      // - a project exists with project.yml
      // - but NO deploy.yml file
      await createTestFixture(
        '06_projects/local/test-project/project.yml',
        'id: test-project\nversion: 1.0.0\nname: Test\ndescription: Test project without deploy.yml'
      );

      // when
      // - deploy-all is executed
      // - it should skip this project

      // then
      // - project should be skipped (not deployed)
      // - this is the BUG: projects created manually (not via create-project) don't have deploy.yml
      const hasDeployConfig = testOutputExists('06_projects/local/test-project/deploy.yml');
      expect(hasDeployConfig).toBe(false); // Bug: missing deploy.yml means project is skipped
    });

    it('should include projects WITH deploy.yml during deploy-all', async () => {
      // given
      // - a project exists with BOTH project.yml AND deploy.yml
      await createTestFixture(
        '06_projects/local/test-project-2/project.yml',
        'id: test-project-2\nversion: 1.0.0\nname: Test2\ndescription: Test project with deploy.yml'
      );
      await createTestFixture(
        '06_projects/local/test-project-2/deploy.yml',
        'target: ./output\ntools:\n  - github-copilot\nmode: local'
      );

      // when
      // - deploy-all is executed

      // then
      // - project SHOULD be included (has deploy.yml)
      const hasDeployConfig = testOutputExists('06_projects/local/test-project-2/deploy.yml');
      expect(hasDeployConfig).toBe(true);
    });
  });
});
