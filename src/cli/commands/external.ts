import { Command } from 'commander';
import { ExternalProjectService } from '../../core/services/external-project.service.js';
import chalk from 'chalk';

export const externalCommand = new Command('external')
  .description('Manage external projects');

externalCommand
  .command('list')
  .description('List all external projects')
  .action(async () => {
    const service = new ExternalProjectService();
    const projects = await service.getAllProjects();
    
    console.log(chalk.blue('\n📋 External Projects:\n'));
    
    if (projects.length === 0) {
      console.log(chalk.gray('  (none)'));
    } else {
      // Group by scope if possible, but service returns flat list currently
      // The service implementation I wrote earlier returns a flat list but doesn't preserve scope info in the return type
      // Let's check ExternalProjectService implementation
      for (const p of projects) {
        console.log(chalk.green(`  • ${p.alias}`) + chalk.gray(` - ${p.path}`));
      }
    }
    console.log('');
  });

externalCommand
  .command('add <path>')
  .description('Add an external project')
  .option('-a, --alias <alias>', 'Project alias')
  .option('-g, --global', 'Add to global registry')
  .action(async (path, options) => {
    const service = new ExternalProjectService();
    try {
      await service.addProject(path, options.alias, options.global);
    } catch (e: any) {
      console.error(chalk.red(`\n❌ Error: ${e.message}`));
      process.exit(1);
    }
  });

externalCommand
  .command('remove <aliasOrPath>')
  .description('Remove an external project')
  .action(async (aliasOrPath) => {
    const service = new ExternalProjectService();
    try {
      await service.removeProject(aliasOrPath);
    } catch (e: any) {
      console.error(chalk.red(`\n❌ Error: ${e.message}`));
      process.exit(1);
    }
  });
