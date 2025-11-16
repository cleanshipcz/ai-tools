/**
 * Tests for gen-project.ts - Recipe script generation
 *
 * CRITICAL BUG: Claude recipe scripts use invalid --agent flag
 * Claude CLI does not support --agent flag; must use --system-prompt instead
 */

import { describe, it, expect } from 'vitest';
import { ProjectGenerator } from './gen-project.js';

describe('ProjectGenerator - Recipe Script Generation', () => {
  describe('Claude Code recipe generation', () => {
    it('should NOT use --agent flag (invalid for Claude)', async () => {
      // This test documents the bug: Claude CLI doesn't support --agent
      // Expected: Scripts should use --system-prompt with agent's system prompt
      // Actual: Scripts incorrectly use --agent flag which Claude doesn't recognize

      const generator = new ProjectGenerator();
      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const script = await (generator as any).buildRecipeScript(mockRecipe, 'claude-code', null);

      // BUG: Current implementation generates invalid command with --agent flag
      // Claude CLI does NOT support --agent flag
      expect(script).not.toContain('--agent'); // This will FAIL, showing the bug

      // After fix, script should contain:
      // - --system-prompt OR --agents JSON (not --agent)
      // - Task as final argument
    });

    it('should use --system-prompt with agent definitions', async () => {
      const generator = new ProjectGenerator();
      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const script = await (generator as any).buildRecipeScript(mockRecipe, 'claude-code', null);

      // After fix: verify correct Claude CLI usage
      expect(script).toContain('--system-prompt');
      expect(script).toContain('You are an expert debugger'); // from bug-fixer agent
    });

    it('should support --model flag for model selection', async () => {
      const generator = new ProjectGenerator();
      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        model: 'claude-sonnet-4.5',
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const script = await (generator as any).buildRecipeScript(mockRecipe, 'claude-code', null);

      // Recipes should support model configuration
      expect(script).toContain('--model "claude-sonnet-4.5"');
    });

    it('should support tool permissions via --allowed-tools', async () => {
      const generator = new ProjectGenerator();
      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        toolOptions: {
          'claude-code': {
            allowedTools: ['Bash', 'Edit', 'Read'],
          },
        },
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const script = await (generator as any).buildRecipeScript(mockRecipe, 'claude-code', null);

      // Should generate --allowed-tools flags based on toolOptions
      expect(script).toContain('--allowed-tools');
    });

    it('should use --print for non-interactive execution', async () => {
      const generator = new ProjectGenerator();
      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const script = await (generator as any).buildRecipeScript(mockRecipe, 'claude-code', null);

      // Should use --print for scripting/non-interactive mode
      expect(script).toContain('--print');
    });
  });

  describe('Copilot CLI recipe generation', () => {
    it('should use @agent syntax for agent invocation', async () => {
      const generator = new ProjectGenerator();
      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const script = await (generator as any).buildRecipeScript(mockRecipe, 'copilot-cli', null);

      // Copilot CLI uses @agent syntax
      expect(script).toContain('@bug-fixer');
    });
  });

  describe('Feature-bound recipes (gen-features.ts)', () => {
    it('should NOT have duplicate broken code with --agent flag', async () => {
      // BUG: gen-features.ts has duplicate generateStepScript that still uses --agent
      // This test ensures we refactor to share the fixed logic
      const { FeatureGenerator } = await import('./gen-features.js');
      const generator = new FeatureGenerator();

      const mockFeature = {
        id: 'test-feature',
        name: 'Test Feature',
        version: '1.0.0',
        description: 'Test',
        recipe: {
          id: 'test-recipe',
          context: {},
        },
      };

      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const script = (generator as any).buildFeatureRecipeScript(
        mockFeature,
        mockRecipe,
        'claude-code'
      );

      // Should NOT use invalid --agent flag
      expect(script).not.toContain('--agent');
    });
  });

  describe('Recipe logging feature', () => {
    it('should include logging setup in generated scripts', async () => {
      const generator = new ProjectGenerator();
      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const script = await (generator as any).buildRecipeScript(mockRecipe, 'copilot-cli', null);

      // Should create .recipe-logs directory
      expect(script).toContain('RECIPE_LOGS_DIR=".recipe-logs"');
      expect(script).toContain('mkdir -p "$RECIPE_LOGS_DIR"');

      // Should generate timestamped log filename
      expect(script).toContain(
        'LOG_FILE="$RECIPE_LOGS_DIR/test-recipe-$(date +%Y%m%d-%H%M%S).log"'
      );

      // Should show log file location
      expect(script).toContain('echo "📝 Logging to: $LOG_FILE"');

      // Should use exec with tee for logging
      expect(script).toContain('exec > >(tee -a "$LOG_FILE") 2>&1');
    });

    it('should include logging setup in feature-bound recipe scripts', async () => {
      const { FeatureGenerator } = await import('./gen-features.js');
      const generator = new FeatureGenerator();

      const mockFeature = {
        id: 'test-feature',
        name: 'Test Feature',
        version: '1.0.0',
        description: 'Test',
        recipe: {
          id: 'test-recipe',
          context: {},
        },
      };

      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const script = await (generator as any).buildFeatureRecipeScript(
        mockFeature,
        mockRecipe,
        'copilot-cli'
      );

      // Should create .recipe-logs directory
      expect(script).toContain('RECIPE_LOGS_DIR=".recipe-logs"');
      expect(script).toContain('mkdir -p "$RECIPE_LOGS_DIR"');

      // Should generate timestamped log filename with feature prefix
      expect(script).toContain(
        'LOG_FILE="$RECIPE_LOGS_DIR/feature-test-feature-$(date +%Y%m%d-%H%M%S).log"'
      );

      // Should use exec with tee for logging
      expect(script).toContain('exec > >(tee -a "$LOG_FILE") 2>&1');
    });

    it('should work for all tool types', async () => {
      const generator = new ProjectGenerator();
      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const tools = ['claude-code', 'copilot-cli', 'cursor'];

      for (const tool of tools) {
        const script = await (generator as any).buildRecipeScript(mockRecipe, tool, null);
        expect(script).toContain('RECIPE_LOGS_DIR=".recipe-logs"');
        expect(script).toContain('exec > >(tee -a "$LOG_FILE") 2>&1');
      }
    });

    it('should place logging setup before variable declarations', async () => {
      const generator = new ProjectGenerator();
      const mockRecipe = {
        id: 'test-recipe',
        description: 'Test recipe',
        variables: {
          test_var: 'test_value',
        },
        steps: [
          {
            id: 'test-step',
            agent: 'bug-fixer',
            task: 'Fix the bug',
          },
        ],
      };

      const script = await (generator as any).buildRecipeScript(mockRecipe, 'copilot-cli', null);

      // Logging should come before variables
      const loggingPos = script.indexOf('RECIPE_LOGS_DIR');
      const variablesPos = script.indexOf('# Variables');

      expect(loggingPos).toBeGreaterThan(0);
      expect(variablesPos).toBeGreaterThan(0);
      expect(loggingPos).toBeLessThan(variablesPos);
    });
  });
});

/**
 * Test Plan:
 * 1. ✅ Write failing test documenting the bug
 * 2. ⏳ Fix buildRecipeScript to load agent definitions
 * 3. ⏳ Generate --system-prompt from agent.prompt.system
 * 4. ⏳ Add --model support from recipe config
 * 5. ⏳ Add tool permissions support
 * 6. ⏳ Verify tests pass
 */
