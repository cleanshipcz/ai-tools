import { ToolAdapter } from '../base.js';
import { Project, Agent } from '../../core/models/types.js';
import { ConfigService } from '../../core/services/config.service.js';
import { LoaderService } from '../../core/services/loader.service.js';
import { ResolverService } from '../../core/services/resolver.service.js';
import { RecipeService } from '../../core/services/recipe.service.js';
import { join } from 'path';
import { mkdir, writeFile, readFile, access } from 'fs/promises';

export class CursorAdapter extends ToolAdapter {
  name = 'cursor';
  private config = ConfigService.getInstance();
  private loader = new LoaderService();
  private resolver = new ResolverService();
  private recipeService = new RecipeService();

  async generate(project: Project, outputDir: string): Promise<void> {
    const cursorDir = join(outputDir, '.cursor');
    await mkdir(cursorDir, { recursive: true });

    // 1. Generate recipes.json from agents
    // This replaces the dependency on pre-built 'adapters' directory
    const agentsDir = this.config.getPath(this.config.dirs.agents);
    try {
      const agentFiles = await this.loader.findYamlFiles(agentsDir);
      const recipes: any[] = [];
      
      for (const file of agentFiles) {
        const agent = await this.loader.loadYaml<Agent>(file);
        
        if (this.resolver.shouldIncludeAgent(agent.id, project)) {
           // Convert Agent to Cursor Recipe format
           // Logic adapted from legacy build.ts
           const recipe = {
             id: agent.id,
             name: agent.id, // Cursor uses name as ID often
             description: agent.description || agent.purpose,
             prompt: agent.prompt?.system || agent.purpose,
             // Add other fields if needed
           };
           recipes.push(recipe);
        }
      }
      
      await writeFile(join(cursorDir, 'recipes.json'), JSON.stringify({ recipes }, null, 2));
      
    } catch {}

    // 2. Generate project-rules.json
    const rules = await this.buildProjectRules(project);
    const config = {
      rules: rules,
      context: {
        name: project.name,
        description: project.description,
      },
    };

    await writeFile(join(cursorDir, 'project-rules.json'), JSON.stringify(config, null, 2));

    // 3. Generate recipes
    await this.recipeService.generateRecipesForTool(this.name, outputDir, project);
  }

  async generateGlobal(outputDir: string): Promise<void> {
    const cursorDir = join(outputDir, '.cursor');
    await mkdir(cursorDir, { recursive: true });

    // Copy base recipes.json without filtering
    const adapterSrcDir = this.config.getPath(this.config.dirs.adapters, 'cursor');
    try {
      await access(adapterSrcDir);
      const recipesFile = join(adapterSrcDir, 'recipes.json');
      const recipesContent = await readFile(recipesFile, 'utf-8');
      await writeFile(join(cursorDir, 'recipes.json'), recipesContent);
    } catch {
      // Ignore
    }
  }

  private async buildProjectRules(project: Project): Promise<string[]> {
    const rules: string[] = [];

    // Add conventions
    if (project.conventions) {
      if (project.conventions.naming) rules.push(...project.conventions.naming);
      if (project.conventions.patterns) rules.push(...project.conventions.patterns);
      if (project.conventions.testing) rules.push(...project.conventions.testing);
      if (project.conventions.structure) rules.push(...project.conventions.structure);
      if (project.conventions.custom) rules.push(...project.conventions.custom);
    }

    // Add AI tools preferences
    if (project.ai_tools) {
      if (project.ai_tools.preferred_rulepacks) {
        const resolvedRules = await this.resolver.resolveRulepacks(project.ai_tools.preferred_rulepacks, project);
        rules.push(...resolvedRules);
      }

      if (project.ai_tools.custom_rules) {
        rules.push(...project.ai_tools.custom_rules);
      }
    }

    return rules;
  }
}
