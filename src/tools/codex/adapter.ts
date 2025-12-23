import { ToolAdapter } from '../base.js';
import { Project, Agent, Prompt } from '../../core/models/types.js';
import { ConfigService } from '../../core/services/config.service.js';
import { LoaderService } from '../../core/services/loader.service.js';
import { ResolverService } from '../../core/services/resolver.service.js';
import { join } from 'path';
import { mkdir, writeFile } from 'fs/promises';

export class CodexAdapter extends ToolAdapter {
  name = 'codex';
  private config = ConfigService.getInstance();
  private loader = new LoaderService();
  private resolver = new ResolverService();

  async generate(project: Project, outputDir: string): Promise<void> {
    const codexDir = join(outputDir, '.codex');
    await mkdir(codexDir, { recursive: true });

    // Generate AGENTS.md (Project Context + Agents)
    const agentsContent = await this.buildAgentsFile(project);
    await writeFile(join(outputDir, 'AGENTS.md'), agentsContent, 'utf-8');

    // Generate prompts
    const promptsDir = this.config.getPath(this.config.dirs.prompts);
    const codexPromptsDir = join(codexDir, 'prompts');
    const { rm } = await import('fs/promises');
    await rm(codexPromptsDir, { recursive: true, force: true });
    await mkdir(codexPromptsDir, { recursive: true });

    const promptsMap = new Map<string, string>();
    let promptFiles: string[] = [];
    try {
        promptFiles = await this.loader.findYamlFilesRelative(promptsDir);
        
        for (const relativePath of promptFiles) {
            const fullPath = join(promptsDir, relativePath);
            const prompt = await this.loader.loadYaml<Prompt>(fullPath);
            const pathWithoutExt = relativePath.replace(/\.ya?ml$/, '').replace(/\\/g, '/');
            promptsMap.set(prompt.id, pathWithoutExt);
        }

        // Helper to generate prompts for a specific context
        const generatePromptsForContext = async (suffix: string = '', context?: { languages?: string[] }) => {
          for (const relativePath of promptFiles) {
            const fullPath = join(promptsDir, relativePath);
            const prompt = await this.loader.loadYaml<Prompt>(fullPath);
            
            if (this.resolver.shouldIncludePrompt(prompt.id, promptsMap, project)) {
              const pathWithoutExt = relativePath.replace(/\.ya?ml$/, '');
              // Convert path separators to hyphens for the filename
              const filename = `prompt-${pathWithoutExt.split(/[/\\]/).join('-')}${suffix}.md`;
              await this.generatePromptFile(prompt, codexPromptsDir, filename);
            }
          }
        };

        // Generate global prompts
        await generatePromptsForContext();

        // Generate stack-specific prompts
        if (project.tech_stacks) {
          for (const [stackName, stackContext] of Object.entries(project.tech_stacks)) {
            await generatePromptsForContext(`-${stackName}`, stackContext);
          }
        }

        // Generate agents as prompts
        // Use generic resolution for agents
        const resolvedAgents = await this.resolver.resolveAllAgents(project);

        for (const { agent, rules, suffix } of resolvedAgents) {
            const filename = `agent-${agent.id}${suffix}.md`;
            await this.generateAgentFile(agent, rules, codexPromptsDir, filename);
        }
    } catch (e) {
        console.warn('Failed to generate prompts for codex', e);
    }
  }

  async generateGlobal(outputDir: string): Promise<void> {
    // Generate global AGENTS.md
    const agentsContent = await this.buildAgentsFile();
    await writeFile(join(outputDir, 'AGENTS.md'), agentsContent, 'utf-8');
  }

  private async buildAgentsFile(project?: Project): Promise<string> {
    const lines: string[] = [];

    // Header
    lines.push('# AI Agents Configuration');
    lines.push('');
    lines.push('Invoke agents by referencing them in your prompts (e.g., "As the code-reviewer agent..."):');
    lines.push('');

    // Available Agents
    lines.push('## Available Agents');
    lines.push('');

    try {
      // Use generic resolution for agents
      const resolvedAgents = await this.resolver.resolveAllAgents(project || {} as Project);

      for (const { agent, rules, suffix } of resolvedAgents) {
          lines.push(`### ${agent.id}${suffix}`);
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

          // Add pre-resolved rules
          if (rules.length > 0) {
            lines.push('**Rules:**');
            lines.push('');
            for (const rule of rules) {
              lines.push(`- ${rule}`);
            }
            lines.push('');
          }

          lines.push('---');
          lines.push('');
      }

    } catch (e) {
      console.warn('Failed to load agents for AGENTS.md generation', e);
    }

    // Project Context (only if project is provided)
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

        if (project.tech_stacks) {
        lines.push('## Tech Stacks');
        lines.push('');
        for (const [name, stack] of Object.entries(project.tech_stacks)) {
            lines.push(`### ${name}`);
            if (stack.languages?.length) lines.push(`- **Languages:** ${stack.languages.join(', ')}`);
            if (stack.frontend?.length) lines.push(`- **Frontend:** ${stack.frontend.join(', ')}`);
            if (stack.backend?.length) lines.push(`- **Backend:** ${stack.backend.join(', ')}`);
            if (stack.database?.length) lines.push(`- **Database:** ${stack.database.join(', ')}`);
            if (stack.infrastructure?.length) lines.push(`- **Infrastructure:** ${stack.infrastructure.join(', ')}`);
            lines.push('');
        }
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

  private async generatePromptFile(prompt: Prompt, outputDir: string, filenameOverride?: string): Promise<void> {
    const content: string[] = [];
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

    const filename = filenameOverride || `${prompt.id}.md`;
    await writeFile(join(outputDir, filename), content.join('\n'));
  }

  private async generateAgentFile(agent: Agent, rules: string[], outputDir: string, filename: string): Promise<void> {
    const content: string[] = [];
    content.push(`# ${agent.id}`);
    content.push('');
    content.push(`**Purpose:** ${agent.purpose}`);
    content.push('');

    if (agent.prompt?.system) {
      content.push('## Persona');
      content.push('');
      content.push(agent.prompt.system);
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

    await writeFile(join(outputDir, filename), content.join('\n'));
  }
}
