import { Command } from 'commander';
import { ConfigService } from '../../core/services/config.service.js';
import { ExternalProjectService } from '../../core/services/external-project.service.js';
import { readdir, access } from 'fs/promises';
import { join } from 'path';
import chalk from 'chalk';

export const listCommand = new Command('list')
  .description('List all available projects')
  .action(async () => {
    await listProjects();
  });

async function listProjects() {
  const config = ConfigService.getInstance();
  const externalService = new ExternalProjectService();

  console.log(chalk.blue('\n📋 Available Projects:\n'));

  // 1. Configured Projects (Global + Local + Custom)
  console.log(chalk.bold('Configured Projects:'));
  const projectSources = await config.getProjectSources();
  let configuredCount = 0;

  for (const source of projectSources) {
    try {
      const entries = await readdir(source, { withFileTypes: true });
      for (const entry of entries) {
        if (entry.isDirectory() && 
            entry.name !== 'template' && 
            entry.name !== '.gitkeep' && 
            !entry.name.startsWith('.')) {
          
          // Check if it has project.yml (optional, but good for filtering)
          const projectPath = join(source, entry.name);
          try {
            await access(join(projectPath, 'project.yml'));
            console.log(chalk.green(`  • ${entry.name}`) + chalk.gray(` (${source})`));
            configuredCount++;
          } catch {
            // Maybe just a folder, skip or show as warning?
            // Legacy script checked for deploy.yml
          }
        }
      }
    } catch {}
  }

  if (configuredCount === 0) console.log(chalk.gray('  (none)'));
  console.log('');

  // 3. External Projects
  console.log(chalk.bold('External Projects:'));
  const externalProjects = await externalService.getAllProjects();
  if (externalProjects.length === 0) {
    console.log(chalk.gray('  (none)'));
  } else {
    for (const p of externalProjects) {
      console.log(chalk.green(`  • ${p.alias}`) + chalk.gray(` - ${p.path}`));
    }
  }
  console.log('');
}
