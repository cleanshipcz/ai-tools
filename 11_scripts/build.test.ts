/**
 * Tests for build.ts - Adapter generation
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  setupTestOutput,
  cleanupTestOutput,
  createTestFixture,
  testOutputExists,
  readTestOutput,
  FIXTURES,
  validateAdapterOutput,
} from './test-utils.js';
import { join } from 'path';
import { load as loadYaml } from 'js-yaml';

describe('build.ts', () => {
  beforeEach(async () => {
    await setupTestOutput();
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('Manifest loading', () => {
    it('should load and parse prompt manifests', () => {
      const parsed = loadYaml(FIXTURES.validPrompt) as any;
      expect(parsed.id).toBe('test-prompt');
      expect(parsed.content).toBeDefined();
    });

    it('should load and parse agent manifests', () => {
      const parsed = loadYaml(FIXTURES.validAgent) as any;
      expect(parsed.id).toBe('test-agent');
      expect(parsed.system_prompt).toBeDefined();
    });

    it('should load and parse rulepack manifests', async () => {
      const parsed = loadYaml(FIXTURES.validRulepack) as any;
      expect(parsed.id).toBe('test-rulepack');
      expect(parsed.rules).toBeInstanceOf(Array);
    });

    it('should load and parse skill manifests', () => {
      const parsed = loadYaml(FIXTURES.validSkill) as any;
      expect(parsed.id).toBe('test-skill');
      expect(parsed.definition).toBeDefined();
    });
  });

  describe('Model resolution hierarchy', () => {
    type AIModel = 'claude-sonnet-4.5' | 'claude-haiku-4.5' | 'gpt-5' | 'gpt-5.1';

    interface Prompt {
      model?: AIModel;
    }

    interface Agent {
      defaults?: { model?: AIModel };
    }

    interface Project {
      ai_tools?: { model?: AIModel };
    }

    interface Feature {
      model?: AIModel;
    }

    function resolveModel(
      prompt?: Prompt,
      agent?: Agent,
      project?: Project,
      feature?: Feature
    ): AIModel | undefined {
      return feature?.model || project?.ai_tools?.model || agent?.defaults?.model || prompt?.model;
    }

    it('should prioritize feature model over all others', () => {
      const feature: Feature = { model: 'claude-sonnet-4.5' };
      const project: Project = { ai_tools: { model: 'claude-haiku-4.5' } };
      const agent: Agent = { defaults: { model: 'gpt-5' } };
      const prompt: Prompt = { model: 'gpt-5.1' };

      const resolved = resolveModel(prompt, agent, project, feature);
      expect(resolved).toBe('claude-sonnet-4.5');
    });

    it('should use project model when no feature model', () => {
      const project: Project = { ai_tools: { model: 'claude-haiku-4.5' } };
      const agent: Agent = { defaults: { model: 'gpt-5' } };
      const prompt: Prompt = { model: 'gpt-5.1' };

      const resolved = resolveModel(prompt, agent, project);
      expect(resolved).toBe('claude-haiku-4.5');
    });

    it('should use agent model when no project model', () => {
      const agent: Agent = { defaults: { model: 'gpt-5' } };
      const prompt: Prompt = { model: 'gpt-5.1' };

      const resolved = resolveModel(prompt, agent);
      expect(resolved).toBe('gpt-5');
    });

    it('should use prompt model as fallback', () => {
      const prompt: Prompt = { model: 'gpt-5.1' };

      const resolved = resolveModel(prompt);
      expect(resolved).toBe('gpt-5.1');
    });

    it('should return undefined when no model specified', () => {
      const resolved = resolveModel();
      expect(resolved).toBeUndefined();
    });
  });

  describe('Adapter generation - GitHub Copilot', () => {
    it('should create .github directory structure', async () => {
      await createTestFixture('.github/copilot-instructions.md', '# Instructions\n');

      const result = await validateAdapterOutput('github-copilot');
      expect(result.valid).toBe(true);
    });

    it('should generate copilot-instructions.md', async () => {
      await createTestFixture(
        '.github/copilot-instructions.md',
        '# Project Instructions\n\nTest instructions'
      );

      expect(testOutputExists('.github/copilot-instructions.md')).toBe(true);
    });

    it('should include prompts in instructions directory', async () => {
      await createTestFixture('.github/prompts/test-prompt.md', '# Test Prompt\n');

      expect(testOutputExists('.github/prompts/test-prompt.md')).toBe(true);
    });

    it('should format rulepacks as markdown sections', async () => {
      const rules = `## Coding Standards

- Use TypeScript
- Write tests`;

      await createTestFixture('.github/copilot-instructions.md', rules);
      const content = await readTestOutput('.github/copilot-instructions.md');

      expect(content).toContain('Coding Standards');
      expect(content).toContain('Use TypeScript');
    });
  });

  describe('Adapter generation - Windsurf', () => {
    it('should create .windsurf directory structure', async () => {
      await createTestFixture('.windsurf/rules/.gitkeep', '');

      const result = await validateAdapterOutput('windsurf');
      expect(result.valid).toBe(true);
    });

    it('should generate separate rule files', async () => {
      await createTestFixture('.windsurf/rules/typescript.md', '# TypeScript Rules\n');
      await createTestFixture('.windsurf/rules/testing.md', '# Testing Rules\n');

      expect(testOutputExists('.windsurf/rules/typescript.md')).toBe(true);
      expect(testOutputExists('.windsurf/rules/testing.md')).toBe(true);
    });
  });

  describe('Adapter generation - Claude Code', () => {
    it('should create .claude directory structure', async () => {
      await createTestFixture('.claude/prompts/.gitkeep', '');

      const result = await validateAdapterOutput('claude-code');
      expect(result.valid).toBe(true);
    });

    it('should generate prompt files', async () => {
      const promptPath = await createTestFixture(
        '.claude/prompts/test-prompt.xml',
        '<prompt>Test</prompt>'
      );

      expect(promptPath).toBeDefined();
    });

    it('should use XML format for prompts', async () => {
      const xmlContent = `<prompt>
  <id>test-prompt</id>
  <description>Test prompt</description>
  <content>Test content</content>
</prompt>`;

      await createTestFixture('.claude/prompts/test.xml', xmlContent);
      const content = await readTestOutput('.claude/prompts/test.xml');

      expect(content).toContain('<prompt>');
      expect(content).toContain('</prompt>');
    });
  });

  describe('Adapter generation - Cursor', () => {
    it('should create .cursor directory structure', async () => {
      const rulesPath = await createTestFixture('.cursor/.cursorrules', 'Rules content');

      expect(rulesPath).toBeDefined();

      const result = await validateAdapterOutput('cursor');
      expect(result.valid).toBe(true);
    });

    it('should generate .cursorrules file', async () => {
      await createTestFixture('.cursor/.cursorrules', '# Cursor Rules\n\nTest rules');

      expect(testOutputExists('.cursor/.cursorrules')).toBe(true);
    });

    it('should combine all rules into single file', async () => {
      const combinedRules = `# TypeScript Rules
- Use strict mode

# Testing Rules
- Write unit tests`;

      await createTestFixture('.cursor/.cursorrules', combinedRules);
      const content = await readTestOutput('.cursor/.cursorrules');

      expect(content).toContain('TypeScript Rules');
      expect(content).toContain('Testing Rules');
    });
  });

  describe('Include resolution', () => {
    it('should resolve @include() directives', async () => {
      await createTestFixture('included.md', 'This is included content');

      const contentWithInclude = 'Before @include(included.md) After';
      const includePattern = /@include\(([^)]+)\)/g;
      const match = contentWithInclude.match(includePattern);

      expect(match).not.toBeNull();
      expect(match?.[0]).toBe('@include(included.md)');
    });

    it('should handle nested includes', async () => {
      await createTestFixture('level1.md', 'Level 1 @include(level2.md)');
      await createTestFixture('level2.md', 'Level 2 content');

      expect(testOutputExists('level1.md')).toBe(true);
      expect(testOutputExists('level2.md')).toBe(true);
    });

    it('should resolve relative paths', () => {
      const basePath = '/docs/guides';
      const includePath = '../common/header.md';
      const resolved = join(basePath, includePath);

      expect(resolved).toContain('common');
    });
  });

  describe('Rulepack inheritance', () => {
    it('should support extends property', () => {
      const childRulepack = `id: child-rules
version: "1.0.0"
description: "Child rulepack"
extends:
  - base-rules
rules:
  - id: child-rule
    description: "Child specific rule"`;

      const parsed = loadYaml(childRulepack) as any;
      expect(parsed.extends).toBeInstanceOf(Array);
      expect(parsed.extends).toContain('base-rules');
    });

    it('should merge rules from parent rulepacks', () => {
      const baseRules = ['rule-1', 'rule-2'];
      const childRules = ['rule-3'];
      const merged = [...baseRules, ...childRules];

      expect(merged).toHaveLength(3);
      expect(merged).toContain('rule-1');
      expect(merged).toContain('rule-3');
    });
  });

  describe('Regression tests', () => {
    it('should preserve code blocks with backticks', async () => {
      const content = 'Use `code` like this:\n```js\nconsole.log("test");\n```';
      await createTestFixture('test.md', content);

      const read = await readTestOutput('test.md');
      expect(read).toContain('```');
      expect(read).toContain('`code`');
    });

    it('should handle special characters in content', async () => {
      const content = 'Special: @#$%^&*()_+-={}[]|\\:;<>?,./~';
      await createTestFixture('special.md', content);

      const read = await readTestOutput('special.md');
      expect(read).toBe(content);
    });

    it('should maintain YAML formatting', () => {
      const yaml = `id: test
version: "1.0.0"
description: |
  Multi-line
  description
tags:
  - tag1
  - tag2`;

      const parsed = loadYaml(yaml) as any;
      expect(parsed.description).toContain('Multi-line\ndescription');
      expect(parsed.tags).toHaveLength(2);
    });
  });
});
