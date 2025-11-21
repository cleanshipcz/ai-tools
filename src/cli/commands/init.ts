import { Command } from 'commander';
import { ConfigService } from '../../core/services/config.service.js';
import { ExternalProjectService } from '../../core/services/external-project.service.js';
import { join, resolve, basename, dirname } from 'path';
import { mkdir, readFile, writeFile, access, copyFile } from 'fs/promises';
import { load as loadYaml, dump as dumpYaml } from 'js-yaml';
import chalk from 'chalk';

export const initCommand = new Command('init')
  .description('Initialize AI tools in an external project')
  .argument('<path>', 'Path to external project')
  .option('-a, --alias <name>', 'Project alias')
  .option('-d, --description <desc>', 'Project description')
  .option('--no-register', 'Do not register in external projects list')
  .option('--global', 'Register globally')
  .action(async (path, options) => {
    await initProject(path, options);
  });

export async function initProject(path: string, options: any) {
  const config = ConfigService.getInstance();
  const externalService = new ExternalProjectService();

  console.log(chalk.blue(`\n🚀 Initializing AI tools in external project\n`));

  const resolvedPath = resolve(path);
  try {
    await access(resolvedPath);
  } catch {
    console.error(chalk.red(`Path does not exist: ${resolvedPath}`));
    process.exit(1);
  }

  const projectName = options.alias || basename(resolvedPath);
  const projectId = toKebabCase(projectName);
  const aiToolsDir = join(resolvedPath, '.cleanship-ai-tools');

  try {
    await access(aiToolsDir);
    console.error(chalk.red(`.cleanship-ai-tools folder already exists at ${aiToolsDir}`));
    process.exit(1);
  } catch {}

  await mkdir(aiToolsDir, { recursive: true });
  console.log(chalk.gray(`  Created: .cleanship-ai-tools/`));

  // Template
  const templateDir = config.getPath(config.dirs.projects, 'global', 'template');

  try {
    // project.yml
    const projectYmlPath = join(templateDir, 'project.yml');
    const projectYmlContent = await readFile(projectYmlPath, 'utf-8');
    const projectManifest = loadYaml(projectYmlContent) as any;

    projectManifest.id = projectId;
    projectManifest.name = toTitleCase(projectName);
    projectManifest.description = options.description || `${toTitleCase(projectName)} project configuration`;
    projectManifest.metadata = {
      created: new Date().toISOString().split('T')[0],
      updated: new Date().toISOString().split('T')[0]
    };

    await writeFile(
      join(aiToolsDir, 'project.yml'),
      dumpYaml(projectManifest, { lineWidth: -1, noRefs: true }),
      'utf-8'
    );
    console.log(chalk.gray(`  Created: project.yml`));

    // deploy.yml
    const deployYmlPath = join(templateDir, 'deploy.yml');
    let deployYmlContent = await readFile(deployYmlPath, 'utf-8');
    deployYmlContent = deployYmlContent.replace(
      /target: .*/,
      'target: ".."  # Deploy to project root'
    );
    await writeFile(join(aiToolsDir, 'deploy.yml'), deployYmlContent, 'utf-8');
    console.log(chalk.gray(`  Created: deploy.yml`));

    // README
    await writeFile(join(aiToolsDir, 'README.md'), `# AI Tools Configuration\n\nFor ${projectName}`, 'utf-8');

    console.log(chalk.green(`\n✅ Initialized successfully!\n`));

    // Register
    if (options.register !== false) {
      await externalService.addProject(aiToolsDir, projectId, options.global);
    }

    console.log(chalk.bold('Next steps:'));
    console.log(chalk.gray(`  1. Edit ${join(aiToolsDir, 'project.yml')}`));
    console.log(chalk.gray(`  2. Run: npm run project:deploy ${projectId}`));

  } catch (e: any) {
    console.error(chalk.red(`Failed to init project: ${e.message}`));
  }
}

function toKebabCase(str: string): string {
  return str.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

function toTitleCase(str: string): string {
  return str.split(/[-_\s]+/).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ');
}
