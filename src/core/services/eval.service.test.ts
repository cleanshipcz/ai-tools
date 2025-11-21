import { describe, it, expect, vi, beforeEach } from 'vitest';
import { EvalService } from './eval.service.js';
import { ConfigService } from './config.service.js';
import { LoaderService } from './loader.service.js';
import { join } from 'path';

// Mock ConfigService
vi.mock('./config.service.js', () => {
  const mockConfig = {
    rootDir: '/mock/root',
    dirs: {
      evals: '20_evals',
    },
    getPath: vi.fn((...parts) => join('/mock/root', ...parts)),
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
  MockLoaderService.prototype.loadYaml = vi.fn();
  return { LoaderService: MockLoaderService };
});

// Mock fs/promises
vi.mock('fs/promises', () => ({
  readFile: vi.fn(),
  stat: vi.fn(),
}));

import { readFile } from 'fs/promises';

describe('EvalService', () => {
  let service: EvalService;
  let mockLoader: any;

  beforeEach(() => {
    vi.clearAllMocks();
    service = new EvalService();
    mockLoader = (service as any).loader;
  });

  it('should run successfully when no suites are found', async () => {
    const result = await service.run();
    expect(result.success).toBe(true);
    expect(result.details).toContain('No eval suites found');
  });

  it('should run a valid suite', async () => {
    // Mock finding files
    mockLoader.findYamlFiles.mockResolvedValue(['/mock/root/20_evals/suites/test-suite.yml']);

    // Mock loading suite
    mockLoader.loadYaml.mockResolvedValue({
      suite: 'test-suite',
      description: 'Test Suite',
      targets: [
        {
          type: 'agent',
          id: 'test-agent',
          dataset: 'datasets/test.jsonl',
        },
      ],
    });

    // Mock reading dataset
    (readFile as any).mockResolvedValue('{"prompt": "test"}');

    const result = await service.run();

    expect(result.success).toBe(true);
    expect(result.suitesRun).toBe(1);
    expect(result.suitesPassed).toBe(1);
    expect(result.details).toContainEqual(expect.stringContaining('Running suite: test-suite'));
    expect(result.details).toContainEqual(expect.stringContaining('Dataset found'));
  });

  it('should fail if dataset is missing', async () => {
    // Mock finding files
    mockLoader.findYamlFiles.mockResolvedValue(['/mock/root/20_evals/suites/test-suite.yml']);

    // Mock loading suite
    mockLoader.loadYaml.mockResolvedValue({
      suite: 'test-suite',
      targets: [
        {
          type: 'agent',
          id: 'test-agent',
          dataset: 'datasets/missing.jsonl',
        },
      ],
    });

    // Mock reading dataset failure
    (readFile as any).mockRejectedValue(new Error('File not found'));

    const result = await service.run();

    expect(result.success).toBe(false);
    expect(result.suitesRun).toBe(1);
    expect(result.suitesFailed).toBe(1);
    expect(result.details).toContainEqual(expect.stringContaining('Dataset not found'));
  });

  it('should filter suites by name', async () => {
    // Mock finding files
    mockLoader.findYamlFiles.mockResolvedValue([
      '/mock/root/20_evals/suites/suite1.yml',
      '/mock/root/20_evals/suites/suite2.yml',
    ]);

    // Mock loading suites
    mockLoader.loadYaml.mockImplementation(async (path: string) => {
      if (path.includes('suite1')) {
        return { suite: 'suite1', targets: [] };
      }
      return { suite: 'suite2', targets: [] };
    });

    const result = await service.run('suite1');

    expect(result.success).toBe(true);
    expect(result.suitesRun).toBe(1);
    expect(result.details).toContainEqual(expect.stringContaining('Running suite: suite1'));
    expect(result.details).not.toContainEqual(expect.stringContaining('Running suite: suite2'));
  });
});
