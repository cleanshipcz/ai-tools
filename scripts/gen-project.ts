#!/usr/bin/env node
import { readFile, writeFile, mkdir, readdir, access, copyFile } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface Project {
  id: string;
  version: string;
  name: string;
  description: string;
  context?: {
    overview?: string;
    purpose?: string;
  };
  tech_stack?: {
    languages?: string[];
    frontend?: string[];
    backend?: string[];
    database?: string[];
    infrastructure?: string[];
    tools?: string[];
  };
  documentation?: Record<string, string | Record<string, string>>;
  commands?: Record<string, string | Record<string, string>>;
  conventions?: {
    naming?: string[];
    patterns?: string[];
    testing?: string[];
    structure?: string[];
    custom?: string[];
  };
  ai_tools?: {
    preferred_agents?: string[];
    preferred_rulepacks?: string[];
    custom_rules?: string[];
    excluded_agents?: string[];
    excluded_rulepacks?: string[];
  };
  metadata?: {
    repository?: string;
    maintainers?: string[];
    created?: string;
    updated?: string;
    tags?: string[];
  };
}

export class ProjectGenerator {
  private project: Project | null = null;
  private projectDir: string = '';

  async generate(projectId: string, tools: string[] = ['all']): Promise<void> {
    console.log(chalk.blue(`\n🔨 Generating project configurations for: ${projectId}\n`));

    // Find and load project
    await this.loadProject(projectId);

    if (!this.project) {
      throw new Error(`Project ${projectId} not found`);
    }

    // Create output directory in repo root
    const outputDir = join(rootDir, '.output', projectId);
    await mkdir(outputDir, { recursive: true });

    // Generate for each tool
    const toolsToGenerate = tools.includes('all')
      ? ['github-copilot', 'windsurf', 'cursor', 'claude-code']
      : tools;

    for (const tool of toolsToGenerate) {
      await this.generateForTool(tool, outputDir);
    }

    console.log(chalk.green(`\n✅ Generation complete! Output in: ${outputDir}\n`));
  }

  private async loadProject(projectId: string): Promise<void> {
    // Check global projects first
    let projectPath = join(rootDir, 'projects', 'global', projectId, 'project.yml');
    let exists = await this.fileExists(projectPath);

    if (!exists) {
      // Check local projects
      projectPath = join(rootDir, 'projects', 'local', projectId, 'project.yml');
      exists = await this.fileExists(projectPath);
    }

    if (!exists) {
      throw new Error(`Project "${projectId}" not found in global or local projects`);
    }

    this.projectDir = dirname(projectPath);
    const content = await readFile(projectPath, 'utf-8');
    this.project = loadYaml(content) as Project;

    console.log(chalk.gray(`  Loaded project: ${this.project.name}`));
  }

  private async fileExists(path: string): Promise<boolean> {
    try {
      await access(path);
      return true;
    } catch {
      return false;
    }
  }

  private async copyDirectory(src: string, dest: string): Promise<void> {
    await mkdir(dest, { recursive: true });

    const entries = await readdir(src, { withFileTypes: true });

    for (const entry of entries) {
      const srcPath = join(src, entry.name);
      const destPath = join(dest, entry.name);

      if (entry.isDirectory()) {
        await this.copyDirectory(srcPath, destPath);
      } else {
        await copyFile(srcPath, destPath);
      }
    }
  }

  private async generateForTool(tool: string, outputDir: string): Promise<void> {
    console.log(chalk.blue(`  Generating for ${tool}...`));

    const toolOutputDir = join(outputDir, tool);
    await mkdir(toolOutputDir, { recursive: true });

    switch (tool) {
      case 'github-copilot':
        await this.generateGitHubCopilot(toolOutputDir);
        break;
      case 'windsurf':
        await this.generateWindsurf(toolOutputDir);
        break;
      case 'cursor':
        await this.generateCursor(toolOutputDir);
        break;
      case 'claude-code':
        await this.generateClaudeCode(toolOutputDir);
        break;
      default:
        console.log(chalk.yellow(`    Unknown tool: ${tool}`));
    }
  }

  private async generateGitHubCopilot(outputDir: string): Promise<void> {
    const githubDir = join(outputDir, '.github');
    await mkdir(githubDir, { recursive: true });

    // Copy base adapters (prompts, instructions)
    const adapterSrcDir = join(rootDir, 'adapters', 'github-copilot', '.github');
    try {
      await access(adapterSrcDir);
      await this.copyDirectory(adapterSrcDir, githubDir);
      console.log(chalk.gray(`    Copied base prompts and instructions`));
    } catch {
      console.log(chalk.yellow(`    ! Base adapters not found. Run 'npm run build' first.`));
    }

    // Generate project-specific copilot-instructions.md
    const instructions = this.buildCopilotInstructions();
    await writeFile(join(githubDir, 'copilot-instructions.md'), instructions, 'utf-8');

    console.log(chalk.gray(`    Generated .github/copilot-instructions.md`));
  }

  private buildCopilotInstructions(): string {
    if (!this.project) return '';

    const lines: string[] = [];

    // Project header
    lines.push(`# ${this.project.name}`);
    lines.push('');
    lines.push(this.project.description);
    lines.push('');

    // Context
    if (this.project.context) {
      lines.push('## Project Overview');
      lines.push('');
      if (this.project.context.overview) {
        lines.push(this.project.context.overview.trim());
        lines.push('');
      }
      if (this.project.context.purpose) {
        lines.push(`**Purpose**: ${this.project.context.purpose}`);
        lines.push('');
      }
    }

    // Tech Stack
    if (this.project.tech_stack) {
      lines.push('## Tech Stack');
      lines.push('');
      if (this.project.tech_stack.languages?.length) {
        lines.push(`**Languages**: ${this.project.tech_stack.languages.join(', ')}`);
      }
      if (this.project.tech_stack.frontend?.length) {
        lines.push(`**Frontend**: ${this.project.tech_stack.frontend.join(', ')}`);
      }
      if (this.project.tech_stack.backend?.length) {
        lines.push(`**Backend**: ${this.project.tech_stack.backend.join(', ')}`);
      }
      if (this.project.tech_stack.database?.length) {
        lines.push(`**Database**: ${this.project.tech_stack.database.join(', ')}`);
      }
      if (this.project.tech_stack.infrastructure?.length) {
        lines.push(`**Infrastructure**: ${this.project.tech_stack.infrastructure.join(', ')}`);
      }
      lines.push('');
    }

    // Commands
    if (this.project.commands) {
      lines.push('## Key Commands');
      lines.push('');
      for (const [category, cmds] of Object.entries(this.project.commands)) {
        if (typeof cmds === 'string') {
          lines.push(`- \`${cmds}\` - ${category}`);
        } else {
          lines.push(`### ${category.charAt(0).toUpperCase() + category.slice(1)}`);
          for (const [name, cmd] of Object.entries(cmds)) {
            lines.push(`- \`${cmd}\` - ${name}`);
          }
          lines.push('');
        }
      }
      lines.push('');
    }

    // Conventions
    if (this.project.conventions) {
      lines.push('## Conventions');
      lines.push('');

      if (this.project.conventions.naming?.length) {
        lines.push('### Naming');
        lines.push('');
        for (const rule of this.project.conventions.naming) {
          lines.push(`- ${rule}`);
        }
        lines.push('');
      }

      if (this.project.conventions.patterns?.length) {
        lines.push('### Patterns');
        lines.push('');
        for (const rule of this.project.conventions.patterns) {
          lines.push(`- ${rule}`);
        }
        lines.push('');
      }

      if (this.project.conventions.testing?.length) {
        lines.push('### Testing');
        lines.push('');
        for (const rule of this.project.conventions.testing) {
          lines.push(`- ${rule}`);
        }
        lines.push('');
      }

      if (this.project.conventions.structure?.length) {
        lines.push('### Project Structure');
        lines.push('');
        for (const rule of this.project.conventions.structure) {
          lines.push(`- ${rule}`);
        }
        lines.push('');
      }

      if (this.project.conventions.custom?.length) {
        lines.push('### Custom');
        lines.push('');
        for (const rule of this.project.conventions.custom) {
          lines.push(`- ${rule}`);
        }
        lines.push('');
      }
    }

    // AI Tools custom rules
    if (this.project.ai_tools?.custom_rules?.length) {
      lines.push('## Project-Specific Rules');
      lines.push('');
      for (const rule of this.project.ai_tools.custom_rules) {
        lines.push(`- ${rule}`);
      }
      lines.push('');
    }

    // Documentation references
    if (this.project.documentation) {
      lines.push('## Documentation');
      lines.push('');
      for (const [key, value] of Object.entries(this.project.documentation)) {
        if (typeof value === 'string') {
          lines.push(`- **${key}**: ${value}`);
        } else if (typeof value === 'object') {
          for (const [subkey, subvalue] of Object.entries(value)) {
            lines.push(`- **${subkey}**: ${subvalue}`);
          }
        }
      }
      lines.push('');
    }

    // Add link to base AI tools
    lines.push('---');
    lines.push('');
    lines.push('## Available AI Tools');
    lines.push('');
    lines.push(
      'This project uses ai-tools for enhanced AI assistance. See the base configurations for available agents, prompts, and skills.'
    );
    lines.push('');

    if (this.project.ai_tools?.preferred_agents?.length) {
      lines.push('**Preferred Agents for this project**:');
      for (const agent of this.project.ai_tools.preferred_agents) {
        lines.push(`- ${agent}`);
      }
      lines.push('');
    }

    return lines.join('\n');
  }

  private async generateWindsurf(outputDir: string): Promise<void> {
    const windsurfDir = join(outputDir, '.windsurf');
    await mkdir(windsurfDir, { recursive: true });

    // Copy base adapters (rules, presets)
    const adapterSrcDir = join(rootDir, 'adapters', 'windsurf');
    try {
      await access(adapterSrcDir);

      // Copy rules
      const rulesDir = join(adapterSrcDir, 'rules');
      const destRulesDir = join(windsurfDir, 'rules');
      await access(rulesDir);
      await this.copyDirectory(rulesDir, destRulesDir);

      // Copy presets
      const presetsDir = join(adapterSrcDir, 'presets');
      const destPresetsDir = join(windsurfDir, 'presets');
      try {
        await access(presetsDir);
        await this.copyDirectory(presetsDir, destPresetsDir);
      } catch {
        // Presets might not exist, that's ok
      }

      console.log(chalk.gray(`    Copied base rules and presets`));
    } catch {
      console.log(chalk.yellow(`    ! Base adapters not found. Run 'npm run build' first.`));
    }

    // Generate project-specific rule file
    const rules = this.buildProjectRules();
    const config = {
      name: this.project?.name || 'Project Rules',
      version: this.project?.version || '1.0.0',
      description: this.project?.description || '',
      rules: rules,
    };

    const rulesDir = join(windsurfDir, 'rules');
    await mkdir(rulesDir, { recursive: true });
    await writeFile(join(rulesDir, 'project-rules.json'), JSON.stringify(config, null, 2));
    console.log(chalk.gray(`    Generated .windsurf/rules/project-rules.json`));
  }

  private async generateCursor(outputDir: string): Promise<void> {
    const cursorDir = join(outputDir, '.cursor');
    await mkdir(cursorDir, { recursive: true });

    // Copy base adapters (recipes)
    const adapterSrcDir = join(rootDir, 'adapters', 'cursor');
    try {
      await access(adapterSrcDir);

      // Copy recipes.json if it exists
      const recipesFile = join(adapterSrcDir, 'recipes.json');
      try {
        await access(recipesFile);
        await copyFile(recipesFile, join(cursorDir, 'recipes.json'));
        console.log(chalk.gray(`    Copied base recipes`));
      } catch {
        // Recipes might not exist yet
      }
    } catch {
      console.log(chalk.yellow(`    ! Base adapters not found. Run 'npm run build' first.`));
    }

    // Generate project rules file
    const rules = this.buildProjectRules();
    const config = {
      rules: rules,
      context: {
        name: this.project?.name || '',
        description: this.project?.description || '',
      },
    };

    await writeFile(join(cursorDir, 'project-rules.json'), JSON.stringify(config, null, 2));
    console.log(chalk.gray(`    Generated .cursor/project-rules.json`));
  }

  private async generateClaudeCode(outputDir: string): Promise<void> {
    const claudeDir = join(outputDir, '.claude');
    await mkdir(claudeDir, { recursive: true });

    // Copy base adapters (prompts, skills)
    const adapterSrcDir = join(rootDir, 'adapters', 'claude-code');
    try {
      await access(adapterSrcDir);

      // Copy prompts
      const promptsDir = join(adapterSrcDir, 'prompts');
      try {
        await access(promptsDir);
        await this.copyDirectory(promptsDir, join(claudeDir, 'prompts'));
      } catch {
        // Prompts might not exist
      }

      // Copy skills
      const skillsDir = join(adapterSrcDir, 'skills');
      try {
        await access(skillsDir);
        await this.copyDirectory(skillsDir, join(claudeDir, 'skills'));
      } catch {
        // Skills might not exist
      }

      // Copy skills.json
      const skillsFile = join(adapterSrcDir, 'skills.json');
      try {
        await access(skillsFile);
        await copyFile(skillsFile, join(claudeDir, 'skills.json'));
      } catch {
        // skills.json might not exist
      }

      console.log(chalk.gray(`    Copied base prompts and skills`));
    } catch {
      console.log(chalk.yellow(`    ! Base adapters not found. Run 'npm run build' first.`));
    }

    // Generate project context file
    const context = {
      project: {
        name: this.project?.name || '',
        description: this.project?.description || '',
        rules: this.buildProjectRules(),
      },
    };

    await writeFile(join(claudeDir, 'project-context.json'), JSON.stringify(context, null, 2));
    console.log(chalk.gray(`    Generated .claude/project-context.json`));
  }

  private buildProjectRules(): string[] {
    const rules: string[] = [];

    if (!this.project) return rules;

    // Add conventions
    if (this.project.conventions) {
      if (this.project.conventions.naming) rules.push(...this.project.conventions.naming);
      if (this.project.conventions.patterns) rules.push(...this.project.conventions.patterns);
      if (this.project.conventions.testing) rules.push(...this.project.conventions.testing);
      if (this.project.conventions.structure) rules.push(...this.project.conventions.structure);
      if (this.project.conventions.custom) rules.push(...this.project.conventions.custom);
    }

    // Add custom AI rules
    if (this.project.ai_tools?.custom_rules) {
      rules.push(...this.project.ai_tools.custom_rules);
    }

    return rules;
  }

  async listProjects(): Promise<void> {
    console.log(chalk.blue('\n📋 Available Projects:\n'));

    console.log(chalk.bold('Global Projects:'));
    await this.listProjectsInDir(join(rootDir, 'projects', 'global'));

    console.log('');
    console.log(chalk.bold('Local Projects:'));
    await this.listProjectsInDir(join(rootDir, 'projects', 'local'));

    console.log('');
  }

  private async listProjectsInDir(dir: string): Promise<void> {
    try {
      const entries = await readdir(dir, { withFileTypes: true });

      for (const entry of entries) {
        if (entry.isDirectory() && entry.name !== '.template') {
          const projectPath = join(dir, entry.name, 'project.yml');
          if (await this.fileExists(projectPath)) {
            const content = await readFile(projectPath, 'utf-8');
            const project = loadYaml(content) as Project;
            console.log(chalk.green(`  • ${entry.name}`) + chalk.gray(` - ${project.description}`));
          }
        }
      }
    } catch (error) {
      console.log(chalk.gray('  (none)'));
    }
  }
}

// CLI - only run if this is the main module
if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2);
  const command = args[0] || 'list';
  const projectId = args[1];
  const tools = args.slice(2);

  const generator = new ProjectGenerator();

  if (command === 'list') {
    generator.listProjects().catch((error) => {
      console.error(chalk.red('Error:'), error.message);
      process.exit(1);
    });
  } else if (command === 'generate') {
    if (!projectId) {
      console.error(chalk.red('Error: Project ID required'));
      console.log('Usage: npm run project:generate <project-id> [tools...]');
      process.exit(1);
    }

    generator.generate(projectId, tools.length > 0 ? tools : ['all']).catch((error) => {
      console.error(chalk.red('Error:'), error.message);
      process.exit(1);
    });
  } else {
    console.error(chalk.red(`Unknown command: ${command}`));
    console.log('Available commands: list, generate');
    process.exit(1);
  }
}
