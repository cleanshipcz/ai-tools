import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CodexAdapter } from './adapter.js';
import { Project, Agent } from '../../core/models/types.js';
import { join } from 'path';
import { writeFile } from 'fs/promises';

// Mock fs/promises
vi.mock('fs/promises', () => ({
  mkdir: vi.fn(),
  writeFile: vi.fn(),
  copyFile: vi.fn(),
  readFile: vi.fn().mockImplementation((path: string) => {
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
      findYamlFiles = vi.fn().mockResolvedValue(['agent1.yml']);
      findYamlFilesRelative = vi.fn().mockResolvedValue(['docs/doc1.yml']);
      loadYaml = vi.fn().mockImplementation((file: string) => {
        if (file.includes('agent1.yml')) {
          return Promise.resolve({
            id: 'agent-1',
            purpose: 'Test Agent 1',
            prompt: { system: 'You are Agent 1' },
            constraints: ['Constraint 1'],
          } as Agent);
        }
        if (file.includes('doc1.yml')) {
            return Promise.resolve({
                id: 'doc-1',
                description: 'Doc 1',
                content: 'Doc content',
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
            rules: ['Rule 1'], 
            suffix: '' 
        }
      ]);
    },
  };
});

describe('CodexAdapter', () => {
  let adapter: CodexAdapter;

  beforeEach(() => {
    adapter = new CodexAdapter();
    vi.clearAllMocks();
  });

  it('should generate AGENTS.md and .codex/prompts', async () => {
    const project: Project = {
      id: 'test-project',
      version: '1.0.0',
      name: 'Test Project',
      description: 'A test project',
      context: {
        overview: 'Overview text',
      },
    };

    await adapter.generate(project, '/output');

    const writeFileMock = writeFile as unknown as ReturnType<typeof vi.fn>;
    const rmMock = (await import('fs/promises')).rm as unknown as ReturnType<typeof vi.fn>;

    // Verify cleanup
    expect(rmMock).toHaveBeenCalledWith(join('/output', '.codex', 'prompts'), { recursive: true, force: true });

    // Verify AGENTS.md
    const agentsCall = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('AGENTS.md'));
    expect(agentsCall).toBeDefined();
    expect(agentsCall![1]).toContain('# AI Agents Configuration');
    expect(agentsCall![1]).toContain('### agent-1');
    expect(agentsCall![1]).toContain('# Project: Test Project');

    // Verify Prompts
    const promptCall = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('prompt-docs-doc1.md'));
    expect(promptCall).toBeDefined();
    expect(promptCall![1]).toContain('# doc-1');

    // Verify Agents as Prompts
    const agentPromptCall = writeFileMock.mock.calls.find((args: any[]) => args[0].endsWith('agent-agent-1.md'));
    expect(agentPromptCall).toBeDefined();
    expect(agentPromptCall![1]).toContain('# agent-1');
  });
});
