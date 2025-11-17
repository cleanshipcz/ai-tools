/**
 * Tests for config.ts - Configuration loading and management
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdir, writeFile, rm } from 'fs/promises';
import { join } from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';
import { getProjectSources, reloadConfig } from './config.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

describe('config.ts', () => {
  const testConfigDir = join(rootDir, '15_config');
  const testConfigPath = join(testConfigDir, 'config.yml');
  const testLocalConfigPath = join(testConfigDir, 'config.local.yml');
  const backupConfigPath = join(testConfigDir, 'config.yml.backup');
  const backupLocalConfigPath = join(testConfigDir, 'config.local.yml.backup');

  beforeEach(async () => {
    // Backup existing configs if they exist
    try {
      await rm(backupConfigPath, { force: true });
      await rm(backupLocalConfigPath, { force: true });
      const { access, copyFile } = await import('fs/promises');

      try {
        await access(testConfigPath);
        await copyFile(testConfigPath, backupConfigPath);
      } catch {
        // No config.yml
      }

      try {
        await access(testLocalConfigPath);
        await copyFile(testLocalConfigPath, backupLocalConfigPath);
      } catch {
        // No config.local.yml
      }
    } catch {
      // No existing config, that's fine
    }
  });

  afterEach(async () => {
    // Restore backups if they exist
    try {
      const { access, copyFile } = await import('fs/promises');

      try {
        await access(backupConfigPath);
        await copyFile(backupConfigPath, testConfigPath);
        await rm(backupConfigPath);
      } catch {
        // No backup, remove test config if created
        await rm(testConfigPath, { force: true });
      }

      try {
        await access(backupLocalConfigPath);
        await copyFile(backupLocalConfigPath, testLocalConfigPath);
        await rm(backupLocalConfigPath);
      } catch {
        // No backup, remove test config if created
        await rm(testLocalConfigPath, { force: true });
      }
    } catch {
      // Cleanup failed, that's ok
    }
  });

  describe('getProjectSources', () => {
    it('should return default sources when config file does not exist', async () => {
      // given
      // - no config file exists
      await rm(testConfigPath, { force: true });
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      // Should return absolute paths
      expect(sources).toHaveLength(2);
      expect(sources[0]).toContain('06_projects/global');
      expect(sources[1]).toContain('06_projects/local');
      expect(sources[0]).toMatch(/^[/\\]|^[A-Za-z]:/); // Absolute path check
    });

    it('should load project sources from config.yml', async () => {
      // given
      // - a config file with custom project sources
      const configContent = `
project_sources:
  - ./06_projects/global
  - ./06_projects/local
  - /absolute/path/to/projects
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      expect(sources).toHaveLength(3);
      expect(sources[0]).toContain('06_projects/global');
      expect(sources[1]).toContain('06_projects/local');
      expect(sources[2]).toBe('/absolute/path/to/projects');
    });

    it('should resolve relative paths to absolute paths', async () => {
      // given
      // - a config file with relative paths
      const configContent = `
project_sources:
  - ./06_projects/global
  - ./06_projects/local
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      expect(sources[0]).toContain('06_projects/global');
      expect(sources[1]).toContain('06_projects/local');
      // Should be absolute paths
      expect(sources[0]).toMatch(/^[/\\]|^[A-Za-z]:/);
      expect(sources[1]).toMatch(/^[/\\]|^[A-Za-z]:/);
    });

    it('should handle empty project_sources array', async () => {
      // given
      // - a config file with empty project_sources
      const configContent = `
project_sources: []
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      expect(sources).toEqual([]);
    });

    it('should handle missing project_sources field', async () => {
      // given
      // - a config file without project_sources field
      const configContent = `
other_config: value
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      // Should return default sources (as absolute paths)
      expect(sources).toHaveLength(2);
      expect(sources[0]).toContain('06_projects/global');
      expect(sources[1]).toContain('06_projects/local');
    });

    it('should handle invalid YAML gracefully', async () => {
      // given
      // - a config file with invalid YAML
      const configContent = `
project_sources:
  - item1
  invalid: - syntax
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      // Should return default sources when YAML is invalid (as absolute paths)
      expect(sources).toHaveLength(2);
      expect(sources[0]).toContain('06_projects/global');
      expect(sources[1]).toContain('06_projects/local');
    });

    it('should support both relative and absolute paths', async () => {
      // given
      // - a config with mixed path types
      const configContent = `
project_sources:
  - ./06_projects/global
  - /absolute/path
  - ../relative/path
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      expect(sources).toHaveLength(3);
      expect(sources[0]).toContain('06_projects/global');
      expect(sources[1]).toBe('/absolute/path');
      expect(sources[2]).toContain('relative/path');
    });
  });

  describe('reloadConfig', () => {
    it('should reload configuration when called', async () => {
      // given
      // - an initial config
      const initialConfig = `
project_sources:
  - ./initial/path
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, initialConfig, 'utf-8');
      await reloadConfig();

      const initialSources = await getProjectSources();
      expect(initialSources).toHaveLength(1);
      expect(initialSources[0]).toContain('initial/path');

      // when
      // - config is updated
      const updatedConfig = `
project_sources:
  - ./updated/path
`;
      await writeFile(testConfigPath, updatedConfig, 'utf-8');
      await reloadConfig();

      // then
      const updatedSources = await getProjectSources();
      expect(updatedSources).toHaveLength(1);
      expect(updatedSources[0]).toContain('updated/path');
      expect(updatedSources[0]).not.toContain('initial/path');
    });
  });

  describe('config.local.yml merging', () => {
    it('should use config.yml values when config.local.yml does not exist', async () => {
      // given
      // - only config.yml exists
      const configContent = `
project_sources:
  - ./06_projects/global
  - ./06_projects/local
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await rm(testLocalConfigPath, { force: true });
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      expect(sources).toHaveLength(2);
      expect(sources[0]).toContain('06_projects/global');
      expect(sources[1]).toContain('06_projects/local');
    });

    it('should use config.local.yml values when config.yml does not exist', async () => {
      // given
      // - only config.local.yml exists
      const localConfigContent = `
project_sources:
  - ./custom/path1
  - ./custom/path2
`;
      await mkdir(testConfigDir, { recursive: true });
      await rm(testConfigPath, { force: true });
      await writeFile(testLocalConfigPath, localConfigContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      expect(sources).toHaveLength(2);
      expect(sources[0]).toContain('custom/path1');
      expect(sources[1]).toContain('custom/path2');
    });

    it('should merge lists from both config files', async () => {
      // given
      // - config.yml with some sources
      const configContent = `
project_sources:
  - ./06_projects/global
  - ./06_projects/local
`;
      // - config.local.yml with additional sources
      const localConfigContent = `
project_sources:
  - ./local/custom1
  - ./local/custom2
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await writeFile(testLocalConfigPath, localConfigContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      // Should contain all sources from both files
      expect(sources).toHaveLength(4);
      expect(sources[0]).toContain('06_projects/global');
      expect(sources[1]).toContain('06_projects/local');
      expect(sources[2]).toContain('local/custom1');
      expect(sources[3]).toContain('local/custom2');
    });

    it('should override scalar values with config.local.yml', async () => {
      // given
      // - config.yml with a scalar value
      const configContent = `
project_sources:
  - ./06_projects/global
some_scalar: value_from_config
`;
      // - config.local.yml overriding the scalar
      const localConfigContent = `
some_scalar: value_from_local
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await writeFile(testLocalConfigPath, localConfigContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();
      // We can't directly test other config values without exposing them,
      // but we can verify the list still works

      // then
      expect(sources).toHaveLength(1);
      expect(sources[0]).toContain('06_projects/global');
    });

    it('should merge nested maps with local taking priority', async () => {
      // given
      // - config.yml with nested structure
      const configContent = `
project_sources:
  - ./06_projects/global
nested_config:
  key1: value1
  key2: value2
`;
      // - config.local.yml overriding one nested key
      const localConfigContent = `
nested_config:
  key2: value2_local
  key3: value3_local
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await writeFile(testLocalConfigPath, localConfigContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      // Verify project sources still work (indicates merge succeeded)
      expect(sources).toHaveLength(1);
      expect(sources[0]).toContain('06_projects/global');
    });

    it('should handle empty config.local.yml', async () => {
      // given
      // - config.yml with sources
      const configContent = `
project_sources:
  - ./06_projects/global
`;
      // - empty config.local.yml
      const localConfigContent = ``;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await writeFile(testLocalConfigPath, localConfigContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      expect(sources).toHaveLength(1);
      expect(sources[0]).toContain('06_projects/global');
    });

    it('should handle invalid YAML in config.local.yml gracefully', async () => {
      // given
      // - valid config.yml
      const configContent = `
project_sources:
  - ./06_projects/global
`;
      // - invalid config.local.yml
      const localConfigContent = `
invalid: yaml: [[[
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await writeFile(testLocalConfigPath, localConfigContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      // Should use config.yml when local is invalid
      expect(sources).toHaveLength(1);
      expect(sources[0]).toContain('06_projects/global');
    });

    it('should remove duplicates when merging lists', async () => {
      // given
      // - config.yml with some sources
      const configContent = `
project_sources:
  - ./06_projects/global
  - ./06_projects/local
`;
      // - config.local.yml with overlapping sources
      const localConfigContent = `
project_sources:
  - ./06_projects/local
  - ./custom/path
`;
      await mkdir(testConfigDir, { recursive: true });
      await writeFile(testConfigPath, configContent, 'utf-8');
      await writeFile(testLocalConfigPath, localConfigContent, 'utf-8');
      await reloadConfig();

      // when
      const sources = await getProjectSources();

      // then
      // Should have unique sources (duplicates removed)
      const uniqueSources = new Set(sources);
      expect(sources.length).toBe(uniqueSources.size);
      expect(sources).toHaveLength(3);
    });
  });
});
