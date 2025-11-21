import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ValidationService } from './validation.service.js';
import { ConfigService } from './config.service.js';
import { LoaderService } from './loader.service.js';
import { join } from 'path';

// Mock ConfigService
vi.mock('./config.service.js', () => {
  const mockConfig = {
    rootDir: '/mock/root',
    dirs: {
      prompts: '03_prompts',
      agents: '04_agents',
      rulepacks: '01_rulepacks',
      skills: '02_skills',
      recipes: '05_recipes',
      schemas: '10_schemas',
      evals: '20_evals',
    },
    getPath: vi.fn((...parts) => join('/mock/root', ...parts)),
    getProjectSources: vi.fn().mockResolvedValue(['/mock/root/06_projects/global']),
  };
  return {
    ConfigService: {
      getInstance: () => mockConfig,
    },
  };
});

// Mock LoaderService
vi.mock('./loader.service.js', () => {
  const MockLoaderService = vi.fn();
  MockLoaderService.prototype.findYamlFiles = vi.fn().mockResolvedValue([]);
  return { LoaderService: MockLoaderService };
});

// Mock fs/promises
vi.mock('fs/promises', () => ({
  readFile: vi.fn(),
  stat: vi.fn(),
}));

import { readFile, stat } from 'fs/promises';

describe('ValidationService', () => {
  let service: ValidationService;
  let mockLoader: any;

  beforeEach(() => {
    vi.clearAllMocks();
    service = new ValidationService();
    mockLoader = (service as any).loader;

    // Default mock for schemas
    (readFile as any).mockImplementation(async (path: string) => {
      if (path.includes('schemas')) {
        return JSON.stringify({ type: 'object' });
      }
      return '';
    });
  });

  it('should validate successfully when no manifests are found', async () => {
    const result = await service.validateAll();
    expect(result.success).toBe(true);
    expect(result.errors).toHaveLength(0);
    expect(result.stats.checked).toBe(0);
  });

  it('should fail when schema loading fails', async () => {
    (readFile as any).mockRejectedValueOnce(new Error('File not found'));
    
    const result = await service.validateAll();
    
    expect(result.success).toBe(false);
    expect(result.errors).toContainEqual(expect.stringContaining('Failed to load schema'));
  });

  it('should validate a valid agent manifest', async () => {
    // Mock schema loading
    (readFile as any).mockImplementation(async (path: string) => {
      if (path.includes('schemas')) {
        return JSON.stringify({
          type: 'object',
          properties: {
            id: { type: 'string' },
            version: { type: 'string' },
          },
        });
      }
      if (path.includes('agent.yml')) {
        return `
id: test-agent
version: 1.0.0
description: Test Agent
`;
      }
      return '';
    });

    // Mock finding files
    mockLoader.findYamlFiles.mockImplementation(async (path: string) => {
      if (path.includes('04_agents')) {
        return ['/mock/root/04_agents/agent.yml'];
      }
      return [];
    });

    const result = await service.validateAll();

    expect(result.success).toBe(true);
    expect(result.errors).toHaveLength(0);
    expect(result.stats.checked).toBe(1);
  });

  it('should fail validation for invalid ID format', async () => {
    // Mock schema loading
    (readFile as any).mockImplementation(async (path: string) => {
      if (path.includes('schemas')) {
        return JSON.stringify({ type: 'object' });
      }
      if (path.includes('agent.yml')) {
        return `
id: TestAgent
version: 1.0.0
`;
      }
      return '';
    });

    // Mock finding files
    mockLoader.findYamlFiles.mockImplementation(async (path: string) => {
      if (path.includes('04_agents')) {
        return ['/mock/root/04_agents/agent.yml'];
      }
      return [];
    });

    const result = await service.validateAll();

    expect(result.success).toBe(false);
    expect(result.errors).toContainEqual(expect.stringContaining('must be in kebab-case'));
  });

  it('should fail validation for duplicate IDs', async () => {
    // Mock schema loading
    (readFile as any).mockImplementation(async (path: string) => {
      if (path.includes('schemas')) {
        return JSON.stringify({ type: 'object' });
      }
      return `
id: test-agent
version: 1.0.0
`;
    });

    // Mock finding files
    mockLoader.findYamlFiles.mockImplementation(async (path: string) => {
      if (path.includes('04_agents')) {
        return ['/mock/root/04_agents/agent1.yml', '/mock/root/04_agents/agent2.yml'];
      }
      return [];
    });

    const result = await service.validateAll();

    expect(result.success).toBe(false);
    expect(result.errors).toContainEqual(expect.stringContaining('Duplicate ID'));
  });

  it('should fail validation for invalid semver', async () => {
    // Mock schema loading
    (readFile as any).mockImplementation(async (path: string) => {
      if (path.includes('schemas')) {
        return JSON.stringify({ type: 'object' });
      }
      return `
id: test-agent
version: v1.0
`;
    });

    // Mock finding files
    mockLoader.findYamlFiles.mockImplementation(async (path: string) => {
      if (path.includes('04_agents')) {
        return ['/mock/root/04_agents/agent.yml'];
      }
      return [];
    });

    const result = await service.validateAll();

    expect(result.success).toBe(false);
    expect(result.errors).toContainEqual(expect.stringContaining('not valid semver'));
  });

  it('should detect potential API keys', async () => {
    // Mock schema loading
    (readFile as any).mockImplementation(async (path: string) => {
      if (path.includes('schemas')) {
        return JSON.stringify({ type: 'object' });
      }
      if (path.includes('agent.yml')) {
        return `
id: test-agent
version: 1.0.0
api_key: sk_live_1234567890abcdefghijklmnopqrstuvwxyz
`;
      }
      return '';
    });

    // Mock finding files
    mockLoader.findYamlFiles.mockImplementation(async (path: string) => {
      if (path.includes('04_agents')) {
        return ['/mock/root/04_agents/agent.yml'];
      }
      return [];
    });

    const result = await service.validateAll();

    expect(result.success).toBe(false);
    expect(result.errors).toContainEqual(expect.stringContaining('Potential API Key found'));
  });

  it('should validate rulepack references', async () => {
    // Mock schema loading
    (readFile as any).mockImplementation(async (path: string) => {
      if (path.includes('schemas')) {
        return JSON.stringify({ type: 'object' });
      }
      if (path.includes('agent.yml')) {
        return `
id: test-agent
version: 1.0.0
rulepacks:
  - missing-rulepack
`;
      }
      return '';
    });

    // Mock finding files
    mockLoader.findYamlFiles.mockImplementation(async (path: string) => {
      if (path.includes('04_agents')) {
        return ['/mock/root/04_agents/agent.yml'];
      }
      return [];
    });

    const result = await service.validateAll();

    expect(result.success).toBe(false);
    expect(result.errors).toContainEqual(expect.stringContaining('Referenced rulepack "missing-rulepack" not found'));
  });
});
