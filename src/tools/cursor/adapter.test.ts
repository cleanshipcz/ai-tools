import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CursorAdapter } from './adapter.js';
import { Project } from '../../core/models/types.js';
import * as fs from 'fs/promises';
import { join } from 'path';

// Hoisted mocks
const mocks = vi.hoisted(() => ({
  config: {
    getPath: vi.fn((...args) => join('/mock/root', ...args)),
    dirs: {
      agents: 'agents',
      adapters: 'adapters',
    },
  },
  loader: {
    findYamlFiles: vi.fn(),
    loadYaml: vi.fn(),
  },
  resolver: {
    shouldIncludeAgent: vi.fn(),
    resolveRulepacks: vi.fn(),
  },
  recipeService: {
    generateRecipesForTool: vi.fn(),
  },
  fs: {
    mkdir: vi.fn(),
    writeFile: vi.fn(),
    readFile: vi.fn(),
    access: vi.fn(),
  }
}));

// Mock fs/promises
vi.mock('fs/promises', async () => {
  return {
    ...mocks.fs,
    default: mocks.fs,
  };
});

// Mock ConfigService
vi.mock('../../core/services/config.service.js', () => ({
  ConfigService: {
    getInstance: () => mocks.config,
  },
}));

// Mock LoaderService
vi.mock('../../core/services/loader.service.js', () => ({
  LoaderService: class {
    findYamlFiles = mocks.loader.findYamlFiles;
    loadYaml = mocks.loader.loadYaml;
  },
}));

// Mock ResolverService
vi.mock('../../core/services/resolver.service.js', () => ({
  ResolverService: class {
    shouldIncludeAgent = mocks.resolver.shouldIncludeAgent;
    resolveRulepacks = mocks.resolver.resolveRulepacks;
  },
}));

// Mock RecipeService
vi.mock('../../core/services/recipe.service.js', () => ({
  RecipeService: class {
    generateRecipesForTool = mocks.recipeService.generateRecipesForTool;
  },
}));

describe('CursorAdapter', () => {
  let adapter: CursorAdapter;

  const mockProject: Project = {
    id: 'test-project',
    version: '1.0.0',
    name: 'Test Project',
    description: 'A test project',
    ai_tools: {
        whitelist_agents: ['agent-1']
    }
  };

  beforeEach(() => {
    vi.clearAllMocks();
    adapter = new CursorAdapter();
  });

  it('should generate project rules and recipes.json', async () => {
    const outputDir = '/mock/output';

    // Mock file system findings
    mocks.loader.findYamlFiles.mockResolvedValue(['/mock/root/agents/agent-1.yml']);

    // Mock YAML loading
    mocks.loader.loadYaml.mockImplementation((path: string) => {
      if (path.includes('agent-1')) {
        return Promise.resolve({
          id: 'agent-1',
          purpose: 'Test Agent',
          description: 'Agent Description',
          prompt: { system: 'System Prompt' }
        });
      }
      return Promise.resolve({});
    });

    // Mock Resolver
    mocks.resolver.shouldIncludeAgent.mockReturnValue(true);
    mocks.resolver.resolveRulepacks.mockResolvedValue([]);

    await adapter.generate(mockProject, outputDir);

    // Verify directories created
    expect(mocks.fs.mkdir).toHaveBeenCalledWith(join(outputDir, '.cursor'), { recursive: true });

    // Verify Project Rules
    expect(mocks.fs.writeFile).toHaveBeenCalledWith(
      join(outputDir, '.cursor', 'project-rules.json'),
      expect.stringContaining('"name": "Test Project"')
    );

    // Verify Recipes Generation (recipes.json)
    expect(mocks.fs.writeFile).toHaveBeenCalledWith(
      join(outputDir, '.cursor', 'recipes.json'),
      expect.stringContaining('"id": "agent-1"')
    );

    // Verify Recipe Generation (service call)
    expect(mocks.recipeService.generateRecipesForTool).toHaveBeenCalledWith('cursor', outputDir, mockProject);
  });
});
