import { readFile, access } from 'fs/promises';
import { join, dirname } from 'path';
import { load as loadYaml } from 'js-yaml';

interface ProjectContext {
  overview?: string;
  purpose?: string;
}

interface TechStack {
  languages?: string[];
  frontend?: string[];
  backend?: string[];
  database?: string[];
  infrastructure?: string[];
  tools?: string[];
}

interface Conventions {
  naming?: string[];
  patterns?: string[];
  testing?: string[];
  structure?: string[];
  custom?: string[];
}

interface Project {
  id: string;
  version: string;
  name: string;
  description: string;
  context?: ProjectContext;
  tech_stack?: TechStack;
  commands?: Record<string, string | Record<string, string>>;
  conventions?: Conventions;
  [key: string]: any;
}

export class ProjectContextLoader {
  private startDir: string;
  private project: Project | null = null;

  constructor(startDir: string = process.cwd()) {
    this.startDir = startDir;
  }

  /**
   * Find project.yml file by searching up the directory tree
   * @returns Path to project.yml or null if not found
   */
  async findProjectFile(): Promise<string | null> {
    let currentDir = this.startDir;
    const root = '/';

    while (true) {
      const projectFile = join(currentDir, 'project.yml');

      try {
        await access(projectFile);
        return projectFile;
      } catch {
        // File doesn't exist, continue searching
      }

      // Check if we've reached the root
      const parentDir = dirname(currentDir);
      if (parentDir === currentDir || currentDir === root) {
        return null;
      }

      currentDir = parentDir;
    }
  }

  /**
   * Load and parse the project.yml file
   * @returns Parsed project object or null if not found/invalid
   */
  async loadProject(): Promise<Project | null> {
    const projectFile = await this.findProjectFile();

    if (!projectFile) {
      return null;
    }

    try {
      const content = await readFile(projectFile, 'utf-8');
      this.project = loadYaml(content) as Project;
      return this.project;
    } catch (error) {
      console.error(`Error loading project file: ${error}`);
      return null;
    }
  }

  /**
   * Format project context as markdown for injection into system prompts
   * @returns Formatted markdown string
   */
  formatContextAsMarkdown(): string {
    if (!this.project) {
      return '';
    }

    const lines: string[] = [];

    // Header
    lines.push(`# Project: ${this.project.name}`);
    lines.push('');
    lines.push(this.project.description);
    lines.push('');

    // Project Overview
    if (this.project.context?.overview || this.project.context?.purpose) {
      lines.push('## Project Overview');
      lines.push('');

      if (this.project.context.overview) {
        lines.push(this.project.context.overview);
        lines.push('');
      }

      if (this.project.context.purpose) {
        lines.push(`**Purpose:** ${this.project.context.purpose}`);
        lines.push('');
      }
    }

    // Tech Stack
    if (this.project.tech_stack && this.hasNonEmptyTechStack(this.project.tech_stack)) {
      lines.push('## Tech Stack');
      lines.push('');

      const techStack = this.project.tech_stack;

      if (techStack.languages && techStack.languages.length > 0) {
        lines.push(`**Languages:** ${techStack.languages.join(', ')}`);
      }
      if (techStack.frontend && techStack.frontend.length > 0) {
        lines.push(`**Frontend:** ${techStack.frontend.join(', ')}`);
      }
      if (techStack.backend && techStack.backend.length > 0) {
        lines.push(`**Backend:** ${techStack.backend.join(', ')}`);
      }
      if (techStack.database && techStack.database.length > 0) {
        lines.push(`**Database:** ${techStack.database.join(', ')}`);
      }
      if (techStack.infrastructure && techStack.infrastructure.length > 0) {
        lines.push(`**Infrastructure:** ${techStack.infrastructure.join(', ')}`);
      }
      if (techStack.tools && techStack.tools.length > 0) {
        lines.push(`**Tools:** ${techStack.tools.join(', ')}`);
      }

      lines.push('');
    }

    // Commands
    if (this.project.commands && Object.keys(this.project.commands).length > 0) {
      lines.push('## Key Commands');
      lines.push('');

      for (const [key, value] of Object.entries(this.project.commands)) {
        if (typeof value === 'string') {
          lines.push(`- \`${value}\` - ${key}`);
        } else if (typeof value === 'object') {
          for (const [subKey, subValue] of Object.entries(value)) {
            lines.push(`- \`${subValue}\` - ${key}:${subKey}`);
          }
        }
      }

      lines.push('');
    }

    // Conventions
    if (this.project.conventions && this.hasNonEmptyConventions(this.project.conventions)) {
      lines.push('## Project Conventions');
      lines.push('');

      const conventions = this.project.conventions;

      if (conventions.naming && conventions.naming.length > 0) {
        lines.push('### Naming');
        lines.push('');
        conventions.naming.forEach((rule) => lines.push(`- ${rule}`));
        lines.push('');
      }

      if (conventions.patterns && conventions.patterns.length > 0) {
        lines.push('### Patterns');
        lines.push('');
        conventions.patterns.forEach((rule) => lines.push(`- ${rule}`));
        lines.push('');
      }

      if (conventions.testing && conventions.testing.length > 0) {
        lines.push('### Testing');
        lines.push('');
        conventions.testing.forEach((rule) => lines.push(`- ${rule}`));
        lines.push('');
      }

      if (conventions.structure && conventions.structure.length > 0) {
        lines.push('### Structure');
        lines.push('');
        conventions.structure.forEach((rule) => lines.push(`- ${rule}`));
        lines.push('');
      }

      if (conventions.custom && conventions.custom.length > 0) {
        lines.push('### Custom');
        lines.push('');
        conventions.custom.forEach((rule) => lines.push(`- ${rule}`));
        lines.push('');
      }
    }

    return lines.join('\n');
  }

  /**
   * Get context formatted for system prompt injection
   * @returns Formatted context string
   */
  getContextForSystemPrompt(): string {
    return this.formatContextAsMarkdown();
  }

  /**
   * Check if tech stack has any non-empty arrays
   */
  private hasNonEmptyTechStack(techStack: TechStack): boolean {
    return !!(
      (techStack.languages && techStack.languages.length > 0) ||
      (techStack.frontend && techStack.frontend.length > 0) ||
      (techStack.backend && techStack.backend.length > 0) ||
      (techStack.database && techStack.database.length > 0) ||
      (techStack.infrastructure && techStack.infrastructure.length > 0) ||
      (techStack.tools && techStack.tools.length > 0)
    );
  }

  /**
   * Check if conventions has any non-empty arrays
   */
  private hasNonEmptyConventions(conventions: Conventions): boolean {
    return !!(
      (conventions.naming && conventions.naming.length > 0) ||
      (conventions.patterns && conventions.patterns.length > 0) ||
      (conventions.testing && conventions.testing.length > 0) ||
      (conventions.structure && conventions.structure.length > 0) ||
      (conventions.custom && conventions.custom.length > 0)
    );
  }
}
