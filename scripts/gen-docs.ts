#!/usr/bin/env node
import { readFile, writeFile, readdir } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface Agent {
  id: string;
  version?: string;
  purpose: string;
  description?: string;
  rulepacks?: string[];
  capabilities?: string[];
  tools?: string[];
  defaults?: any;
}

interface Prompt {
  id: string;
  version?: string;
  description: string;
  tags?: string[];
  variables?: any[];
}

interface Skill {
  id: string;
  version?: string;
  description: string;
  tags?: string[];
}

class DocsGenerator {
  private agents: Agent[] = [];
  private prompts: Prompt[] = [];
  private skills: Skill[] = [];

  async generate(): Promise<void> {
    console.log(chalk.blue('📝 Generating documentation...\n'));

    await this.loadManifests();
    await this.generateAgentsDocs();

    console.log(chalk.green('✅ Documentation generated!'));
  }

  private async loadManifests(): Promise<void> {
    // Load agents
    const agentsDir = join(rootDir, 'agents');
    try {
      const agentFiles = await this.findYamlFiles(agentsDir);
      for (const file of agentFiles) {
        const content = await readFile(file, 'utf-8');
        this.agents.push(loadYaml(content) as Agent);
      }
      console.log(chalk.gray(`  Loaded ${this.agents.length} agents`));
    } catch (error) {
      console.log(chalk.yellow('  No agents found'));
    }

    // Load prompts
    const promptsDir = join(rootDir, 'prompts');
    try {
      const promptFiles = await this.findYamlFiles(promptsDir);
      for (const file of promptFiles) {
        const content = await readFile(file, 'utf-8');
        this.prompts.push(loadYaml(content) as Prompt);
      }
      console.log(chalk.gray(`  Loaded ${this.prompts.length} prompts`));
    } catch (error) {
      console.log(chalk.yellow('  No prompts found'));
    }

    // Load skills
    const skillsDir = join(rootDir, 'skills');
    try {
      const skillFiles = await this.findYamlFiles(skillsDir);
      for (const file of skillFiles) {
        const content = await readFile(file, 'utf-8');
        this.skills.push(loadYaml(content) as Skill);
      }
      console.log(chalk.gray(`  Loaded ${this.skills.length} skills`));
    } catch (error) {
      console.log(chalk.yellow('  No skills found'));
    }
  }

  private async findYamlFiles(dir: string): Promise<string[]> {
    const files: string[] = [];

    try {
      const entries = await readdir(dir, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = join(dir, entry.name);

        if (entry.isDirectory()) {
          files.push(...(await this.findYamlFiles(fullPath)));
        } else if (
          entry.isFile() &&
          (entry.name.endsWith('.yml') || entry.name.endsWith('.yaml'))
        ) {
          files.push(fullPath);
        }
      }
    } catch (error) {
      // Directory doesn't exist
    }

    return files;
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
    const docsDir = join(rootDir, 'docs');
    const outputPath = join(docsDir, 'AGENTS.md');
    await writeFile(outputPath, sections.join('\n'));

    console.log(chalk.gray(`  Generated ${outputPath}`));
  }
}

// Main execution
const generator = new DocsGenerator();
generator.generate().catch((error) => {
  console.error(chalk.red('Documentation generation failed:'), error);
  process.exit(1);
});
