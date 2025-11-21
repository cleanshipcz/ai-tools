import { describe, it, expect, vi, beforeEach } from 'vitest';
import { GitHubCopilotAdapter } from './adapter.js';
import { Project, Agent } from '../../core/models/types.js';
import { join } from 'path';
import { writeFile } from 'fs/promises';

// Mock fs/promises
vi.mock('fs/promises', async () => {
  const actual = await vi.importActual('fs/promises');
  return {
    ...actual,
    mkdir: vi.fn(),
    writeFile: vi.fn(),
    copyFile: vi.fn(),
    readFile: vi.fn().mockImplementation((path: string) => {
      if (path.endsWith('instructions.md')) {
        return Promise.resolve('# Base Instructions\n');
      }
      return Promise.resolve('');
    }),
    access: vi.fn().mockResolvedValue(undefined),
  };
});

// Mock ConfigService
vi.mock('../../core/services/config.service.js', () => ({
  ConfigService: {
    getInstance: () => ({
      getPath: vi.fn().mockReturnValue('/mock/path'),
      dirs: { agents: 'agents', adapters: 'adapters' },
    }),
  },
}));

// Mock LoaderService
vi.mock('../../core/services/loader.service.js', () => {
  return {
    LoaderService: class {
      findYamlFiles = vi.fn().mockResolvedValue(['agent1.yml', 'agent2.yml']);
      loadYaml = vi.fn().mockImplementation((file: string) => {
        if (file === 'agent1.yml') {
          return Promise.resolve({
            id: 'agent-1',
            purpose: 'Test Agent 1',
            prompt: { system: 'You are Agent 1' },
            constraints: ['Constraint 1'],
          } as Agent);
        }
        if (file === 'agent2.yml') {
          return Promise.resolve({
            id: 'agent-2',
            purpose: 'Test Agent 2',
            rulepacks: ['rulepack-1'],
          } as Agent);
        }
        return Promise.resolve({});
      });
    },
  };
});

// Mock ResolverService
vi.mock('../../core/services/resolver.service.js', () => {
  return {
    ResolverService: class {
      shouldIncludeAgent = vi.fn().mockReturnValue(true);
      resolveRulepacks = vi.fn().mockResolvedValue(['Rule 1']);
    },
  };
});

// Mock RecipeService
vi.mock('../../core/services/recipe.service.js', () => {
  return {
    RecipeService: class {
      generateRecipesForTool = vi.fn().mockResolvedValue(undefined);
    },
  };
});

describe('GitHubCopilotAdapter', () => {
  let adapter: GitHubCopilotAdapter;

  beforeEach(() => {
    adapter = new GitHubCopilotAdapter();
    vi.clearAllMocks();
  });

  it('should generate instructions.md with project context', async () => {
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
      },
      commands: {
        build: 'npm run build',
      },
    };

    await adapter.generate(project, '/output');

    const writeFileMock = writeFile as unknown as ReturnType<typeof vi.fn>;
    const call = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('instructions.md'));
    expect(call).toBeDefined();
    
    const content = call[1] as string;
    
    // Base content
    expect(content).toContain('# Base Instructions');
    
    // Agents
    expect(content).toContain('## Available Agents');
    expect(content).toContain('### agent-1');
    expect(content).toContain('You are Agent 1');
    expect(content).toContain('### agent-2');
    expect(content).toContain('Rule 1');

    // Project Context
    expect(content).toContain('# Project: Test Project');
    expect(content).toContain('Overview text');
    expect(content).toContain('**Languages:** typescript');
    expect(content).toContain('- `npm run build` - build');
  });

  it('should generate global instructions.md without project context', async () => {
    await adapter.generateGlobal('/output');

    const writeFileMock = writeFile as unknown as ReturnType<typeof vi.fn>;
    const call = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('instructions.md'));
    expect(call).toBeDefined();
    
    const content = call[1] as string;
    
    // Base content
    expect(content).toContain('# Base Instructions');
    
    // Agents
    expect(content).toContain('## Available Agents');
    expect(content).toContain('### agent-1');
    expect(content).toContain('### agent-2');

    // NO Project Context
    expect(content).not.toContain('# Project: Test Project');
    expect(content).not.toContain('Overview text');
  });
});
