import { Command } from 'commander';
import chalk from 'chalk';
import { DiffService } from '../../core/services/diff.service.js';

export const diffCommand = new Command('diff')
  .description('Compare two files')
  .requiredOption('--before <file>', 'Path to the before/baseline file')
  .requiredOption('--after <file>', 'Path to the after/current file')
  .option('--format <type>', 'Diff format: "lines" (default) or "words"', 'lines')
  .action(async (options) => {
    console.log(chalk.blue('🔍 Comparing outputs...\n'));

    const service = new DiffService();
    try {
      const result = await service.compare({
        before: options.before,
        after: options.after,
        format: options.format as 'lines' | 'words',
      });

      console.log(chalk.bold('Diff:\n'));

      for (const part of result.changes) {
        const lines = part.value.split('\n').filter((l: string) => l.length > 0);

        if (part.added) {
          for (const line of lines) {
            console.log(chalk.green(`+ ${line}`));
          }
        } else if (part.removed) {
          for (const line of lines) {
            console.log(chalk.red(`- ${line}`));
          }
        } else {
          // Show a few lines of context
          const contextLines = lines.slice(0, 2);
          for (const line of contextLines) {
            console.log(chalk.gray(`  ${line}`));
          }
          if (lines.length > 2) {
            console.log(chalk.gray(`  ... (${lines.length - 2} more unchanged lines)`));
          }
        }
      }

      console.log(chalk.bold('\nSummary:'));
      console.log(chalk.green(`  Additions: ${result.stats.additions}`));
      console.log(chalk.red(`  Deletions: ${result.stats.deletions}`));
      console.log(chalk.gray(`  Unchanged: ${result.stats.unchanged}`));

      if (result.stats.additions + result.stats.deletions === 0) {
        console.log(chalk.yellow('\n  No changes detected'));
      } else {
        console.log(chalk.blue(`\n  Change ratio: ${result.stats.changeRatio.toFixed(1)}%`));
      }
    } catch (error: any) {
      console.error(chalk.red('Diff failed:'), error.message);
      process.exit(1);
    }
  });
