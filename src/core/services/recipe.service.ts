import { ConfigService } from './config.service.js';
import { LoaderService } from './loader.service.js';
import { ResolverService } from './resolver.service.js';
import { Recipe, RecipeStep, AIModel, Project } from '../models/types.js';
import { join } from 'path';
import { mkdir, writeFile, readFile, readdir } from 'fs/promises';

export class RecipeService {
  private config = ConfigService.getInstance();
  private loader = new LoaderService();
  private resolver = new ResolverService();

  async generateRecipesForTool(tool: string, outputDir: string, project?: Project): Promise<void> {
    // Only generate recipes for CLI-based tools that support automated workflows
    const supportedTools = ['claude-code', 'copilot-cli', 'cursor', 'windsurf', 'github-copilot'];
    if (!supportedTools.includes(tool)) {
      return;
    }

    const recipesDir = this.config.getPath(this.config.dirs.recipes);
    let recipeFiles: string[];
    try {
      recipeFiles = await this.loader.findYamlFilesRelative(recipesDir);
    } catch {
      return;
    }

    if (recipeFiles.length === 0) {
      return;
    }

    // Determine output directory based on tool
    let recipesOutputDir: string;
    if (tool === 'claude-code') {
      recipesOutputDir = join(outputDir, '.claude', '.cs.recipes');
    } else if (tool === 'copilot-cli') {
      recipesOutputDir = join(outputDir, '.cs.recipes');
    } else if (tool === 'cursor') {
      recipesOutputDir = join(outputDir, '.cursor', '.cs.recipes');
    } else if (tool === 'github-copilot') {
      recipesOutputDir = join(outputDir, '.github', '.cs.recipes');
    } else if (tool === 'windsurf') {
      recipesOutputDir = join(outputDir, '.windsurf', '.cs.recipes');
    } else {
      recipesOutputDir = join(outputDir, '.cs.recipes');
    }

    await mkdir(recipesOutputDir, { recursive: true });

    // Helper to generate recipes for a specific context
    const generateRecipesForContext = async (suffix: string = '', context?: { languages?: string[] }) => {
      for (const recipeFile of recipeFiles) {
        const recipeId = recipeFile.replace(/\.ya?ml$/, '').replace(/\\/g, '/').split('/').pop()!; // Use basename for ID in filename

        // Filter recipes based on project config
        if (project && !this.resolver.shouldIncludeRecipe(recipeId, project)) {
          continue;
        }

        const fullPath = join(recipesDir, recipeFile);
        const recipe = await this.loader.loadYaml<Recipe>(fullPath);

        // Skip if recipe doesn't support this tool
        if (recipe.tools && Array.isArray(recipe.tools) && !recipe.tools.includes(tool)) {
          continue;
        }
        
        // If context is provided, we might want to filter recipes?
        // Or just generate them with the suffix and let the script load the correct context.
        // For now, we generate all included recipes for each stack, appending suffix.

        const scriptPath = join(recipesOutputDir, `${recipeId}${suffix}.sh`);
        await this.generateRecipeScript(recipe, tool, scriptPath, suffix);
      }
    };

    // Generate global recipes
    await generateRecipesForContext();

    // Generate stack-specific recipes
    if (project?.tech_stacks) {
      for (const [stackName, stackContext] of Object.entries(project.tech_stacks)) {
        await generateRecipesForContext(`-${stackName}`, stackContext);
      }
    }
  }

  async listRecipes(): Promise<Recipe[]> {
    const recipesDir = this.config.getPath(this.config.dirs.recipes);
    const recipes: Recipe[] = [];
    try {
      const files = await this.loader.findYamlFiles(recipesDir);
      for (const file of files) {
        const recipe = await this.loader.loadYaml<Recipe>(file);
        recipes.push(recipe);
      }
    } catch (error) {
      // Ignore
    }
    return recipes;
  }

  async loadRecipe(id: string): Promise<Recipe | null> {
    const recipesDir = this.config.getPath(this.config.dirs.recipes);
    try {
      const files = await this.loader.findYamlFiles(recipesDir);
      for (const file of files) {
        const recipe = await this.loader.loadYaml<Recipe>(file);
        if (recipe.id === id) return recipe;
      }
    } catch (error) {
      // Ignore
    }
    return null;
  }

  async generateRecipeScript(recipe: Recipe, tool: string, outputPath: string, suffix: string = ''): Promise<void> {
    // Ensure output directory exists
    const { dirname } = await import('path');
    await mkdir(dirname(outputPath), { recursive: true });

    let script = '#!/bin/bash\n\n';
    script += `# Recipe: ${recipe.id}${suffix}\n`;
    script += `# Description: ${recipe.description}\n`;
    script += `# Tool: ${tool}\n\n`;

    script += 'set -e\n\n';

    // Common functions
    script += 'function load_project_context() {\n';
    script += `  if [ -f ".claude/project-context${suffix}.json" ]; then\n`;
    script += `    PROJECT_CONTEXT=$(cat .claude/project-context${suffix}.json)\n`;
    script += `  elif [ -f ".cursor/project-rules${suffix}.json" ]; then\n`;
    script += `    PROJECT_CONTEXT=$(cat .cursor/project-rules${suffix}.json)\n`;
    script += `  elif [ -f ".windsurf/rules/project-context${suffix}.md" ]; then\n`;
    script += `    PROJECT_CONTEXT=$(cat .windsurf/rules/project-context${suffix}.md)\n`;
    // Fallback to global if specific not found
    script += '  elif [ -f ".claude/project-context.json" ]; then\n';
    script += '    PROJECT_CONTEXT=$(cat .claude/project-context.json)\n';
    script += '  elif [ -f ".cursor/project-rules.json" ]; then\n';
    script += '    PROJECT_CONTEXT=$(cat .cursor/project-rules.json)\n';
    script += '  elif [ -f ".windsurf/rules/project-context.md" ]; then\n';
    script += '    PROJECT_CONTEXT=$(cat .windsurf/rules/project-context.md)\n';
    script += '  fi\n';
    script += '}\n\n';

    script += '# Load project context for system prompts\n';
    script += 'echo "📋 Loading project context..."\n';
    script += 'load_project_context\n';
    script += 'if [ -n "$PROJECT_CONTEXT" ]; then\n';
    script += '  echo "✓ Project context loaded"\n';
    script += 'else\n';
    script += '  echo "⚠️  No project context found"\n';
    script += 'fi\n';
    script += 'echo ""\n\n';

    // Variables
    if (recipe.variables) {
      script += '# Variables\n';
      for (const [key, value] of Object.entries(recipe.variables)) {
        const varName = key.toUpperCase();
        // Replace {{VAR}} with ${VAR}
        let varValue = (value as string).replace(
          /{{([^}]+)}}/g,
          (_, v) => `\${${v.toUpperCase()}}`
        );
        script += `: \${${varName}:="${varValue}"}\n`;
      }
      script += '\n';
    }

    // Steps logic
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

    // Pre-loop
    for (let i = 0; i < preLoopSteps.length; i++) {
      script += await this.generateStepScript(
        preLoopSteps[i],
        i,
        tool,
        recipe.variables,
        conversationStrategy,
        toolOptions,
        recipe
      );
    }

    // Loop
    if (recipe.loop && loopSteps.length > 0) {
      const maxIterations = recipe.loop.maxIterations || 3;

      script += `# Loop: ${loopStepIds.join(' → ')} (max ${maxIterations} iterations)\n`;
      script += `for iteration in $(seq 1 ${maxIterations}); do\n`;
      script += `  echo "\\n▶️  Iteration $iteration/${maxIterations}"\n`;
      script += `  \n`;

      for (let i = 0; i < loopSteps.length; i++) {
        const stepScript = await this.generateStepScript(
          loopSteps[i],
          i, // Index relative to loop? No, unique ID needed?
          // Actually the index is just for logging.
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

    // Post-loop
    const baseStepNum = preLoopSteps.length + (recipe.loop ? 1 : 0);
    for (let i = 0; i < postLoopSteps.length; i++) {
      script += await this.generateStepScript(
        postLoopSteps[i],
        baseStepNum + i,
        tool,
        recipe.variables,
        conversationStrategy,
        toolOptions,
        recipe
      );
    }

    script += 'echo "✅ Recipe completed!"\n';

    await writeFile(outputPath, script, { mode: 0o755 });
  }

  public async generateStepScript(
    step: RecipeStep,
    index: number,
    tool: string,
    variables?: Record<string, string>,
    conversationStrategy?: string,
    toolOptions?: Record<string, any>,
    recipe?: Recipe
  ): Promise<string> {
    let script = '';
    script += `# Step ${index + 1}: ${step.task}\n`;
    script += `echo "👉 Step ${index + 1}: ${step.task}"\n`;

    // Condition check
    if (step.condition) {
      if (step.condition.type === 'file_exists' && step.condition.check?.value) {
        script += `if [ -f "${step.condition.check.value}" ]; then\n`;
        script += `  echo "  (Skipping: File ${step.condition.check.value} exists)"\n`;
        script += `else\n`;
      }
    }

    // Build the task string
    let task = step.task;
    if (variables) {
      for (const key of Object.keys(variables)) {
        const regex = new RegExp(`{{${key}}}`, 'g');
        task = task.replace(regex, `\${${key.toUpperCase()}}`);
      }
    }

    // Tool specific command generation
    if (tool === 'claude-code') {
      // Claude Code CLI
      let flags = '';
      if (step.agent) {
        // Bug fix: Use --system-prompt instead of --agent if we can resolve the agent's prompt
        // But here we just have agent ID.
        // Ideally we should load the agent and get its system prompt.
        // For now, let's assume we can pass agent ID if the tool supported it, but Claude doesn't.
        // We need to fetch the agent system prompt.
        const agent = await this.loadAgent(step.agent);
        if (agent?.prompt?.system) {
            // Escape quotes
            const systemPrompt = agent.prompt.system.replace(/"/g, '\\"');
            flags += ` --system-prompt "${systemPrompt}"`;
        }
      }
      
      // Add model if specified
      const model = step.model || recipe?.model;
      if (model) {
          // Map internal model names to Claude CLI model names if needed
          // For now assume they match or are compatible
          // flags += ` --model ${model}`; 
          // Claude CLI might not support all model flags directly or uses different names.
      }

      // Escape task
      const escapedTask = task.replace(/"/g, '\\"');
      script += `claude ${flags} -p "${escapedTask}"\n`;

    } else if (tool === 'copilot-cli') {
      // Copilot CLI
      let flags = '';
      // ... (Porting logic from gen-project.ts) ...
      // Simplified for now:
      const escapedTask = task.replace(/"/g, '\\"');
      script += `echo "${escapedTask}" | copilot explain\n`; // Example, real usage depends on 'copilot' command
    } else if (tool === 'windsurf') {
      // Windsurf (IDE) - Print task for user to execute manually or via IDE features
      script += `echo "  (Execute this step in Windsurf)"\n`;
    } else {
      // Generic or other tools
      script += `echo "  (Tool ${tool} not fully supported for auto-execution)"\n`;
    }

    if (step.condition) {
      script += `fi\n`;
    }

    script += `echo ""\n`;
    return script;
  }

  public async loadAgent(agentId: string) {
      const agentsDir = this.config.getPath(this.config.dirs.agents);
      const files = await this.loader.findYamlFiles(agentsDir);
      for (const file of files) {
          const agent = await this.loader.loadYaml<any>(file); // Use any to avoid circular dep on Agent type if not exported fully or just for simplicity
          if (agent.id === agentId) return agent;
      }
      return null;
  }
}
