#!/usr/bin/env node
import { readFile, writeFile, mkdir, readdir } from 'fs/promises';
import { join, dirname, relative, basename } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface Rulepack {
  id: string;
  version?: string;
  description?: string;
  extends?: string[];
  rules: string[];
}

interface Agent {
  id: string;
  version?: string;
  purpose: string;
  rulepacks?: string[];
  prompt?: any;
  defaults?: any;
  capabilities?: string[];
  tools?: string[];
  constraints?: string[];
}

interface Prompt {
  id: string;
  version?: string;
  description: string;
  content?: string;
  system?: string;
  user?: string;
  includes?: string[];
  rules?: string[];
  variables?: any[];
  outputs?: any;
}

interface Skill {
  id: string;
  version?: string;
  description: string;
  command?: any;
  mcp_tool?: string;
  timeout_sec?: number;
  inputs?: any[];
  outputs?: any;
}

class Builder {
  private rulepacks = new Map<string, Rulepack>();
  private agents = new Map<string, Agent>();
  private prompts = new Map<string, Prompt>();
  private skills = new Map<string, Skill>();

  async build(): Promise<void> {
    console.log(chalk.blue('🔨 Starting build...\n'));

    // Load all manifests
    await this.loadRulepacks();
    await this.loadAgents();
    await this.loadPrompts();
    await this.loadSkills();

    // Generate adapters
    await this.generateWindsurfAdapters();
    await this.generateClaudeCodeAdapters();
    await this.generateCursorAdapters();
    await this.generateGitHubCopilotAdapters();

    // Generate Anthropic-compatible SKILL.md files
    await this.generateAnthropicSkills();

    console.log(chalk.green('\n✅ Build complete!'));
  }

  private async loadRulepacks(): Promise<void> {
    const dir = join(rootDir, 'rulepacks');
    const files = await this.findYamlFiles(dir);

    for (const file of files) {
      const content = await readFile(file, 'utf-8');
      const rulepack = loadYaml(content) as Rulepack;
      this.rulepacks.set(rulepack.id, rulepack);
    }

    console.log(chalk.gray(`  Loaded ${this.rulepacks.size} rulepacks`));
  }

  private async loadAgents(): Promise<void> {
    const dir = join(rootDir, 'agents');
    try {
      const files = await this.findYamlFiles(dir);

      for (const file of files) {
        const content = await readFile(file, 'utf-8');
        const agent = loadYaml(content) as Agent;
        this.agents.set(agent.id, agent);
      }

      console.log(chalk.gray(`  Loaded ${this.agents.size} agents`));
    } catch (error) {
      console.log(chalk.yellow(`  No agents found`));
    }
  }

  private async loadPrompts(): Promise<void> {
    const dir = join(rootDir, 'prompts');
    try {
      const files = await this.findYamlFiles(dir);

      for (const file of files) {
        const content = await readFile(file, 'utf-8');
        const prompt = loadYaml(content) as Prompt;
        this.prompts.set(prompt.id, prompt);
      }

      console.log(chalk.gray(`  Loaded ${this.prompts.size} prompts`));
    } catch (error) {
      console.log(chalk.yellow(`  No prompts found`));
    }
  }

  private async loadSkills(): Promise<void> {
    const dir = join(rootDir, 'skills');
    try {
      const files = await this.findYamlFiles(dir);

      for (const file of files) {
        const content = await readFile(file, 'utf-8');
        const skill = loadYaml(content) as Skill;
        this.skills.set(skill.id, skill);
      }

      console.log(chalk.gray(`  Loaded ${this.skills.size} skills`));
    } catch (error) {
      console.log(chalk.yellow(`  No skills found`));
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

  private resolveRulepacks(rulepackIds: string[]): string[] {
    const resolved: string[] = [];
    const visited = new Set<string>();

    const resolve = (id: string) => {
      if (visited.has(id)) return;
      visited.add(id);

      const rulepack = this.rulepacks.get(id);
      if (!rulepack) {
        console.warn(chalk.yellow(`  Warning: Rulepack "${id}" not found`));
        return;
      }

      // Resolve parent rulepacks first
      if (rulepack.extends) {
        for (const parentId of rulepack.extends) {
          resolve(parentId);
        }
      }

      // Add this rulepack's rules
      resolved.push(...rulepack.rules);
    };

    for (const id of rulepackIds) {
      resolve(id);
    }

    return resolved;
  }

  private async generateWindsurfAdapters(): Promise<void> {
    console.log(chalk.blue('\n  Generating Windsurf adapters...'));

    const outputDir = join(rootDir, 'adapters', 'windsurf', 'rules');
    await mkdir(outputDir, { recursive: true });

    // Generate rule files for each agent
    for (const [id, agent] of this.agents) {
      const rules = agent.rulepacks ? this.resolveRulepacks(agent.rulepacks) : [];

      const windsurfConfig = {
        name: agent.id,
        version: agent.version || '1.0.0',
        description: agent.purpose,
        rules: rules,
        settings: agent.defaults || {},
      };

      const outputPath = join(outputDir, `${id}.json`);
      await writeFile(outputPath, JSON.stringify(windsurfConfig, null, 2));
    }

    // Generate preset files
    const presetsDir = join(rootDir, 'adapters', 'windsurf', 'presets');
    await mkdir(presetsDir, { recursive: true });

    const basePreset = {
      name: 'Base Configuration',
      version: '1.0.0',
      rules: this.resolveRulepacks(['base']),
    };

    await writeFile(join(presetsDir, 'base.json'), JSON.stringify(basePreset, null, 2));

    console.log(chalk.gray(`    Generated ${this.agents.size} agent configs`));
  }

  private async generateClaudeCodeAdapters(): Promise<void> {
    console.log(chalk.blue('  Generating Claude Code adapters...'));

    const outputDir = join(rootDir, 'adapters', 'claude-code');
    await mkdir(outputDir, { recursive: true });

    // Generate skills.json
    const skillsArray = Array.from(this.skills.values()).map((skill) => ({
      id: skill.id,
      version: skill.version || '1.0.0',
      description: skill.description,
      command: skill.command,
      mcp_tool: skill.mcp_tool,
      timeout_sec: skill.timeout_sec || 300,
      inputs: skill.inputs || [],
      outputs: skill.outputs || {},
    }));

    await writeFile(
      join(outputDir, 'skills.json'),
      JSON.stringify({ skills: skillsArray }, null, 2)
    );

    // Generate prompt files
    const promptsDir = join(outputDir, 'prompts');
    await mkdir(promptsDir, { recursive: true });

    for (const [id, prompt] of this.prompts) {
      const claudePrompt = {
        id: prompt.id,
        version: prompt.version || '1.0.0',
        description: prompt.description,
        system: prompt.system || '',
        user: prompt.user || prompt.content || '',
        variables: prompt.variables || [],
        outputs: prompt.outputs || { format: 'text' },
      };

      await writeFile(join(promptsDir, `${id}.json`), JSON.stringify(claudePrompt, null, 2));
    }

    console.log(chalk.gray(`    Generated ${this.skills.size} skills`));
    console.log(chalk.gray(`    Generated ${this.prompts.size} prompts`));
  }

  private async generateCursorAdapters(): Promise<void> {
    console.log(chalk.blue('  Generating Cursor adapters...'));

    const outputDir = join(rootDir, 'adapters', 'cursor');
    await mkdir(outputDir, { recursive: true });

    // Generate recipes.json
    const recipes = Array.from(this.agents.values()).map((agent) => ({
      id: agent.id,
      name: agent.purpose,
      version: agent.version || '1.0.0',
      description: agent.purpose,
      prompt: agent.prompt?.system || '',
      rules: agent.rulepacks ? this.resolveRulepacks(agent.rulepacks) : [],
      tools: agent.tools || [],
    }));

    await writeFile(join(outputDir, 'recipes.json'), JSON.stringify({ recipes }, null, 2));

    console.log(chalk.gray(`    Generated ${recipes.length} recipes`));
  }

  private async generateGitHubCopilotAdapters(): Promise<void> {
    console.log(chalk.blue('  Generating GitHub Copilot adapters...'));

    const baseDir = join(rootDir, 'adapters', 'github-copilot');
    await mkdir(baseDir, { recursive: true });

    // Create .github structure
    const githubDir = join(baseDir, '.github');
    const instructionsDir = join(githubDir, 'instructions');
    const promptsDir = join(githubDir, 'prompts');
    await mkdir(instructionsDir, { recursive: true });
    await mkdir(promptsDir, { recursive: true });

    // ========================================
    // 1. GLOBAL INSTRUCTIONS (Available Tools + Template)
    // ========================================
    const global: string[] = [];

    global.push(
      '<!-- This file is loaded for ALL files. Lists available tools you can reference in chat. -->'
    );
    global.push('# Available AI Tools');
    global.push('');
    global.push(
      'This repository provides reusable AI agent configurations. Copy relevant sections to your project.'
    );
    global.push('');

    // List available agents (brief listing only - full definitions in prompts)
    if (this.agents.size > 0) {
      global.push('## Agents');
      global.push('');
      global.push('**Available agent personas (invoke via `#prompt:agent-<name>`):**');
      global.push('');
      for (const [id, agent] of this.agents) {
        global.push(`- **${id}**: ${agent.purpose}`);
      }
      global.push('');
      global.push('*Example: `@workspace #prompt:agent-code-reviewer` - Review this code*');
      global.push('');
    }

    // List available prompts
    if (this.prompts.size > 0) {
      global.push('## Prompts');
      global.push('');
      global.push('**Reusable prompts (attach via paperclip or `#prompt:name`):**');
      global.push('');
      for (const [id, prompt] of this.prompts) {
        global.push(`- **${id}**: ${prompt.description}`);
      }
      global.push('');
    }

    // List available skills
    if (this.skills.size > 0) {
      global.push('## Skills');
      global.push('');
      global.push('**Executable commands available:**');
      global.push('');
      for (const [id, skill] of this.skills) {
        global.push(`- **${id}**: ${skill.description}`);
      }
      global.push('');
    }

    // Template guidance for users
    global.push('---');
    global.push('');
    global.push('## Customize for Your Project');
    global.push('');
    global.push('**Add your project specifics above (keep concise):**');
    global.push('');
    global.push('1. **Project overview** - What it does (2-3 sentences)');
    global.push('2. **Tech stack** - Languages, frameworks');
    global.push('3. **Build commands** - How to run/test');
    global.push('4. **Key conventions** - Naming, patterns');
    global.push('');
    global.push('**Example:**');
    global.push('```markdown');
    global.push('# My Project');
    global.push('E-commerce platform with React + Node.js');
    global.push('');
    global.push('## Stack');
    global.push('- Frontend: React, TypeScript');
    global.push('- Backend: Node.js, PostgreSQL');
    global.push('');
    global.push('## Commands');
    global.push('`npm run dev` - Start dev server');
    global.push('`npm test` - Run tests');
    global.push('```');

    // Write global instructions
    await writeFile(join(githubDir, 'copilot-instructions.md'), global.join('\n'), 'utf-8');
    console.log(chalk.gray(`    Generated .github/copilot-instructions.md`));

    // ========================================
    // 2. PATH-SPECIFIC INSTRUCTIONS (By Rulepack Language)
    // ========================================
    const languagePatterns: Record<string, string> = {
      'coding-kotlin': '**/*.kt',
      'coding-python': '**/*.py',
      'coding-typescript': '**/*.ts,**/*.tsx',
    };

    for (const [rulepackId, pattern] of Object.entries(languagePatterns)) {
      const rulepack = this.rulepacks.get(rulepackId);
      if (!rulepack) continue;

      const pathInstructions: string[] = [];
      pathInstructions.push('---');
      pathInstructions.push(`applyTo: "${pattern}"`);
      pathInstructions.push('---');
      pathInstructions.push('');
      pathInstructions.push(`# ${rulepack.description || rulepackId}`);
      pathInstructions.push('');

      const rules = this.resolveRulepacks([rulepackId]);
      for (const rule of rules) {
        pathInstructions.push(`- ${rule}`);
      }
      pathInstructions.push('');

      await writeFile(
        join(instructionsDir, `${rulepackId}.instructions.md`),
        pathInstructions.join('\n'),
        'utf-8'
      );
    }
    console.log(
      chalk.gray(
        `    Generated ${Object.keys(languagePatterns).length} path-specific .instructions.md files`
      )
    );

    // ========================================
    // 3. PROMPT FILES (Task Prompts)
    // ========================================
    for (const [id, prompt] of this.prompts) {
      const promptContent: string[] = [];
      promptContent.push(`# ${id}`);
      promptContent.push('');
      promptContent.push(prompt.description);
      promptContent.push('');

      if (prompt.variables && prompt.variables.length > 0) {
        promptContent.push('## Variables');
        promptContent.push('');
        for (const variable of prompt.variables) {
          const required = variable.required ? ' (required)' : '';
          promptContent.push(
            `- \`{{${variable.name}}}\`${required}: ${variable.description || ''}`
          );
        }
        promptContent.push('');
      }

      if (prompt.content) {
        promptContent.push('## Prompt');
        promptContent.push('');
        promptContent.push(prompt.content);
        promptContent.push('');
      }

      await writeFile(join(promptsDir, `${id}.prompt.md`), promptContent.join('\n'), 'utf-8');
    }
    console.log(chalk.gray(`    Generated ${this.prompts.size} task .prompt.md files`));

    // ========================================
    // 4. AGENT PROMPTS (Agent Personas)
    // ========================================
    for (const [id, agent] of this.agents) {
      const agentPrompt: string[] = [];

      agentPrompt.push(`# Agent: ${id}`);
      agentPrompt.push('');
      agentPrompt.push(`**Purpose:** ${agent.purpose}`);
      agentPrompt.push('');

      // Include system prompt (the actual agent behavior)
      if (agent.prompt?.system) {
        agentPrompt.push('## Persona');
        agentPrompt.push('');
        agentPrompt.push(agent.prompt.system.trim());
        agentPrompt.push('');
      }

      // Include constraints
      if (agent.constraints && agent.constraints.length > 0) {
        agentPrompt.push('## Constraints');
        agentPrompt.push('');
        for (const constraint of agent.constraints) {
          agentPrompt.push(`- ${constraint}`);
        }
        agentPrompt.push('');
      }

      // Include expanded rulepacks (actual rules)
      if (agent.rulepacks && agent.rulepacks.length > 0) {
        agentPrompt.push('## Rules to Follow');
        agentPrompt.push('');

        const rules = this.resolveRulepacks(agent.rulepacks);
        for (const rule of rules) {
          agentPrompt.push(`- ${rule}`);
        }
        agentPrompt.push('');
      }

      await writeFile(join(promptsDir, `agent-${id}.prompt.md`), agentPrompt.join('\n'), 'utf-8');
    }
    console.log(chalk.gray(`    Generated ${this.agents.size} agent .prompt.md files`));
  }

  private async generateAnthropicSkills(): Promise<void> {
    console.log(chalk.blue('  Generating Anthropic SKILL.md files...'));

    const { execSync } = await import('child_process');

    try {
      execSync('npm run skills', {
        cwd: rootDir,
        stdio: 'inherit',
      });
    } catch (error) {
      console.error(chalk.red('    Failed to generate SKILL.md files'));
      throw error;
    }
  }
}

// Main execution
const builder = new Builder();
builder.build().catch((error) => {
  console.error(chalk.red('Build failed:'), error);
  process.exit(1);
});
