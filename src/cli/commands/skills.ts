import { Command } from 'commander';
import chalk from 'chalk';
import { SkillService } from '../../core/services/skill.service.js';

export const skillsCommand = new Command('skills')
  .description('Manage skills');

skillsCommand
  .command('generate')
  .description('Generate Anthropic-compatible SKILL.md files')
  .action(async () => {
    const service = new SkillService();
    try {
      await service.generateSkills();
    } catch (error: any) {
      console.error(chalk.red('\n❌ Error:'), error.message);
      process.exit(1);
    }
  });
