import { Command } from 'commander';
import { ConfigService } from '../../core/services/config.service.js';
import { join, basename } from 'path';
import { mkdir, readFile, writeFile, readdir } from 'fs/promises';
import { load as loadYaml, dump as dumpYaml } from 'js-yaml';
import chalk from 'chalk';

export const createCommand = new Command('create')
  .description('Create a new project')
  .argument('<name>', 'Project name')
  .option('--global', 'Create in global scope')
  .option('--local', 'Create in local scope (default)')
  .option('-d, --description <desc>', 'Project description')
  .action(async (name, options) => {
    await createProject(name, options);
  });

export async function createProject(name: string, options: any) {
  const config = ConfigService.getInstance();
  const scope = options.global ? 'global' : 'local';
  
  console.log(chalk.blue(`\n📦 Creating new ${scope} project: ${name}\n`));

  // Validate project ID
  const projectId = toKebabCase(name);
  if (projectId !== name) {
    console.log(chalk.yellow(`  Note: Project ID converted to kebab-case: ${projectId}`));
  }

  // Check if exists
  const projectDir = config.getPath(config.dirs.projects, scope, projectId);
  try {
    await readdir(projectDir);
    console.error(chalk.red(`Project already exists: ${projectId} in ${scope}`));
    process.exit(1);
  } catch (e: any) {
    if (e.code !== 'ENOENT') throw e;
  }

  // Create directory
  await mkdir(projectDir, { recursive: true });
  console.log(chalk.gray(`  Created directory: ${projectDir}`));

  // Find template
  // We can use a hardcoded template path or look for one
  // For now, let's assume we have a template in global projects
  const templateDir = config.getPath(config.dirs.projects, 'global', 'template');
  
  try {
    // Copy project.yml
    const projectYmlPath = join(templateDir, 'project.yml');
    const projectYmlContent = await readFile(projectYmlPath, 'utf-8');
    const projectManifest = loadYaml(projectYmlContent) as any;

    // Customize
    projectManifest.id = projectId;
    projectManifest.name = toTitleCase(name);
    if (options.description) {
      projectManifest.description = options.description;
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

    // .gitkeep for local
    if (scope === 'local') {
      await writeFile(join(projectDir, '.gitkeep'), '', 'utf-8');
    }

    console.log(chalk.green(`\n✅ Project created successfully!\n`));
    console.log(chalk.bold('Next steps:'));
    console.log(chalk.gray(`  1. Edit ${join(projectDir, 'project.yml')}`));
    console.log(chalk.gray(`  2. Run: npm run project:deploy ${projectId}`));

  } catch (e: any) {
    console.error(chalk.red(`Failed to create project: ${e.message}`));
    // Cleanup
    // await rm(projectDir, { recursive: true, force: true });
  }
}

function toKebabCase(str: string): string {
  return str.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

function toTitleCase(str: string): string {
  return str.split(/[-_\s]+/).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ');
}
