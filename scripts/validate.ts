#!/usr/bin/env node
import { readFile, readdir, stat } from 'fs/promises';
import { join, dirname, relative } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import Ajv from 'ajv';
import addFormats from 'ajv-formats';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface ManifestFile {
  path: string;
  type: 'prompt' | 'agent' | 'rulepack' | 'skill' | 'eval' | 'project' | 'recipe';
  id: string;
  version?: string;
  content: any;
}

// Security patterns to check for
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

class Validator {
  private ajv: Ajv;
  private errors: string[] = [];
  private warnings: string[] = [];
  private manifestsById = new Map<string, ManifestFile>();

  constructor() {
    this.ajv = new Ajv({ allErrors: true, strict: false });
    addFormats(this.ajv);
  }

  async validate(): Promise<boolean> {
    console.log(chalk.blue('🔍 Starting validation...\n'));

    // Load schemas
    const schemas = await this.loadSchemas();
    for (const [name, schema] of Object.entries(schemas)) {
      this.ajv.addSchema(schema, name);
    }

    // Collect all manifests
    const manifests = await this.collectManifests();

    // Run validations
    this.validateSchemas(manifests, schemas);
    this.validateIds(manifests);
    this.validateVersions(manifests);
    this.validateReferences(manifests);
    await this.validateSecurity(manifests);
    await this.validateIncludes(manifests);

    // Report results
    this.printResults();

    return this.errors.length === 0;
  }

  private async loadSchemas(): Promise<Record<string, any>> {
    const schemasDir = join(rootDir, 'schemas');
    const schemaFiles = {
      prompt: 'prompt.schema.json',
      agent: 'agent.schema.json',
      rulepack: 'rulepack.schema.json',
      skill: 'skill.schema.json',
      eval: 'eval.schema.json',
      project: 'project.schema.json',
      recipe: 'recipe.schema.json',
    };

    const schemas: Record<string, any> = {};
    for (const [name, file] of Object.entries(schemaFiles)) {
      try {
        const content = await readFile(join(schemasDir, file), 'utf-8');
        schemas[name] = JSON.parse(content);
      } catch (error) {
        this.errors.push(`Failed to load schema ${file}: ${error}`);
      }
    }

    return schemas;
  }

  private async collectManifests(): Promise<ManifestFile[]> {
    const manifests: ManifestFile[] = [];
    const dirs = {
      prompts: 'prompt',
      agents: 'agent',
      rulepacks: 'rulepack',
      skills: 'skill',
      recipes: 'recipe',
      'evals/suites': 'eval',
      'projects/global': 'project',
      'projects/local': 'project',
    };

    for (const [dir, type] of Object.entries(dirs)) {
      const fullPath = join(rootDir, dir);
      try {
        const files = await this.findYamlFiles(fullPath);
        for (const file of files) {
          // Skip template files and deployment configs
          if (file.includes('/template/')) continue;
          if (file.endsWith('deploy.yml') || file.endsWith('deploy.local.yml')) continue;
          // Skip feature manifests (validated separately)
          if (file.includes('/features/') && file.endsWith('/feature.yml')) continue;

          try {
            const content = await readFile(file, 'utf-8');
            const parsed = loadYaml(content) as any;

            if (!parsed || typeof parsed !== 'object') {
              this.errors.push(`${relative(rootDir, file)}: Invalid YAML content`);
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
            this.errors.push(`${relative(rootDir, file)}: Failed to parse YAML - ${error}`);
          }
        }
      } catch (error) {
        // Directory might not exist yet
        this.warnings.push(`Directory ${dir} not found, skipping`);
      }
    }

    return manifests;
  }

  private async findYamlFiles(dir: string): Promise<string[]> {
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
      // Ignore directories that don't exist
    }

    return files;
  }

  private validateSchemas(manifests: ManifestFile[], schemas: Record<string, any>): void {
    for (const manifest of manifests) {
      const schema = schemas[manifest.type];
      if (!schema) {
        this.errors.push(
          `${relative(rootDir, manifest.path)}: No schema found for type ${manifest.type}`
        );
        continue;
      }

      const validate = this.ajv.compile(schema);
      const valid = validate(manifest.content);

      if (!valid && validate.errors) {
        for (const error of validate.errors) {
          const path = error.instancePath || '/';
          this.errors.push(
            `${relative(rootDir, manifest.path)}: Schema validation failed at ${path}: ${error.message}`
          );
        }
      }
    }
  }

  private validateIds(manifests: ManifestFile[]): void {
    for (const manifest of manifests) {
      const id = manifest.id;

      // Check kebab-case
      if (!KEBAB_CASE_PATTERN.test(id)) {
        this.errors.push(`${relative(rootDir, manifest.path)}: ID "${id}" must be in kebab-case`);
      }

      // Check uniqueness
      const existing = this.manifestsById.get(id);
      if (existing) {
        this.errors.push(
          `${relative(rootDir, manifest.path)}: Duplicate ID "${id}" (also in ${relative(rootDir, existing.path)})`
        );
      } else {
        this.manifestsById.set(id, manifest);
      }
    }
  }

  private validateVersions(manifests: ManifestFile[]): void {
    for (const manifest of manifests) {
      if (manifest.version && !SEMVER_PATTERN.test(manifest.version)) {
        this.errors.push(
          `${relative(rootDir, manifest.path)}: Version "${manifest.version}" is not valid semver`
        );
      }
    }
  }

  private validateReferences(manifests: ManifestFile[]): void {
    for (const manifest of manifests) {
      // Check rulepack references
      if (manifest.content.rulepacks) {
        for (const rulepackId of manifest.content.rulepacks) {
          if (!this.manifestsById.has(rulepackId)) {
            this.errors.push(
              `${relative(rootDir, manifest.path)}: Referenced rulepack "${rulepackId}" not found`
            );
          }
        }
      }

      // Check extends references
      if (manifest.content.extends) {
        for (const parentId of manifest.content.extends) {
          if (!this.manifestsById.has(parentId)) {
            this.errors.push(
              `${relative(rootDir, manifest.path)}: Extended rulepack "${parentId}" not found`
            );
          }
        }
      }
    }
  }

  private async validateSecurity(manifests: ManifestFile[]): Promise<void> {
    for (const manifest of manifests) {
      const content = await readFile(manifest.path, 'utf-8');

      for (const { name, pattern, exclude } of SECURITY_PATTERNS) {
        const matches = content.match(new RegExp(pattern, 'g'));
        if (matches) {
          for (const match of matches) {
            if (!exclude || !exclude.test(match)) {
              this.errors.push(
                `${relative(rootDir, manifest.path)}: Potential ${name} found in file`
              );
            }
          }
        }
      }
    }
  }

  private async validateIncludes(manifests: ManifestFile[]): Promise<void> {
    for (const manifest of manifests) {
      if (manifest.content.includes) {
        for (const includePath of manifest.content.includes) {
          const absolutePath = join(dirname(manifest.path), includePath);
          try {
            await stat(absolutePath);
          } catch (error) {
            this.errors.push(
              `${relative(rootDir, manifest.path)}: Included file "${includePath}" not found`
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
            this.errors.push(
              `${relative(rootDir, manifest.path)}: Included file "${includePath}" not found`
            );
          }
        }
      }
    }
  }

  private printResults(): void {
    console.log();

    if (this.warnings.length > 0) {
      console.log(chalk.yellow('⚠️  Warnings:'));
      for (const warning of this.warnings) {
        console.log(chalk.yellow(`  - ${warning}`));
      }
      console.log();
    }

    if (this.errors.length > 0) {
      console.log(chalk.red('❌ Errors:'));
      for (const error of this.errors) {
        console.log(chalk.red(`  - ${error}`));
      }
      console.log();
      console.log(chalk.red(`Found ${this.errors.length} error(s)`));
    } else {
      console.log(chalk.green('✅ All validations passed!'));
      console.log(chalk.gray(`  Validated ${this.manifestsById.size} manifests`));
    }
  }
}

// Main execution
const validator = new Validator();
validator.validate().then((success) => {
  process.exit(success ? 0 : 1);
});
