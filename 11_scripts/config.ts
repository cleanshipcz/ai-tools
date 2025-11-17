/**
 * Configuration loading and management
 *
 * Loads configuration from 15_config/config.yml and provides
 * functions to access configuration values.
 */

import { readFile } from 'fs/promises';
import { join, resolve, isAbsolute } from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';
import { load as loadYaml } from 'js-yaml';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

// Configuration file paths
const CONFIG_DIR = join(rootDir, '15_config');
const CONFIG_FILE = join(CONFIG_DIR, 'config.yml');
const LOCAL_CONFIG_FILE = join(CONFIG_DIR, 'config.local.yml');

// Default configuration values
const DEFAULT_PROJECT_SOURCES = ['./06_projects/global', './06_projects/local'];

interface Config {
  project_sources?: string[];
  [key: string]: any;
}

// Cache for loaded configuration
let configCache: Config | null = null;

/**
 * Deep merge two configuration objects
 *
 * Merge rules:
 * - Scalars: local takes priority
 * - Arrays: concatenate both (config + local), remove duplicates
 * - Objects: recursively merge, local takes priority for conflicts
 */
function mergeConfig(base: any, override: any): any {
  if (!base) return override;
  if (!override) return base;

  // If override is not an object, it takes priority
  if (typeof override !== 'object' || override === null) {
    return override;
  }

  // If base is not an object but override is, use override
  if (typeof base !== 'object' || base === null) {
    return override;
  }

  // Both are objects, merge them
  const result: any = Array.isArray(base) ? [] : {};

  // Handle array concatenation
  if (Array.isArray(base) && Array.isArray(override)) {
    // Concatenate arrays and remove duplicates
    const combined = [...base, ...override];
    return Array.from(new Set(combined));
  }

  // Handle object merging
  if (!Array.isArray(base) && !Array.isArray(override)) {
    // Start with all keys from base
    for (const key in base) {
      result[key] = base[key];
    }

    // Merge or override with keys from override
    for (const key in override) {
      if (key in base) {
        result[key] = mergeConfig(base[key], override[key]);
      } else {
        result[key] = override[key];
      }
    }

    return result;
  }

  // Type mismatch (array vs object), override takes priority
  return override;
}

/**
 * Load configuration from config.yml and config.local.yml
 * Merges both files according to merge rules
 * Returns default configuration if files don't exist or are invalid
 */
async function loadConfig(): Promise<Config> {
  if (configCache !== null) {
    return configCache;
  }

  let baseConfig: Config = {};
  let localConfig: Config = {};

  // Load config.yml
  try {
    const content = await readFile(CONFIG_FILE, 'utf-8');
    const parsed = loadYaml(content) as Config;

    // Validate that parsed result is an object
    if (typeof parsed === 'object' && parsed !== null) {
      baseConfig = parsed;
    }
  } catch (error) {
    // File doesn't exist or YAML parsing failed
    // Continue with empty base config
  }

  // Load config.local.yml
  try {
    const content = await readFile(LOCAL_CONFIG_FILE, 'utf-8');
    const parsed = loadYaml(content) as Config;

    // Validate that parsed result is an object
    if (typeof parsed === 'object' && parsed !== null) {
      localConfig = parsed;
    }
  } catch (error) {
    // File doesn't exist or YAML parsing failed
    // Continue with empty local config
  }

  // Merge configurations
  const merged = mergeConfig(baseConfig, localConfig);
  configCache = merged;
  return merged;
}

/**
 * Reload configuration from disk
 * Useful for testing or when configuration file changes
 */
export async function reloadConfig(): Promise<void> {
  configCache = null;
  await loadConfig();
}

/**
 * Get project source directories
 *
 * Returns an array of absolute paths to directories containing projects.
 * Defaults to ['./06_projects/global', './06_projects/local'] if not configured.
 *
 * Relative paths are resolved relative to the repository root.
 * Absolute paths are returned as-is.
 *
 * @returns Array of absolute paths to project source directories
 */
export async function getProjectSources(): Promise<string[]> {
  const config = await loadConfig();

  // Get project sources from config or use defaults
  const sources = config.project_sources || DEFAULT_PROJECT_SOURCES;

  // Resolve relative paths to absolute paths
  return sources.map((source) => {
    if (isAbsolute(source)) {
      return source;
    }
    return resolve(rootDir, source);
  });
}

/**
 * Get raw project sources (without resolving paths)
 * Useful for displaying configuration or writing to config files
 *
 * @returns Array of project source paths as configured
 */
export async function getRawProjectSources(): Promise<string[]> {
  const config = await loadConfig();
  return config.project_sources || DEFAULT_PROJECT_SOURCES;
}
