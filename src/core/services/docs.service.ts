import { join } from 'path';
import { writeFile, mkdir } from 'fs/promises';
import { ConfigService } from './config.service.js';
import { LoaderService } from './loader.service.js';
import { Agent, Prompt, Skill } from '../models/types.js';
import chalk from 'chalk';

export class DocsService {
  private config = ConfigService.getInstance();
  private loader = new LoaderService();
  private agents: Agent[] = [];
  private prompts: Prompt[] = [];
  private skills: Skill[] = [];

  async generateDocs(): Promise<void> {
    console.log(chalk.blue('📝 Generating documentation...\n'));

    await this.loadManifests();
    await this.generateAgentsDocs();

    console.log(chalk.green('✅ Documentation generated!'));
  }

  private async loadManifests(): Promise<void> {
    // Load agents
    const agentsDir = this.config.getPath(this.config.dirs.agents);
    try {
      const agentFiles = await this.loader.findYamlFiles(agentsDir);
      for (const file of agentFiles) {
        this.agents.push(await this.loader.loadYaml<Agent>(file));
      }
      console.log(chalk.gray(`  Loaded ${this.agents.length} agents`));
    } catch (error) {
      console.log(chalk.yellow('  No agents found'));
    }

    // Load prompts
    const promptsDir = this.config.getPath(this.config.dirs.prompts);
    try {
      const promptFiles = await this.loader.findYamlFiles(promptsDir);
      for (const file of promptFiles) {
        this.prompts.push(await this.loader.loadYaml<Prompt>(file));
      }
      console.log(chalk.gray(`  Loaded ${this.prompts.length} prompts`));
    } catch (error) {
      console.log(chalk.yellow('  No prompts found'));
    }

    // Load skills
    const skillsDir = this.config.getPath(this.config.dirs.skills);
    try {
      const skillFiles = await this.loader.findYamlFiles(skillsDir);
      for (const file of skillFiles) {
        this.skills.push(await this.loader.loadYaml<Skill>(file));
      }
      console.log(chalk.gray(`  Loaded ${this.skills.length} skills`));
    } catch (error) {
      console.log(chalk.yellow('  No skills found'));
    }
  }

  private async generateAgentsDocs(): Promise<void> {
    const sections: string[] = [];

    // Header
    sections.push('# AI Tools Documentation\n');
    sections.push('> Auto-generated documentation for agents, prompts, and skills\n');
    sections.push(`_Last updated: ${new Date().toISOString().split('T')[0]}_\n`);

    // Table of Contents
    sections.push('## Table of Contents\n');
    sections.push('- [Agents](#agents)');
    sections.push('- [Prompts](#prompts)');
    sections.push('- [Skills](#skills)\n');

    // Agents section
    if (this.agents.length > 0) {
      sections.push('## Agents\n');
      sections.push(
        'Agents are complete AI assistants with specific purposes, configured with rulepacks, tools, and capabilities.\n'
      );

      for (const agent of this.agents.sort((a, b) => a.id.localeCompare(b.id))) {
        sections.push(`### \`${agent.id}\`\n`);
        if (agent.version) {
          sections.push(`**Version:** ${agent.version}\n`);
        }
        sections.push(`**Purpose:** ${agent.purpose}\n`);

        if (agent.description) {
          sections.push(`${agent.description}\n`);
        }

        if (agent.rulepacks && agent.rulepacks.length > 0) {
          sections.push('**Rulepacks:**');
          for (const rulepack of agent.rulepacks) {
            sections.push(`- \`${rulepack}\``);
          }
          sections.push('');
        }

        if (agent.capabilities && agent.capabilities.length > 0) {
          sections.push('**Required Capabilities:**');
          for (const capability of agent.capabilities) {
            sections.push(`- \`${capability}\``);
          }
          sections.push('');
        }

        if (agent.tools && agent.tools.length > 0) {
          sections.push('**Tools:**');
          for (const tool of agent.tools) {
            sections.push(`- \`${tool}\``);
          }
          sections.push('');
        }

        if (agent.defaults) {
          sections.push('**Default Settings:**');
          sections.push('```yaml');
          if (agent.defaults.temperature !== undefined) {
            sections.push(`temperature: ${agent.defaults.temperature}`);
          }
          if (agent.defaults.model) {
            sections.push(`model: ${agent.defaults.model}`);
          }
          if (agent.defaults.style) {
            sections.push(`style: ${agent.defaults.style}`);
          }
          sections.push('```\n');
        }

        sections.push('---\n');
      }
    }

    // Prompts section
    if (this.prompts.length > 0) {
      sections.push('## Prompts\n');
      sections.push(
        'Prompts are atomic, reusable templates with variables and clear specifications.\n'
      );

      // Group by tag
      const promptsByTag = new Map<string, Prompt[]>();
      for (const prompt of this.prompts) {
        // Prompt type in types.ts doesn't have tags?
        // It does: tags?: string[];
        const tags = prompt.tags || ['uncategorized'];
        for (const tag of tags) {
          if (!promptsByTag.has(tag)) {
            promptsByTag.set(tag, []);
          }
          promptsByTag.get(tag)!.push(prompt);
        }
      }

      for (const [tag, prompts] of Array.from(promptsByTag.entries()).sort()) {
        sections.push(`### ${tag.charAt(0).toUpperCase() + tag.slice(1)}\n`);

        for (const prompt of prompts.sort((a, b) => a.id.localeCompare(b.id))) {
          sections.push(`#### \`${prompt.id}\`\n`);
          if (prompt.version) {
            sections.push(`**Version:** ${prompt.version}  `);
          }
          sections.push(`${prompt.description}\n`);

          if (prompt.variables && prompt.variables.length > 0) {
            sections.push('**Variables:**');
            for (const variable of prompt.variables) {
              const required = variable.required ? '(required)' : '(optional)';
              sections.push(`- \`${variable.name}\` ${required}`);
              if (variable.description) {
                sections.push(`  ${variable.description}`);
              }
            }
            sections.push('');
          }
        }
      }

      sections.push('---\n');
    }

    // Skills section
    if (this.skills.length > 0) {
      sections.push('## Skills\n');
      sections.push(
        'Skills are executable tools and commands that agents can use to perform specific tasks.\n'
      );

      // Group by tag
      const skillsByTag = new Map<string, Skill[]>();
      for (const skill of this.skills) {
        const tags = skill.tags || ['uncategorized'];
        for (const tag of tags) {
          if (!skillsByTag.has(tag)) {
            skillsByTag.set(tag, []);
          }
          skillsByTag.get(tag)!.push(skill);
        }
      }

      for (const [tag, skills] of Array.from(skillsByTag.entries()).sort()) {
        sections.push(`### ${tag.charAt(0).toUpperCase() + tag.slice(1)}\n`);

        for (const skill of skills.sort((a, b) => a.id.localeCompare(b.id))) {
          sections.push(`#### \`${skill.id}\`\n`);
          if (skill.version) {
            sections.push(`**Version:** ${skill.version}  `);
          }
          sections.push(`${skill.description}\n`);
        }
      }
    }

    // Write to file
    const docsDir = join(process.cwd(), 'docs');
    await mkdir(docsDir, { recursive: true });
    const outputPath = join(docsDir, 'AGENTS.md');
    await writeFile(outputPath, sections.join('\n'));

    console.log(chalk.gray(`  Generated ${outputPath}`));
  }
}
