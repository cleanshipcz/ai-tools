import { Command } from 'commander';
import chalk from 'chalk';
import { RecipeService } from '../../core/services/recipe.service.js';
import { RecipeRunnerService } from '../../core/services/recipe-runner.service.js';
import { join } from 'path';

export const recipesCommand = new Command('recipes')
  .description('Manage and run recipes');

recipesCommand
  .command('list')
  .description('List available recipes')
  .action(async () => {
    const service = new RecipeService();
    const recipes = await service.listRecipes();

    console.log(chalk.blue.bold('\n📋 Available Recipes:\n'));

    for (const recipe of recipes) {
      console.log(chalk.cyan(`  • ${recipe.id}`));
      console.log(chalk.gray(`    ${recipe.description}`));
      console.log(chalk.gray(`    Tools: ${recipe.tools?.join(', ') || 'any'}`));
      console.log(chalk.gray(`    Steps: ${recipe.steps.length}\n`));
    }
  });

recipesCommand
  .command('run')
  .description('Run a recipe interactively')
  .argument('<recipe-id>', 'ID of the recipe to run')
  .argument('[tool]', 'Tool to use (claude-code, copilot-cli, cursor)', 'claude-code')
  .action(async (recipeId, tool) => {
    const service = new RecipeService();
    const recipe = await service.loadRecipe(recipeId);

    if (!recipe) {
      console.error(chalk.red(`❌ Recipe "${recipeId}" not found`));
      process.exit(1);
    }

    if (!['claude-code', 'copilot-cli', 'cursor'].includes(tool)) {
      console.error(chalk.red(`❌ Invalid tool: ${tool}`));
      console.log(chalk.gray('Supported tools: claude-code, copilot-cli, cursor'));
      process.exit(1);
    }

    const runner = new RecipeRunnerService(recipe, tool as any);
    await runner.run();
  });

recipesCommand
  .command('generate')
  .description('Generate an executable bash script for a recipe')
  .argument('<recipe-id>', 'ID of the recipe')
  .argument('[tool]', 'Tool to use (claude-code, copilot-cli, cursor)', 'claude-code')
  .argument('[output-path]', 'Output path for the script')
  .action(async (recipeId, tool, outputPath) => {
    const service = new RecipeService();
    const recipe = await service.loadRecipe(recipeId);

    if (!recipe) {
      console.error(chalk.red(`❌ Recipe "${recipeId}" not found`));
      process.exit(1);
    }

    const scriptPath = outputPath || join(process.cwd(), '.output', 'scripts', `${recipeId}-${tool}.sh`);
    
    try {
      await service.generateRecipeScript(recipe, tool, scriptPath);
      console.log(chalk.green(`\n✅ Generated executable script: ${scriptPath}\n`));
      console.log(chalk.cyan('Run with:'));
      console.log(chalk.white(`  ${scriptPath}\n`));
    } catch (error: any) {
      console.error(chalk.red('❌ Error generating script:'), error.message);
      process.exit(1);
    }
  });
