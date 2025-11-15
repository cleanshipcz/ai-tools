#!/usr/bin/env node
import { readFile, writeFile, mkdir, copyFile, readdir } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml, dump as dumpYaml } from 'js-yaml';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface CreateOptions {
  scope: 'global' | 'local';
  name: string;
  description?: string;
  interactive?: boolean;
}

class ProjectCreator {
  async create(options: CreateOptions): Promise<void> {
    const { scope, name, description } = options;

    console.log(chalk.blue(`\n📦 Creating new ${scope} project: ${name}\n`));

    // Validate project ID (kebab-case)
    const projectId = this.toKebabCase(name);
    if (projectId !== name) {
      console.log(chalk.yellow(`  Note: Project ID converted to kebab-case: ${projectId}`));
    }

    // Check if project already exists
    const projectDir = join(rootDir, 'projects', scope, projectId);
    try {
      await readdir(projectDir);
      throw new Error(`Project already exists: ${projectId} in ${scope}`);
    } catch (error: any) {
      if (error.code !== 'ENOENT') throw error;
    }

    // Create project directory
    await mkdir(projectDir, { recursive: true });
    console.log(chalk.gray(`  Created directory: projects/${scope}/${projectId}/`));

    // Copy and customize template files
    const templateDir = join(rootDir, 'projects', 'global', 'template');

    // Copy project.yml
    const projectYmlPath = join(templateDir, 'project.yml');
    const projectYmlContent = await readFile(projectYmlPath, 'utf-8');
    const projectManifest = loadYaml(projectYmlContent) as any;

    // Customize the manifest
    projectManifest.id = projectId;
    projectManifest.name = this.toTitleCase(name);
    if (description) {
      projectManifest.description = description;
    }
    projectManifest.metadata = projectManifest.metadata || {};
    projectManifest.metadata.created = new Date().toISOString().split('T')[0];
    projectManifest.metadata.updated = new Date().toISOString().split('T')[0];

    await writeFile(
      join(projectDir, 'project.yml'),
      dumpYaml(projectManifest, { lineWidth: -1, noRefs: true }),
      'utf-8'
    );
    console.log(chalk.gray(`  Created project.yml`));

    // Copy deploy.yml
    const deployYmlPath = join(templateDir, 'deploy.yml');
    const deployYmlContent = await readFile(deployYmlPath, 'utf-8');

    await writeFile(join(projectDir, 'deploy.yml'), deployYmlContent, 'utf-8');
    console.log(chalk.gray(`  Created deploy.yml`));

    // Create .gitkeep for local projects
    if (scope === 'local') {
      await writeFile(join(projectDir, '.gitkeep'), '', 'utf-8');
      console.log(chalk.gray(`  Created .gitkeep (for git tracking)`));
    }

    console.log(chalk.green(`\n✅ Project created successfully!\n`));
    console.log(chalk.bold('Next steps:'));
    console.log(
      chalk.gray(`  1. Edit projects/${scope}/${projectId}/project.yml with your project details`)
    );
    console.log(
      chalk.gray(`  2. Edit projects/${scope}/${projectId}/deploy.yml with deployment settings`)
    );
    if (scope === 'global') {
      console.log(
        chalk.gray(
          `  3. Create projects/${scope}/${projectId}/deploy.local.yml for local overrides`
        )
      );
    }
    console.log(chalk.gray(`  4. Run: npm run project:deploy ${projectId}`));
    console.log('');
  }

  private toKebabCase(str: string): string {
    return str
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  private toTitleCase(str: string): string {
    return str
      .split(/[-_\s]+/)
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }
}

// CLI
if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2);

  // Parse arguments
  let name = '';
  let scope: 'global' | 'local' = 'local';
  let description = '';

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];

    if (arg === '--global') {
      scope = 'global';
    } else if (arg === '--local') {
      scope = 'local';
    } else if (arg === '--description' || arg === '-d') {
      description = args[++i] || '';
    } else if (!name) {
      name = arg;
    }
  }

  if (!name) {
    console.error(chalk.red('Error: Project name required'));
    console.log('\nUsage: npm run project:create <name> [--global] [--description "..."]');
    console.log('\nOptions:');
    console.log('  --global          Create in projects/global/ (default: local)');
    console.log('  --local           Create in projects/local/ (default)');
    console.log('  --description, -d Project description');
    console.log('\nExamples:');
    console.log('  npm run project:create my-web-app');
    console.log('  npm run project:create company-api --global -d "Internal REST API"');
    process.exit(1);
  }

  const creator = new ProjectCreator();
  creator.create({ scope, name, description }).catch((error) => {
    console.error(chalk.red('\n❌ Error:'), error.message);
    process.exit(1);
  });
}
