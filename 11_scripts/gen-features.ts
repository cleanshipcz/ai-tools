#!/usr/bin/env node
import { readFile, writeFile, mkdir, readdir, access } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface Feature {
  id: string;
  version: string;
  name: string;
  description: string;
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

export class FeatureGenerator {
  async generateFeatures(projectId: string): Promise<void> {
    console.log(chalk.blue(`\n🎯 Generating features for project: ${projectId}\n`));

    // Find project directory
    const projectDir = await this.findProjectDir(projectId);
    if (!projectDir) {
      throw new Error(`Project not found: ${projectId}`);
    }

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
    let projectDir = join(rootDir, 'projects', 'global', projectId);
    try {
      await access(projectDir);
      return projectDir;
    } catch {
      // Try local
      projectDir = join(rootDir, 'projects', 'local', projectId);
      try {
        await access(projectDir);
        return projectDir;
      } catch {
        return null;
      }
    }
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
    const script = this.buildFeatureRecipeScript(feature, recipe, tool);
    await writeFile(scriptPath, script, { mode: 0o755 });
  }

  private buildFeatureRecipeScript(feature: Feature, recipe: any, tool: string): string {
    let script = '#!/bin/bash\n';
    script += `# Feature-bound recipe script\n`;
    script += `# Feature: ${feature.name}\n`;
    script += `# Recipe: ${recipe.id}\n`;
    script += `# Tool: ${tool}\n`;
    script += `# Generated: ${new Date().toISOString()}\n\n`;
    script += 'set -e  # Exit on error\n\n';

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
      script += this.generateStepScript(
        preLoopSteps[i],
        i,
        tool,
        recipe.variables,
        conversationStrategy,
        toolOptions
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
        const stepScript = this.generateStepScript(
          loopSteps[i],
          i,
          tool,
          recipe.variables,
          conversationStrategy,
          toolOptions
        );
        // Indent loop content
        script += stepScript
          .split('\n')
          .map((line) => (line ? `  ${line}` : line))
          .join('\n');
      }

      script += `done\n\n`;
    }

    // Generate post-loop steps
    const baseStepNum = preLoopSteps.length + (recipe.loop ? 1 : 0);
    for (let i = 0; i < postLoopSteps.length; i++) {
      script += this.generateStepScript(
        postLoopSteps[i],
        baseStepNum + i,
        tool,
        recipe.variables,
        conversationStrategy,
        toolOptions
      );
    }

    script += `echo "✅ Feature '${feature.name}' workflow completed!"\n`;
    return script;
  }

  private generateStepScript(
    step: any,
    index: number,
    tool: string,
    variables: any,
    conversationStrategy: string = 'separate',
    toolOptions?: any
  ): string {
    let script = '';

    script += `# Step: ${step.id}\n`;
    script += `echo "▶️  ${step.id} (${step.agent})"\n`;

    // Interpolate variables in task
    let task = step.task;
    if (variables) {
      for (const key of Object.keys(variables)) {
        task = task.replace(new RegExp(`{{${key}}}`, 'g'), `\${${key.toUpperCase()}}`);
      }
    }

    // Determine if should continue conversation based on strategy
    const shouldContinue =
      conversationStrategy === 'continue' && step.continueConversation !== false && index > 0;

    // Generate tool-specific command with response capture
    if (tool === 'claude-code') {
      const continueFlag = shouldContinue ? '-c $CONVERSATION_ID' : '';
      script += `RESPONSE=$(claude ${continueFlag} --agent ${step.agent} "${task.replace(/"/g, '\\"')}")\n`;
      script += `echo "$RESPONSE"\n`;
      if (index === 0 && conversationStrategy === 'continue') {
        script += `CONVERSATION_ID=$(echo "$RESPONSE" | grep -oP 'Conversation ID: \\K[a-zA-Z0-9-]+')\n`;
      }
    } else if (tool === 'copilot-cli') {
      const flags: string[] = [];

      if (shouldContinue) {
        flags.push('--continue');
      }

      // Add tool-specific options
      if (toolOptions?.['copilot-cli']) {
        const opts = toolOptions['copilot-cli'];
        if (opts.allowAllTools) flags.push('--allow-all-tools');
        if (opts.allowAllPaths) flags.push('--allow-all-paths');
        if (opts.disallowTempDir) flags.push('--disallow-temp-dir');
        if (opts.addDirs) {
          opts.addDirs.forEach((dir: string) => flags.push(`--add-dir "${dir}"`));
        }
        if (opts.allowTools) {
          opts.allowTools.forEach((tool: string) => flags.push(`--allow-tool "${tool}"`));
        }
        if (opts.denyTools) {
          opts.denyTools.forEach((tool: string) => flags.push(`--deny-tool "${tool}"`));
        }
      }

      const flagsStr = flags.length > 0 ? ' ' + flags.join(' ') : '';
      script += `RESPONSE=$(echo "@${step.agent} ${task}" | copilot${flagsStr})\n`;
      script += `echo "$RESPONSE"\n`;
    } else if (tool === 'cursor') {
      script += `# Manual: Open Cursor Composer and execute:\n`;
      script += `# @${step.agent} ${task}\n`;
      script += `echo "⚠️  Please execute in Cursor Composer and press Enter"\n`;
      script += `read -p "Continue? "\n`;
      script += `RESPONSE=""\n`;
    }

    // Handle conditional execution
    if (step.condition) {
      if (step.condition.type === 'on-success' && step.condition.check) {
        const check = step.condition.check;
        if (check.type === 'contains' && check.value) {
          script += `\n# Check condition\n`;
          script += `if [[ ! "$RESPONSE" == *"${check.value}"* ]]; then\n`;
          script += `  echo "⚠️  Condition not met (expected: ${check.value})"\n`;
          script += `  exit 1\n`;
          script += `fi\n`;
        }
      }
    }

    script += '\n';
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
