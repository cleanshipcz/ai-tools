import { Command } from 'commander';
import chalk from 'chalk';
import { EvalService } from '../../core/services/eval.service.js';

export const evalCommand = new Command('eval')
  .description('Run evaluation suites')
  .option('--suite <name>', 'Run a specific suite')
  .action(async (options) => {
    console.log(chalk.blue('🧪 Running evaluations...\n'));

    const service = new EvalService();
    const result = await service.run(options.suite);

    // Print details
    for (const line of result.details) {
      if (line.includes('✓')) {
        console.log(chalk.gray(line));
      } else if (line.includes('✗')) {
        console.log(chalk.red(line));
      } else if (line.includes('⚠')) {
        console.log(chalk.yellow(line));
      } else {
        console.log(line); // Default log
      }
    }

    console.log();

    if (result.success) {
      console.log(chalk.green('✅ All evaluations passed!'));
    } else {
      console.log(chalk.red('❌ Some evaluations failed'));
      process.exit(1);
    }
  });
