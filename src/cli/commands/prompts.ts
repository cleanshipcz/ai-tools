import { Command } from 'commander';
import chalk from 'chalk';
import readline from 'readline';
import { PromptService } from '../../core/services/prompt.service.js';

export const promptsCommand = new Command('prompts')
  .description('Manage prompts');

promptsCommand
  .command('library')
  .description('Generate user-friendly prompt library (Markdown)')
  .action(async () => {
    const service = new PromptService();
    try {
      await service.generateLibrary();
    } catch (error: any) {
      console.error(chalk.red('\n❌ Error:'), error.message);
      process.exit(1);
    }
  });

promptsCommand
  .command('html')
  .description('Generate interactive HTML prompt browser')
  .action(async () => {
    const service = new PromptService();
    try {
      await service.generateHTML();
    } catch (error: any) {
      console.error(chalk.red('\n❌ Error:'), error.message);
      process.exit(1);
    }
  });

promptsCommand
  .command('use')
  .description('Interactively use a prompt')
  .argument('<prompt-id>', 'ID of the prompt to use')
  .action(async (promptId) => {
    const service = new PromptService();
    try {
      const prompt = await service.getPrompt(promptId);

      if (!prompt) {
        console.error(chalk.red(`❌ Prompt "${promptId}" not found`));
        console.log('\nAvailable prompts:');
        const prompts = await service.listPrompts();
        
        // Group by category
        const byCategory: Record<string, typeof prompts> = {};
        prompts.forEach((p) => {
          if (!byCategory[p.category]) byCategory[p.category] = [];
          byCategory[p.category].push(p);
        });

        Object.entries(byCategory)
          .sort()
          .forEach(([category, items]) => {
            console.log(chalk.blue(`\n  ${category.toUpperCase()}:`));
            items.forEach((item) => {
              console.log(`    • ${item.id.padEnd(25)} - ${item.description}`);
            });
          });
        
        process.exit(1);
      }

      console.log(chalk.blue(`\n📝 Using prompt: ${prompt.id}`));
      console.log(`📄 ${prompt.description}\n`);

      if (!prompt.variables || prompt.variables.length === 0) {
        console.log(chalk.green("✅ No variables needed. Here's your prompt:\n"));
        console.log('─'.repeat(80));
        console.log(prompt.content);
        console.log('─'.repeat(80));
        return;
      }

      // Collect variable values
      const values: Record<string, string> = {};
      const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
      });

      console.log('Please provide values for the following variables:\n');

      for (const variable of prompt.variables) {
        const required = variable.required ? '(required)' : '(optional)';
        const value = await question(
          rl,
          `  ${chalk.bold(variable.name)} ${chalk.gray(required)} - ${variable.description}\n  → `
        );

        if (!value && variable.required) {
          console.error(chalk.red(`\n❌ Error: ${variable.name} is required`));
          rl.close();
          process.exit(1);
        }

        if (value) {
          values[variable.name] = value;
        }
      }

      rl.close();

      const filledPrompt = service.fillPrompt(prompt, values);

      console.log(chalk.green('\n✅ Prompt ready! Copy everything below:\n'));
      console.log('═'.repeat(80));
      console.log(filledPrompt.trim());
      console.log('═'.repeat(80));
      console.log(chalk.gray('\n💡 Paste this into ChatGPT, Claude, or any LLM'));

    } catch (error: any) {
      console.error(chalk.red('\n❌ Error:'), error.message);
      process.exit(1);
    }
  });

function question(rl: readline.Interface, prompt: string): Promise<string> {
  return new Promise((resolve) => {
    rl.question(prompt, (answer) => {
      resolve(answer.trim());
    });
  });
}
