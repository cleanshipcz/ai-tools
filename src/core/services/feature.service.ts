import { join } from 'path';
import { mkdir, writeFile, access } from 'fs/promises';
import { ConfigService } from './config.service.js';
import { LoaderService } from './loader.service.js';
import { RecipeService } from './recipe.service.js';
import { Feature, Project, AIModel, Recipe } from '../models/types.js';
import chalk from 'chalk';

export class FeatureService {
  private config = ConfigService.getInstance();
  private loader = new LoaderService();
  private recipeService = new RecipeService();
  private project?: Project;
  private agentsMap = new Map<string, any>();

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
    const outputDir = join(this.config.getPath(this.config.dirs.output), projectId, 'features');
    await mkdir(outputDir, { recursive: true });

    await this.generateGitHubCopilotFeatures(features, outputDir);
    await this.generateWindsurfFeatures(features, outputDir);
    await this.generateClaudeCodeFeatures(features, outputDir);
    await this.generateCursorFeatures(features, outputDir);

    // Generate feature-bound recipe scripts
    await this.generateFeatureRecipes(projectId, features);

    console.log(chalk.green(`\n✅ Generated feature snippets in: ${outputDir}\n`));
  }

  private async findProjectDir(projectId: string): Promise<string | null> {
    const projectSources = await this.config.getProjectSources();

    for (const source of projectSources) {
      const projectDir = join(source, projectId);
      try {
        await access(projectDir);
        return projectDir;
      } catch {
        continue;
      }
    }

    return null;
  }

  private async loadProject(projectDir: string): Promise<void> {
    const projectPath = join(projectDir, 'project.yml');
    try {
      this.project = await this.loader.loadYaml<Project>(projectPath);
    } catch (error) {
      console.log(chalk.yellow(`  Warning: Could not load project.yml`));
    }
  }

  private async loadAgents(): Promise<void> {
    const agentsDir = this.config.getPath(this.config.dirs.agents);
    try {
      const files = await this.loader.findYamlFiles(agentsDir);
      for (const file of files) {
        const agent = await this.loader.loadYaml<any>(file);
        this.agentsMap.set(agent.id, agent);
      }
    } catch (error) {
      // Ignore if agents dir doesn't exist
    }
  }

  private resolveModel(feature: Feature, recipeStepAgent?: string): AIModel | undefined {
    // Priority: Feature > Project > Agent > Prompt
    if (feature.model) {
      return feature.model;
    }

    if (this.project?.ai_tools?.model) {
      return this.project.ai_tools.model;
    }

    if (recipeStepAgent) {
      const agent = this.agentsMap.get(recipeStepAgent);
      if (agent?.defaults?.model) {
        return agent.defaults.model;
      }
    }

    return undefined;
  }

  private async loadFeatures(featuresDir: string): Promise<Feature[]> {
    const features: Feature[] = [];
    try {
      // Use readdir to match legacy structure (iterating subdirectories)
      const { readdir } = await import('fs/promises');
      const entries = await readdir(featuresDir, { withFileTypes: true });

      for (const entry of entries) {
        if (entry.isDirectory()) {
          const featurePath = join(featuresDir, entry.name, 'feature.yml');
          try {
            await access(featurePath);
            const feature = await this.loader.loadYaml<Feature>(featurePath);
            features.push(feature);
            console.log(chalk.gray(`    Loaded: ${feature.name}`));
          } catch {
            // No feature.yml
          }
        }
      }
    } catch (error) {
      // Error reading dir
    }

    return features;
  }

  private async generateGitHubCopilotFeatures(features: Feature[], outputDir: string): Promise<void> {
    const copilotDir = join(outputDir, 'github-copilot');
    await mkdir(copilotDir, { recursive: true });

    for (const feature of features) {
      const content: string[] = [];

      content.push(`# Feature: ${feature.name}`);
      content.push('');
      content.push(feature.description);
      content.push('');

      if (feature.model) {
        content.push(`**Default Model for this Feature:** ${feature.model}`);
        content.push('');
        content.push('*This is the highest priority model setting, overriding project and agent defaults.*');
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

      if (feature.conventions?.length) {
        content.push('## Conventions');
        content.push('');
        for (const convention of feature.conventions) {
          content.push(`- ${convention}`);
        }
        content.push('');
      }

      await writeFile(join(copilotDir, `feature-${feature.id}.md`), content.join('\n'), 'utf-8');
    }
    console.log(chalk.gray(`    Generated ${features.length} GitHub Copilot feature files`));
  }

  private async generateWindsurfFeatures(features: Feature[], outputDir: string): Promise<void> {
    const windsurfDir = join(outputDir, '.windsurf', 'workflows');
    await mkdir(windsurfDir, { recursive: true });

    for (const feature of features) {
      const content: string[] = [];

      content.push('---');
      content.push(`description: ${feature.description}`);
      content.push('auto_execution_mode: 3');
      content.push('---');
      content.push('');

      content.push(`# Feature: ${feature.name}`);
      content.push('');
      content.push(feature.description);
      content.push('');

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

      if (feature.context?.dependencies && feature.context.dependencies.length > 0) {
        content.push('## Dependencies');
        content.push('');
        for (const dep of feature.context.dependencies) {
          content.push(`- ${dep}`);
        }
        content.push('');
      }

      if (feature.conventions && feature.conventions.length > 0) {
        content.push('## Conventions');
        content.push('');
        for (const convention of feature.conventions) {
          content.push(`- ${convention}`);
        }
        content.push('');
      }

      if (feature.recipe?.context?.feature_description) {
        content.push('## Implementation Steps');
        content.push('');
        content.push(feature.recipe.context.feature_description);
        content.push('');
      }

      if (feature.recipe?.context?.acceptance_criteria) {
        content.push('## Acceptance Criteria');
        content.push('');
        content.push(feature.recipe.context.acceptance_criteria);
        content.push('');
      }

      await writeFile(join(windsurfDir, `feature-${feature.id}.md`), content.join('\n'), 'utf-8');
    }
    console.log(chalk.gray(`    Generated ${features.length} Windsurf workflow files`));
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
    }));

    await writeFile(
      join(cursorDir, 'features.json'),
      JSON.stringify({ features: allFeatures }, null, 2),
      'utf-8'
    );
    console.log(chalk.gray(`    Generated Cursor features.json`));
  }

  private async generateFeatureRecipes(projectId: string, features: Feature[]): Promise<void> {
    const featuresWithRecipes = features.filter((f) => f.recipe?.id);

    if (featuresWithRecipes.length === 0) {
      return;
    }

    console.log(chalk.blue(`\n  Generating feature-bound recipes...`));

    const recipesDir = this.config.getPath(this.config.dirs.recipes);

    for (const feature of featuresWithRecipes) {
      const recipeId = feature.recipe!.id;
      const recipePath = join(recipesDir, `${recipeId}.yml`);

      try {
        const recipe = await this.loader.loadYaml<Recipe>(recipePath);

        const tools = feature.recipe!.tools || recipe.tools || ['claude-code', 'copilot-cli', 'cursor'];

        for (const tool of tools) {
          if (recipe.tools && Array.isArray(recipe.tools) && !recipe.tools.includes(tool)) {
            continue;
          }

          const toolOutputDir = join(this.config.getPath(this.config.dirs.output), projectId, tool);

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
        console.log(chalk.yellow(`    ! Could not generate recipe for feature ${feature.name}: ${error.message}`));
      }
    }
  }

  private async generateFeatureRecipeScript(
    feature: Feature,
    recipe: Recipe,
    tool: string,
    scriptPath: string
  ): Promise<void> {
    const script = await this.buildFeatureRecipeScript(feature, recipe, tool);
    await writeFile(scriptPath, script, { mode: 0o755 });
  }

  private async buildFeatureRecipeScript(
    feature: Feature,
    recipe: Recipe,
    tool: string
  ): Promise<string> {
    const resolvedModel = this.resolveModel(feature);

    let script = '#!/bin/bash\n';
    script += `# Feature-bound recipe script\n`;
    script += `# Feature: ${feature.name}\n`;
    script += `# Recipe: ${recipe.id}\n`;
    script += `# Tool: ${tool}\n`;

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

    script += '# Setup directories\n';
    script += 'RECIPE_DOCS_DIR=".recipe-docs"\n';
    script += 'mkdir -p "$RECIPE_DOCS_DIR"\n';
    script += 'RECIPE_LOGS_DIR=".recipe-logs"\n';
    script += 'mkdir -p "$RECIPE_LOGS_DIR"\n';
    script += 'echo "📁 Documents: $RECIPE_DOCS_DIR"\n\n';

    script += '# Setup logging\n';
    script += `LOG_FILE="$RECIPE_LOGS_DIR/feature-${feature.id}-$(date +%Y%m%d-%H%M%S).log"\n`;
    script += 'echo "📝 Logging to: $LOG_FILE"\n';
    script += 'echo ""\n\n';

    script += '# Redirect all output to both console and log file\n';
    script += 'exec > >(tee -a "$LOG_FILE") 2>&1\n\n';

    if (resolvedModel) {
      script += '# Model Configuration (resolved from hierarchy)\n';
      script += `MODEL="${resolvedModel}"\n\n`;
    }

    script += '# Feature Context\n';
    if (feature.recipe?.context) {
      for (const [key, value] of Object.entries(feature.recipe.context)) {
        const varName = key.toUpperCase();
        const escapedValue = (value as string).replace(/"/g, '\\"');
        script += `${varName}="${escapedValue}"\n`;
      }
    }
    script += '\n';

    if (recipe.variables) {
      script += '# Variables\n';
      for (const [key, value] of Object.entries(recipe.variables)) {
        const varName = key.toUpperCase();
        let varValue = (value as string).replace(
          /{{([^}]+)}}/g,
          (_, v) => `\${${v.toUpperCase()}}`
        );

        if (feature.recipe?.context && feature.recipe.context[key.toLowerCase()]) {
          varValue = `\${${varName}}`;
        }

        script += `: \${${varName}:="${varValue}"}\n`;
      }
      script += '\n';
    }

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

    for (let i = 0; i < preLoopSteps.length; i++) {
      script += await this.recipeService.generateStepScript(
        preLoopSteps[i],
        i,
        tool,
        recipe.variables,
        conversationStrategy,
        toolOptions,
        recipe
      );
    }

    if (recipe.loop && loopSteps.length > 0) {
      const maxIterations = recipe.loop.maxIterations || 3;

      script += `# Loop: ${loopStepIds.join(' → ')} (max ${maxIterations} iterations)\n`;
      script += `for iteration in $(seq 1 ${maxIterations}); do\n`;
      script += `  echo "\\n▶️  Iteration $iteration/${maxIterations}"\n`;
      script += `  \n`;

      for (let i = 0; i < loopSteps.length; i++) {
        const stepScript = await this.recipeService.generateStepScript(
          loopSteps[i],
          i,
          tool,
          recipe.variables,
          conversationStrategy,
          toolOptions,
          recipe
        );
        script += stepScript
          .split('\n')
          .map((line: string) => (line ? `  ${line}` : line))
          .join('\n');
      }

      script += `done\n\n`;
    }

    const baseStepNum = preLoopSteps.length + (recipe.loop ? 1 : 0);
    for (let i = 0; i < postLoopSteps.length; i++) {
      script += await this.recipeService.generateStepScript(
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
