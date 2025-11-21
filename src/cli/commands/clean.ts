import { Command } from 'commander';
import chalk from 'chalk';
import { CleanService } from '../../core/services/clean.service.js';

export const cleanCommand = new Command('clean')
  .description('Clean generated artifacts')
  .action(async () => {
    console.log(chalk.blue('🧹 Cleaning generated files...\n'));

    const service = new CleanService();
    const cleanedPaths = await service.clean();

    for (const path of cleanedPaths) {
      console.log(chalk.gray(`  Cleaned: ${path}`));
    }

    console.log(chalk.green(`\n✅ Cleaned ${cleanedPaths.length} directories\n`));
  });
