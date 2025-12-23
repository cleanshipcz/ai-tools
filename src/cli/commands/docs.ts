import { Command } from 'commander';
import chalk from 'chalk';
import { DocsService } from '../../core/services/docs.service.js';

export const docsCommand = new Command('docs')
  .description('Manage documentation');

docsCommand
  .command('generate')
  .description('Generate documentation from manifests')
  .action(async () => {
    const service = new DocsService();
    try {
      await service.generateDocs();
    } catch (error: any) {
      console.error(chalk.red('\n❌ Error:'), error.message);
      process.exit(1);
    }
  });
