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
    whitelist_agents?: string[];
    whitelist_prompts?: string[];
    whitelist_rulepacks?: string[];
    blacklist_agents?: string[];
    blacklist_prompts?: string[];
    blacklist_rulepacks?: string[];
  };
  metadata?: {
    repository?: string;
    maintainers?: string[];
    created?: string;
    updated?: string;
    tags?: string[];
  };
}

interface PromptInfo {
  id: string;
  path: string; // e.g., "refactor/extract-method"
}

export class ProjectGenerator {
  private project: Project | null = null;
  private projectDir: string = '';
  private promptsMap = new Map<string, string>(); // Maps prompt ID to its path

  async generate(projectId: string, tools: string[] = ['all']): Promise<void> {
    console.log(chalk.blue(`\n🔨 Generating project configurations for: ${projectId}\n`));

    // Load prompts map
    await this.loadPromptsMap();

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
      ? ['github-copilot', 'windsurf', 'cursor', 'claude-code', 'copilot-cli']
      : tools;

    for (const tool of toolsToGenerate) {
      await this.generateForTool(tool, outputDir);
    }

    // Generate features if they exist
    await this.generateFeatures(projectId, outputDir);

    console.log(chalk.green(`\n✅ Generation complete! Output in: ${outputDir}\n`));
  }

  private async generateFeatures(projectId: string, outputDir: string): Promise<void> {
    // Check if features directory exists
    const featuresDir = join(this.projectDir, 'features');
    try {
      await access(featuresDir);

      // Import and use FeatureGenerator
      const { FeatureGenerator } = await import('./gen-features.js');
      const featureGen = new FeatureGenerator();
      await featureGen.generateFeatures(projectId);
    } catch {
      // No features directory, skip
    }
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
      // Check external projects
      const externalPath = await this.findExternalProject(projectId);
      if (externalPath) {
        projectPath = join(externalPath, 'project.yml');
        exists = await this.fileExists(projectPath);
      }
    }

    if (!exists) {
      throw new Error(`Project "${projectId}" not found in global, local, or external projects`);
    }

    this.projectDir = dirname(projectPath);
    const content = await readFile(projectPath, 'utf-8');
    this.project = loadYaml(content) as Project;

    console.log(chalk.gray(`  Loaded project: ${this.project.name}`));
  }

  private async findExternalProject(projectId: string): Promise<string | null> {
    try {
      const { ExternalProjectManager } = await import('./external-projects.js');
      const manager = new ExternalProjectManager();
      const projects = await manager.getAllProjects();

      const found = projects.find((p) => p.alias === projectId);
      return found ? found.path : null;
    } catch {
      return null;
    }
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
      case 'copilot-cli':
        await this.generateCopilotCLI(toolOutputDir);
        break;
      default:
        console.log(chalk.yellow(`    Unknown tool: ${tool}`));
    }
  }

  private async loadPromptsMap(): Promise<void> {
    const promptsDir = join(rootDir, 'prompts');
    try {
      const files = await this.findYamlFilesRelative(promptsDir);

      for (const file of files) {
        const fullPath = join(promptsDir, file);
        const content = await readFile(fullPath, 'utf-8');
        const prompt = loadYaml(content) as any;

        if (prompt.id) {
          // Store the path without extension (e.g., "refactor/extract-method")
          const pathWithoutExt = file.replace(/\.ya?ml$/, '').replace(/\\/g, '/');
          this.promptsMap.set(prompt.id, pathWithoutExt);
        }
      }
    } catch (error) {
      // Prompts directory might not exist
    }
  }

  private async findYamlFilesRelative(dir: string, basePath: string = ''): Promise<string[]> {
    const files: string[] = [];

    try {
      const entries = await readdir(dir, { withFileTypes: true });

      for (const entry of entries) {
        const relativePath = join(basePath, entry.name).replace(/\\/g, '/');
        const fullPath = join(dir, entry.name);

        if (entry.isDirectory()) {
          // Skip shared directory as it contains includes, not prompts
          if (entry.name !== 'shared') {
            files.push(...(await this.findYamlFilesRelative(fullPath, relativePath)));
          }
        } else if (
          entry.isFile() &&
          (entry.name.endsWith('.yml') || entry.name.endsWith('.yaml'))
        ) {
          files.push(relativePath);
        }
      }
    } catch (error) {
      // Directory doesn't exist
    }

    return files;
  }

  private shouldIncludeAgent(agentId: string): boolean {
    if (!this.project?.ai_tools) return true;

    const { whitelist_agents, blacklist_agents } = this.project.ai_tools;

    // If whitelist is defined, only include agents in the whitelist
    if (whitelist_agents && whitelist_agents.length > 0) {
      return whitelist_agents.includes(agentId);
    }

    // If blacklist is defined, exclude agents in the blacklist
    if (blacklist_agents && blacklist_agents.length > 0) {
      return !blacklist_agents.includes(agentId);
    }

    // No filtering configured, include all
    return true;
  }

  private shouldIncludePrompt(promptIdOrPath: string): boolean {
    if (!this.project?.ai_tools) return true;

    const { whitelist_prompts, blacklist_prompts } = this.project.ai_tools;

    // Get the full path for this prompt (e.g., "refactor/extract-method")
    // promptIdOrPath could be either the ID or already a path
    const promptPath = this.promptsMap.get(promptIdOrPath) || promptIdOrPath;

    // If whitelist is defined, only include prompts in the whitelist
    if (whitelist_prompts && whitelist_prompts.length > 0) {
      return whitelist_prompts.some((pattern) => {
        // Support both "extract-method" and "refactor/extract-method" formats
        return (
          promptPath === pattern || promptPath.endsWith(`/${pattern}`) || promptIdOrPath === pattern
        );
      });
    }

    // If blacklist is defined, exclude prompts in the blacklist
    if (blacklist_prompts && blacklist_prompts.length > 0) {
      return !blacklist_prompts.some((pattern) => {
        // Support both "extract-method" and "refactor/extract-method" formats
        return (
          promptPath === pattern || promptPath.endsWith(`/${pattern}`) || promptIdOrPath === pattern
        );
      });
    }

    // No filtering configured, include all
    return true;
  }

  private shouldIncludeRulepack(rulepackId: string): boolean {
    if (!this.project?.ai_tools) return true;

    const { whitelist_rulepacks, blacklist_rulepacks } = this.project.ai_tools;

    // If whitelist is defined, only include rulepacks in the whitelist
    if (whitelist_rulepacks && whitelist_rulepacks.length > 0) {
      return whitelist_rulepacks.includes(rulepackId);
    }

    // If blacklist is defined, exclude rulepacks in the blacklist
    if (blacklist_rulepacks && blacklist_rulepacks.length > 0) {
      return !blacklist_rulepacks.includes(rulepackId);
    }

    // No filtering configured, include all
    return true;
  }

  private async generateGitHubCopilot(outputDir: string): Promise<void> {
    const githubDir = join(outputDir, '.github');
    await mkdir(githubDir, { recursive: true });

    // Copy base adapters (prompts, instructions) with filtering
    const adapterSrcDir = join(rootDir, 'adapters', 'github-copilot', '.github');
    try {
      await access(adapterSrcDir);
      await this.copyDirectoryWithFiltering(adapterSrcDir, githubDir);
      console.log(chalk.gray(`    Copied base prompts and instructions`));
    } catch {
      console.log(chalk.yellow(`    ! Base adapters not found. Run 'npm run build' first.`));
    }

    // Generate project-specific copilot-instructions.md
    const instructions = this.buildCopilotInstructions();
    await writeFile(join(githubDir, 'copilot-instructions.md'), instructions, 'utf-8');

    console.log(chalk.gray(`    Generated .github/copilot-instructions.md`));
  }

  private async copyDirectoryWithFiltering(src: string, dest: string): Promise<void> {
    await mkdir(dest, { recursive: true });

    const entries = await readdir(src, { withFileTypes: true });

    for (const entry of entries) {
      const srcPath = join(src, entry.name);
      const destPath = join(dest, entry.name);

      if (entry.isDirectory()) {
        await this.copyDirectoryWithFiltering(srcPath, destPath);
      } else {
        // Apply filtering based on file name patterns
        if (entry.name.startsWith('agent-')) {
          // Extract agent ID from filename: agent-code-reviewer.prompt.md -> code-reviewer
          const agentId = entry.name.replace(/^agent-/, '').replace(/\.prompt\.md$/, '');
          if (!this.shouldIncludeAgent(agentId)) {
            continue; // Skip this agent file
          }
        } else if (entry.name.endsWith('.prompt.md') && entry.name.startsWith('prompt-')) {
          // Regular prompt file - the filename format is "prompt-<category>-<prompt-id>.prompt.md"
          // where only the first hyphen after "prompt-" separates category from ID
          // (e.g., "prompt-refactor-extract-method.prompt.md")
          const filenameWithoutExt = entry.name
            .replace(/\.prompt\.md$/, '')
            .replace(/^prompt-/, '');

          // Convert filename back to path format by replacing only the first hyphen
          // (e.g., "refactor-extract-method" -> "refactor/extract-method")
          const promptPath = filenameWithoutExt.replace('-', '/');

          // Check against our prompts map to find which prompt this corresponds to
          // and pass the path to shouldIncludePrompt for matching
          let shouldInclude = false;
          for (const [id, path] of this.promptsMap.entries()) {
            if (path === promptPath) {
              // Pass the path (not the ID) since the whitelist uses paths
              shouldInclude = this.shouldIncludePrompt(path);
              break;
            }
          }

          if (!shouldInclude) {
            continue; // Skip this prompt file
          }
        } else if (entry.name.endsWith('.instructions.md')) {
          // Rulepack instructions file
          const rulepackId = entry.name.replace(/\.instructions\.md$/, '');
          if (!this.shouldIncludeRulepack(rulepackId)) {
            continue; // Skip this rulepack file
          }
        }

        await copyFile(srcPath, destPath);
      }
    }
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

    // Copy base adapters (rules, presets) with filtering
    const adapterSrcDir = join(rootDir, 'adapters', 'windsurf');
    try {
      await access(adapterSrcDir);

      // Copy rules with filtering
      const rulesDir = join(adapterSrcDir, 'rules');
      const destRulesDir = join(windsurfDir, 'rules');
      await access(rulesDir);
      await this.copyWindsurfRulesWithFiltering(rulesDir, destRulesDir);

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

  private async copyWindsurfRulesWithFiltering(src: string, dest: string): Promise<void> {
    await mkdir(dest, { recursive: true });

    const entries = await readdir(src, { withFileTypes: true });

    for (const entry of entries) {
      const srcPath = join(src, entry.name);
      const destPath = join(dest, entry.name);

      if (entry.isDirectory()) {
        await this.copyWindsurfRulesWithFiltering(srcPath, destPath);
      } else if (entry.name.endsWith('.json')) {
        // Check if this is an agent rule file
        const agentId = entry.name.replace(/\.json$/, '');
        if (!this.shouldIncludeAgent(agentId)) {
          continue; // Skip this agent file
        }
        await copyFile(srcPath, destPath);
      } else {
        await copyFile(srcPath, destPath);
      }
    }
  }

  private async generateCursor(outputDir: string): Promise<void> {
    const cursorDir = join(outputDir, '.cursor');
    await mkdir(cursorDir, { recursive: true });

    // Copy base adapters (recipes) with filtering
    const adapterSrcDir = join(rootDir, 'adapters', 'cursor');
    try {
      await access(adapterSrcDir);

      // Copy and filter recipes.json if it exists
      const recipesFile = join(adapterSrcDir, 'recipes.json');
      try {
        await access(recipesFile);
        const recipesContent = await readFile(recipesFile, 'utf-8');
        const recipesData = JSON.parse(recipesContent);

        // Filter recipes (agents)
        if (recipesData.recipes) {
          recipesData.recipes = recipesData.recipes.filter((recipe: any) =>
            this.shouldIncludeAgent(recipe.id)
          );
        }

        await writeFile(join(cursorDir, 'recipes.json'), JSON.stringify(recipesData, null, 2));
        console.log(chalk.gray(`    Copied and filtered recipes`));
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

    // Copy base adapters (prompts, skills) with filtering
    const adapterSrcDir = join(rootDir, 'adapters', 'claude-code');
    try {
      await access(adapterSrcDir);

      // Copy prompts with filtering
      const promptsDir = join(adapterSrcDir, 'prompts');
      try {
        await access(promptsDir);
        await this.copyClaudePromptsWithFiltering(promptsDir, join(claudeDir, 'prompts'));
      } catch {
        // Prompts might not exist
      }

      // Copy skills (not filtered by agents/prompts)
      const skillsDir = join(adapterSrcDir, 'skills');
      try {
        await access(skillsDir);
        await this.copyDirectory(skillsDir, join(claudeDir, 'skills'));
      } catch {
        // Skills might not exist
      }

      // Copy skills.json (not filtered)
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

  private async generateCopilotCLI(outputDir: string): Promise<void> {
    console.log(chalk.blue(`  Generating for copilot-cli...`));

    // Generate complete AGENTS.md with base agents + project-specific content
    const agentsContent = await this.buildCopilotCLIAgentsFile();
    await writeFile(join(outputDir, 'AGENTS.md'), agentsContent, 'utf-8');

    console.log(chalk.gray(`    Generated AGENTS.md with project context`));
  }

  private async buildCopilotCLIAgentsFile(): Promise<string> {
    const lines: string[] = [];

    // Read base AGENTS.md from adapters
    const adapterSrcDir = join(rootDir, 'adapters', 'copilot-cli');
    try {
      await access(adapterSrcDir);
      const baseAgentsFile = join(adapterSrcDir, 'AGENTS.md');
      const baseContent = await readFile(baseAgentsFile, 'utf-8');
      lines.push(baseContent);
      lines.push('');
      lines.push('---');
      lines.push('');
    } catch {
      // Base adapters not found, continue with project-specific content only
    }

    if (!this.project) return lines.join('\n');

    // Add project-specific section
    lines.push(`# Project: ${this.project.name}`);
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
        lines.push(`**Purpose:** ${this.project.context.purpose}`);
        lines.push('');
      }
    }

    // Tech Stack
    if (this.project.tech_stack) {
      lines.push('## Tech Stack');
      lines.push('');
      if (this.project.tech_stack.languages?.length) {
        lines.push(`**Languages:** ${this.project.tech_stack.languages.join(', ')}`);
      }
      if (this.project.tech_stack.frontend?.length) {
        lines.push(`**Frontend:** ${this.project.tech_stack.frontend.join(', ')}`);
      }
      if (this.project.tech_stack.backend?.length) {
        lines.push(`**Backend:** ${this.project.tech_stack.backend.join(', ')}`);
      }
      if (this.project.tech_stack.database?.length) {
        lines.push(`**Database:** ${this.project.tech_stack.database.join(', ')}`);
      }
      if (this.project.tech_stack.infrastructure?.length) {
        lines.push(`**Infrastructure:** ${this.project.tech_stack.infrastructure.join(', ')}`);
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
      lines.push('## Project Conventions');
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

    if (this.project.ai_tools?.preferred_agents?.length) {
      lines.push('## Preferred Agents');
      lines.push('');
      for (const agent of this.project.ai_tools.preferred_agents) {
        lines.push(`- ${agent}`);
      }
      lines.push('');
    }

    return lines.join('\n');
  }

  private async copyClaudePromptsWithFiltering(src: string, dest: string): Promise<void> {
    await mkdir(dest, { recursive: true });

    const entries = await readdir(src, { withFileTypes: true });

    for (const entry of entries) {
      const srcPath = join(src, entry.name);
      const destPath = join(dest, entry.name);

      if (entry.isDirectory()) {
        await this.copyClaudePromptsWithFiltering(srcPath, destPath);
      } else if (entry.name.endsWith('.json')) {
        // Claude Code uses JSON files for prompts
        const promptId = entry.name.replace(/\.json$/, '');
        if (!this.shouldIncludePrompt(promptId)) {
          continue; // Skip this prompt file
        }
        await copyFile(srcPath, destPath);
      } else {
        await copyFile(srcPath, destPath);
      }
    }
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
    console.log(chalk.bold('External Projects:'));
    await this.listExternalProjects();

    console.log('');
  }

  private async listExternalProjects(): Promise<void> {
    try {
      const { ExternalProjectManager } = await import('./external-projects.js');
      const manager = new ExternalProjectManager();
      const projects = await manager.getAllProjects();

      if (projects.length === 0) {
        console.log(chalk.gray('  (none)'));
        return;
      }

      for (const project of projects) {
        const projectPath = join(project.path, 'project.yml');
        if (await this.fileExists(projectPath)) {
          const content = await readFile(projectPath, 'utf-8');
          const projectData = loadYaml(content) as Project;
          const scopeLabel = project.scope === 'global' ? '[global]' : '[local]';
          console.log(
            chalk.green(`  • ${project.alias}`) +
              chalk.gray(` - ${projectData.description}`) +
              chalk.dim(` ${scopeLabel}`)
          );
        } else {
          console.log(
            chalk.yellow(`  • ${project.alias}`) +
              chalk.gray(` - No project.yml found`) +
              chalk.dim(` [${project.scope}]`)
          );
        }
      }
    } catch (error) {
      console.log(chalk.gray('  (none)'));
    }
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
