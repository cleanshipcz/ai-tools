import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { backupExisting } from './deploy.js';
import { ConfigService } from '../../core/services/config.service.js';
import * as fs from 'fs/promises';
import { join } from 'path';

// Mocks
const mocks = vi.hoisted(() => ({
  getPath: vi.fn(),
  mkdir: vi.fn(),
  access: vi.fn(),
  readdir: vi.fn(),
  rm: vi.fn(),
  copyFile: vi.fn(),
  stat: vi.fn(),
}));

vi.mock('../../core/services/config.service.js', () => ({
  ConfigService: {
    getInstance: () => ({
      getPath: mocks.getPath,
      dirs: { backups: '.backups' },
    }),
  },
}));

vi.mock('fs/promises', async () => {
  const actual = await vi.importActual('fs/promises');
  return {
    ...actual,
    mkdir: mocks.mkdir,
    access: mocks.access,
    readdir: mocks.readdir,
    rm: mocks.rm,
    copyFile: mocks.copyFile,
    stat: mocks.stat,
  };
});

vi.mock('child_process', () => ({
  execSync: vi.fn(),
}));

describe('deploy command', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getPath.mockReturnValue('/mock/backups/project-id');
    mocks.stat.mockResolvedValue({ isDirectory: () => true });
  });

  describe('backupExisting', () => {
    it('should rotate backups and keep only the latest 10', async () => {
      // Setup 12 existing backups
      const existingBackups = Array.from({ length: 12 }, (_, i) => ({
        name: `2023-01-${String(i + 1).padStart(2, '0')}T00-00-00-000Z`,
        isDirectory: () => true,
      }));
      
      // Mock readdir to return these backups
      mocks.readdir.mockResolvedValue(existingBackups);

      await backupExisting(
        'project-id',
        { target: '/target', tools: [], mode: 'local' },
        '/target'
      );

      // Should have called rm for the 2 oldest backups
      // Oldest are 2023-01-01 and 2023-01-02
      expect(mocks.rm).toHaveBeenCalledTimes(2);
      expect(mocks.rm).toHaveBeenCalledWith(
        join('/mock/backups/project-id', '2023-01-01T00-00-00-000Z'),
        { recursive: true, force: true }
      );
      expect(mocks.rm).toHaveBeenCalledWith(
        join('/mock/backups/project-id', '2023-01-02T00-00-00-000Z'),
        { recursive: true, force: true }
      );
    });

    it('should not delete backups if count is <= 10', async () => {
      // Setup 5 existing backups
      const existingBackups = Array.from({ length: 5 }, (_, i) => ({
        name: `2023-01-${String(i + 1).padStart(2, '0')}T00-00-00-000Z`,
        isDirectory: () => true,
      }));
      
      mocks.readdir.mockResolvedValue(existingBackups);

      await backupExisting(
        'project-id',
        { target: '/target', tools: [], mode: 'local' },
        '/target'
      );

      expect(mocks.rm).not.toHaveBeenCalled();
    });
  });
});
