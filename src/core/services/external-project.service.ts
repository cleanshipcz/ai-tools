import { ConfigService } from './config.service.js';
import { LoaderService } from './loader.service.js';
import { join, resolve, basename } from 'path';
import { readFile, writeFile, access, readdir } from 'fs/promises';
import { load as loadYaml, dump as dumpYaml } from 'js-yaml';
import chalk from 'chalk';

export interface ExternalProject {
  path: string;
  alias: string;
  addedAt: string;
}

export interface ExternalProjectRegistry {
  projects: ExternalProject[];
}

export class ExternalProjectService {
  private config = ConfigService.getInstance();
  private loader = new LoaderService();

  async getAllProjects(): Promise<ExternalProject[]> {
    const globalProjects = await this.loadRegistry(this.config.getPath(this.config.dirs.projects, 'projects.global.yml'));
    const localProjects = await this.loadRegistry(this.config.getPath(this.config.dirs.projects, 'projects.local.yml'));

    // Merge, preferring local (user-specific) overrides if alias matches? 
    // Or just combine. The original script combined them.
    // Let's combine but deduplicate by alias if needed, or just return all.
    // Original implementation returned all.

    return [...globalProjects, ...localProjects];
  }

  async addProject(projectPath: string, alias?: string, isGlobal: boolean = false): Promise<void> {
    const absolutePath = resolve(projectPath);
    
    try {
      await access(absolutePath);
    } catch {
      throw new Error(`Project path does not exist: ${absolutePath}`);
    }

    const projectAlias = alias || basename(absolutePath);
    const registryPath = isGlobal 
      ? this.config.getPath(this.config.dirs.projects, 'projects.global.yml')
      : this.config.getPath(this.config.dirs.projects, 'projects.local.yml');

    const projects = await this.loadRegistry(registryPath);

    // Check if already exists
    if (projects.some(p => p.path === absolutePath || p.alias === projectAlias)) {
      console.log(chalk.yellow(`Project already registered: ${projectAlias} (${absolutePath})`));
      return;
    }

    projects.push({
      path: absolutePath,
      alias: projectAlias,
      addedAt: new Date().toISOString()
    });

    await this.saveRegistry(registryPath, projects);
    console.log(chalk.green(`Added external project: ${projectAlias} -> ${absolutePath}`));
  }

  async removeProject(aliasOrPath: string): Promise<void> {
    const globalPath = this.config.getPath(this.config.dirs.projects, 'projects.global.yml');
    const localPath = this.config.getPath(this.config.dirs.projects, 'projects.local.yml');

    let removed = false;

    for (const path of [globalPath, localPath]) {
      const projects = await this.loadRegistry(path);
      const initialLength = projects.length;
      const filtered = projects.filter(p => p.alias !== aliasOrPath && p.path !== aliasOrPath);
      
      if (filtered.length < initialLength) {
        await this.saveRegistry(path, filtered);
        removed = true;
      }
    }

    if (removed) {
      console.log(chalk.green(`Removed project: ${aliasOrPath}`));
    } else {
      console.log(chalk.yellow(`Project not found: ${aliasOrPath}`));
    }
  }

  private async loadRegistry(path: string): Promise<ExternalProject[]> {
    try {
      const content = await readFile(path, 'utf-8');
      const data = loadYaml(content) as any;
      return data?.projects || [];
    } catch {
      return [];
    }
  }

  private async saveRegistry(path: string, projects: ExternalProject[]): Promise<void> {
    // Preserve comments if possible? js-yaml dump loses comments.
    // For now, simple dump.
    const data = {
      projects
    };
    
    // If file doesn't exist, create it with header
    // But here we just overwrite
    await writeFile(path, dumpYaml(data));
  }
}
