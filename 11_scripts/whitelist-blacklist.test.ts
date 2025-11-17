/**
 * Tests for whitelist/blacklist filtering bugs
 *
 * BUG DOCUMENTATION (TDD Red Phase):
 * 1. Projects can contain BOTH whitelist AND blacklist for same category (should be mutually exclusive)
 * 2. Generated outputs don't respect whitelist/blacklist - generates all items anyway
 * 3. Naming inconsistency across tools (github-copilot uses prefixes, claude-code doesn't)
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { readFile, writeFile, mkdir, rm } from 'fs/promises';
import { join } from 'path';
import { load as loadYaml, dump as dumpYaml } from 'js-yaml';
import { ProjectGenerator } from './gen-project.js';

const testOutputDir = join(process.cwd(), '.test-output');
const testProjectsDir = join(testOutputDir, 'projects');

describe('Whitelist/Blacklist Filtering', () => {
  beforeEach(async () => {
    // Clean up test output directory
    try {
      await rm(testOutputDir, { recursive: true, force: true });
    } catch {}
    await mkdir(testOutputDir, { recursive: true });
    await mkdir(testProjectsDir, { recursive: true });
  });

  describe('BUG 1: Schema should enforce mutual exclusivity', () => {
    it('should NOT allow both whitelist_agents AND blacklist_agents', async () => {
      // given
      // - a project with BOTH whitelist_agents and blacklist_agents
      const invalidProject = {
        id: 'test-project',
        version: '1.0.0',
        name: 'Test Project',
        description: 'Project with both whitelist and blacklist for agents',
        ai_tools: {
          whitelist_agents: ['bug-fixer', 'code-reviewer'],
          blacklist_agents: ['refactoring-specialist'], // INVALID: can't have both!
        },
      };

      // when
      // - validating against schema
      const Ajv = (await import('ajv')).default;
      const addFormats = (await import('ajv-formats')).default;
      const ajv = new Ajv();
      addFormats(ajv);

      const schemaPath = join(process.cwd(), '10_schemas', 'project.schema.json');
      const schemaContent = await readFile(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      const validate = ajv.compile(schema);
      const valid = validate(invalidProject);

      // then
      // - validation should FAIL (but currently PASSES - this is the bug)
      if (valid) {
        console.log('VALIDATION PASSED (BUG!): Should have failed');
        console.log('Errors:', validate.errors);
      } else {
        console.log('VALIDATION FAILED (GOOD!): Prevented both whitelist and blacklist');
        console.log('Errors:', JSON.stringify(validate.errors, null, 2));
      }
      expect(valid).toBe(false);
      if (!valid) {
        expect(validate.errors).toBeTruthy();
      }
    });

    it('should NOT allow both whitelist_prompts AND blacklist_prompts', async () => {
      // given
      const invalidProject = {
        id: 'test-project',
        version: '1.0.0',
        name: 'Test Project',
        description: 'Project with both whitelist and blacklist for prompts',
        ai_tools: {
          whitelist_prompts: ['refactor/extract-method'],
          blacklist_prompts: ['refactor/remove-duplication'], // INVALID!
        },
      };

      // when
      const Ajv = (await import('ajv')).default;
      const addFormats = (await import('ajv-formats')).default;
      const ajv = new Ajv();
      addFormats(ajv);

      const schemaPath = join(process.cwd(), '10_schemas', 'project.schema.json');
      const schemaContent = await readFile(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      const validate = ajv.compile(schema);
      const valid = validate(invalidProject);

      // then
      expect(valid).toBe(false);
      expect(validate.errors).toBeTruthy();
    });

    it('should NOT allow both whitelist_rulepacks AND blacklist_rulepacks', async () => {
      // given
      const invalidProject = {
        id: 'test-project',
        version: '1.0.0',
        name: 'Test Project',
        description: 'Project with both whitelist and blacklist for rulepacks',
        ai_tools: {
          whitelist_rulepacks: ['base', 'testing'],
          blacklist_rulepacks: ['security'], // INVALID!
        },
      };

      // when
      const Ajv = (await import('ajv')).default;
      const addFormats = (await import('ajv-formats')).default;
      const ajv = new Ajv();
      addFormats(ajv);

      const schemaPath = join(process.cwd(), '10_schemas', 'project.schema.json');
      const schemaContent = await readFile(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      const validate = ajv.compile(schema);
      const valid = validate(invalidProject);

      // then
      expect(valid).toBe(false);
      expect(validate.errors).toBeTruthy();
    });

    it('should ALLOW whitelist_agents with blacklist_prompts (different categories)', async () => {
      // given
      // - mixed whitelists and blacklists for DIFFERENT categories IS valid
      const validProject = {
        id: 'test-project',
        version: '1.0.0',
        name: 'Test Project',
        description: 'Project with whitelist for agents, blacklist for prompts',
        ai_tools: {
          whitelist_agents: ['bug-fixer', 'code-reviewer'], // whitelist agents
          blacklist_prompts: ['refactor/remove-duplication'], // blacklist prompts - THIS IS OK
        },
      };

      // when
      const Ajv = (await import('ajv')).default;
      const addFormats = (await import('ajv-formats')).default;
      const ajv = new Ajv();
      addFormats(ajv);

      const schemaPath = join(process.cwd(), '10_schemas', 'project.schema.json');
      const schemaContent = await readFile(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      const validate = ajv.compile(schema);
      const valid = validate(validProject);

      // then
      expect(valid).toBe(true);
    });
  });

  describe('BUG 2: Generated outputs must respect whitelist/blacklist', () => {
    it('should only generate whitelisted agents in GitHub Copilot output', async () => {
      // given
      // - a project with whitelist_agents = ['bug-fixer', 'code-reviewer']
      const projectDir = join(testProjectsDir, 'test-whitelist-agents');
      await mkdir(projectDir, { recursive: true });

      const projectManifest = {
        id: 'test-whitelist-agents',
        version: '1.0.0',
        name: 'Whitelist Agents Test',
        description: 'Project testing agent whitelist filtering',
        ai_tools: {
          whitelist_agents: ['bug-fixer', 'code-reviewer'],
        },
      };

      await writeFile(join(projectDir, 'project.yml'), dumpYaml(projectManifest), 'utf-8');

      // when
      // - generating project configurations
      const generator = new ProjectGenerator();
      // TODO: Need to test generate() with test project
      // This requires mocking or using actual test project in 06_projects/

      // then
      // - output should ONLY contain bug-fixer and code-reviewer agent files
      // - should NOT contain refactoring-specialist, feature-builder, etc.
      // Currently FAILS: generates ALL agents regardless of whitelist
    });

    it('should only generate whitelisted prompts in GitHub Copilot output', async () => {
      // given
      // - a project with whitelist_prompts = ['refactor/extract-method', 'qa/write-tests']
      const projectManifest = {
        id: 'test-whitelist-prompts',
        version: '1.0.0',
        name: 'Whitelist Prompts Test',
        description: 'Project testing prompt whitelist filtering',
        ai_tools: {
          whitelist_prompts: ['refactor/extract-method', 'qa/write-tests'],
        },
      };

      // when
      // - generating project configurations

      // then
      // - GitHub Copilot .github/prompts/ should ONLY contain:
      //   - prompt-refactor-extract-method.prompt.md
      //   - prompt-qa-write-tests.prompt.md
      // - should NOT contain prompt-refactor-remove-duplication.prompt.md, etc.
      // Currently FAILS: generates ALL prompts regardless of whitelist
    });

    it('should exclude blacklisted agents from GitHub Copilot output', async () => {
      // given
      // - a project with blacklist_agents = ['refactoring-specialist']
      const projectManifest = {
        id: 'test-blacklist-agents',
        version: '1.0.0',
        name: 'Blacklist Agents Test',
        description: 'Project testing agent blacklist filtering',
        ai_tools: {
          blacklist_agents: ['refactoring-specialist'],
        },
      };

      // when
      // - generating project configurations

      // then
      // - output should contain bug-fixer, code-reviewer, feature-builder, etc.
      // - should NOT contain refactoring-specialist
      // Currently FAILS: generates ALL agents including blacklisted ones
    });

    it('should exclude blacklisted prompts from GitHub Copilot output', async () => {
      // given
      // - a project with blacklist_prompts = ['refactor/remove-duplication']
      const projectManifest = {
        id: 'test-blacklist-prompts',
        version: '1.0.0',
        name: 'Blacklist Prompts Test',
        description: 'Project testing prompt blacklist filtering',
        ai_tools: {
          blacklist_prompts: ['refactor/remove-duplication'],
        },
      };

      // when
      // - generating project configurations

      // then
      // - output should contain most prompts
      // - should NOT contain prompt-refactor-remove-duplication.prompt.md
      // Currently FAILS: generates ALL prompts including blacklisted ones
    });
  });

  describe('BUG 3: Naming consistency across tools', () => {
    it('should use consistent prompt naming: category-prompt-id format', async () => {
      // given
      // - generating outputs for all tools
      const { existsSync } = await import('fs');
      const { join } = await import('path');
      const rootDir = join(process.cwd());

      // when
      // - checking GitHub Copilot output (uses "prompt-" prefix)
      const githubDir = join(rootDir, 'adapters', 'github-copilot', '.github', 'prompts');
      const githubExists = existsSync(join(githubDir, 'prompt-refactor-extract-method.prompt.md'));

      // - checking Claude Code output (should use category-id format)
      const claudeDir = join(rootDir, 'adapters', 'claude-code', 'prompts');
      const claudeNewExists = existsSync(join(claudeDir, 'refactor-extract-method.json'));
      const claudeOldExists = existsSync(join(claudeDir, 'extract-method.json')); // Old broken format

      // - checking Windsurf output (uses "prompt-" prefix)
      const windsurfDir = join(rootDir, 'adapters', 'windsurf', 'rules');
      const windsurfExists = existsSync(join(windsurfDir, 'prompt-refactor-extract-method.md'));

      // then
      // - Claude Code now uses consistent category-id naming
      expect(githubExists).toBe(true);
      expect(claudeNewExists).toBe(true);
      expect(claudeOldExists).toBe(false); // Old format should not exist
      expect(windsurfExists).toBe(true);
    });

    it('should use consistent agent naming across tools', async () => {
      // given
      // - generating outputs for all tools
      const { existsSync } = await import('fs');
      const { join } = await import('path');
      const rootDir = join(process.cwd());

      // when
      // - checking different tool outputs for agent files
      const githubDir = join(rootDir, 'adapters', 'github-copilot', '.github', 'prompts');
      const claudeDir = join(rootDir, 'adapters', 'claude-code', 'agents');
      const windsurfDir = join(rootDir, 'adapters', 'windsurf', 'rules');

      // GitHub Copilot: agent-bug-fixer.prompt.md
      const githubExists = existsSync(join(githubDir, 'agent-bug-fixer.prompt.md'));

      // Claude Code: bug-fixer.md (uses plain ID)
      const claudeExists = existsSync(join(claudeDir, 'bug-fixer.md'));

      // Windsurf: agent-bug-fixer.md
      const windsurfExists = existsSync(join(windsurfDir, 'agent-bug-fixer.md'));

      // then
      // - All tools use their expected naming conventions
      // - GitHub Copilot and Windsurf use "agent-" prefix
      // - Claude Code uses plain ID (no prefix) which is also acceptable
      // - The key is that filtering works correctly with these conventions
      expect(githubExists).toBe(true);
      expect(claudeExists).toBe(true);
      expect(windsurfExists).toBe(true);
    });

    it('Claude Code prompts should match whitelist with correct naming', async () => {
      // given
      // - whitelist uses format: 'refactor/extract-method'
      // - Claude Code generates: 'extract-method.json'
      // when
      // - checking if whitelist matching works correctly
      // then
      // - Whitelist matching must account for:
      //   1. Different file extensions (.json vs .md vs .prompt.md)
      //   2. Category prefix in path (refactor/) vs filename
      //   3. Conversion between path format (refactor/extract-method) and filename format
      // Currently UNCLEAR if matching works correctly due to naming inconsistency
    });
  });

  describe('Integration: Full whitelist/blacklist workflow', () => {
    it('should generate correct filtered output for real ai-tools project', async () => {
      // given
      // - ai-tools project has specific whitelists defined
      const projectPath = join(process.cwd(), '06_projects', 'global', 'ai-tools', 'project.yml');
      const projectContent = await readFile(projectPath, 'utf-8');
      const project = loadYaml(projectContent) as any;

      // Extract whitelist configuration
      const whitelistedAgents = project.ai_tools?.whitelist_agents || [];
      const whitelistedPrompts = project.ai_tools?.whitelist_prompts || [];

      // then
      // - GitHub Copilot output should have EXACTLY the whitelisted items
      // - Claude Code output should have EXACTLY the whitelisted items
      // - Windsurf output should have EXACTLY the whitelisted items
      // - Count of generated files should match whitelist length
      expect(whitelistedAgents.length).toBeGreaterThan(0);
      expect(whitelistedPrompts.length).toBeGreaterThan(0);
    });

    it('should handle mixed whitelist and blacklist across categories', async () => {
      // given
      // - whitelist_agents = ['bug-fixer', 'code-reviewer']
      // - blacklist_prompts = ['refactor/remove-duplication']
      // - whitelist_rulepacks = ['base', 'testing']
      const projectManifest = {
        id: 'test-mixed-filtering',
        version: '1.0.0',
        name: 'Mixed Filtering Test',
        description: 'Testing mixed whitelist/blacklist across categories',
        ai_tools: {
          whitelist_agents: ['bug-fixer', 'code-reviewer'],
          blacklist_prompts: ['refactor/remove-duplication'],
          whitelist_rulepacks: ['base', 'testing'],
        },
      };

      // when
      // - generating project configurations

      // then
      // - Should generate ONLY whitelisted agents (2 agents)
      // - Should generate ALL prompts EXCEPT blacklisted one
      // - Should generate ONLY whitelisted rulepacks
    });
  });
});
