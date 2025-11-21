import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ClaudeAdapter } from './adapter.js';
import { Project } from '../../core/models/types.js';
import * as fs from 'fs/promises';
import { join } from 'path';

// Hoisted mocks to be accessible inside vi.mock and tests
const mocks = vi.hoisted(() => ({
  config: {
    getPath: vi.fn((...args) => join('/mock/root', ...args)),
    dirs: {
      prompts: 'prompts',
      agents: 'agents',
      skills: 'skills',
      adapters: 'adapters',
    },
  },
  loader: {
    findYamlFiles: vi.fn(),
    findYamlFilesRelative: vi.fn(),
    loadYaml: vi.fn(),
  },
  resolver: {
    shouldIncludeAgent: vi.fn(),
    shouldIncludePrompt: vi.fn(),
    resolveRulepacks: vi.fn(),
  },
  recipeService: {
    generateRecipesForTool: vi.fn(),
  },
  fs: {
    mkdir: vi.fn(),
    writeFile: vi.fn(),
    copyFile: vi.fn(),
    readdir: vi.fn(),
    access: vi.fn(),
    readFile: vi.fn(),
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
    findYamlFilesRelative = mocks.loader.findYamlFilesRelative;
    loadYaml = mocks.loader.loadYaml;
  },
}));

// Mock ResolverService
vi.mock('../../core/services/resolver.service.js', () => ({
  ResolverService: class {
    shouldIncludeAgent = mocks.resolver.shouldIncludeAgent;
    shouldIncludePrompt = mocks.resolver.shouldIncludePrompt;
    resolveRulepacks = mocks.resolver.resolveRulepacks;
  },
}));

// Mock RecipeService
vi.mock('../../core/services/recipe.service.js', () => ({
  RecipeService: class {
    generateRecipesForTool = mocks.recipeService.generateRecipesForTool;
  },
}));

describe('ClaudeAdapter', () => {
  let adapter: ClaudeAdapter;

  const mockProject: Project = {
    id: 'test-project',
    version: '1.0.0',
    name: 'Test Project',
    description: 'A test project',
    ai_tools: {
        whitelist_agents: ['agent-1'],
        whitelist_prompts: ['prompt-1']
    }
  };

  beforeEach(() => {
    vi.clearAllMocks();
    adapter = new ClaudeAdapter();
  });

  it('should generate project context, agents, prompts, and skills', async () => {
    const outputDir = '/mock/output';

    // Mock file system findings
    mocks.loader.findYamlFilesRelative.mockResolvedValue(['prompt-1.yml']);
    mocks.loader.findYamlFiles.mockImplementation((dir: string) => {
        if (dir.includes('agents')) return Promise.resolve(['/mock/root/agents/agent-1.yml']);
        if (dir.includes('skills')) return Promise.resolve(['/mock/root/skills/skill-1/skill.yml']);
        return Promise.resolve([]);
    });

    // Mock YAML loading
    mocks.loader.loadYaml.mockImplementation((path: string) => {
      if (path.includes('prompt-1')) {
        return Promise.resolve({
          id: 'prompt-1',
          description: 'Test Prompt',
          content: 'Prompt Content',
          variables: []
        });
      }
      if (path.includes('agent-1')) {
        return Promise.resolve({
          id: 'agent-1',
          purpose: 'Test Agent',
          description: 'Agent Description',
          prompt: { system: 'System Prompt' }
        });
      }
      if (path.includes('skill-1')) {
        return Promise.resolve({
          id: 'skill-1',
          description: 'Test Skill'
        });
      }
      return Promise.resolve({});
    });

    // Mock Resolver
    mocks.resolver.shouldIncludePrompt.mockReturnValue(true);
    mocks.resolver.shouldIncludeAgent.mockReturnValue(true);
    mocks.resolver.resolveRulepacks.mockResolvedValue([]);

    // Mock fs.readdir for skill directory copy
    mocks.fs.readdir.mockResolvedValue(['SKILL.md']);

    await adapter.generate(mockProject, outputDir);

    // Verify directories created
    expect(mocks.fs.mkdir).toHaveBeenCalledWith(join(outputDir, '.claude'), { recursive: true });
    expect(mocks.fs.mkdir).toHaveBeenCalledWith(join(outputDir, '.claude', 'prompts'), { recursive: true });
    expect(mocks.fs.mkdir).toHaveBeenCalledWith(join(outputDir, '.claude', 'agents'), { recursive: true });
    expect(mocks.fs.mkdir).toHaveBeenCalledWith(join(outputDir, '.claude', 'skills', 'skill-1'), { recursive: true });

    // Verify Project Context
    expect(mocks.fs.writeFile).toHaveBeenCalledWith(
      join(outputDir, '.claude', 'project-context.json'),
      expect.stringContaining('"name": "Test Project"')
    );

    // Verify Prompt Generation
    expect(mocks.fs.writeFile).toHaveBeenCalledWith(
      join(outputDir, '.claude', 'prompts', 'prompt-1.json'),
      expect.stringContaining('"id": "prompt-1"')
    );

    // Verify Agent Generation
    expect(mocks.fs.writeFile).toHaveBeenCalledWith(
      join(outputDir, '.claude', 'agents', 'agent-1.md'),
      expect.stringContaining('# Test Agent')
    );

    // Verify Skill Generation (skills.json and copy)
    expect(mocks.fs.writeFile).toHaveBeenCalledWith(
      join(outputDir, '.claude', 'skills.json'),
      expect.stringContaining('"id": "skill-1"')
    );
    expect(mocks.fs.copyFile).toHaveBeenCalled(); // For skill files

    // Verify Recipe Generation
    expect(mocks.recipeService.generateRecipesForTool).toHaveBeenCalledWith('claude-code', outputDir, mockProject);
  });
});
