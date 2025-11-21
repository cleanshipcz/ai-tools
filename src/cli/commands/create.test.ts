import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createCommand, createProject } from './create.js';
import { ConfigService } from '../../core/services/config.service.js';
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

// Mock fs/promises
vi.mock('fs/promises', () => ({
  mkdir: vi.fn(),
  readFile: vi.fn(),
  writeFile: vi.fn(),
  readdir: vi.fn(),
}));

import { mkdir, readFile, writeFile, readdir } from 'fs/promises';

describe('create command', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    
    // Mock readdir to throw ENOENT (project doesn't exist)
    (readdir as any).mockRejectedValue({ code: 'ENOENT' });

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

  it('should create a new local project', async () => {
    await createProject('my-project', { local: true });

    expect(mkdir).toHaveBeenCalledWith(expect.stringContaining('my-project'), { recursive: true });
    expect(writeFile).toHaveBeenCalledWith(
      expect.stringContaining('project.yml'),
      expect.stringContaining('id: my-project'),
      'utf-8'
    );
  });

  it('should convert name to kebab-case', async () => {
    await createProject('My Project', { local: true });

    expect(mkdir).toHaveBeenCalledWith(expect.stringContaining('my-project'), { recursive: true });
    expect(writeFile).toHaveBeenCalledWith(
      expect.stringContaining('project.yml'),
      expect.stringContaining('id: my-project'),
      'utf-8'
    );
  });
});
