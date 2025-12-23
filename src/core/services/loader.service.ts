import { readFile, readdir, stat } from 'fs/promises';
import { join } from 'path';
import { load as loadYaml } from 'js-yaml';
import { ConfigService } from './config.service.js';

export class LoaderService {
  private config = ConfigService.getInstance();
  private cache = new Map<string, any>();

  async loadYaml<T>(path: string, useCache = true): Promise<T> {
    if (useCache && this.cache.has(path)) {
      return this.cache.get(path) as T;
    }

    try {
      const content = await readFile(path, 'utf-8');
      const data = loadYaml(content) as T;
      if (useCache) {
        this.cache.set(path, data);
      }
      return data;
    } catch (error) {
      throw new Error(`Failed to load YAML file at ${path}: ${error}`);
    }
  }

  async findYamlFiles(dir: string): Promise<string[]> {
    const files: string[] = [];
    
    try {
      const entries = await readdir(dir, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = join(dir, entry.name);

        if (entry.isDirectory()) {
          files.push(...(await this.findYamlFiles(fullPath)));
        } else if (
          entry.isFile() &&
          (entry.name.endsWith('.yml') || entry.name.endsWith('.yaml'))
        ) {
          files.push(fullPath);
        }
      }
    } catch (error) {
      // Directory might not exist, return empty list
    }

    return files;
  }

  /**
   * Finds YAML files and returns their paths relative to the base directory
   * @param dir Directory to search
   * @param basePath Base path for relative calculation (internal recursion use)
   */
  async findYamlFilesRelative(dir: string, basePath: string = ''): Promise<string[]> {
    const files: string[] = [];

    try {
      const entries = await readdir(dir, { withFileTypes: true });

      for (const entry of entries) {
        const relativePath = join(basePath, entry.name).replace(/\\/g, '/');
        const fullPath = join(dir, entry.name);

        if (entry.isDirectory()) {
          // Skip shared directory as it contains includes, not standalone items
          if (entry.name !== 'shared') {
            files.push(...(await this.findYamlFilesRelative(fullPath, relativePath)));
          }
        } else if (
          entry.isFile() &&
          (entry.name.endsWith('.yml') || entry.name.endsWith('.yaml'))
        ) {
          files.push(relativePath);
        }
      }
    } catch (error) {
      // Directory doesn't exist
    }

    return files;
  }
}
