import { describe, it, expect, vi, beforeEach } from 'vitest';
import { WindsurfAdapter } from './adapter.js';
import { Project } from '../../core/models/types.js';
import { join } from 'path';
import { readFile } from 'fs/promises';

// Mock fs/promises
vi.mock('fs/promises', async () => {
  const actual = await vi.importActual('fs/promises');
  return {
    ...actual,
    mkdir: vi.fn(),
    writeFile: vi.fn(),
    copyFile: vi.fn(),
    readdir: vi.fn().mockResolvedValue([]),
  };
});

// Mock ConfigService
vi.mock('../../core/services/config.service.js', () => ({
  ConfigService: {
    getInstance: () => ({
      getPath: vi.fn().mockReturnValue('/mock/path'),
      dirs: { agents: 'agents', prompts: 'prompts' },
    }),
  },
}));

// Mock LoaderService and ResolverService
vi.mock('../../core/services/loader.service.js', () => {
  return {
    LoaderService: class {
      findYamlFiles = vi.fn().mockResolvedValue([]);
      findYamlFilesRelative = vi.fn().mockResolvedValue([]);
      loadYaml = vi.fn().mockResolvedValue({});
    },
  };
});
vi.mock('../../core/services/resolver.service.js', () => {
  return {
    ResolverService: class {
      shouldIncludeAgent = vi.fn().mockReturnValue(true);
      shouldIncludePrompt = vi.fn().mockReturnValue(true);
      resolveRulepacks = vi.fn().mockResolvedValue([]);
    },
  };
});
vi.mock('../../core/services/recipe.service.js', () => {
  const generateRecipesForTool = vi.fn().mockResolvedValue(undefined);
  return {
    RecipeService: class {
      generateRecipesForTool = generateRecipesForTool;
    },
    // Export the spy so we can check it
    __mockGenerateRecipesForTool: generateRecipesForTool,
  };
});

import { writeFile } from 'fs/promises';

describe('WindsurfAdapter', () => {
  let adapter: WindsurfAdapter;

  beforeEach(() => {
    adapter = new WindsurfAdapter();
    vi.clearAllMocks();
  });

  it('should generate complete project-context.md', async () => {
    const project: Project = {
      id: 'test-project',
      version: '1.0.0',
      name: 'Test Project',
      description: 'A test project',
      context: {
        overview: 'Overview text',
        purpose: 'Purpose text',
      },
      tech_stack: {
        languages: ['typescript'],
        infrastructure: ['docker', 'k8s'],
        tools: ['vitest'],
      },
      commands: {
        build: 'npm run build',
        test: {
          unit: 'npm run test:unit',
        },
      },
      conventions: {
        naming: ['camelCase'],
        testing: ['jest'],
        structure: ['src/'],
      },
      documentation: {
        readme: 'README.md',
        api: {
          v1: 'docs/v1.md',
        },
      },
    };

    await adapter.generate(project, '/output');

    const writeFileMock = writeFile as unknown as ReturnType<typeof vi.fn>;
    const call = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('project-context.md'));
    expect(call).toBeDefined();
    if (!call) return;

    const content = call[1] as string;
    
    // Check for all sections
    expect(content).toContain('# Project: Test Project');
    expect(content).toContain('## Project Overview');
    expect(content).toContain('Overview text');
    
    expect(content).toContain('## Tech Stack');
    expect(content).toContain('**Languages**: typescript');
    expect(content).toContain('**Infrastructure**: docker, k8s');
    expect(content).toContain('**Tools**: vitest');

    expect(content).toContain('## Key Commands');
    expect(content).toContain('- `npm run build` - build');
    expect(content).toContain('- `npm run test:unit` - unit');

    expect(content).toContain('## Project Conventions');
    expect(content).toContain('### Naming');
    expect(content).toContain('- camelCase');
    expect(content).toContain('### Testing');
    expect(content).toContain('- jest');
    expect(content).toContain('### Project Structure');
    expect(content).toContain('- src/');

    expect(content).toContain('## Documentation');
    expect(content).toContain('- **readme**: README.md');
    expect(content).toContain('- **api/v1**: docs/v1.md');
  });

  it('should call recipeService.generateRecipesForTool', async () => {
    const project: Project = {
      id: 'test-project',
      version: '1.0.0',
      name: 'Test Project',
      description: 'A test project',
    };

    await adapter.generate(project, '/output');

    // Verify recipe service was called
    // We import the mocked module which now exposes the spy
    const mockedModule = await import('../../core/services/recipe.service.js') as any;
    const mockGenerateRecipes = mockedModule.__mockGenerateRecipesForTool;
    
    expect(mockGenerateRecipes).toHaveBeenCalledWith('windsurf', '/output', project);
  });
});
