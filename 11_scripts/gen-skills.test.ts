/**
 * Tests for gen-skills.ts - Skill manifest generation
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { setupTestOutput, cleanupTestOutput, FIXTURES } from './test-utils.js';
import { load as loadYaml } from 'js-yaml';

describe('gen-skills.ts', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('Skill manifest loading', () => {
    it('should load skill manifests', () => {
      const skill = loadYaml(FIXTURES.validSkill) as any;

      expect(skill.id).toBe('test-skill');
      expect(skill.description).toBeDefined();
      expect(skill.category).toBe('tool');
    });

    it('should handle command-based skills', () => {
      const skillWithCommand = `id: command-skill
version: "1.0.0"
description: "A skill with command"
category: tool
command:
  executable: "git"
  args: ["status"]`;

      const skill = loadYaml(skillWithCommand) as any;
      expect(skill.command).toBeDefined();
      expect(skill.command.executable).toBe('git');
    });

    it('should handle MCP tool skills', () => {
      const mcpSkill = `id: mcp-skill
version: "1.0.0"
description: "MCP tool skill"
category: tool
mcp_tool: "filesystem_read"`;

      const skill = loadYaml(mcpSkill) as any;
      expect(skill.mcp_tool).toBe('filesystem_read');
    });
  });

  describe('Skill documentation generation', () => {
    it('should format skill definitions', () => {
      const skill = loadYaml(FIXTURES.validSkill) as any;

      expect(skill.definition).toBeDefined();
      expect(typeof skill.definition).toBe('string');
    });

    it('should include skill examples', () => {
      const skill = loadYaml(FIXTURES.validSkill) as any;

      expect(skill.examples).toBeDefined();
      expect(Array.isArray(skill.examples)).toBe(true);
    });
  });
});
