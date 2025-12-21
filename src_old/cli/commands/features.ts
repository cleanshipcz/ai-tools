import { Command } from 'commander';
import chalk from 'chalk';
import { FeatureService } from '../../core/services/feature.service.js';

export const featuresCommand = new Command('features')
  .description('Manage project features');

featuresCommand
  .command('generate')
  .description('Generate feature snippets and recipes')
  .argument('<project-id>', 'Project ID')
  .action(async (projectId) => {
    const service = new FeatureService();
    try {
      await service.generateFeatures(projectId);
    } catch (error: any) {
      console.error(chalk.red('\n❌ Error:'), error.message);
      process.exit(1);
    }
  });
