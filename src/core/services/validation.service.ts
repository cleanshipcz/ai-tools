import { readFile, stat } from 'fs/promises';
import { join, dirname, relative } from 'path';
import { load as loadYaml } from 'js-yaml';
import Ajv from 'ajv';
import addFormats from 'ajv-formats';
import { ConfigService } from './config.service.js';
import { LoaderService } from './loader.service.js';

export interface ValidationResult {
  success: boolean;
  errors: string[];
  warnings: string[];
  stats: {
    checked: number;
  };
}

interface ManifestFile {
  path: string;
  type: 'prompt' | 'agent' | 'rulepack' | 'skill' | 'eval' | 'project' | 'recipe' | 'feature';
  id: string;
  version?: string;
  content: any;
}

const SECURITY_PATTERNS = [
  { name: 'API Key', pattern: /\b[A-Za-z0-9_-]{32,}\b/, exclude: /example|sample|test/i },
  {
    name: 'Password',
    pattern: /password\s*[:=]\s*['"][^'"]+['"]/,
    exclude: /\$\{|example|sample|placeholder/i,
  },
  {
    name: 'Secret',
    pattern: /secret\s*[:=]\s*['"][^'"]+['"]/,
    exclude: /\$\{|example|sample|placeholder/i,
  },
  { name: 'Private Key', pattern: /-----BEGIN (RSA |DSA )?PRIVATE KEY-----/, exclude: null },
  { name: 'AWS Access Key', pattern: /AKIA[0-9A-Z]{16}/, exclude: null },
  { name: 'GitHub Token', pattern: /ghp_[a-zA-Z0-9]{36}/, exclude: null },
];

const SEMVER_PATTERN = /^\d+\.\d+\.\d+$/;
const KEBAB_CASE_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

export class ValidationService {
  private config = ConfigService.getInstance();
  private loader = new LoaderService();
  private ajv: Ajv;
  private manifestsById = new Map<string, ManifestFile>();

  constructor() {
    this.ajv = new Ajv({ allErrors: true, strict: false });
    addFormats(this.ajv);
  }

  async validateAll(): Promise<ValidationResult> {
    const result: ValidationResult = {
      success: true,
      errors: [],
      warnings: [],
      stats: { checked: 0 },
    };

    // Load schemas
    const schemas = await this.loadSchemas(result);
    for (const [name, schema] of Object.entries(schemas)) {
      this.ajv.addSchema(schema, name);
    }

    // Collect all manifests
    const manifests = await this.collectManifests(result);
    result.stats.checked = manifests.length;

    // Run validations
    this.validateSchemas(manifests, schemas, result);
    this.validateIds(manifests, result);
    this.validateVersions(manifests, result);
    this.validateReferences(manifests, result);
    await this.validateSecurity(manifests, result);
    await this.validateIncludes(manifests, result);
    this.validateFeatureContent(manifests, result);

    result.success = result.errors.length === 0;
    return result;
  }

  private async loadSchemas(result: ValidationResult): Promise<Record<string, any>> {
    const schemasDir = this.config.getPath(this.config.dirs.schemas);
    const schemaFiles = {
      prompt: 'prompt.schema.json',
      agent: 'agent.schema.json',
      rulepack: 'rulepack.schema.json',
      skill: 'skill.schema.json',
      eval: 'eval.schema.json',
      project: 'project.schema.json',
      recipe: 'recipe.schema.json',
      feature: 'feature.schema.json',
    };

    const schemas: Record<string, any> = {};
    for (const [name, file] of Object.entries(schemaFiles)) {
      try {
        const content = await readFile(join(schemasDir, file), 'utf-8');
        schemas[name] = JSON.parse(content);
      } catch (error) {
        result.errors.push(`Failed to load schema ${file}: ${error}`);
      }
    }

    return schemas;
  }

  private async collectManifests(result: ValidationResult): Promise<ManifestFile[]> {
    const manifests: ManifestFile[] = [];
    const dirs = {
      [this.config.dirs.prompts]: 'prompt',
      [this.config.dirs.agents]: 'agent',
      [this.config.dirs.rulepacks]: 'rulepack',
      [this.config.dirs.skills]: 'skill',
      [this.config.dirs.recipes]: 'recipe',
      [join(this.config.dirs.evals, 'suites')]: 'eval',
    };

    for (const [dirName, type] of Object.entries(dirs)) {
      const fullPath = this.config.getPath(dirName);
      try {
        const files = await this.loader.findYamlFiles(fullPath);
        for (const file of files) {
          // Skip template files and deployment configs
          if (file.includes('/template/')) continue;
          if (file.endsWith('deploy.yml') || file.endsWith('deploy.local.yml')) continue;
          // Skip feature manifests (will be collected separately)
          if (file.includes('/features/') && file.endsWith('feature.yml')) continue;

          try {
            const content = await readFile(file, 'utf-8');
            const parsed = loadYaml(content) as any;

            if (!parsed || typeof parsed !== 'object') {
              result.errors.push(`${relative(this.config.rootDir, file)}: Invalid YAML content`);
              continue;
            }

            manifests.push({
              path: file,
              type: type as any,
              id: parsed.id || parsed.suite || 'unknown',
              version: parsed.version,
              content: parsed,
            });
          } catch (error) {
            result.errors.push(`${relative(this.config.rootDir, file)}: Failed to parse YAML - ${error}`);
          }
        }
      } catch (error) {
        // Directory might not exist yet
        result.warnings.push(`Directory ${dirName} not found, skipping`);
      }
    }

    // Collect project manifests from configured sources
    const projectSources = await this.config.getProjectSources();
    for (const source of projectSources) {
      try {
        const files = await this.loader.findYamlFiles(source);
        for (const file of files) {
          // Skip template files and deployment configs
          if (file.includes('/template/')) continue;
          if (file.endsWith('deploy.yml') || file.endsWith('deploy.local.yml')) continue;
          // Skip feature manifests (will be collected separately)
          if (file.includes('/features/') && file.endsWith('feature.yml')) continue;
          // Only process project.yml files
          if (!file.endsWith('project.yml')) continue;

          try {
            const content = await readFile(file, 'utf-8');
            const parsed = loadYaml(content) as any;

            if (!parsed || typeof parsed !== 'object') {
              result.errors.push(`${relative(this.config.rootDir, file)}: Invalid YAML content`);
              continue;
            }

            manifests.push({
              path: file,
              type: 'project',
              id: parsed.id || 'unknown',
              version: parsed.version,
              content: parsed,
            });
          } catch (error) {
            result.errors.push(`${relative(this.config.rootDir, file)}: Failed to parse YAML - ${error}`);
          }
        }
      } catch (error) {
        // Directory might not exist or can't be read
      }
    }

    // Collect feature manifests separately from configured sources
    for (const source of projectSources) {
      const fullPath = source;
      try {
        const files = await this.loader.findYamlFiles(fullPath);
        for (const file of files) {
          if (file.includes('/features/') && file.endsWith('feature.yml')) {
            try {
              const content = await readFile(file, 'utf-8');
              const parsed = loadYaml(content) as any;

              if (!parsed || typeof parsed !== 'object') {
                result.errors.push(`${relative(this.config.rootDir, file)}: Invalid YAML content`);
                continue;
              }

              manifests.push({
                path: file,
                type: 'feature',
                id: parsed.id || 'unknown',
                version: parsed.version,
                content: parsed,
              });
            } catch (error) {
              result.errors.push(`${relative(this.config.rootDir, file)}: Failed to parse YAML - ${error}`);
            }
          }
        }
      } catch (error) {
        // Directory might not exist yet
      }
    }

    return manifests;
  }

  private validateSchemas(manifests: ManifestFile[], schemas: Record<string, any>, result: ValidationResult): void {
    for (const manifest of manifests) {
      const schema = schemas[manifest.type];
      if (!schema) {
        result.errors.push(
          `${relative(this.config.rootDir, manifest.path)}: No schema found for type ${manifest.type}`
        );
        continue;
      }

      const validate = this.ajv.compile(schema);
      const valid = validate(manifest.content);

      if (!valid && validate.errors) {
        for (const error of validate.errors) {
          const path = error.instancePath || '/';
          result.errors.push(
            `${relative(this.config.rootDir, manifest.path)}: Schema validation failed at ${path}: ${error.message}`
          );
        }
      }
    }
  }

  private validateIds(manifests: ManifestFile[], result: ValidationResult): void {
    for (const manifest of manifests) {
      const id = manifest.id;

      // Check kebab-case
      if (!KEBAB_CASE_PATTERN.test(id)) {
        result.errors.push(`${relative(this.config.rootDir, manifest.path)}: ID "${id}" must be in kebab-case`);
      }

      // Check uniqueness
      const existing = this.manifestsById.get(id);
      if (existing) {
        result.errors.push(
          `${relative(this.config.rootDir, manifest.path)}: Duplicate ID "${id}" (also in ${relative(this.config.rootDir, existing.path)})`
        );
      } else {
        this.manifestsById.set(id, manifest);
      }
    }
  }

  private validateVersions(manifests: ManifestFile[], result: ValidationResult): void {
    for (const manifest of manifests) {
      if (manifest.version && !SEMVER_PATTERN.test(manifest.version)) {
        result.errors.push(
          `${relative(this.config.rootDir, manifest.path)}: Version "${manifest.version}" is not valid semver`
        );
      }
    }
  }

  private validateReferences(manifests: ManifestFile[], result: ValidationResult): void {
    for (const manifest of manifests) {
      // Check rulepack references
      if (manifest.content.rulepacks) {
        for (const rulepackId of manifest.content.rulepacks) {
          if (!this.manifestsById.has(rulepackId)) {
            result.errors.push(
              `${relative(this.config.rootDir, manifest.path)}: Referenced rulepack "${rulepackId}" not found`
            );
          }
        }
      }

      // Check extends references
      if (manifest.content.extends) {
        for (const parentId of manifest.content.extends) {
          if (!this.manifestsById.has(parentId)) {
            result.errors.push(
              `${relative(this.config.rootDir, manifest.path)}: Extended rulepack "${parentId}" not found`
            );
          }
        }
      }
    }
  }

  private async validateSecurity(manifests: ManifestFile[], result: ValidationResult): Promise<void> {
    for (const manifest of manifests) {
      const content = await readFile(manifest.path, 'utf-8');

      for (const { name, pattern, exclude } of SECURITY_PATTERNS) {
        const matches = content.match(new RegExp(pattern, 'g'));
        if (matches) {
          for (const match of matches) {
            if (!exclude || !exclude.test(match)) {
              result.errors.push(
                `${relative(this.config.rootDir, manifest.path)}: Potential ${name} found in file`
              );
            }
          }
        }
      }
    }
  }

  private async validateIncludes(manifests: ManifestFile[], result: ValidationResult): Promise<void> {
    for (const manifest of manifests) {
      if (manifest.content.includes) {
        for (const includePath of manifest.content.includes) {
          const absolutePath = join(dirname(manifest.path), includePath);
          try {
            await stat(absolutePath);
          } catch (error) {
            result.errors.push(
              `${relative(this.config.rootDir, manifest.path)}: Included file "${includePath}" not found`
            );
          }
        }
      }

      // Check prompt includes
      if (manifest.content.prompt?.includes) {
        for (const includePath of manifest.content.prompt.includes) {
          const absolutePath = join(dirname(manifest.path), includePath);
          try {
            await stat(absolutePath);
          } catch (error) {
            result.errors.push(
              `${relative(this.config.rootDir, manifest.path)}: Included file "${includePath}" not found`
            );
          }
        }
      }
    }
  }

  private validateFeatureContent(manifests: ManifestFile[], result: ValidationResult): void {
    // Only validate feature.yml files
    const featureManifests = manifests.filter((m) => m.type === 'feature');

    for (const manifest of featureManifests) {
      const checkForBackticks = (value: any, fieldPath: string) => {
        if (typeof value === 'string' && value.includes('`')) {
          result.errors.push(
            `${relative(this.config.rootDir, manifest.path)}: Backticks (\`) found in ${fieldPath}. ` +
              `Backticks cause bash command substitution errors in generated scripts. ` +
              `Use alternative formatting (indented code blocks, quotes, etc.)`
          );
        } else if (Array.isArray(value)) {
          value.forEach((item, index) => checkForBackticks(item, `${fieldPath}[${index}]`));
        } else if (value && typeof value === 'object') {
          for (const [key, val] of Object.entries(value)) {
            checkForBackticks(val, `${fieldPath}.${key}`);
          }
        }
      };

      // Check all fields recursively in feature manifests
      const content = manifest.content;
      checkForBackticks(content, 'root');
    }
  }
}
