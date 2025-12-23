import { rm } from 'fs/promises';
import { join } from 'path';
import { ConfigService } from './config.service.js';

export class CleanService {
  private config = ConfigService.getInstance();

  async clean(): Promise<string[]> {
    const pathsToClean = [
      this.config.getPath(this.config.dirs.adapters),
      this.config.getPath(this.config.dirs.output),
      join(this.config.getPath(this.config.dirs.evals), 'reports'),
    ];

    const cleanedPaths: string[] = [];

    for (const path of pathsToClean) {
      try {
        await rm(path, { recursive: true, force: true });
        cleanedPaths.push(path);
      } catch (error: any) {
        // Ignore errors if file doesn't exist, but rethrow others if needed
        // For clean, we usually just want to try our best
        if (error.code !== 'ENOENT') {
          console.warn(`Failed to clean ${path}: ${error.message}`);
        }
      }
    }

    return cleanedPaths;
  }
}
