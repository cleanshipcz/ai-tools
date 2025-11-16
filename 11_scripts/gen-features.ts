#!/usr/bin/env node
import { readFile, writeFile, mkdir, readdir, access } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import chalk from 'chalk';
import { ProjectGenerator } from './gen-project.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

type AIModel =
  | 'claude-sonnet-4.5'
  | 'claude-sonnet-4'
  | 'claude-haiku-4.5'
  | 'gpt-5'
  | 'gpt-5.1'
  | 'gpt-5.1-codex-mini'
  | 'gpt-5.1-codex';

interface Feature {
  id: string;
  version: string;
  name: string;
  description: string;
  model?: AIModel;
  context?: {
    overview?: string;
    architecture?: string;
    dependencies?: string[];
  };
  files?: {
    entry_points?: string[];
    key_files?: string[];
    patterns?: string[];
  };
  snippets?: Array<{
    id: string;
    title: string;
    description?: string;
    content: string;
    language?: string;
  }>;
  conventions?: string[];
  recipe?: {
    id: string;
    context?: Record<string, string>;
    tools?: string[];
  };
  metadata?: {
    status?: 'draft' | 'active' | 'deprecated' | 'archived';
    owner?: string;
    created?: string;
    updated?: string;
    tags?: string[];
  };
}

interface Project {
  id: string;
  name: string;
  ai_tools?: {
    model?: AIModel;
  };
}

export class FeatureGenerator {
  private project?: Project;
  private agentsMap: Map<string, any> = new Map();

  async generateFeatures(projectId: string): Promise<void> {
    console.log(chalk.blue(`\n🎯 Generating features for project: ${projectId}\n`));

    // Find project directory
    const projectDir = await this.findProjectDir(projectId);
    if (!projectDir) {
      throw new Error(`Project not found: ${projectId}`);
    }

    // Load project configuration
    await this.loadProject(projectDir);

    // Load all agents for model resolution
    await this.loadAgents();

    // Find features directory
    const featuresDir = join(projectDir, 'features');
    try {
      await access(featuresDir);
    } catch {
      console.log(chalk.yellow('  No features directory found'));
      return;
    }

    // Load all features
    const features = await this.loadFeatures(featuresDir);

    if (features.length === 0) {
      console.log(chalk.yellow('  No features found'));
      return;
    }

    console.log(chalk.gray(`  Found ${features.length} feature(s)\n`));

    // Generate output for each tool
    const outputDir = join(rootDir, '.output', projectId, 'features');
    await mkdir(outputDir, { recursive: true });

    await this.generateGitHubCopilotFeatures(features, outputDir);
    await this.generateWindsurfFeatures(features, outputDir);
    await this.generateClaudeCodeFeatures(features, outputDir);
    await this.generateCursorFeatures(features, outputDir);

    // Generate feature-bound recipe scripts
    await this.generateFeatureRecipes(projectId, features, outputDir);

    console.log(chalk.green(`\n✅ Generated feature snippets in: ${outputDir}\n`));
  }

  private async findProjectDir(projectId: string): Promise<string | null> {
    // Check global
    let projectDir = join(rootDir, '06_projects', 'global', projectId);
    try {
      await access(projectDir);
      return projectDir;
    } catch {
      // Try local
      projectDir = join(rootDir, '06_projects', 'local', projectId);
      try {
        await access(projectDir);
        return projectDir;
      } catch {
        return null;
      }
    }
  }

  private async loadProject(projectDir: string): Promise<void> {
    const projectPath = join(projectDir, 'project.yml');
    try {
      const content = await readFile(projectPath, 'utf-8');
      this.project = loadYaml(content) as Project;
    } catch (error) {
      console.log(chalk.yellow(`  Warning: Could not load project.yml`));
    }
  }

  private async loadAgents(): Promise<void> {
    const agentsDir = join(rootDir, '04_agents');
    const entries = await readdir(agentsDir);

    for (const entry of entries) {
      if (entry.endsWith('.yml')) {
        const agentPath = join(agentsDir, entry);
        const content = await readFile(agentPath, 'utf-8');
        const agent = loadYaml(content) as any;
        this.agentsMap.set(agent.id, agent);
      }
    }
  }

  private resolveModel(feature: Feature, recipeStepAgent?: string): AIModel | undefined {
    // Priority: Feature > Project > Agent > Prompt
    // For recipes, we consider the agent mentioned in the recipe step

    // 1. Feature-level (highest priority)
    if (feature.model) {
      return feature.model;
    }

    // 2. Project-level
    if (this.project?.ai_tools?.model) {
      return this.project.ai_tools.model;
    }

    // 3. Agent-level (from recipe step agent)
    if (recipeStepAgent) {
      const agent = this.agentsMap.get(recipeStepAgent);
      if (agent?.defaults?.model) {
        return agent.defaults.model;
      }
    }

    // No model configured in hierarchy
    return undefined;
  }

  private async loadFeatures(featuresDir: string): Promise<Feature[]> {
    const features: Feature[] = [];
    const entries = await readdir(featuresDir, { withFileTypes: true });

    for (const entry of entries) {
      if (entry.isDirectory()) {
        const featurePath = join(featuresDir, entry.name, 'feature.yml');
        try {
          await access(featurePath);
          const content = await readFile(featurePath, 'utf-8');
          const feature = loadYaml(content) as Feature;
          features.push(feature);
          console.log(chalk.gray(`    Loaded: ${feature.name}`));
        } catch {
          // No feature.yml in this directory
        }
      }
    }

    return features;
  }

  private async generateGitHubCopilotFeatures(
    features: Feature[],
    outputDir: string
  ): Promise<void> {
    const copilotDir = join(outputDir, 'github-copilot');
    await mkdir(copilotDir, { recursive: true });

    for (const feature of features) {
      const content: string[] = [];

      content.push(`# Feature: ${feature.name}`);
      content.push('');
      content.push(feature.description);
      content.push('');

      // Add default model if configured
      if (feature.model) {
        content.push(`**Default Model for this Feature:** ${feature.model}`);
        content.push('');
        content.push(
          '*This is the highest priority model setting, overriding project and agent defaults.*'
        );
        content.push('');
      }

      if (feature.context?.overview) {
        content.push('## Overview');
        content.push('');
        content.push(feature.context.overview);
        content.push('');
      }

      if (feature.context?.architecture) {
        content.push('## Architecture');
        content.push('');
        content.push(feature.context.architecture);
        content.push('');
      }

      if (feature.files) {
        content.push('## Related Files');
        content.push('');
        if (feature.files.entry_points?.length) {
          content.push('**Entry Points:**');
          for (const file of feature.files.entry_points) {
            content.push(`- \`${file}\``);
          }
          content.push('');
        }
        if (feature.files.key_files?.length) {
          content.push('**Key Files:**');
          for (const file of feature.files.key_files) {
            content.push(`- \`${file}\``);
          }
          content.push('');
        }
      }

      if (feature.conventions?.length) {
        content.push('## Conventions');
        content.push('');
        for (const convention of feature.conventions) {
          content.push(`- ${convention}`);
        }
        content.push('');
      }

      if (feature.snippets?.length) {
        content.push('## Code Snippets');
        content.push('');
        for (const snippet of feature.snippets) {
          content.push(`### ${snippet.title}`);
          if (snippet.description) {
            content.push('');
            content.push(snippet.description);
          }
          content.push('');
          content.push('```' + (snippet.language || ''));
          content.push(snippet.content);
          content.push('```');
          content.push('');
        }
      }

      await writeFile(join(copilotDir, `feature-${feature.id}.md`), content.join('\n'), 'utf-8');
    }

    console.log(chalk.gray(`    Generated ${features.length} GitHub Copilot feature files`));
  }

  private async generateWindsurfFeatures(features: Feature[], outputDir: string): Promise<void> {
    const windsurfDir = join(outputDir, 'windsurf');
    await mkdir(windsurfDir, { recursive: true });

    for (const feature of features) {
      const config = {
        name: feature.name,
        description: feature.description,
        model: feature.model,
        context: feature.context || {},
        files: feature.files || {},
        conventions: feature.conventions || [],
        snippets: feature.snippets || [],
      };

      await writeFile(
        join(windsurfDir, `feature-${feature.id}.json`),
        JSON.stringify(config, null, 2),
        'utf-8'
      );
    }

    console.log(chalk.gray(`    Generated ${features.length} Windsurf feature files`));
  }

  private async generateClaudeCodeFeatures(features: Feature[], outputDir: string): Promise<void> {
    const claudeDir = join(outputDir, 'claude-code');
    await mkdir(claudeDir, { recursive: true });

    for (const feature of features) {
      const content: string[] = [];

      content.push(`# ${feature.name}`);
      content.push('');
      content.push(feature.description);
      content.push('');

      // Add default model if configured
      if (feature.model) {
        content.push(`**Default Model:** ${feature.model}`);
        content.push('');
      }

      if (feature.context) {
        if (feature.context.overview) {
          content.push(feature.context.overview);
          content.push('');
        }
        if (feature.context.architecture) {
          content.push('## Architecture');
          content.push('');
          content.push(feature.context.architecture);
          content.push('');
        }
      }

      if (feature.snippets?.length) {
        content.push('## Examples');
        content.push('');
        for (const snippet of feature.snippets) {
          content.push(`**${snippet.title}**`);
          if (snippet.description) {
            content.push(snippet.description);
          }
          content.push('');
          content.push('```' + (snippet.language || ''));
          content.push(snippet.content);
          content.push('```');
          content.push('');
        }
      }

      await writeFile(join(claudeDir, `feature-${feature.id}.md`), content.join('\n'), 'utf-8');
    }

    console.log(chalk.gray(`    Generated ${features.length} Claude Code feature files`));
  }

  private async generateCursorFeatures(features: Feature[], outputDir: string): Promise<void> {
    const cursorDir = join(outputDir, 'cursor');
    await mkdir(cursorDir, { recursive: true });

    const allFeatures = features.map((feature) => ({
      id: feature.id,
      name: feature.name,
      description: feature.description,
      model: feature.model,
      context: feature.context || {},
      files: feature.files || {},
      conventions: feature.conventions || [],
      snippets: feature.snippets || [],
    }));

    await writeFile(
      join(cursorDir, 'features.json'),
      JSON.stringify({ features: allFeatures }, null, 2),
      'utf-8'
    );

    console.log(chalk.gray(`    Generated Cursor features.json`));
  }

  private async generateFeatureRecipes(
    projectId: string,
    features: Feature[],
    outputDir: string
  ): Promise<void> {
    // Filter features that have recipe bindings
    const featuresWithRecipes = features.filter((f) => f.recipe?.id);

    if (featuresWithRecipes.length === 0) {
      return;
    }

    console.log(chalk.blue(`\n  Generating feature-bound recipes...`));

    const recipesDir = join(rootDir, '05_recipes');

    for (const feature of featuresWithRecipes) {
      const recipeId = feature.recipe!.id;
      const recipePath = join(recipesDir, `${recipeId}.yml`);

      try {
        // Load the recipe
        const recipeContent = await readFile(recipePath, 'utf-8');
        const recipe = loadYaml(recipeContent) as any;

        // Determine which tools to generate for
        const tools = feature.recipe!.tools ||
          recipe.tools || ['claude-code', 'copilot-cli', 'cursor'];

        // Generate a script for each tool
        for (const tool of tools) {
          // Skip if recipe doesn't support this tool
          if (recipe.tools && Array.isArray(recipe.tools) && !recipe.tools.includes(tool)) {
            continue;
          }

          // Put feature scripts in the tool's .cs.recipes directory
          // so they're deployed alongside generic recipes
          const toolOutputDir = join(rootDir, '.output', projectId, tool);

          let recipesDir: string;
          if (tool === 'claude-code') {
            recipesDir = join(toolOutputDir, '.claude', '.cs.recipes');
          } else if (tool === 'copilot-cli') {
            recipesDir = join(toolOutputDir, '.cs.recipes');
          } else if (tool === 'cursor') {
            recipesDir = join(toolOutputDir, '.cursor', '.cs.recipes');
          } else if (tool === 'github-copilot') {
            recipesDir = join(toolOutputDir, '.github', '.cs.recipes');
          } else if (tool === 'windsurf') {
            recipesDir = join(toolOutputDir, '.windsurf', '.cs.recipes');
          } else {
            recipesDir = join(toolOutputDir, '.cs.recipes');
          }

          await mkdir(recipesDir, { recursive: true });

          const scriptPath = join(recipesDir, `feature-${feature.id}.sh`);
          await this.generateFeatureRecipeScript(feature, recipe, tool, scriptPath);

          console.log(chalk.gray(`    Generated ${tool} script for feature: ${feature.name}`));
        }
      } catch (error: any) {
        console.log(
          chalk.yellow(
            `    ! Could not generate recipe for feature ${feature.name}: ${error.message}`
          )
        );
      }
    }
  }

  private async generateFeatureRecipeScript(
    feature: Feature,
    recipe: any,
    tool: string,
    scriptPath: string
  ): Promise<void> {
    const script = await this.buildFeatureRecipeScript(feature, recipe, tool);
    await writeFile(scriptPath, script, { mode: 0o755 });
  }

  private async buildFeatureRecipeScript(
    feature: Feature,
    recipe: any,
    tool: string
  ): Promise<string> {
    // Resolve model from hierarchy (feature > project > agent)
    const resolvedModel = this.resolveModel(feature);

    let script = '#!/bin/bash\n';
    script += `# Feature-bound recipe script\n`;
    script += `# Feature: ${feature.name}\n`;
    script += `# Recipe: ${recipe.id}\n`;
    script += `# Tool: ${tool}\n`;

    // Add model information with hierarchy explanation
    if (resolvedModel) {
      const source = feature.model
        ? 'feature-level (highest priority)'
        : this.project?.ai_tools?.model
          ? 'project-level'
          : 'agent-level (default)';
      script += `# Model: ${resolvedModel} (${source})\n`;
    }

    script += `# Generated: ${new Date().toISOString()}\n\n`;
    script += 'set -e  # Exit on error\n\n';

    // Setup logging
    script += '# Setup logging\n';
    script += 'RECIPE_LOGS_DIR=".recipe-logs"\n';
    script += 'mkdir -p "$RECIPE_LOGS_DIR"\n';
    script += `LOG_FILE="$RECIPE_LOGS_DIR/feature-${feature.id}-$(date +%Y%m%d-%H%M%S).log"\n`;
    script += 'echo "📝 Logging to: $LOG_FILE"\n';
    script += 'echo ""\n\n';

    // Start logging with exec and tee
    script += '# Redirect all output to both console and log file\n';
    script += 'exec > >(tee -a "$LOG_FILE") 2>&1\n\n';

    // Add model variable if configured
    if (resolvedModel) {
      script += '# Model Configuration (resolved from hierarchy)\n';
      script += `MODEL="${resolvedModel}"\n\n`;
    }

    // Add feature context as variables
    script += '# Feature Context\n';
    if (feature.recipe?.context) {
      for (const [key, value] of Object.entries(feature.recipe.context)) {
        const varName = key.toUpperCase();
        const escapedValue = (value as string).replace(/"/g, '\\"');
        script += `${varName}="${escapedValue}"\n`;
      }
    }
    script += '\n';

    // Add recipe variables (with feature context as defaults)
    if (recipe.variables) {
      script += '# Variables\n';
      for (const [key, value] of Object.entries(recipe.variables)) {
        const varName = key.toUpperCase();
        let varValue = (value as string).replace(
          /{{([^}]+)}}/g,
          (_, v) => `\${${v.toUpperCase()}}`
        );

        // If feature context provides this variable, use it as default
        if (feature.recipe?.context && feature.recipe.context[key.toLowerCase()]) {
          varValue = `\${${varName}}`;
        }

        script += `: \${${varName}:="${varValue}"}\n`;
      }
      script += '\n';
    }

    // Separate steps into pre-loop, loop, and post-loop
    const loopStepIds = recipe.loop?.steps || [];
    const loopStepIndices = loopStepIds.map((id: string) =>
      recipe.steps.findIndex((s: any) => s.id === id)
    );

    const preLoopSteps = recipe.steps.filter(
      (_: any, i: number) => loopStepIndices.length === 0 || i < Math.min(...loopStepIndices)
    );
    const loopSteps = recipe.steps.filter((_: any, i: number) => loopStepIndices.includes(i));
    const postLoopSteps = recipe.steps.filter(
      (_: any, i: number) => loopStepIndices.length > 0 && i > Math.max(...loopStepIndices)
    );

    const conversationStrategy = recipe.conversationStrategy || 'separate';
    const toolOptions = recipe.toolOptions;

    // Generate pre-loop steps
    for (let i = 0; i < preLoopSteps.length; i++) {
      script += await ProjectGenerator.generateStepScriptStatic(
        preLoopSteps[i],
        i,
        tool,
        recipe.variables,
        conversationStrategy,
        toolOptions,
        recipe
      );
    }

    // Generate loop if defined
    if (recipe.loop && loopSteps.length > 0) {
      const maxIterations = recipe.loop.maxIterations || 3;

      script += `# Loop: ${loopStepIds.join(' → ')} (max ${maxIterations} iterations)\n`;
      script += `for iteration in $(seq 1 ${maxIterations}); do\n`;
      script += `  echo "\\n▶️  Iteration $iteration/${maxIterations}"\n`;
      script += `  \n`;

      for (let i = 0; i < loopSteps.length; i++) {
        const stepScript = await ProjectGenerator.generateStepScriptStatic(
          loopSteps[i],
          i,
          tool,
          recipe.variables,
          conversationStrategy,
          toolOptions,
          recipe
        );
        // Indent loop content
        script += stepScript
          .split('\n')
          .map((line: string) => (line ? `  ${line}` : line))
          .join('\n');
      }

      script += `done\n\n`;
    }

    // Generate post-loop steps
    const baseStepNum = preLoopSteps.length + (recipe.loop ? 1 : 0);
    for (let i = 0; i < postLoopSteps.length; i++) {
      script += await ProjectGenerator.generateStepScriptStatic(
        postLoopSteps[i],
        baseStepNum + i,
        tool,
        recipe.variables,
        conversationStrategy,
        toolOptions,
        recipe
      );
    }

    script += `echo "✅ Feature '${feature.name}' workflow completed!"\n`;
    return script;
  }
}

// CLI
if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2);
  const projectId = args[0];

  if (!projectId) {
    console.error(chalk.red('Error: Project ID required'));
    console.log('\nUsage: npm run project:generate-features <project-id>');
    process.exit(1);
  }

  const generator = new FeatureGenerator();
  generator.generateFeatures(projectId).catch((error) => {
    console.error(chalk.red('\n❌ Error:'), error.message);
    process.exit(1);
  });
}
