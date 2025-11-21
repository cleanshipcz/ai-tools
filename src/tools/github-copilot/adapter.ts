import { ToolAdapter } from '../base.js';
import { Project, Agent } from '../../core/models/types.js';
import { ConfigService } from '../../core/services/config.service.js';
import { RecipeService } from '../../core/services/recipe.service.js';
import { LoaderService } from '../../core/services/loader.service.js';
import { ResolverService } from '../../core/services/resolver.service.js';
import { join } from 'path';
import { mkdir, readFile, writeFile, access } from 'fs/promises';

export class GitHubCopilotAdapter extends ToolAdapter {
  name = 'github-copilot';
  private config = ConfigService.getInstance();
  private recipeService = new RecipeService();
  private loader = new LoaderService();
  private resolver = new ResolverService();

  async generate(project: Project, outputDir: string): Promise<void> {
    const githubDir = join(outputDir, '.github');
    await mkdir(githubDir, { recursive: true });

    // Generate instructions.md with base instructions + agents + project context
    const instructionsContent = await this.buildInstructionsFile(project);
    await writeFile(join(githubDir, 'instructions.md'), instructionsContent, 'utf-8');

    // Generate recipes
    await this.recipeService.generateRecipesForTool(this.name, outputDir, project);
  }

  async generateGlobal(outputDir: string): Promise<void> {
    const githubDir = join(outputDir, '.github');
    await mkdir(githubDir, { recursive: true });

    // Generate global instructions.md with base instructions + agents (no project context)
    const instructionsContent = await this.buildInstructionsFile();
    await writeFile(join(githubDir, 'instructions.md'), instructionsContent, 'utf-8');
  }

  private async buildInstructionsFile(project?: Project): Promise<string> {
    const lines: string[] = [];

    // 1. Read base instructions.md header
    const adapterSrcDir = this.config.getPath(this.config.dirs.adapters, 'github-copilot');
    try {
      await access(adapterSrcDir);
      const baseFile = join(adapterSrcDir, 'instructions.md');
      const baseContent = await readFile(baseFile, 'utf-8');
      lines.push(baseContent);
    } catch {
      // Base adapters not found, add default header
      lines.push('# GitHub Copilot Instructions\n');
    }

    // 2. Generate Available Agents section dynamically
    lines.push('');
    lines.push('## Available Agents');
    lines.push('');
    lines.push('You can act as the following agents when requested:');
    lines.push('');

    const agentsDir = this.config.getPath(this.config.dirs.agents);
    try {
      const agentFiles = await this.loader.findYamlFiles(agentsDir);
      
      for (const file of agentFiles) {
        const agent = await this.loader.loadYaml<Agent>(file);
        
        let include = true;
        if (project) {
          include = this.resolver.shouldIncludeAgent(agent.id, project);
        }
        
        if (include) {
          lines.push(`### ${agent.id}`);
          lines.push('');
          lines.push(`**Purpose:** ${agent.purpose}`);
          lines.push('');
          
          if (agent.prompt?.system) {
            lines.push('**Persona:**');
            lines.push('');
            lines.push(agent.prompt.system);
            lines.push('');
          }

          if (agent.constraints && agent.constraints.length > 0) {
            lines.push('**Constraints:**');
            lines.push('');
            for (const constraint of agent.constraints) {
              lines.push(`- ${constraint}`);
            }
            lines.push('');
          }

          // Resolve and add rules from rulepacks
          if (agent.rulepacks && agent.rulepacks.length > 0) {
            if (project) {
                const rules = await this.resolver.resolveRulepacks(agent.rulepacks, project);
                if (rules.length > 0) {
                  lines.push('**Rules:**');
                  lines.push('');
                  for (const rule of rules) {
                    lines.push(`- ${rule}`);
                  }
                  lines.push('');
                }
            }
          }

          lines.push('---');
          lines.push('');
        }
      }
    } catch (e) {
      console.warn('Failed to load agents for instructions.md generation', e);
    }

    // 3. Add project-specific section (only if project is provided)
    if (project) {
        lines.push(`# Project: ${project.name}`);
        lines.push('');
        lines.push(project.description);
        lines.push('');

        // Context
        if (project.context) {
        lines.push('## Project Overview');
        lines.push('');
        if (project.context.overview) {
            lines.push(project.context.overview.trim());
            lines.push('');
        }
        if (project.context.purpose) {
            lines.push(`**Purpose:** ${project.context.purpose}`);
            lines.push('');
        }
        }

        // Tech Stack
        if (project.tech_stack) {
        lines.push('## Tech Stack');
        lines.push('');
        if (project.tech_stack.languages?.length) {
            lines.push(`**Languages:** ${project.tech_stack.languages.join(', ')}`);
        }
        if (project.tech_stack.frontend?.length) {
            lines.push(`**Frontend:** ${project.tech_stack.frontend.join(', ')}`);
        }
        if (project.tech_stack.backend?.length) {
            lines.push(`**Backend:** ${project.tech_stack.backend.join(', ')}`);
        }
        if (project.tech_stack.database?.length) {
            lines.push(`**Database:** ${project.tech_stack.database.join(', ')}`);
        }
        if (project.tech_stack.infrastructure?.length) {
            lines.push(`**Infrastructure:** ${project.tech_stack.infrastructure.join(', ')}`);
        }
        lines.push('');
        }

        // Commands
        if (project.commands) {
        lines.push('## Key Commands');
        lines.push('');
        for (const [category, cmds] of Object.entries(project.commands)) {
            if (typeof cmds === 'string') {
            lines.push(`- \`${cmds}\` - ${category}`);
            } else {
            lines.push(`### ${category.charAt(0).toUpperCase() + category.slice(1)}`);
            for (const [name, cmd] of Object.entries(cmds)) {
                lines.push(`- \`${cmd}\` - ${name}`);
            }
            }
        }
        lines.push('');
        }
    }

    return lines.join('\n');
  }
}
