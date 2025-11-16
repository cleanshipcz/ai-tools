#!/usr/bin/env node
import { readFile, readdir, writeFile, mkdir } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import { spawn } from 'child_process';
import chalk from 'chalk';
import * as readline from 'readline';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface RecipeStep {
  id: string;
  agent: string;
  task: string;
  prompt?: string;
  inputs?: Record<string, string>;
  outputDocument?: string;
  includeDocuments?: string[];
  continueConversation?: boolean;
  waitForConfirmation?: boolean;
  condition?: {
    type: 'always' | 'on-success' | 'on-failure' | 'user-decision';
    check?: {
      type: 'command' | 'regex' | 'contains' | 'user-approval';
      cmd?: string;
      pattern?: string;
      value?: string;
      prompt?: string;
    };
  };
}

interface RecipeLoop {
  steps: string[];
  maxIterations: number;
  condition: {
    type: 'command' | 'user-decision' | 'max-iterations';
    cmd?: string;
    prompt?: string;
  };
}

interface Recipe {
  id: string;
  version: string;
  description: string;
  tags?: string[];
  tools?: string[];
  steps: RecipeStep[];
  loop?: RecipeLoop;
  variables?: Record<string, string>;
  metadata?: any;
}

interface Agent {
  id: string;
  version: string;
  purpose: string;
  [key: string]: any;
}

class RecipeRunner {
  private recipe: Recipe;
  private agents: Map<string, Agent> = new Map();
  private tool: 'claude-code' | 'copilot-cli' | 'cursor';
  private conversationId?: string;
  private outputFile: string;
  private rl: readline.Interface;
  private documents: Map<string, string> = new Map();

  constructor(recipe: Recipe, tool: 'claude-code' | 'copilot-cli' | 'cursor') {
    this.recipe = recipe;
    this.tool = tool;
    this.outputFile = `/tmp/recipe-output-${Date.now()}.txt`;
    this.rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
    });
  }

  async run(): Promise<void> {
    console.log(chalk.blue.bold(`\n🚀 Running recipe: ${this.recipe.id}\n`));
    console.log(chalk.gray(this.recipe.description));
    console.log(chalk.gray(`Tool: ${this.tool}\n`));

    // Load agents
    await this.loadAgents();

    // Check if recipe supports this tool
    if (this.recipe.tools && !this.recipe.tools.includes(this.tool)) {
      console.log(
        chalk.yellow(
          `⚠️  Warning: Recipe doesn't explicitly support ${this.tool}. Supported tools: ${this.recipe.tools.join(', ')}`
        )
      );
      const proceed = await this.askUser('Continue anyway? (y/n): ');
      if (proceed.toLowerCase() !== 'y') {
        console.log(chalk.red('Recipe execution cancelled.'));
        this.rl.close();
        return;
      }
    }

    // Execute steps
    let iteration = 1;
    const maxIterations = this.recipe.loop?.maxIterations || 1;

    while (iteration <= maxIterations) {
      if (this.recipe.loop && iteration > 1) {
        console.log(chalk.cyan(`\n📍 Loop iteration ${iteration}/${maxIterations}\n`));
      }

      for (const step of this.recipe.steps) {
        const shouldExecute = await this.shouldExecuteStep(step);
        if (!shouldExecute) {
          console.log(chalk.gray(`⏭️  Skipping step: ${step.id}`));
          continue;
        }

        await this.executeStep(step);

        if (step.waitForConfirmation) {
          const proceed = await this.askUser('\nContinue to next step? (y/n): ');
          if (proceed.toLowerCase() !== 'y') {
            console.log(chalk.yellow('Recipe execution paused by user.'));
            break;
          }
        }
      }

      // Check loop condition
      if (this.recipe.loop && iteration < maxIterations) {
        const shouldContinue = await this.checkLoopCondition();
        if (!shouldContinue) {
          console.log(chalk.green('Loop condition met, exiting loop.'));
          break;
        }
      } else if (iteration >= maxIterations) {
        console.log(chalk.yellow('Maximum iterations reached.'));
        break;
      }

      iteration++;
    }

    console.log(chalk.green.bold('\n✅ Recipe execution completed!\n'));
    this.rl.close();
  }

  private async loadAgents(): Promise<void> {
    const agentsDir = join(rootDir, 'agents');
    const files = await readdir(agentsDir);

    for (const file of files) {
      if (!file.endsWith('.yml') || file === 'README.md') continue;

      const content = await readFile(join(agentsDir, file), 'utf-8');
      const agent = loadYaml(content) as Agent;
      this.agents.set(agent.id, agent);
    }
  }

  private async shouldExecuteStep(step: RecipeStep): Promise<boolean> {
    if (!step.condition || step.condition.type === 'always') {
      return true;
    }

    if (step.condition.type === 'user-decision') {
      const response = await this.askUser(
        step.condition.check?.prompt || 'Execute this step? (y/n): '
      );
      return response.toLowerCase() === 'y';
    }

    if (step.condition.type === 'on-success' && step.condition.check) {
      return await this.checkCondition(step.condition.check);
    }

    return true;
  }

  private async checkCondition(check: {
    type: string;
    cmd?: string;
    pattern?: string;
    value?: string;
  }): Promise<boolean> {
    try {
      const output = await readFile(this.outputFile, 'utf-8');

      if (check.type === 'contains' && check.value) {
        return output.includes(check.value);
      }

      if (check.type === 'regex' && check.pattern) {
        const regex = new RegExp(check.pattern);
        return regex.test(output);
      }

      if (check.type === 'command' && check.cmd) {
        return await this.runCommand(check.cmd);
      }
    } catch (error) {
      console.log(chalk.red(`Error checking condition: ${error}`));
    }

    return false;
  }

  private async checkLoopCondition(): Promise<boolean> {
    if (!this.recipe.loop) return false;

    const condition = this.recipe.loop.condition;

    if (condition.type === 'max-iterations') {
      return false; // Handled by main loop
    }

    if (condition.type === 'user-decision') {
      const response = await this.askUser(condition.prompt || 'Continue loop? (y/n): ');
      return response.toLowerCase() === 'y';
    }

    if (condition.type === 'command' && condition.cmd) {
      return await this.runCommand(condition.cmd);
    }

    return false;
  }

  private async executeStep(step: RecipeStep): Promise<void> {
    console.log(chalk.cyan(`\n▶️  Step: ${step.id}`));
    console.log(chalk.gray(`   Agent: ${step.agent}`));

    const agent = this.agents.get(step.agent);
    if (!agent) {
      console.log(chalk.red(`   ❌ Agent not found: ${step.agent}`));
      return;
    }

    // Interpolate variables in task
    let task = step.task;
    if (this.recipe.variables) {
      for (const [key, value] of Object.entries(this.recipe.variables)) {
        task = task.replace(new RegExp(`{{${key}}}`, 'g'), value);
      }
    }
    if (step.inputs) {
      for (const [key, value] of Object.entries(step.inputs)) {
        task = task.replace(new RegExp(`{{${key}}}`, 'g'), value);
      }
    }

    // Include documents in task context if specified
    if (step.includeDocuments && step.includeDocuments.length > 0) {
      task += '\n\n--- Reference Documents ---\n';
      for (const docPath of step.includeDocuments) {
        const docContent = this.documents.get(docPath);
        if (docContent) {
          task += `\n\n**Document: ${docPath}**\n\n${docContent}\n`;
        } else {
          console.log(chalk.yellow(`   ⚠️  Document not found: ${docPath}`));
        }
      }
    }

    // Add output document instruction if specified
    if (step.outputDocument) {
      task += `\n\n**IMPORTANT**: Save your complete response to the file: ${step.outputDocument}`;
      console.log(chalk.gray(`   📄 Output will be saved to: ${step.outputDocument}`));
    }

    console.log(chalk.gray(`\n${task}\n`));

    // Execute based on tool
    await this.executeWithTool(step.agent, task, step.continueConversation !== false);

    // If this step has an output document, ask user to save it
    if (step.outputDocument) {
      await this.saveDocument(step.outputDocument);
    }
  }

  private async executeWithTool(
    agentId: string,
    task: string,
    continueConversation: boolean
  ): Promise<void> {
    console.log(
      chalk.yellow(
        `\n⚡ Executing with ${this.tool}${continueConversation && this.conversationId ? ' (continuing conversation)' : ''}`
      )
    );

    if (this.tool === 'claude-code') {
      await this.executeClaudeCode(agentId, task, continueConversation);
    } else if (this.tool === 'copilot-cli') {
      await this.executeCopilotCLI(agentId, task, continueConversation);
    } else if (this.tool === 'cursor') {
      await this.executeCursor(agentId, task, continueConversation);
    }

    console.log(chalk.green('   ✓ Step completed\n'));
  }

  private async executeClaudeCode(
    agentId: string,
    task: string,
    continueConversation: boolean
  ): Promise<void> {
    const args = [];

    if (continueConversation && this.conversationId) {
      args.push('-c', this.conversationId);
    }

    args.push('--agent', agentId);
    args.push(task);

    console.log(chalk.gray(`   Command: claude ${args.join(' ')}\n`));
    console.log(
      chalk.yellow(
        '   📝 Please execute the above command in your terminal and return here when done.\n'
      )
    );

    await this.askUser('Press Enter when command is completed...');

    // Try to capture conversation ID for continuation
    if (!this.conversationId) {
      const convId = await this.askUser(
        'Enter conversation ID for continuation (or press Enter to skip): '
      );
      if (convId.trim()) {
        this.conversationId = convId.trim();
      }
    }
  }

  private async executeCopilotCLI(
    agentId: string,
    task: string,
    continueConversation: boolean
  ): Promise<void> {
    console.log(
      chalk.gray(
        `   Command: copilot${continueConversation ? ' (continue in same session)' : ''}\n`
      )
    );
    console.log(chalk.gray(`   Use agent: @${agentId}\n`));
    console.log(chalk.gray(`   Task: ${task}\n`));
    console.log(
      chalk.yellow(
        '   📝 Please execute the following in GitHub Copilot CLI and return here when done:\n'
      )
    );
    console.log(chalk.white(`      @${agentId} ${task}\n`));

    await this.askUser('Press Enter when command is completed...');
  }

  private async executeCursor(
    agentId: string,
    task: string,
    continueConversation: boolean
  ): Promise<void> {
    console.log(
      chalk.gray(`   Mode: Composer${continueConversation ? ' (continue in same session)' : ''}\n`)
    );
    console.log(chalk.gray(`   Agent: ${agentId}\n`));
    console.log(chalk.gray(`   Task: ${task}\n`));
    console.log(
      chalk.yellow(
        '   📝 Please execute the following in Cursor Composer and return here when done:\n'
      )
    );
    console.log(chalk.white(`      ${task}\n`));

    await this.askUser('Press Enter when command is completed...');
  }

  private async runCommand(cmd: string): Promise<boolean> {
    return new Promise((resolve) => {
      const child = spawn(cmd, { shell: true });
      child.on('close', (code) => {
        resolve(code === 0);
      });
      child.on('error', () => {
        resolve(false);
      });
    });
  }

  private async askUser(question: string): Promise<string> {
    return new Promise((resolve) => {
      this.rl.question(chalk.cyan(question), (answer) => {
        resolve(answer);
      });
    });
  }

  private async saveDocument(docPath: string): Promise<void> {
    console.log(chalk.yellow(`\n📝 Please save the AI's response to: ${docPath}`));
    console.log(
      chalk.gray(
        '   Ensure the document is created in your project directory with the full output.'
      )
    );

    await this.askUser('Press Enter once the document has been saved...');

    // Try to read the document to cache it for future steps
    try {
      const content = await readFile(join(process.cwd(), docPath), 'utf-8');
      this.documents.set(docPath, content);
      console.log(chalk.green(`   ✓ Document loaded and cached: ${docPath}`));
    } catch (error) {
      console.log(
        chalk.yellow(
          `   ⚠️  Could not read document: ${docPath}. It will not be available for subsequent steps.`
        )
      );
    }
  }
}

async function loadRecipe(recipePath: string): Promise<Recipe> {
  const content = await readFile(recipePath, 'utf-8');
  return loadYaml(content) as Recipe;
}

async function listRecipes(): Promise<void> {
  const recipesDir = join(rootDir, 'recipes');
  const files = await readdir(recipesDir);

  console.log(chalk.blue.bold('\n📋 Available Recipes:\n'));

  for (const file of files) {
    if (!file.endsWith('.yml') || file === 'README.md') continue;

    const recipe = await loadRecipe(join(recipesDir, file));
    console.log(chalk.cyan(`  • ${recipe.id}`));
    console.log(chalk.gray(`    ${recipe.description}`));
    console.log(chalk.gray(`    Tools: ${recipe.tools?.join(', ') || 'any'}`));
    console.log(chalk.gray(`    Steps: ${recipe.steps.length}\n`));
  }
}

async function generateScript(recipeId: string, tool: string, outputPath?: string): Promise<void> {
  const recipePath = join(rootDir, 'recipes', `${recipeId}.yml`);
  const recipe = await loadRecipe(recipePath);

  const scriptPath = outputPath || join(rootDir, '.output', 'scripts', `${recipeId}-${tool}.sh`);
  await mkdir(dirname(scriptPath), { recursive: true });

  let script = '#!/bin/bash\n';
  script += `# Auto-generated recipe script: ${recipe.id}\n`;
  script += `# Description: ${recipe.description}\n`;
  script += `# Tool: ${tool}\n`;
  script += `# Generated: ${new Date().toISOString()}\n\n`;
  script += 'set -e  # Exit on error\n\n';

  // Add variables
  if (recipe.variables) {
    script += '# Variables\n';
    for (const [key, value] of Object.entries(recipe.variables)) {
      const varValue = value.replace(/{{([^}]+)}}/g, (_, varName) => `\${${varName}}`);
      script += `${key.toUpperCase()}="${varValue}"\n`;
    }
    script += '\n';
  }

  // Generate commands for each step
  for (let i = 0; i < recipe.steps.length; i++) {
    const step = recipe.steps[i];

    script += `# Step ${i + 1}: ${step.id}\n`;
    script += `echo "▶️  Step ${i + 1}/${recipe.steps.length}: ${step.id} (${step.agent})"\n`;

    // Interpolate variables in task
    let task = step.task;
    if (recipe.variables) {
      for (const key of Object.keys(recipe.variables)) {
        task = task.replace(new RegExp(`{{${key}}}`, 'g'), `\${${key.toUpperCase()}}`);
      }
    }

    // Include documents in task if specified
    if (step.includeDocuments && step.includeDocuments.length > 0) {
      script += `# Include reference documents\n`;
      task += '\\n\\n--- Reference Documents ---\\n';
      for (const docPath of step.includeDocuments) {
        script += `if [ -f "${docPath}" ]; then\n`;
        script += `  DOC_CONTENT=$(cat "${docPath}")\n`;
        task += `\\n\\n**Document: ${docPath}**\\n\\n\${DOC_CONTENT}\\n`;
        script += `fi\n`;
      }
    }

    // Add output document instruction if specified
    if (step.outputDocument) {
      task += `\\n\\n**IMPORTANT**: Save your complete response to the file: ${step.outputDocument}`;
      script += `echo "📄 Output will be saved to: ${step.outputDocument}"\n`;
      script += `mkdir -p $(dirname "${step.outputDocument}")\n`;
    }

    // Generate tool-specific command
    if (tool === 'claude-code') {
      const continueFlag =
        step.continueConversation !== false && i > 0 ? '-c $CONVERSATION_ID' : '';
      script += `RESPONSE=$(claude ${continueFlag} --agent ${step.agent} "${task.replace(/"/g, '\\"')}")\n`;
      script += `echo "$RESPONSE"\n`;
      if (step.outputDocument) {
        script += `echo "$RESPONSE" > "${step.outputDocument}"\n`;
        script += `echo "✓ Document saved: ${step.outputDocument}"\n`;
      }
      if (i === 0) {
        script += `CONVERSATION_ID=$(echo "$RESPONSE" | grep -oP 'Conversation ID: \\K[a-zA-Z0-9-]+')\n`;
      }
    } else if (tool === 'copilot-cli') {
      script += `echo "@${step.agent} ${task}" | copilot\n`;
      if (step.outputDocument) {
        script += `echo "📝 Please save the response to: ${step.outputDocument}"\n`;
        script += `read -p "Press Enter once saved..."\n`;
      }
    } else if (tool === 'cursor') {
      script += `# Manual: Open Cursor Composer and execute:\n`;
      script += `# @${step.agent} ${task}\n`;
      script += `echo "⚠️  Please execute in Cursor Composer and press Enter"\n`;
      if (step.outputDocument) {
        script += `echo "📝 Save the response to: ${step.outputDocument}"\n`;
      }
      script += `read -p "Continue? "\n`;
    }

    script += '\n';
  }

  script += 'echo "✅ Recipe completed!"\n';

  await writeFile(scriptPath, script, { mode: 0o755 });
  console.log(chalk.green(`\n✅ Generated executable script: ${scriptPath}\n`));
  console.log(chalk.cyan('Run with:'));
  console.log(chalk.white(`  ${scriptPath}\n`));
}

async function main() {
  const args = process.argv.slice(2);
  const command = args[0];

  if (command === 'list') {
    await listRecipes();
    return;
  }

  if (command === 'generate' || command === 'gen') {
    const recipeId = args[1];
    const tool = args[2] || 'claude-code';
    const outputPath = args[3];

    if (!recipeId) {
      console.log(chalk.red('Error: Recipe ID required'));
      console.log(chalk.gray('Usage: npm run recipe:generate <recipe-id> [tool] [output-path]'));
      console.log(chalk.gray('Tools: claude-code (default), copilot-cli, cursor'));
      process.exit(1);
    }

    await generateScript(recipeId, tool, outputPath);
    return;
  }

  if (command === 'run') {
    const recipeId = args[1];
    const tool = (args[2] || 'claude-code') as 'claude-code' | 'copilot-cli' | 'cursor';

    if (!recipeId) {
      console.log(chalk.red('Error: Recipe ID required'));
      console.log(chalk.gray('Usage: npm run recipe:run <recipe-id> [tool]'));
      console.log(chalk.gray('Tools: claude-code (default), copilot-cli, cursor'));
      process.exit(1);
    }

    const recipePath = join(rootDir, 'recipes', `${recipeId}.yml`);
    const recipe = await loadRecipe(recipePath);
    const runner = new RecipeRunner(recipe, tool);
    await runner.run();
    return;
  }

  console.log(chalk.yellow('Unknown command'));
  console.log(chalk.gray('\nUsage:'));
  console.log(chalk.gray('  npm run recipe:list'));
  console.log(chalk.gray('  npm run recipe:generate <recipe-id> [tool]'));
  console.log(chalk.gray('  npm run recipe:run <recipe-id> [tool]'));
  process.exit(1);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(chalk.red('Error:'), error);
    process.exit(1);
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(chalk.red('Error:'), error);
    process.exit(1);
  });
}

export { RecipeRunner, loadRecipe, listRecipes, generateScript };
