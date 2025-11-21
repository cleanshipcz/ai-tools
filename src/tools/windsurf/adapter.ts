import { ToolAdapter } from '../base.js';
import { Project, Agent, Prompt } from '../../core/models/types.js';
import { ConfigService } from '../../core/services/config.service.js';
import { LoaderService } from '../../core/services/loader.service.js';
import { ResolverService } from '../../core/services/resolver.service.js';
import { RecipeService } from '../../core/services/recipe.service.js';
import { join } from 'path';
import { mkdir, writeFile, copyFile, readdir } from 'fs/promises';

export class WindsurfAdapter extends ToolAdapter {
  name = 'windsurf';
  private config = ConfigService.getInstance();
  private loader = new LoaderService();
  private resolver = new ResolverService();
  private recipeService = new RecipeService();

  async generate(project: Project, outputDir: string): Promise<void> {
    const windsurfDir = join(outputDir, '.windsurf');
    const rulesDir = join(windsurfDir, 'rules');
    await mkdir(rulesDir, { recursive: true });

    // 1. Generate project-context.md
    await this.generateProjectContext(project, rulesDir);

    // 2. Copy and filter agents/prompts/rulepacks
    // This logic mirrors copyDirectoryWithFiltering but adapted for Windsurf structure
    // Windsurf uses flat structure in .windsurf/rules
    
    // Load all agents and filter
    const agentsDir = this.config.getPath(this.config.dirs.agents);
    const agentFiles = await this.loader.findYamlFiles(agentsDir);
    
    for (const file of agentFiles) {
      const agent = await this.loader.loadYaml<Agent>(file);
      if (this.resolver.shouldIncludeAgent(agent.id, project)) {
        await this.generateAgentRule(agent, rulesDir);
      }
    }

    // Load all prompts and filter
    const promptsDir = this.config.getPath(this.config.dirs.prompts);
    // We need the map to check whitelist against paths
    const promptsMap = new Map<string, string>();
    const promptFiles = await this.loader.findYamlFilesRelative(promptsDir);
    
    for (const relativePath of promptFiles) {
        const fullPath = join(promptsDir, relativePath);
        const prompt = await this.loader.loadYaml<Prompt>(fullPath);
        const pathWithoutExt = relativePath.replace(/\.ya?ml$/, '').replace(/\\/g, '/');
        promptsMap.set(prompt.id, pathWithoutExt);
    }

    for (const relativePath of promptFiles) {
      const fullPath = join(promptsDir, relativePath);
      const prompt = await this.loader.loadYaml<Prompt>(fullPath);
      
      if (this.resolver.shouldIncludePrompt(prompt.id, promptsMap, project)) {
        const pathWithoutExt = relativePath.replace(/\.ya?ml$/, '');
        // Convert path separators to hyphens for the filename
        // e.g. docs/document-api -> prompt-docs-document-api.md
        const filename = `prompt-${pathWithoutExt.split(/[/\\]/).join('-')}.md`;
        await this.generatePromptRule(prompt, rulesDir, filename);
      }
    }

    // 3. Generate recipes
    await this.recipeService.generateRecipesForTool(this.name, outputDir, project);
  }

  async generateGlobal(outputDir: string): Promise<void> {
    const rulesDir = join(outputDir, 'rules');
    await mkdir(rulesDir, { recursive: true });

    // Load all agents
    const agentsDir = this.config.getPath(this.config.dirs.agents);
    const agentFiles = await this.loader.findYamlFiles(agentsDir);
    for (const file of agentFiles) {
      const agent = await this.loader.loadYaml<Agent>(file);
      await this.generateAgentRule(agent, rulesDir);
    }

    // Load all prompts
    const promptsDir = this.config.getPath(this.config.dirs.prompts);
    const promptFiles = await this.loader.findYamlFilesRelative(promptsDir);
    for (const relativePath of promptFiles) {
      const fullPath = join(promptsDir, relativePath);
      const prompt = await this.loader.loadYaml<Prompt>(fullPath);
      
      const pathWithoutExt = relativePath.replace(/\.ya?ml$/, '');
      const filename = `prompt-${pathWithoutExt.split(/[/\\]/).join('-')}.md`;
      await this.generatePromptRule(prompt, rulesDir, filename);
    }
  }

  private async generateProjectContext(project: Project, outputDir: string): Promise<void> {
    const content: string[] = [];

    content.push('---');
    content.push('trigger: always_on');
    content.push('---');
    content.push('');
    content.push(`# Project: ${project.name}`);
    content.push('');
    content.push(project.description);
    content.push('');

    if (project.context) {
      content.push('## Project Overview');
      content.push('');
      if (project.context.overview) content.push(project.context.overview);
      if (project.context.purpose) content.push(`**Purpose**: ${project.context.purpose}`);
      content.push('');
    }

    if (project.tech_stack) {
      content.push('## Tech Stack');
      content.push('');
      if (project.tech_stack.languages) content.push(`**Languages**: ${project.tech_stack.languages.join(', ')}`);
      if (project.tech_stack.frontend) content.push(`**Frontend**: ${project.tech_stack.frontend.join(', ')}`);
      if (project.tech_stack.backend) content.push(`**Backend**: ${project.tech_stack.backend.join(', ')}`);
      if (project.tech_stack.database) content.push(`**Database**: ${project.tech_stack.database.join(', ')}`);
      if (project.tech_stack.infrastructure) content.push(`**Infrastructure**: ${project.tech_stack.infrastructure.join(', ')}`);
      if (project.tech_stack.tools) content.push(`**Tools**: ${project.tech_stack.tools.join(', ')}`);
      content.push('');
    }

    if (project.commands) {
      content.push('## Key Commands');
      content.push('');
      for (const [category, cmds] of Object.entries(project.commands)) {
        if (typeof cmds === 'string') {
          content.push(`- \`${cmds}\` - ${category}`);
        } else {
          // Check if it's a nested object of commands or just a category key
          // The schema allows string | Record<string, string>
          // If it's a record, we might want to flatten or show as subsection
          // The previous implementation treated it as category -> { name: cmd }
          // But the example shows flat list under Key Commands
          
          // Let's follow the example:
          // - `cmd` - name
          
          for (const [name, cmd] of Object.entries(cmds)) {
             content.push(`- \`${cmd}\` - ${name}`);
          }
        }
      }
      content.push('');
    }

    if (project.conventions) {
      content.push('## Project Conventions');
      content.push('');
      if (project.conventions.naming) {
        content.push('### Naming');
        project.conventions.naming.forEach(c => content.push(`- ${c}`));
      }
      if (project.conventions.patterns) {
        content.push('### Patterns');
        project.conventions.patterns.forEach(c => content.push(`- ${c}`));
      }
      if (project.conventions.testing) {
        content.push('### Testing');
        project.conventions.testing.forEach(c => content.push(`- ${c}`));
      }
      if (project.conventions.structure) {
        content.push('### Project Structure');
        project.conventions.structure.forEach(c => content.push(`- ${c}`));
      }
      content.push('');
    }

    if (project.documentation) {
      content.push('## Documentation');
      content.push('');
      for (const [name, path] of Object.entries(project.documentation)) {
        if (typeof path === 'string') {
          content.push(`- **${name}**: ${path}`);
        } else {
           // Nested docs?
           for (const [subName, subPath] of Object.entries(path)) {
             content.push(`- **${name}/${subName}**: ${subPath}`);
           }
        }
      }
      content.push('');
    }

    await writeFile(join(outputDir, 'project-context.md'), content.join('\n'));
  }

  private async generateAgentRule(agent: Agent, outputDir: string): Promise<void> {
    const rules = agent.rulepacks ? await this.resolver.resolveRulepacks(agent.rulepacks) : [];

    const content: string[] = [];
    content.push('---');
    content.push('trigger: manual');
    content.push('---');
    content.push('');
    content.push(`# Agent: ${agent.id}`);
    content.push('');
    content.push(`**Purpose:** ${agent.purpose}`);
    content.push('');

    if (agent.prompt?.system) {
      content.push('## Persona');
      content.push('');
      content.push(agent.prompt.system.trim());
      content.push('');
    }

    if (agent.constraints && agent.constraints.length > 0) {
      content.push('## Constraints');
      content.push('');
      for (const constraint of agent.constraints) {
        content.push(`- ${constraint}`);
      }
      content.push('');
    }

    if (rules.length > 0) {
      content.push('## Rules');
      content.push('');
      for (const rule of rules) {
        content.push(`- ${rule}`);
      }
      content.push('');
    }

    await writeFile(join(outputDir, `agent-${agent.id}.md`), content.join('\n'));
  }

  private async generatePromptRule(prompt: Prompt, outputDir: string, filenameOverride?: string): Promise<void> {
    const content: string[] = [];
    content.push('---');
    content.push('trigger: manual');
    content.push('---');
    content.push('');
    content.push(`# ${prompt.id}`);
    content.push('');
    content.push(prompt.description);
    content.push('');

    if (prompt.variables && prompt.variables.length > 0) {
      content.push('## Variables');
      content.push('');
      for (const variable of prompt.variables) {
        const required = variable.required ? ' (required)' : '';
        content.push(`- \`{{${variable.name}}}\`${required}: ${variable.description || ''}`);
      }
      content.push('');
    }

    if (prompt.content) {
      content.push('## Prompt');
      content.push('');
      content.push(prompt.content);
      content.push('');
    }

    if (prompt.system) {
      content.push('## System Prompt');
      content.push('');
      content.push(prompt.system);
      content.push('');
    }

    if (prompt.user) {
      content.push('## User Prompt');
      content.push('');
      content.push(prompt.user);
      content.push('');
    }

    // Use provided filename or fallback to ID-based name
    const filename = filenameOverride || `prompt-${prompt.id}.md`;
    await writeFile(join(outputDir, filename), content.join('\n'));
  }
}
