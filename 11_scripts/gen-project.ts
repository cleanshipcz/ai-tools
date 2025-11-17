#!/usr/bin/env node
import { readFile, writeFile, mkdir, readdir, access, copyFile } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

type AIModel =
  | 'claude-sonnet-4.5'
  | 'claude-sonnet-4'
  | 'claude-haiku-4.5'
  | 'gpt-5'
  | 'gpt-5.1'
  | 'gpt-5.1-codex-mini'
  | 'gpt-5.1-codex';

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
    model?: AIModel;
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

interface Agent {
  id: string;
  version: string;
  purpose: string;
  description?: string;
  rulepacks?: string[];
  defaults?: {
    temperature?: number;
    model?: AIModel;
    style?: string;
  };
  prompt?: {
    system?: string;
    user_template?: string;
  };
  tools?: string[];
  constraints?: string[];
  metadata?: {
    author?: string;
    created?: string;
    updated?: string;
    tags?: string[];
  };
}

interface Recipe {
  id: string;
  version: string;
  description: string;
  tags?: string[];
  tools?: string[];
  conversationStrategy?: string;
  toolOptions?: Record<string, any>;
  variables?: Record<string, string>;
  model?: AIModel;
  steps: RecipeStep[];
  loop?: {
    steps: string[];
    maxIterations?: number;
  };
  metadata?: {
    author?: string;
    created?: string;
  };
}

interface RecipeStep {
  id: string;
  agent: string;
  task: string;
  model?: AIModel;
  outputDocument?: string;
  includeDocuments?: string[];
  continueConversation?: boolean;
  waitForConfirmation?: boolean;
  condition?: {
    type: string;
    check?: {
      type: string;
      value?: string;
    };
  };
}

export class ProjectGenerator {
  private project: Project | null = null;
  private projectDir: string = '';
  private promptsMap = new Map<string, string>(); // Maps prompt ID to its path
  private agentsCache = new Map<string, Agent>(); // Cache loaded agents

  // Public method to allow sharing agent loading logic
  public async loadAgentById(agentId: string): Promise<Agent | null> {
    return this.loadAgent(agentId);
  }

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

    // Merge feature workflows into main windsurf output
    await this.mergeFeatureWorkflows(projectId, outputDir);

    console.log(chalk.green(`\n✅ Generation complete! Output in: ${outputDir}\n`));
  }

  private async mergeFeatureWorkflows(projectId: string, outputDir: string): Promise<void> {
    // Check if features output exists
    const featuresOutputDir = join(
      rootDir,
      '.output',
      projectId,
      'features',
      '.windsurf',
      'workflows'
    );

    try {
      await access(featuresOutputDir);

      // Copy workflows to main windsurf output
      const windsurfOutputDir = join(outputDir, 'windsurf', '.windsurf', 'workflows');
      await mkdir(windsurfOutputDir, { recursive: true });

      const entries = await readdir(featuresOutputDir);
      for (const entry of entries) {
        const srcPath = join(featuresOutputDir, entry);
        const destPath = join(windsurfOutputDir, entry);
        await copyFile(srcPath, destPath);
      }

      console.log(chalk.gray(`  Merged ${entries.length} workflow(s) into windsurf output`));
    } catch {
      // No features or workflows, skip
    }
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
    let projectPath = join(rootDir, '06_projects', 'global', projectId, 'project.yml');
    let exists = await this.fileExists(projectPath);

    if (!exists) {
      // Check local projects
      projectPath = join(rootDir, '06_projects', 'local', projectId, 'project.yml');
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

    // Generate recipes for this tool (recipes go in shared directory)
    await this.generateRecipes(tool, outputDir);
  }

  private async loadPromptsMap(): Promise<void> {
    const promptsDir = join(rootDir, '03_prompts');
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

  private async loadAgent(agentId: string): Promise<Agent | null> {
    // Check cache first
    if (this.agentsCache.has(agentId)) {
      return this.agentsCache.get(agentId)!;
    }

    // Load from file
    const agentPath = join(rootDir, '04_agents', `${agentId}.yml`);
    try {
      const content = await readFile(agentPath, 'utf-8');
      const agent = loadYaml(content) as Agent;
      this.agentsCache.set(agentId, agent);
      return agent;
    } catch (error) {
      console.warn(chalk.yellow(`    ! Warning: Agent ${agentId} not found`));
      return null;
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

    // Default Model Configuration
    if (this.project.ai_tools?.model) {
      lines.push(`**Default Model for this Project:** ${this.project.ai_tools.model}`);
      lines.push('');
      lines.push(
        '*This overrides agent defaults but can be overridden by feature-specific model settings.*'
      );
      lines.push('');
    }

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

    // Copy base adapters (rules) with filtering
    const adapterSrcDir = join(rootDir, 'adapters', 'windsurf');
    try {
      await access(adapterSrcDir);

      // Copy rules with filtering (now markdown files)
      const rulesDir = join(adapterSrcDir, 'rules');
      const destRulesDir = join(windsurfDir, 'rules');
      await access(rulesDir);
      await this.copyWindsurfRulesWithFiltering(rulesDir, destRulesDir);

      console.log(chalk.gray(`    Copied base rules`));
    } catch {
      console.log(chalk.yellow(`    ! Base adapters not found. Run 'npm run build' first.`));
    }

    // Generate project-specific context file with trigger: always_on
    await this.generateWindsurfProjectContext(windsurfDir);
  }

  private async generateWindsurfProjectContext(windsurfDir: string): Promise<void> {
    const rulesDir = join(windsurfDir, 'rules');
    await mkdir(rulesDir, { recursive: true });

    const content: string[] = [];

    // YAML frontmatter with always_on trigger
    content.push('---');
    content.push('trigger: always_on');
    content.push('---');
    content.push('');

    // Project header
    content.push(`# Project: ${this.project?.name || 'Unknown Project'}`);
    content.push('');
    content.push(this.project?.description || '');
    content.push('');

    // Overview
    if (this.project?.context?.overview) {
      content.push('## Project Overview');
      content.push('');
      content.push(this.project.context.overview);
      content.push('');
    }

    if (this.project?.context?.purpose) {
      content.push(`**Purpose:** ${this.project.context.purpose}`);
      content.push('');
    }

    // Tech Stack
    if (this.project?.tech_stack) {
      content.push('## Tech Stack');
      content.push('');

      const techStack = this.project.tech_stack;
      if (techStack.languages && techStack.languages.length > 0) {
        content.push(`**Languages:** ${techStack.languages.join(', ')}`);
      }
      if (techStack.frontend && techStack.frontend.length > 0) {
        content.push(`**Frontend:** ${techStack.frontend.join(', ')}`);
      }
      if (techStack.backend && techStack.backend.length > 0) {
        content.push(`**Backend:** ${techStack.backend.join(', ')}`);
      }
      if (techStack.database && techStack.database.length > 0) {
        content.push(`**Database:** ${techStack.database.join(', ')}`);
      }
      if (techStack.infrastructure && techStack.infrastructure.length > 0) {
        content.push(`**Infrastructure:** ${techStack.infrastructure.join(', ')}`);
      }
      content.push('');
    }

    // Commands
    if (this.project?.commands) {
      content.push('## Key Commands');
      content.push('');
      this.formatCommandSection(this.project.commands, content);
      content.push('');
    }

    // Conventions
    if (this.project?.conventions) {
      content.push('## Project Conventions');
      content.push('');

      if (this.project.conventions.naming && this.project.conventions.naming.length > 0) {
        content.push('### Naming');
        content.push('');
        for (const rule of this.project.conventions.naming) {
          content.push(`- ${rule}`);
        }
        content.push('');
      }

      if (this.project.conventions.patterns && this.project.conventions.patterns.length > 0) {
        content.push('### Patterns');
        content.push('');
        for (const rule of this.project.conventions.patterns) {
          content.push(`- ${rule}`);
        }
        content.push('');
      }

      if (this.project.conventions.testing && this.project.conventions.testing.length > 0) {
        content.push('### Testing');
        content.push('');
        for (const rule of this.project.conventions.testing) {
          content.push(`- ${rule}`);
        }
        content.push('');
      }

      if (this.project.conventions.structure && this.project.conventions.structure.length > 0) {
        content.push('### Project Structure');
        content.push('');
        for (const rule of this.project.conventions.structure) {
          content.push(`- ${rule}`);
        }
        content.push('');
      }
    }

    // Project-specific rules
    if (this.project?.ai_tools?.custom_rules && this.project.ai_tools.custom_rules.length > 0) {
      content.push('## Project-Specific Rules');
      content.push('');
      for (const rule of this.project.ai_tools.custom_rules) {
        content.push(`- ${rule}`);
      }
      content.push('');
    }

    // Documentation references
    if (this.project?.documentation) {
      content.push('## Documentation');
      content.push('');
      this.formatDocumentationSection(this.project.documentation, content);
      content.push('');
    }

    // Preferred agents
    if (
      this.project?.ai_tools?.preferred_agents &&
      this.project.ai_tools.preferred_agents.length > 0
    ) {
      content.push('## Preferred Agents');
      content.push('');
      for (const agent of this.project.ai_tools.preferred_agents) {
        content.push(`- ${agent}`);
      }
      content.push('');
    }

    await writeFile(join(rulesDir, 'project-context.md'), content.join('\n'));
    console.log(chalk.gray(`    Generated .windsurf/rules/project-context.md`));
  }

  private formatCommandSection(
    commands: Record<string, string | Record<string, string>>,
    content: string[]
  ): void {
    for (const [category, value] of Object.entries(commands)) {
      if (typeof value === 'string') {
        content.push(`- \`${value}\` - ${category}`);
      } else if (typeof value === 'object') {
        content.push(`### ${category.charAt(0).toUpperCase() + category.slice(1)}`);
        for (const [subcategory, command] of Object.entries(value)) {
          content.push(`- \`${command}\` - ${subcategory}`);
        }
        content.push('');
      }
    }
  }

  private formatDocumentationSection(
    docs: Record<string, string | Record<string, string>>,
    content: string[]
  ): void {
    for (const [category, value] of Object.entries(docs)) {
      if (typeof value === 'string') {
        content.push(`- **${category}**: ${value}`);
      } else if (typeof value === 'object') {
        for (const [subcategory, path] of Object.entries(value)) {
          content.push(`- **${subcategory}**: ${path}`);
        }
      }
    }
  }

  private async copyWindsurfRulesWithFiltering(src: string, dest: string): Promise<void> {
    await mkdir(dest, { recursive: true });

    const entries = await readdir(src, { withFileTypes: true });

    for (const entry of entries) {
      const srcPath = join(src, entry.name);
      const destPath = join(dest, entry.name);

      if (entry.isDirectory()) {
        await this.copyWindsurfRulesWithFiltering(srcPath, destPath);
      } else if (entry.name.endsWith('.md')) {
        // Check if this is an agent or prompt rule file
        if (entry.name.startsWith('agent-')) {
          const agentId = entry.name.replace(/^agent-/, '').replace(/\.md$/, '');
          if (!this.shouldIncludeAgent(agentId)) {
            continue; // Skip this agent file
          }
        } else if (entry.name.startsWith('prompt-')) {
          // Convert prompt-docs-create-tutorial.md to docs/create-tutorial
          const withoutPrefix = entry.name.replace(/^prompt-/, '').replace(/\.md$/, '');
          const promptPath = withoutPrefix.replace(/-/, '/'); // Only replace FIRST hyphen
          if (!this.shouldIncludePrompt(promptPath)) {
            continue; // Skip this prompt file
          }
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

      // Copy agents with filtering
      const agentsDir = join(adapterSrcDir, 'agents');
      try {
        await access(agentsDir);
        await this.copyClaudeAgentsWithFiltering(agentsDir, join(claudeDir, 'agents'));
      } catch {
        // Agents might not exist
      }

      console.log(chalk.gray(`    Copied base prompts, skills, and agents`));
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

  private async generateRecipes(tool: string, outputDir: string): Promise<void> {
    // Only generate recipes for CLI-based tools that support automated workflows
    const supportedTools = ['claude-code', 'copilot-cli', 'cursor'];
    if (!supportedTools.includes(tool)) {
      return;
    }

    const recipesDir = join(rootDir, '05_recipes');
    let recipeFiles: string[];
    try {
      const entries = await readdir(recipesDir);
      recipeFiles = entries.filter((f) => f.endsWith('.yml') || f.endsWith('.yaml'));
    } catch {
      // No recipes directory
      return;
    }

    if (recipeFiles.length === 0) {
      return;
    }

    // Create .cs.recipes inside the tool's config directory
    const toolOutputDir = join(outputDir, tool);
    let recipesOutputDir: string;

    // Tool-specific paths to keep recipes with their tool configs
    if (tool === 'claude-code') {
      recipesOutputDir = join(toolOutputDir, '.claude', '.cs.recipes');
    } else if (tool === 'copilot-cli') {
      // copilot-cli generates AGENTS.md at root, so put recipes alongside
      recipesOutputDir = join(toolOutputDir, '.cs.recipes');
    } else if (tool === 'cursor') {
      recipesOutputDir = join(toolOutputDir, '.cursor', '.cs.recipes');
    } else if (tool === 'github-copilot') {
      recipesOutputDir = join(toolOutputDir, '.github', '.cs.recipes');
    } else if (tool === 'windsurf') {
      recipesOutputDir = join(toolOutputDir, '.windsurf', '.cs.recipes');
    } else {
      // Fallback for unknown tools
      recipesOutputDir = join(toolOutputDir, '.cs.recipes');
    }

    await mkdir(recipesOutputDir, { recursive: true });

    let generatedCount = 0;

    for (const recipeFile of recipeFiles) {
      const recipeId = recipeFile.replace(/\.ya?ml$/, '');

      // Load recipe to check if it supports this tool
      const recipePath = join(recipesDir, recipeFile);
      const recipeContent = await readFile(recipePath, 'utf-8');
      const recipe = loadYaml(recipeContent) as any;

      // Skip if recipe doesn't support this tool (when tools are specified)
      if (recipe.tools && Array.isArray(recipe.tools) && !recipe.tools.includes(tool)) {
        continue;
      }

      // Generate script inline (without verbose console output)
      const scriptPath = join(recipesOutputDir, `${recipeId}.sh`);
      await this.generateRecipeScript(recipe, tool, scriptPath);
      generatedCount++;
    }

    if (generatedCount > 0) {
      // Construct readable path based on tool
      let pathDisplay: string;
      if (tool === 'claude-code') {
        pathDisplay = '.claude/.cs.recipes/';
      } else if (tool === 'copilot-cli') {
        pathDisplay = '.cs.recipes/ (alongside AGENTS.md)';
      } else if (tool === 'cursor') {
        pathDisplay = '.cursor/.cs.recipes/';
      } else if (tool === 'github-copilot') {
        pathDisplay = '.github/.cs.recipes/';
      } else if (tool === 'windsurf') {
        pathDisplay = '.windsurf/.cs.recipes/';
      } else {
        pathDisplay = '.cs.recipes/';
      }
      console.log(chalk.gray(`    Generated ${generatedCount} recipe script(s) in ${pathDisplay}`));
    }
  }

  private async generateRecipeScript(recipe: any, tool: string, scriptPath: string): Promise<void> {
    const script = await this.buildRecipeScript(recipe, tool, null);
    await writeFile(scriptPath, script, { mode: 0o755 });
  }

  private async buildRecipeScript(recipe: any, tool: string, featureContext: any): Promise<string> {
    let script = '#!/bin/bash\n';
    script += `# Auto-generated recipe script: ${recipe.id}\n`;
    script += `# Description: ${recipe.description}\n`;
    script += `# Tool: ${tool}\n`;
    script += `# Generated: ${new Date().toISOString()}\n\n`;
    script += 'set -e  # Exit on error\n\n';

    // Setup directories for logs and documents
    script += '# Setup directories\n';
    script += 'RECIPE_DOCS_DIR=".recipe-docs"\n';
    script += 'mkdir -p "$RECIPE_DOCS_DIR"\n';
    script += 'RECIPE_LOGS_DIR=".recipe-logs"\n';
    script += 'mkdir -p "$RECIPE_LOGS_DIR"\n';
    script += 'echo "📁 Documents: $RECIPE_DOCS_DIR"\n\n';

    // Setup logging
    script += '# Setup logging\n';
    script += `LOG_FILE="$RECIPE_LOGS_DIR/${recipe.id}-$(date +%Y%m%d-%H%M%S).log"\n`;
    script += 'echo "📝 Logging to: $LOG_FILE"\n';
    script += 'echo ""\n\n';

    // Start logging with exec and tee
    script += '# Redirect all output to both console and log file\n';
    script += 'exec > >(tee -a "$LOG_FILE") 2>&1\n\n';

    // Add project context loading function
    script += '# Function to load project context\n';
    script += 'load_project_context() {\n';
    script += '  if command -v npx &> /dev/null; then\n';
    script +=
      '    PROJECT_CONTEXT=$(npx tsx 11_scripts/format-project-context.ts 2>/dev/null || echo "")\n';
    script += '  else\n';
    script += '    PROJECT_CONTEXT=""\n';
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

    // Add feature context variables if provided
    if (featureContext) {
      script += '# Feature Context\n';
      for (const [key, value] of Object.entries(featureContext)) {
        const varName = key.toUpperCase();
        const escapedValue = (value as string).replace(/"/g, '\\"');
        script += `${varName}="${escapedValue}"\n`;
      }
      script += '\n';
    }

    // Add recipe variables
    if (recipe.variables) {
      script += '# Variables\n';
      for (const [key, value] of Object.entries(recipe.variables)) {
        const varName = key.toUpperCase();
        let varValue = (value as string).replace(
          /{{([^}]+)}}/g,
          (_, v) => `\${${v.toUpperCase()}}`
        );

        // If feature context provides this variable, use it as default
        if (featureContext && featureContext[key.toLowerCase()]) {
          varValue = `\${${varName}}`;
        }

        script += `: \${${varName}:="${varValue}"}\n`;
      }
      script += '\n';
    }

    // Separate steps into pre-loop, loop, and post-loop
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

    // Generate pre-loop steps
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

    // Generate loop if defined
    if (recipe.loop && loopSteps.length > 0) {
      const maxIterations = recipe.loop.maxIterations || 3;

      script += `# Loop: ${loopStepIds.join(' → ')} (max ${maxIterations} iterations)\n`;
      script += `for iteration in $(seq 1 ${maxIterations}); do\n`;
      script += `  echo "\\n▶️  Iteration $iteration/${maxIterations}"\n`;
      script += `  \n`;

      for (let i = 0; i < loopSteps.length; i++) {
        const stepScript = await this.generateStepScript(
          loopSteps[i],
          i,
          tool,
          recipe.variables,
          conversationStrategy,
          toolOptions,
          recipe
        );
        // Indent loop content
        script += stepScript
          .split('\n')
          .map((line: string) => (line ? `  ${line}` : line))
          .join('\n');
      }

      script += `done\n\n`;
    }

    // Generate post-loop steps
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
    return script;
  }

  // Static helper for agent loading (can be used by other generators)
  public static async loadAgentStatic(agentId: string): Promise<Agent | null> {
    const agentPath = join(rootDir, '04_agents', `${agentId}.yml`);
    try {
      const content = await readFile(agentPath, 'utf-8');
      const agent = loadYaml(content) as Agent;
      return agent;
    } catch (error) {
      return null;
    }
  }

  private async generateStepScript(
    step: any,
    index: number,
    tool: string,
    variables: any,
    conversationStrategy: string = 'separate',
    toolOptions?: any,
    recipe?: any
  ): Promise<string> {
    // Use shared static method
    return ProjectGenerator.generateStepScriptStatic(
      step,
      index,
      tool,
      variables,
      conversationStrategy,
      toolOptions,
      recipe,
      (agentId) => this.loadAgent(agentId)
    );
  }

  // Shared static method for generating step scripts
  // Can be used by both ProjectGenerator and FeatureGenerator
  public static async generateStepScriptStatic(
    step: any,
    index: number,
    tool: string,
    variables: any,
    conversationStrategy: string = 'separate',
    toolOptions?: any,
    recipe?: any,
    loadAgentFn?: (agentId: string) => Promise<Agent | null>
  ): Promise<string> {
    let script = '';

    script += `# Step: ${step.id}\n`;
    script += `echo "▶️  ${step.id} (${step.agent})"\n`;

    // Interpolate variables in task
    let task = step.task;
    if (variables) {
      for (const key of Object.keys(variables)) {
        task = task.replace(new RegExp(`{{${key}}}`, 'g'), `\${${key.toUpperCase()}}`);
      }
    }

    // Include reference documents from previous steps if specified
    if (step.includeDocuments && step.includeDocuments.length > 0) {
      task += '\n\n---\n\n## Reference Documents (Context)\n\n';
      for (const docPath of step.includeDocuments) {
        const docFileName = docPath.split('/').pop();
        task += `### Document: \`${docPath}\`\n\n`;
        task += `\${DOC_CONTENT_${docFileName?.toUpperCase().replace(/[^A-Z0-9]/g, '_')}}\n\n---\n\n`;
      }
      task += '\n**Please use the documents above as context for your work.**\n\n';
    }

    // Add output document instruction if specified
    if (step.outputDocument) {
      task += `\n\n---\n\n**IMPORTANT**: Save your complete response to the file: \`${step.outputDocument}\`\n`;
    }

    // Determine if should continue conversation based on strategy
    const shouldContinue =
      conversationStrategy === 'continue' && step.continueConversation !== false && index > 0;

    // Generate tool-specific command with response capture
    if (tool === 'claude-code') {
      const flags: string[] = [];

      // Continuation
      if (shouldContinue) {
        flags.push('--continue');
      }

      // Model selection (step-level > recipe-level > agent default)
      const model = step.model || recipe?.model;
      if (model) {
        flags.push(`--model "${model}"`);
      }

      // Use @agent syntax instead of --system-prompt
      // Agent system prompts are loaded from .claude/agents/ by claude CLI

      // Tool permissions from toolOptions
      if (toolOptions?.['claude-code']) {
        const opts = toolOptions['claude-code'];
        if (opts.allowedTools) {
          const tools = Array.isArray(opts.allowedTools)
            ? opts.allowedTools.join(',')
            : opts.allowedTools;
          flags.push(`--allowed-tools "${tools}"`);
        }
        if (opts.disallowedTools) {
          const tools = Array.isArray(opts.disallowedTools)
            ? opts.disallowedTools.join(',')
            : opts.disallowedTools;
          flags.push(`--disallowed-tools "${tools}"`);
        }
        if (opts.allowAllTools) {
          flags.push('--allow-all-tools');
        }
      }

      // Non-interactive mode (use --print for scripting)
      flags.push('--print');

      const flagsStr = flags.length > 0 ? ' ' + flags.join(' ') : '';

      // Build the complete task with document loading
      let fullTask = task;
      if (step.includeDocuments && step.includeDocuments.length > 0) {
        script += `# Load documents for this step\n`;
        for (const docPath of step.includeDocuments) {
          const docFileName = docPath.split('/').pop();
          const varName = `DOC_CONTENT_${docFileName?.toUpperCase().replace(/[^A-Z0-9]/g, '_')}`;
          script += `if [ -f ".recipe-docs/${docFileName}" ]; then\n`;
          script += `  ${varName}=$(cat ".recipe-docs/${docFileName}")\n`;
          script += `else\n`;
          script += `  ${varName}="[Document not found: ${docPath}]"\n`;
          script += `fi\n`;
        }
        script += `\n`;
        // Replace the placeholders in task with actual variables
        for (const docPath of step.includeDocuments) {
          const docFileName = docPath.split('/').pop();
          const varName = `DOC_CONTENT_${docFileName?.toUpperCase().replace(/[^A-Z0-9]/g, '_')}`;
          fullTask = fullTask.replace(`\${${varName}}`, `$${varName}`);
        }
      }

      // Escape backticks in fullTask for bash execution (prevent command substitution)
      const escapedTask = fullTask.replace(/`/g, '\\`').replace(/"/g, '\\"');

      // Build system prompt with project context and recipe info
      script += `# Build system prompt with project context and recipe info\n`;
      script += `SYSTEM_PROMPT=""\n`;
      script += `if [ -n "$PROJECT_CONTEXT" ]; then\n`;
      script += `  SYSTEM_PROMPT="$PROJECT_CONTEXT\\n\\n---\\n\\n"\n`;
      script += `fi\n`;
      script += `SYSTEM_PROMPT="\${SYSTEM_PROMPT}Recipe: ${recipe?.id || 'unknown'}\\nStep: ${index + 1} (${step.id})"\n\n`;

      script += `echo "⚡ Executing with claude-code..."\n`;
      script += `RESPONSE=$(claude @${step.agent}${flagsStr} \\\n`;
      script += `  --append-system-prompt "$SYSTEM_PROMPT" \\\n`;
      script += `  --permission-mode acceptEdits \\\n`;
      script += `  --allowedTools "Bash(git diff *),Read,Write,Edit" \\\n`;
      script += `  -p "${escapedTask}")\n`;
      script += `echo "$RESPONSE"\n`;
    } else if (tool === 'copilot-cli') {
      const flags: string[] = [];

      if (shouldContinue) {
        flags.push('--continue');
      }

      // Add tool-specific options
      if (toolOptions?.['copilot-cli']) {
        const opts = toolOptions['copilot-cli'];
        if (opts.allowAllTools) flags.push('--allow-all-tools');
        if (opts.allowAllPaths) flags.push('--allow-all-paths');
        if (opts.disallowTempDir) flags.push('--disallow-temp-dir');
        if (opts.addDirs) {
          opts.addDirs.forEach((dir: string) => flags.push(`--add-dir "${dir}"`));
        }
        if (opts.allowTools) {
          opts.allowTools.forEach((tool: string) => flags.push(`--allow-tool "${tool}"`));
        }
        if (opts.denyTools) {
          opts.denyTools.forEach((tool: string) => flags.push(`--deny-tool "${tool}"`));
        }
      }

      const flagsStr = flags.length > 0 ? ' ' + flags.join(' ') : '';

      // Build the complete task with document loading
      let fullTask = task;
      if (step.includeDocuments && step.includeDocuments.length > 0) {
        script += `# Load documents for this step\n`;
        for (const docPath of step.includeDocuments) {
          const docFileName = docPath.split('/').pop();
          const varName = `DOC_CONTENT_${docFileName?.toUpperCase().replace(/[^A-Z0-9]/g, '_')}`;
          script += `if [ -f ".recipe-docs/${docFileName}" ]; then\n`;
          script += `  ${varName}=$(cat ".recipe-docs/${docFileName}")\n`;
          script += `else\n`;
          script += `  ${varName}="[Document not found: ${docPath}]"\n`;
          script += `fi\n`;
        }
        script += `\n`;
        // Replace the placeholders in task with actual variables
        for (const docPath of step.includeDocuments) {
          const docFileName = docPath.split('/').pop();
          const varName = `DOC_CONTENT_${docFileName?.toUpperCase().replace(/[^A-Z0-9]/g, '_')}`;
          fullTask = fullTask.replace(`\${${varName}}`, `$${varName}`);
        }
      }

      // Escape backticks in fullTask for bash execution (prevent command substitution)
      const escapedTask = fullTask.replace(/`/g, '\\`').replace(/"/g, '\\"');
      script += `RESPONSE=$(echo "@${step.agent} ${escapedTask}" | copilot${flagsStr})\n`;
      script += `echo "$RESPONSE"\n`;
    } else if (tool === 'cursor') {
      script += `# Manual: Open Cursor Composer and execute:\n`;

      // Build the complete task with document loading
      let fullTask = task;
      if (step.includeDocuments && step.includeDocuments.length > 0) {
        script += `# Load documents for manual execution:\n`;
        for (const docPath of step.includeDocuments) {
          const docFileName = docPath.split('/').pop();
          script += `# - .recipe-docs/${docFileName}\n`;
        }
      }

      script += `# @${step.agent} ${fullTask}\n`;
      script += `echo "⚠️  Please execute in Cursor Composer and press Enter"\n`;
      script += `read -p "Continue? "\n`;
      script += `RESPONSE=""\n`;
    }

    // Save output document if specified
    if (step.outputDocument) {
      const docFileName = step.outputDocument.split('/').pop();
      script += `# Save step output to document\n`;
      script += `echo "$RESPONSE" > ".recipe-docs/${docFileName}"\n`;
      script += `echo "✓ Document saved: ${step.outputDocument}"\n\n`;
    }

    // Handle conditional execution of next steps
    if (step.condition) {
      if (step.condition.type === 'on-success' && step.condition.check) {
        const check = step.condition.check;
        if (check.type === 'contains' && check.value) {
          script += `\n# Check condition for next step\n`;
          script += `if [[ ! "$RESPONSE" == *"${check.value}"* ]]; then\n`;
          script += `  echo "⚠️  Condition not met (expected: ${check.value})"\n`;
          script += `  exit 1\n`;
          script += `fi\n`;
        }
      }
    }

    script += '\n';
    return script;
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

  private async copyClaudeAgentsWithFiltering(src: string, dest: string): Promise<void> {
    await mkdir(dest, { recursive: true });

    const entries = await readdir(src, { withFileTypes: true });

    for (const entry of entries) {
      const srcPath = join(src, entry.name);
      const destPath = join(dest, entry.name);

      if (entry.isDirectory()) {
        await this.copyClaudeAgentsWithFiltering(srcPath, destPath);
      } else if (entry.name.endsWith('.md')) {
        // Claude Code agents are .md files with YAML frontmatter
        // Extract agent ID from filename (e.g., "code-reviewer.md" -> "code-reviewer")
        const agentId = entry.name.replace(/\.md$/, '');
        if (!this.shouldIncludeAgent(agentId)) {
          continue; // Skip this agent file
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
    await this.listProjectsInDir(join(rootDir, '06_projects', 'global'));

    console.log('');
    console.log(chalk.bold('Local Projects:'));
    await this.listProjectsInDir(join(rootDir, '06_projects', 'local'));

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
