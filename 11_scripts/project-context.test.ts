import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdir, writeFile, rm } from 'fs/promises';
import { join } from 'path';
import { ProjectContextLoader } from './project-context.js';
import { dump as dumpYaml } from 'js-yaml';

describe('ProjectContextLoader', () => {
  // Use /tmp to avoid finding project.yml in parent directories
  const testDir = join('/tmp', '.test-project-context-' + Date.now());

  beforeEach(async () => {
    await mkdir(testDir, { recursive: true });
  });

  afterEach(async () => {
    await rm(testDir, { recursive: true, force: true });
  });

  describe('findProjectFile', () => {
    it('should find project.yml in current directory', async () => {
      const projectFile = join(testDir, 'project.yml');
      await writeFile(projectFile, 'id: test-project\n');

      const loader = new ProjectContextLoader(testDir);
      const found = await loader.findProjectFile();

      expect(found).toBe(projectFile);
    });

    it('should find project.yml in parent directory', async () => {
      const subDir = join(testDir, 'sub', 'nested');
      await mkdir(subDir, { recursive: true });

      const projectFile = join(testDir, 'project.yml');
      await writeFile(projectFile, 'id: test-project\n');

      const loader = new ProjectContextLoader(subDir);
      const found = await loader.findProjectFile();

      expect(found).toBe(projectFile);
    });

    it('should return null if no project.yml found', async () => {
      const loader = new ProjectContextLoader(testDir);
      const found = await loader.findProjectFile();

      expect(found).toBeNull();
    });

    it('should stop at filesystem root', async () => {
      const loader = new ProjectContextLoader('/');
      const found = await loader.findProjectFile();

      expect(found).toBeNull();
    });
  });

  describe('loadProject', () => {
    it('should load and parse project.yml', async () => {
      const projectData = {
        id: 'test-project',
        version: '1.0.0',
        name: 'Test Project',
        description: 'A test project for unit tests',
        context: {
          overview: 'This is a test project',
          purpose: 'For testing purposes',
        },
        tech_stack: {
          languages: ['typescript', 'javascript'],
          backend: ['node.js'],
        },
      };

      const projectFile = join(testDir, 'project.yml');
      await writeFile(projectFile, dumpYaml(projectData));

      const loader = new ProjectContextLoader(testDir);
      const project = await loader.loadProject();

      expect(project).not.toBeNull();
      expect(project?.id).toBe('test-project');
      expect(project?.name).toBe('Test Project');
      expect(project?.context?.overview).toBe('This is a test project');
      expect(project?.tech_stack?.languages).toEqual(['typescript', 'javascript']);
    });

    it('should return null if project file not found', async () => {
      const loader = new ProjectContextLoader(testDir);
      const project = await loader.loadProject();

      expect(project).toBeNull();
    });

    it('should handle invalid YAML gracefully', async () => {
      const projectFile = join(testDir, 'project.yml');
      await writeFile(projectFile, 'invalid: yaml: content: [[[');

      const loader = new ProjectContextLoader(testDir);
      const project = await loader.loadProject();

      expect(project).toBeNull();
    });
  });

  describe('formatContextAsMarkdown', () => {
    it('should format complete project context as markdown', async () => {
      const projectData = {
        id: 'test-project',
        version: '1.0.0',
        name: 'Test Project',
        description: 'A test project for unit tests',
        context: {
          overview: 'This is a test project',
          purpose: 'For testing purposes',
        },
        tech_stack: {
          languages: ['typescript', 'javascript'],
          backend: ['node.js', 'express'],
          database: ['postgresql'],
        },
        commands: {
          dev: 'npm run dev',
          build: 'npm run build',
          test: 'npm test',
        },
        conventions: {
          naming: ['Use camelCase for variables', 'Use PascalCase for classes'],
          testing: ['Write tests first', 'Use Vitest'],
        },
      };

      const projectFile = join(testDir, 'project.yml');
      await writeFile(projectFile, dumpYaml(projectData));

      const loader = new ProjectContextLoader(testDir);
      await loader.loadProject();
      const markdown = loader.formatContextAsMarkdown();

      expect(markdown).toContain('# Project: Test Project');
      expect(markdown).toContain('A test project for unit tests');
      expect(markdown).toContain('## Project Overview');
      expect(markdown).toContain('This is a test project');
      expect(markdown).toContain('For testing purposes');
      expect(markdown).toContain('## Tech Stack');
      expect(markdown).toContain('**Languages:** typescript, javascript');
      expect(markdown).toContain('**Backend:** node.js, express');
      expect(markdown).toContain('**Database:** postgresql');
      expect(markdown).toContain('## Key Commands');
      expect(markdown).toContain('- `npm run dev` - dev');
      expect(markdown).toContain('- `npm run build` - build');
      expect(markdown).toContain('- `npm test` - test');
      expect(markdown).toContain('## Project Conventions');
      expect(markdown).toContain('### Naming');
      expect(markdown).toContain('- Use camelCase for variables');
      expect(markdown).toContain('- Use PascalCase for classes');
      expect(markdown).toContain('### Testing');
      expect(markdown).toContain('- Write tests first');
      expect(markdown).toContain('- Use Vitest');
    });

    it('should handle minimal project with only required fields', async () => {
      const projectData = {
        id: 'minimal-project',
        version: '1.0.0',
        name: 'Minimal Project',
        description: 'Minimal project with only required fields',
      };

      const projectFile = join(testDir, 'project.yml');
      await writeFile(projectFile, dumpYaml(projectData));

      const loader = new ProjectContextLoader(testDir);
      await loader.loadProject();
      const markdown = loader.formatContextAsMarkdown();

      expect(markdown).toContain('# Project: Minimal Project');
      expect(markdown).toContain('Minimal project with only required fields');
      expect(markdown).not.toContain('## Project Overview');
      expect(markdown).not.toContain('## Tech Stack');
      expect(markdown).not.toContain('## Key Commands');
      expect(markdown).not.toContain('## Project Conventions');
    });

    it('should return empty string if no project loaded', () => {
      const loader = new ProjectContextLoader(testDir);
      const markdown = loader.formatContextAsMarkdown();

      expect(markdown).toBe('');
    });

    it('should handle empty sections gracefully', async () => {
      const projectData = {
        id: 'test-project',
        version: '1.0.0',
        name: 'Test Project',
        description: 'Test project',
        tech_stack: {
          languages: [],
        },
        commands: {},
        conventions: {
          naming: [],
        },
      };

      const projectFile = join(testDir, 'project.yml');
      await writeFile(projectFile, dumpYaml(projectData));

      const loader = new ProjectContextLoader(testDir);
      await loader.loadProject();
      const markdown = loader.formatContextAsMarkdown();

      expect(markdown).toContain('# Project: Test Project');
      expect(markdown).not.toContain('## Tech Stack');
      expect(markdown).not.toContain('## Key Commands');
      expect(markdown).not.toContain('## Project Conventions');
    });
  });

  describe('getContextForSystemPrompt', () => {
    it('should return formatted context suitable for system prompt', async () => {
      const projectData = {
        id: 'test-project',
        version: '1.0.0',
        name: 'Test Project',
        description: 'A test project',
        context: {
          overview: 'Test overview',
        },
        tech_stack: {
          languages: ['typescript'],
        },
      };

      const projectFile = join(testDir, 'project.yml');
      await writeFile(projectFile, dumpYaml(projectData));

      const loader = new ProjectContextLoader(testDir);
      await loader.loadProject();
      const context = loader.getContextForSystemPrompt();

      expect(context).toContain('# Project: Test Project');
      expect(context).toContain('Test overview');
      expect(context).toContain('typescript');
    });

    it('should return empty string if no project loaded', () => {
      const loader = new ProjectContextLoader(testDir);
      const context = loader.getContextForSystemPrompt();

      expect(context).toBe('');
    });
  });
});
