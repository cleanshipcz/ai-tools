import { describe, it, expect, vi, beforeEach } from 'vitest';
import { initCommand, initProject } from './init.js';
import { ConfigService } from '../../core/services/config.service.js';
import { ExternalProjectService } from '../../core/services/external-project.service.js';
import { join } from 'path';

// Mock ConfigService
vi.mock('../../core/services/config.service.js', () => {
  const mockConfig = {
    rootDir: '/mock/root',
    dirs: {
      projects: '06_projects',
    },
    getPath: vi.fn((...parts) => join('/mock/root', ...parts)),
  };
  return {
    ConfigService: {
      getInstance: () => mockConfig,
    },
  };
});

// Mock ExternalProjectService
vi.mock('../../core/services/external-project.service.js', () => {
  const MockExternalService = vi.fn();
  MockExternalService.prototype.addProject = vi.fn();
  return { ExternalProjectService: MockExternalService };
});

// Mock fs/promises
vi.mock('fs/promises', () => ({
  mkdir: vi.fn(),
  readFile: vi.fn(),
  writeFile: vi.fn(),
  access: vi.fn(),
}));

import { mkdir, readFile, writeFile, access } from 'fs/promises';

describe('init command', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    
    // Mock access to succeed for project path, fail for .cleanship-ai-tools (so it creates it)
    (access as any).mockImplementation(async (path: string) => {
      if (path.includes('.cleanship-ai-tools')) {
        throw new Error('ENOENT');
      }
      return Promise.resolve();
    });

    // Mock readFile for template
    (readFile as any).mockImplementation(async (path: string) => {
      if (path.includes('project.yml')) {
        return 'id: template\nversion: 1.0.0\n';
      }
      if (path.includes('deploy.yml')) {
        return 'target: .\n';
      }
      return '';
    });
  });

  it('should initialize a new external project', async () => {
    await initProject('/path/to/external-project', { alias: 'my-external-app' });

    expect(mkdir).toHaveBeenCalledWith(expect.stringContaining('.cleanship-ai-tools'), { recursive: true });
    expect(writeFile).toHaveBeenCalledWith(
      expect.stringContaining('project.yml'),
      expect.stringContaining('id: my-external-app'),
      'utf-8'
    );
  });

  it('should register the project by default', async () => {
    const mockAddProject = ExternalProjectService.prototype.addProject as any;
    
    await initProject('/path/to/external-project', { alias: 'my-external-app' });

    expect(mockAddProject).toHaveBeenCalled();
  });
});
