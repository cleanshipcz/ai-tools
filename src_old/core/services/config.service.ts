import { join, dirname, resolve, isAbsolute } from 'path';
import { fileURLToPath } from 'url';
import { readFile } from 'fs/promises';
import { load as loadYaml } from 'js-yaml';

export interface AppConfig {
  project_sources?: string[];
  [key: string]: any;
}

export class ConfigService {
  private static instance: ConfigService;
  
  public readonly rootDir: string;
  private configCache: AppConfig | null = null;
  
  // Directory names
  public readonly dirs = {
    projects: '06_projects',
    prompts: '03_prompts',
    agents: '04_agents',
    rulepacks: '01_rulepacks',
    skills: '02_skills',
    recipes: '05_recipes',
    schemas: '10_schemas',
    scripts: '11_scripts',
    templates: '12_templates',
    evals: '20_evals',
    docs: '90_docs',
    adapters: 'adapters',
    output: '.output',
    recipeDocs: '.recipe-docs',
    recipeLogs: '.recipe-logs',
    backups: '.backups',
    config: '15_config',
  };

  // File names
  public readonly files = {
    projectManifest: 'project.yml',
    deployConfig: 'deploy.yml',
    deployLocalConfig: 'deploy.local.yml',
    featureManifest: 'feature.yml',
    configFile: 'config.yml',
    localConfigFile: 'config.local.yml',
  };

  // Scopes
  public readonly scopes = {
    global: 'global',
    local: 'local',
  };

  private constructor() {
    const __filename = fileURLToPath(import.meta.url);
    const __dirname = dirname(__filename);
    // src/core/services -> src/core -> src -> root
    this.rootDir = resolve(__dirname, '../../..');
  }

  public static getInstance(): ConfigService {
    if (!ConfigService.instance) {
      ConfigService.instance = new ConfigService();
    }
    return ConfigService.instance;
  }

  public getPath(...parts: string[]): string {
    return join(this.rootDir, ...parts);
  }

  public async getProjectSources(): Promise<string[]> {
    const config = await this.loadConfig();
    const defaultSources = [
      this.getPath(this.dirs.projects, 'global'),
      this.getPath(this.dirs.projects, 'local')
    ];

    const configuredSources = config.project_sources || [];
    
    // Resolve configured sources
    const resolvedConfiguredSources = configuredSources.map(source => {
      if (isAbsolute(source)) return source;
      return resolve(this.rootDir, source);
    });

    // Combine and deduplicate
    // We put configured sources LAST so they can override? 
    // Or FIRST? The legacy script merged them.
    // Legacy script: "Both lists are merged (config + local), duplicates removed"
    // And defaults were: ['./06_projects/global', './06_projects/local']
    
    // If config has project_sources, it REPLACES the default in the merged config object?
    // Legacy mergeConfig:
    // if (Array.isArray(base) && Array.isArray(override)) { return [...base, ...override] }
    
    // So if config.yml has defaults, and config.local.yml has extras, they are combined.
    // But if config.yml doesn't have defaults explicitly listed, they might be missing if we don't add them.
    // The legacy script had DEFAULT_PROJECT_SOURCES constant.
    
    const allSources = [...defaultSources, ...resolvedConfiguredSources];
    return Array.from(new Set(allSources));
  }

  private async loadConfig(): Promise<AppConfig> {
    if (this.configCache) return this.configCache;

    const configPath = this.getPath(this.dirs.config, this.files.configFile);
    const localConfigPath = this.getPath(this.dirs.config, this.files.localConfigFile);

    let baseConfig: AppConfig = {};
    let localConfig: AppConfig = {};

    try {
      const content = await readFile(configPath, 'utf-8');
      baseConfig = loadYaml(content) as AppConfig || {};
    } catch {}

    try {
      const content = await readFile(localConfigPath, 'utf-8');
      localConfig = loadYaml(content) as AppConfig || {};
    } catch {}

    this.configCache = this.mergeConfig(baseConfig, localConfig);
    return this.configCache!;
  }

  private mergeConfig(base: any, override: any): any {
    if (!base) return override;
    if (!override) return base;
    if (typeof override !== 'object' || override === null) return override;
    if (typeof base !== 'object' || base === null) return override;

    if (Array.isArray(base) && Array.isArray(override)) {
      return Array.from(new Set([...base, ...override]));
    }

    if (!Array.isArray(base) && !Array.isArray(override)) {
      const result = { ...base };
      for (const key in override) {
        if (key in base) {
          result[key] = this.mergeConfig(base[key], override[key]);
        } else {
          result[key] = override[key];
        }
      }
      return result;
    }

    return override;
  }
}

