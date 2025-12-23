import { Command } from 'commander';
import chalk from 'chalk';
import { ValidationService } from '../../core/services/validation.service.js';

export const validateCommand = new Command('validate')
  .description('Validate all manifests against schemas and rules')
  .action(async () => {
    console.log(chalk.blue('🔍 Starting validation...\n'));

    const validator = new ValidationService();
    const result = await validator.validateAll();

    console.log();

    if (result.warnings.length > 0) {
      console.log(chalk.yellow('⚠️  Warnings:'));
      for (const warning of result.warnings) {
        console.log(chalk.yellow(`  - ${warning}`));
      }
      console.log();
    }

    if (result.errors.length > 0) {
      console.log(chalk.red('❌ Errors:'));
      for (const error of result.errors) {
        console.log(chalk.red(`  - ${error}`));
      }
      console.log();
      console.log(chalk.red(`Found ${result.errors.length} error(s)`));
      process.exit(1);
    } else {
      console.log(chalk.green('✅ All validations passed!'));
      console.log(chalk.gray(`  Validated ${result.stats.checked} manifests`));
    }
  });
