/**
 * Test utilities and helpers for the test suite
 */

import { mkdir, rm, writeFile, readFile, readdir } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { existsSync } from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT_DIR = join(__dirname, '..');
export const TEST_OUTPUT_DIR = join(ROOT_DIR, '.output-test');

/**
 * Setup test output directory
 */
export async function setupTestOutput(): Promise<string> {
  await mkdir(TEST_OUTPUT_DIR, { recursive: true });
  return TEST_OUTPUT_DIR;
}

/**
 * Cleanup test output directory
 */
export async function cleanupTestOutput(): Promise<void> {
  if (existsSync(TEST_OUTPUT_DIR)) {
    // Clean directory contents but keep the directory itself
    const files = await readdir(TEST_OUTPUT_DIR);
    for (const file of files) {
      try {
        await rm(join(TEST_OUTPUT_DIR, file), { recursive: true, force: true });
      } catch (error) {
        // Ignore cleanup errors (files may be deleted by other tests)
      }
    }
  }
}

/**
 * Create test fixture file
 */
export async function createTestFixture(relativePath: string, content: string): Promise<string> {
  // Ensure base test output directory exists first
  await mkdir(TEST_OUTPUT_DIR, { recursive: true });
  const fullPath = join(TEST_OUTPUT_DIR, relativePath);
  await mkdir(dirname(fullPath), { recursive: true });
  await writeFile(fullPath, content, 'utf-8');
  return fullPath;
}

/**
 * Read test output file
 */
export async function readTestOutput(relativePath: string): Promise<string> {
  const fullPath = join(TEST_OUTPUT_DIR, relativePath);
  return await readFile(fullPath, 'utf-8');
}

/**
 * Check if test output file exists
 */
export function testOutputExists(relativePath: string): boolean {
  const fullPath = join(TEST_OUTPUT_DIR, relativePath);
  return existsSync(fullPath);
}

/**
 * List files in test output directory
 */
export async function listTestOutput(relativePath = ''): Promise<string[]> {
  const fullPath = join(TEST_OUTPUT_DIR, relativePath);
  if (!existsSync(fullPath)) {
    return [];
  }
  return await readdir(fullPath);
}

/**
 * Sample YAML fixtures for testing
 */
export const FIXTURES = {
  validPrompt: `id: test-prompt
version: "1.0.0"
description: "A test prompt for unit testing"
category: testing
content: |
  This is a test prompt.
  It has multiple lines.
tags:
  - test
  - sample
`,

  validAgent: `id: test-agent
version: "1.0.0"
description: "A test agent for unit testing"
system_prompt: |
  You are a test agent.
  You help with testing.
capabilities:
  - testing
  - validation
model: claude-sonnet-4.5
tools:
  - test-tool
`,

  validRulepack: `id: test-rulepack
version: "1.0.0"
description: "A test rulepack for unit testing"
category: style
rules:
  - id: test-rule-1
    description: "Use proper naming"
    pattern: "camelCase"
  - id: test-rule-2
    description: "Add documentation"
    pattern: "JSDoc comments"
`,

  validSkill: `id: test-skill
version: "1.0.0"
description: "A test skill for unit testing"
category: tool
definition: |
  Test skill definition
examples:
  - input: "test input"
    output: "test output"
`,

  validProject: `id: test-project
version: "1.0.0"
description: "A test project for unit testing"
agents:
  - code-reviewer
rulepacks:
  - typescript-style
skills:
  - git-ops
prompts:
  - write-tests
`,

  invalidYaml: `id: broken
version: 1.0.0
description: Missing quotes and
  improper indentation
    random: stuff
`,

  missingRequiredField: `id: incomplete
version: "1.0.0"
# Missing description field
content: "Some content"
`,
};

/**
 * Schema names for validation
 */
export const SCHEMA_NAMES = {
  prompt: 'prompt.schema.json',
  agent: 'agent.schema.json',
  rulepack: 'rulepack.schema.json',
  skill: 'skill.schema.json',
  project: 'project.schema.json',
  eval: 'eval.schema.json',
};

/**
 * Test data directory paths
 */
export const TEST_DIRS = {
  prompts: join(ROOT_DIR, '03_prompts'),
  agents: join(ROOT_DIR, '04_agents'),
  rulepacks: join(ROOT_DIR, '01_rulepacks'),
  skills: join(ROOT_DIR, '02_skills'),
  projects: join(ROOT_DIR, '06_projects'),
  schemas: join(ROOT_DIR, '10_schemas'),
  evals: join(ROOT_DIR, '20_evals'),
};

/**
 * Expected adapter output directories for each tool
 */
export const ADAPTER_DIRS = {
  'github-copilot': '.github',
  windsurf: '.windsurf',
  'claude-code': '.claude',
  cursor: '.cursor',
};

/**
 * Validate adapter output structure
 */
export async function validateAdapterOutput(
  tool: keyof typeof ADAPTER_DIRS
): Promise<{ valid: boolean; errors: string[] }> {
  const errors: string[] = [];
  const baseDir = join(TEST_OUTPUT_DIR, ADAPTER_DIRS[tool]);

  if (!existsSync(baseDir)) {
    errors.push(`Missing adapter directory: ${ADAPTER_DIRS[tool]}`);
    return { valid: false, errors };
  }

  // Tool-specific validation
  switch (tool) {
    case 'github-copilot':
      if (!existsSync(join(baseDir, 'copilot-instructions.md'))) {
        errors.push('Missing copilot-instructions.md');
      }
      break;
    case 'windsurf':
      if (!existsSync(join(baseDir, 'rules'))) {
        errors.push('Missing rules directory');
      }
      break;
    case 'claude-code':
      if (!existsSync(join(baseDir, 'prompts'))) {
        errors.push('Missing prompts directory');
      }
      break;
    case 'cursor':
      if (!existsSync(join(baseDir, '.cursorrules'))) {
        errors.push('Missing .cursorrules file');
      }
      break;
  }

  return { valid: errors.length === 0, errors };
}
