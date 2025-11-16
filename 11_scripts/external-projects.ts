#!/usr/bin/env node
import { readFile, writeFile, access, mkdir } from 'fs/promises';
import { join, dirname, resolve } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml, dump as dumpYaml } from 'js-yaml';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface ExternalProject {
  path: string;
  alias?: string;
}

interface ProjectRegistry {
  projects: ExternalProject[];
}

export class ExternalProjectManager {
  private globalRegistryPath = join(rootDir, '06_projects', 'projects.global.yml');
  private localRegistryPath = join(rootDir, '06_projects', 'projects.local.yml');

  async list(): Promise<void> {
    console.log(chalk.blue('\n📋 External Projects:\n'));

    // Load global registry
    console.log(chalk.bold('Global Projects:'));
    const globalProjects = await this.loadRegistry(this.globalRegistryPath);
    if (globalProjects.length === 0) {
      console.log(chalk.gray('  (none)'));
    } else {
      for (const project of globalProjects) {
        const alias = project.alias || this.getAliasFromPath(project.path);
        console.log(chalk.green(`  • ${alias}`) + chalk.gray(` - ${project.path}`));
      }
    }

    console.log('');

    // Load local registry
    console.log(chalk.bold('Local Projects:'));
    const localProjects = await this.loadRegistry(this.localRegistryPath);
    if (localProjects.length === 0) {
      console.log(chalk.gray('  (none)'));
    } else {
      for (const project of localProjects) {
        const alias = project.alias || this.getAliasFromPath(project.path);
        console.log(chalk.green(`  • ${alias}`) + chalk.gray(` - ${project.path}`));
      }
    }

    console.log('');
  }

  async add(path: string, options: { global?: boolean; alias?: string } = {}): Promise<void> {
    const isGlobal = options.global || false;
    const registryPath = isGlobal ? this.globalRegistryPath : this.localRegistryPath;
    const scope = isGlobal ? 'global' : 'local';

    console.log(chalk.blue(`\n➕ Adding external project to ${scope} registry\n`));

    // Resolve and validate path
    const resolvedPath = resolve(path);

    try {
      await access(resolvedPath);
    } catch {
      throw new Error(`Path does not exist: ${resolvedPath}`);
    }

    // Check if it has project.yml
    const projectYmlPath = join(resolvedPath, 'project.yml');
    try {
      await access(projectYmlPath);
      console.log(chalk.gray(`  Found project.yml`));
    } catch {
      console.log(chalk.yellow(`  Warning: No project.yml found at ${resolvedPath}`));
      console.log(chalk.yellow(`  You may need to create one for the project to work correctly`));
    }

    // Load registry
    const projects = await this.loadRegistry(registryPath);

    // Check if already exists
    const exists = projects.some((p) => resolve(p.path) === resolvedPath);
    if (exists) {
      throw new Error(`Project already in registry: ${resolvedPath}`);
    }

    // Add to registry
    const alias = options.alias || this.getAliasFromPath(resolvedPath);
    projects.push({
      path: resolvedPath,
      alias: alias,
    });

    // Save registry
    await this.saveRegistry(registryPath, projects);

    console.log(chalk.green(`✅ Added: ${alias}`));
    console.log(chalk.gray(`   Path: ${resolvedPath}\n`));
  }

  async remove(aliasOrPath: string, options: { global?: boolean } = {}): Promise<void> {
    const isGlobal = options.global || false;
    const registryPath = isGlobal ? this.globalRegistryPath : this.localRegistryPath;
    const scope = isGlobal ? 'global' : 'local';

    console.log(chalk.blue(`\n➖ Removing external project from ${scope} registry\n`));

    // Load registry
    const projects = await this.loadRegistry(registryPath);

    // Find project by alias or path
    const resolvedPath = resolve(aliasOrPath);
    const index = projects.findIndex(
      (p) => p.alias === aliasOrPath || resolve(p.path) === resolvedPath
    );

    if (index === -1) {
      throw new Error(`Project not found in ${scope} registry: ${aliasOrPath}`);
    }

    const removed = projects[index];
    projects.splice(index, 1);

    // Save registry
    await this.saveRegistry(registryPath, projects);

    console.log(chalk.green(`✅ Removed: ${removed.alias || removed.path}\n`));
  }

  async getAllProjects(): Promise<Array<{ path: string; alias: string; scope: string }>> {
    const result: Array<{ path: string; alias: string; scope: string }> = [];

    // Load global
    const globalProjects = await this.loadRegistry(this.globalRegistryPath);
    for (const project of globalProjects) {
      result.push({
        path: project.path,
        alias: project.alias || this.getAliasFromPath(project.path),
        scope: 'global',
      });
    }

    // Load local
    const localProjects = await this.loadRegistry(this.localRegistryPath);
    for (const project of localProjects) {
      result.push({
        path: project.path,
        alias: project.alias || this.getAliasFromPath(project.path),
        scope: 'local',
      });
    }

    return result;
  }

  private async loadRegistry(path: string): Promise<ExternalProject[]> {
    try {
      const content = await readFile(path, 'utf-8');
      const registry = loadYaml(content) as ProjectRegistry;
      return registry.projects || [];
    } catch {
      // File doesn't exist or is empty
      return [];
    }
  }

  private async saveRegistry(path: string, projects: ExternalProject[]): Promise<void> {
    await mkdir(dirname(path), { recursive: true });
    const registry: ProjectRegistry = { projects };
    await writeFile(path, dumpYaml(registry, { lineWidth: -1, noRefs: true }), 'utf-8');
  }

  private getAliasFromPath(path: string): string {
    // Get the parent directory name (assume .cleanship-ai-tools is at the end)
    const parts = path.split('/').filter(Boolean);

    // If path ends with .cleanship-ai-tools, use parent dir name
    if (parts[parts.length - 1] === '.cleanship-ai-tools') {
      return parts[parts.length - 2] || 'external-project';
    }

    return parts[parts.length - 1] || 'external-project';
  }
}

// CLI
if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2);
  const command = args[0];

  const manager = new ExternalProjectManager();

  if (command === 'list') {
    manager.list().catch((error) => {
      console.error(chalk.red('\n❌ Error:'), error.message);
      process.exit(1);
    });
  } else if (command === 'add') {
    const path = args[1];
    if (!path) {
      console.error(chalk.red('Error: Path required'));
      console.log('\nUsage: external-projects add <path> [--global] [--alias <name>]');
      process.exit(1);
    }

    const options = {
      global: args.includes('--global'),
      alias: args.includes('--alias') ? args[args.indexOf('--alias') + 1] : undefined,
    };

    manager.add(path, options).catch((error) => {
      console.error(chalk.red('\n❌ Error:'), error.message);
      process.exit(1);
    });
  } else if (command === 'remove') {
    const aliasOrPath = args[1];
    if (!aliasOrPath) {
      console.error(chalk.red('Error: Alias or path required'));
      console.log('\nUsage: external-projects remove <alias-or-path> [--global]');
      process.exit(1);
    }

    const options = {
      global: args.includes('--global'),
    };

    manager.remove(aliasOrPath, options).catch((error) => {
      console.error(chalk.red('\n❌ Error:'), error.message);
      process.exit(1);
    });
  } else {
    console.error(chalk.red(`Unknown command: ${command || '(none)'}`));
    console.log('\nAvailable commands:');
    console.log('  list                                       - List all external projects');
    console.log('  add <path> [--global] [--alias <name>]     - Add external project');
    console.log('  remove <alias-or-path> [--global]          - Remove external project');
    console.log('\nExamples:');
    console.log('  npm run project:external list');
    console.log('  npm run project:external add /path/to/project/.cleanship-ai-tools');
    console.log('  npm run project:external add /path/to/project --alias my-app --global');
    console.log('  npm run project:external remove my-app');
    process.exit(1);
  }
}
