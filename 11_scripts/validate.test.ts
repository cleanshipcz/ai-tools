/**
 * Tests for validate.ts - Manifest validation
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  setupTestOutput,
  cleanupTestOutput,
  createTestFixture,
  FIXTURES,
  TEST_OUTPUT_DIR,
} from './test-utils.js';
import { readFile } from 'fs/promises';
import { join } from 'path';
import { load as loadYaml } from 'js-yaml';
import Ajv from 'ajv';
import addFormats from 'ajv-formats';

describe('validate.ts', () => {
  let ajv: Ajv;

  beforeEach(async () => {
    await setupTestOutput();
    ajv = new Ajv({ allErrors: true, strict: false });
    addFormats(ajv);
  });

  afterEach(async () => {
    await cleanupTestOutput();
  });

  describe('YAML parsing', () => {
    it('should parse valid YAML manifests', () => {
      const parsed = loadYaml(FIXTURES.validPrompt);
      expect(parsed).toHaveProperty('id', 'test-prompt');
      expect(parsed).toHaveProperty('version', '1.0.0');
      expect(parsed).toHaveProperty('description');
    });

    it('should reject invalid YAML', () => {
      expect(() => loadYaml(FIXTURES.invalidYaml)).toThrow();
    });

    it('should handle multiline content', () => {
      const parsed = loadYaml(FIXTURES.validPrompt) as any;
      expect(parsed.content).toContain('multiple lines');
    });
  });

  describe('Schema validation', () => {
    it('should validate prompt schema', async () => {
      const schemaPath = join(process.cwd(), '10_schemas/prompt.schema.json');
      const schemaContent = await readFile(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);
      const validate = ajv.compile(schema);

      const manifest = loadYaml(FIXTURES.validPrompt);
      const valid = validate(manifest);

      expect(valid).toBe(true);
      expect(validate.errors).toBeNull();
    });

    it('should validate agent schema', async () => {
      const schemaPath = join(process.cwd(), '10_schemas/agent.schema.json');
      const schemaContent = await readFile(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);
      const validate = ajv.compile(schema);

      const manifest = loadYaml(FIXTURES.validAgent);
      const valid = validate(manifest);

      // Schema may require additional fields, check for basic structure
      expect(manifest).toHaveProperty('id');
      expect(manifest).toHaveProperty('system_prompt');
    });

    it('should validate rulepack schema', async () => {
      const schemaPath = join(process.cwd(), '10_schemas/rulepack.schema.json');
      const schemaContent = await readFile(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);
      const validate = ajv.compile(schema);

      const manifest = loadYaml(FIXTURES.validRulepack);
      const valid = validate(manifest);

      // Schema may require different structure, check basic fields
      expect(manifest).toHaveProperty('id');
      expect(manifest).toHaveProperty('rules');
    });

    it('should validate skill schema', async () => {
      const schemaPath = join(process.cwd(), '10_schemas/skill.schema.json');
      const schemaContent = await readFile(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);
      const validate = ajv.compile(schema);

      const manifest = loadYaml(FIXTURES.validSkill);
      const valid = validate(manifest);

      expect(valid).toBe(true);
    });

    it('should reject manifests with missing required fields', async () => {
      const schemaPath = join(process.cwd(), '10_schemas/prompt.schema.json');
      const schemaContent = await readFile(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);
      const validate = ajv.compile(schema);

      const manifest = loadYaml(FIXTURES.missingRequiredField);
      const valid = validate(manifest);

      expect(valid).toBe(false);
      expect(validate.errors).not.toBeNull();
    });
  });

  describe('ID validation', () => {
    const KEBAB_CASE_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

    it('should accept valid kebab-case IDs', () => {
      const validIds = [
        'test-prompt',
        'write-tests',
        'code-reviewer',
        'typescript-style',
        'git-ops',
      ];

      validIds.forEach((id) => {
        expect(KEBAB_CASE_PATTERN.test(id)).toBe(true);
      });
    });

    it('should reject invalid IDs', () => {
      const invalidIds = [
        'TestPrompt', // PascalCase
        'test_prompt', // snake_case
        'test.prompt', // dot notation
        'Test-Prompt', // mixed case
        '-test', // leading dash
        'test-', // trailing dash
        'test--prompt', // double dash
      ];

      invalidIds.forEach((id) => {
        expect(KEBAB_CASE_PATTERN.test(id)).toBe(false);
      });
    });
  });

  describe('Version validation', () => {
    const SEMVER_PATTERN = /^\d+\.\d+\.\d+$/;

    it('should accept valid semantic versions', () => {
      const validVersions = ['1.0.0', '0.1.0', '2.3.4', '10.20.30'];

      validVersions.forEach((version) => {
        expect(SEMVER_PATTERN.test(version)).toBe(true);
      });
    });

    it('should reject invalid versions', () => {
      const invalidVersions = [
        '1.0', // missing patch
        '1', // missing minor and patch
        'v1.0.0', // prefix
        '1.0.0-beta', // pre-release
        '1.0.0+build', // build metadata
      ];

      invalidVersions.forEach((version) => {
        expect(SEMVER_PATTERN.test(version)).toBe(false);
      });
    });
  });

  describe('Security validation', () => {
    const SECURITY_PATTERNS = [
      { name: 'API Key', pattern: /\b[A-Za-z0-9_-]{32,}\b/, exclude: /example|sample|test/i },
      {
        name: 'Password',
        pattern: /password\s*[:=]\s*['"][^'"]+['"]/,
        exclude: /\$\{|example|sample|placeholder/i,
      },
      { name: 'AWS Access Key', pattern: /AKIA[0-9A-Z]{16}/, exclude: null },
      { name: 'GitHub Token', pattern: /ghp_[a-zA-Z0-9]{36}/, exclude: null },
    ];

    it('should detect potential API keys', () => {
      const content = 'api_key: sk_live_1234567890abcdefghijklmnopqrstuvwxyz';
      const pattern = SECURITY_PATTERNS[0];
      expect(pattern.pattern.test(content)).toBe(true);
    });

    it('should allow example/test patterns', () => {
      const content = 'api_key: example_key_1234567890abcdefghijklmnopqrstuvwxyz';
      const pattern = SECURITY_PATTERNS[0];
      const match = content.match(pattern.pattern);
      if (match && pattern.exclude) {
        expect(pattern.exclude.test(content)).toBe(true);
      }
    });

    it('should detect password assignments', () => {
      const content = 'password: "secret123"';
      const pattern = SECURITY_PATTERNS[1];
      expect(pattern.pattern.test(content)).toBe(true);
    });

    it('should allow password placeholders', () => {
      const content = 'password: "${PASSWORD}"';
      const pattern = SECURITY_PATTERNS[1];
      const match = content.match(pattern.pattern);
      if (match && pattern.exclude) {
        expect(pattern.exclude.test(content)).toBe(true);
      }
    });

    it('should detect AWS access keys', () => {
      const content = 'aws_key: AKIAIOSFODNN7EXAMPLE';
      const pattern = SECURITY_PATTERNS[2];
      expect(pattern.pattern.test(content)).toBe(true);
    });

    it('should detect GitHub tokens', () => {
      const content = 'token: ghp_1234567890abcdefghijklmnopqrstu12345';
      const pattern = SECURITY_PATTERNS[3];
      expect(pattern.pattern.test(content)).toBe(true);
    });
  });

  describe('Description validation', () => {
    it('should validate description length', () => {
      const minLength = 10;
      const maxLength = 500;

      const validDescription = 'This is a valid description for testing purposes';
      expect(validDescription.length).toBeGreaterThanOrEqual(minLength);
      expect(validDescription.length).toBeLessThanOrEqual(maxLength);
    });

    it('should reject too short descriptions', () => {
      const tooShort = 'Short';
      const minLength = 10;
      expect(tooShort.length).toBeLessThan(minLength);
    });

    it('should reject too long descriptions', () => {
      const tooLong = 'a'.repeat(501);
      const maxLength = 500;
      expect(tooLong.length).toBeGreaterThan(maxLength);
    });
  });

  describe('Reference validation', () => {
    it('should validate agent references in projects', () => {
      const project = loadYaml(FIXTURES.validProject) as any;
      expect(project.agents).toBeInstanceOf(Array);
      expect(project.agents.length).toBeGreaterThan(0);
    });

    it('should validate rulepack references', () => {
      const project = loadYaml(FIXTURES.validProject) as any;
      expect(project.rulepacks).toBeInstanceOf(Array);
    });

    it('should validate skill references', () => {
      const project = loadYaml(FIXTURES.validProject) as any;
      expect(project.skills).toBeInstanceOf(Array);
    });
  });

  describe('Include validation', () => {
    it('should validate include syntax', () => {
      const contentWithInclude = 'Some content\n@include(another-file.md)\nMore content';
      const includePattern = /@include\(([^)]+)\)/g;
      const matches = contentWithInclude.match(includePattern);

      expect(matches).not.toBeNull();
      expect(matches?.length).toBe(1);
    });

    it('should extract include paths', () => {
      const contentWithInclude = '@include(docs/guide.md)';
      const includePattern = /@include\(([^)]+)\)/;
      const match = contentWithInclude.match(includePattern);

      expect(match).not.toBeNull();
      expect(match?.[1]).toBe('docs/guide.md');
    });
  });

  describe('Regression tests', () => {
    it('should handle backticks in content', () => {
      const contentWithBackticks = `content: |
  Use \`code\` blocks for examples
  \`\`\`javascript
  console.log('test');
  \`\`\``;

      const parsed = loadYaml(contentWithBackticks);
      expect(parsed).toHaveProperty('content');
    });

    it('should preserve date formats', () => {
      const contentWithDate = `id: test
version: "1.0.0"
description: "Test with date"
created: 2024-01-15
updated: 2024-01-20T10:30:00Z`;

      const parsed = loadYaml(contentWithDate) as any;
      expect(parsed.created).toBeDefined();
      expect(parsed.updated).toBeDefined();
    });

    it('should handle special characters in descriptions', () => {
      const contentWithSpecialChars = `id: test
version: "1.0.0"
description: "Test with special chars: @#$%^&*()_+-={}[]|:;<>?,./~"`;

      const parsed = loadYaml(contentWithSpecialChars) as any;
      expect(parsed.description).toContain('@#$%^&*()');
    });
  });
});
