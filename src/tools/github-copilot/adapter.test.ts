import { describe, it, expect, vi, beforeEach } from 'vitest';
import { GitHubCopilotAdapter } from './adapter.js';
import { Project, Agent } from '../../core/models/types.js';
import { join } from 'path';
import { writeFile } from 'fs/promises';

// Mock fs/promises
vi.mock('fs/promises', () => ({
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
  rm: vi.fn(),
}));

// Mock ConfigService
vi.mock('../../core/services/config.service.js', () => ({
  ConfigService: {
    getInstance: () => ({
      getPath: vi.fn().mockReturnValue('/mock/path'),
      dirs: { agents: 'agents', adapters: 'adapters', prompts: 'prompts' },
    }),
  },
}));

// Mock LoaderService
vi.mock('../../core/services/loader.service.js', () => {
  return {
    LoaderService: class {
      findYamlFiles = vi.fn().mockResolvedValue(['agent1.yml', 'agent2.yml']);
      findYamlFilesRelative = vi.fn().mockResolvedValue(['docs/doc1.yml', 'refactor/ref1.yml']);
      loadYaml = vi.fn().mockImplementation((file: string) => {
        if (file.includes('agent1.yml')) {
          return Promise.resolve({
            id: 'agent-1',
            purpose: 'Test Agent 1',
            prompt: { system: 'You are Agent 1' },
            constraints: ['Constraint 1'],
          } as Agent);
        }
        if (file.includes('agent2.yml')) {
          return Promise.resolve({
            id: 'agent-2',
            purpose: 'Test Agent 2',
            rulepacks: ['rulepack-1'],
          } as Agent);
        }
        if (file.includes('doc1.yml')) {
            return Promise.resolve({
                id: 'doc-1',
                description: 'Doc 1',
                content: 'Doc content',
            } as any);
        }
        if (file.includes('ref1.yml')) {
            return Promise.resolve({
                id: 'ref-1',
                description: 'Ref 1',
                content: 'Ref content',
            } as any);
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
      shouldIncludePrompt = vi.fn().mockReturnValue(true);
      resolveRulepacks = vi.fn().mockResolvedValue(['Rule 1']);
      resolveAllAgents = vi.fn().mockResolvedValue([
        { 
            agent: { id: 'agent-1', purpose: 'Test Agent 1', prompt: { system: 'You are Agent 1' }, constraints: ['Constraint 1'] }, 
            rules: [], 
            suffix: '' 
        },
        { 
            agent: { id: 'agent-2', purpose: 'Test Agent 2' }, 
            rules: ['Rule 1'], 
            suffix: '' 
        },
        { 
            agent: { id: 'agent-1', purpose: 'Test Agent 1' }, 
            rules: [], 
            suffix: '-backend' 
        }
      ]);
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
    
    const content = call![1] as string;
    
    // Base content
    expect(content).toContain('# Base Instructions');
    
    // Agents (from resolveAllAgents mock)
    expect(content).toContain('## Available Agents');
    expect(content).toContain('### agent-1');
    expect(content).toContain('You are Agent 1');
    expect(content).toContain('### agent-2');
    expect(content).toContain('Rule 1');
    expect(content).toContain('### agent-1-backend');

    // Project Context
    expect(content).toContain('# Project: Test Project');
    expect(content).toContain('Overview text');
    expect(content).toContain('**Languages:** typescript');
    expect(content).toContain('- `npm run build` - build');
  });

  it('should generate prompts and agents in .github/prompts', async () => {
    const project: Project = {
      id: 'test-project',
      version: '1.0.0',
      name: 'Test Project',
      description: 'A test project',
    };

    await adapter.generate(project, '/output');

    const writeFileMock = writeFile as unknown as ReturnType<typeof vi.fn>;
    const rmMock = (await import('fs/promises')).rm as unknown as ReturnType<typeof vi.fn>;

    // Verify cleanup
    expect(rmMock).toHaveBeenCalledWith(join('/output', '.github', 'prompts'), { recursive: true, force: true });

    // Verify Prompts
    // doc1.yml -> prompt-docs-doc1.md
    const promptCall1 = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('prompt-docs-doc1.md'));
    expect(promptCall1).toBeDefined();
    expect(promptCall1![1]).toContain('# doc-1');

    // ref1.yml -> prompt-refactor-ref1.md
    const promptCall2 = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('prompt-refactor-ref1.md'));
    expect(promptCall2).toBeDefined();

    // Verify Agents
    // agent-1 -> agent-agent-1.md
    const agentCall1 = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('agent-agent-1.md'));
    expect(agentCall1).toBeDefined();
    expect(agentCall1![1]).toContain('# agent-1');
    expect(agentCall1![1]).toContain('You are Agent 1');

    // agent-1-backend -> agent-agent-1-backend.md
    const agentCallBackend = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('agent-agent-1-backend.md'));
    expect(agentCallBackend).toBeDefined();
    expect(agentCallBackend![1]).toContain('# agent-1');
  });

  it('should generate global instructions.md without project context', async () => {
    await adapter.generateGlobal('/output');

    const writeFileMock = writeFile as unknown as ReturnType<typeof vi.fn>;
    const call = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('instructions.md'));
    expect(call).toBeDefined();
    
    const content = call![1] as string;
    
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
