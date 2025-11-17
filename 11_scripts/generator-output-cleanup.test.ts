/**
 * TDD tests for generator output cleanup
 *
 * These tests define what should NOT appear in generated markdown outputs:
 * - "Default Model:" lines should not appear in agent rules
 * - "Default Model:" lines should not appear in prompt rules
 * - "Preferred Agents:" sections should not appear in project context
 * - "Default Model:" lines should not appear in project context
 *
 * RED phase: These tests will FAIL until we update the generators
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { readFileSync, existsSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REPO_ROOT = join(__dirname, '..');

// Helper to ensure adapters are built
function ensureAdaptersBuilt() {
  try {
    execSync('npm run build', { cwd: REPO_ROOT, stdio: 'pipe' });
  } catch (e) {
    // Build might fail, but we need to check what was generated
  }
}

describe('Generator Output Cleanup - TDD Red Phase', () => {
  beforeEach(() => {
    ensureAdaptersBuilt();
  });

  describe('Agent rules (build.ts)', () => {
    it('should NOT contain "Default Model:" in agent markdown files', () => {
      const agentRulesDir = join(REPO_ROOT, 'adapters', 'windsurf', 'rules');

      if (!existsSync(agentRulesDir)) {
        throw new Error('Agent rules directory not found - build may have failed');
      }

      const agentFiles = readdirSync(agentRulesDir).filter(
        (f) => f.startsWith('agent-') && f.endsWith('.md')
      );

      expect(agentFiles.length).toBeGreaterThan(0);

      // Check each agent file
      agentFiles.forEach((file) => {
        const content = readFileSync(join(agentRulesDir, file), 'utf-8');

        // This should FAIL - "Default Model:" should not appear
        expect(content).not.toContain('Default Model:');
        expect(content).not.toMatch(/\*Default Model:\*\s+`/);
        expect(content).not.toMatch(/\*\*Default Model:\*\*\s+/);
      });
    });
  });

  describe('Prompt rules (build.ts)', () => {
    it('should NOT contain "Default Model:" in prompt markdown files', () => {
      const promptRulesDir = join(REPO_ROOT, 'adapters', 'windsurf', 'rules');

      if (!existsSync(promptRulesDir)) {
        throw new Error('Prompt rules directory not found - build may have failed');
      }

      const promptFiles = readdirSync(promptRulesDir).filter(
        (f) => f.startsWith('prompt-') && f.endsWith('.md')
      );

      expect(promptFiles.length).toBeGreaterThan(0);

      // Check each prompt file
      promptFiles.forEach((file) => {
        const content = readFileSync(join(promptRulesDir, file), 'utf-8');

        // This should FAIL - "Default Model:" should not appear
        expect(content).not.toContain('Default Model:');
        expect(content).not.toMatch(/\*Default Model:\*\s+`/);
        expect(content).not.toMatch(/\*\*Default Model:\*\*\s+/);
      });
    });
  });

  describe('Project context (gen-project.ts)', () => {
    it('should NOT contain "Default Model:" in project-context.md', () => {
      // IMPORTANT: Delete old output first, then generate fresh
      try {
        execSync('rm -rf .output/ai-tools', { cwd: REPO_ROOT, stdio: 'pipe' });
      } catch (e) {
        // Ignore if doesn't exist
      }

      // Generate fresh project context
      try {
        execSync('npm run project:generate ai-tools', { cwd: REPO_ROOT, stdio: 'pipe' });
      } catch (e) {
        throw new Error(`Generation failed: ${e}`);
      }

      const projectContextPath = join(
        REPO_ROOT,
        '.output',
        'ai-tools',
        'windsurf',
        '.windsurf',
        'rules',
        'project-context.md'
      );

      if (!existsSync(projectContextPath)) {
        throw new Error('project-context.md not found - generation may have failed');
      }

      const content = readFileSync(projectContextPath, 'utf-8');

      // This should FAIL - "Default Model:" should not appear
      expect(content).not.toContain('Default Model:');
      expect(content).not.toMatch(/\*\*Default Model:\*\*\s+/);
    });

    it('should NOT contain "Preferred Agents:" in project-context.md', () => {
      const projectContextPath = join(
        REPO_ROOT,
        '.output',
        'ai-tools',
        'windsurf',
        '.windsurf',
        'rules',
        'project-context.md'
      );

      if (!existsSync(projectContextPath)) {
        throw new Error('project-context.md not found - generation may have failed');
      }

      const content = readFileSync(projectContextPath, 'utf-8');

      // This should FAIL - "Preferred Agents:" should not appear
      expect(content).not.toContain('Preferred Agents');
      expect(content).not.toContain('**Preferred Agents for this project**');
      expect(content).not.toMatch(/## Preferred Agents/);
    });
  });

  describe('Feature workflows (gen-features.ts)', () => {
    it('should NOT reference entry_points or snippets fields', () => {
      // Check the gen-features.ts source code itself
      const genFeaturesPath = join(REPO_ROOT, '11_scripts', 'gen-features.ts');
      const sourceCode = readFileSync(genFeaturesPath, 'utf-8');

      // This should FAIL - these field references should not exist
      expect(sourceCode).not.toContain('feature.files.entry_points');
      expect(sourceCode).not.toContain('feature.files?.entry_points');
      expect(sourceCode).not.toContain('feature.snippets');
      expect(sourceCode).not.toMatch(/entry_points\s*:/);
    });
  });
});
