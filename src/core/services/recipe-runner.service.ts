import { readFile, readdir, writeFile, mkdir } from 'fs/promises';
import { join } from 'path';
import { spawn } from 'child_process';
import chalk from 'chalk';
import * as readline from 'readline';
import { Recipe, RecipeStep, Agent } from '../models/types.js';
import { ConfigService } from './config.service.js';
import { LoaderService } from './loader.service.js';

export class RecipeRunnerService {
  private recipe: Recipe;
  private agents: Map<string, Agent> = new Map();
  private tool: 'claude-code' | 'copilot-cli' | 'cursor';
  private conversationId?: string;
  private outputFile: string;
  private rl: readline.Interface;
  private documents: Map<string, string> = new Map();
  private recipeDocsDir: string;
  private config = ConfigService.getInstance();
  private loader = new LoaderService();

  constructor(recipe: Recipe, tool: 'claude-code' | 'copilot-cli' | 'cursor') {
    this.recipe = recipe;
    this.tool = tool;
    this.outputFile = `/tmp/recipe-output-${Date.now()}.txt`;
    this.recipeDocsDir = join(process.cwd(), '.recipe-docs');
    this.rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
    });
  }

  async run(): Promise<void> {
    console.log(chalk.blue.bold(`\n🚀 Running recipe: ${this.recipe.id}\n`));
    console.log(chalk.gray(this.recipe.description));
    console.log(chalk.gray(`Tool: ${this.tool}\n`));

    // Initialize recipe docs directory
    await mkdir(this.recipeDocsDir, { recursive: true });
    console.log(chalk.gray(`📁 Recipe documents directory: ${this.recipeDocsDir}\n`));

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
    const agentsDir = this.config.getPath(this.config.dirs.agents);
    try {
      const files = await this.loader.findYamlFiles(agentsDir);
      for (const file of files) {
        const agent = await this.loader.loadYaml<Agent>(file);
        this.agents.set(agent.id, agent);
      }
    } catch (error) {
      // Ignore
    }
  }

  private async loadDocuments(): Promise<void> {
    try {
      const files = await readdir(this.recipeDocsDir);
      for (const file of files) {
        if (!file.endsWith('.md')) continue;

        const docPath = `.recipe-docs/${file}`;
        const fullPath = join(this.recipeDocsDir, file);
        const content = await readFile(fullPath, 'utf-8');
        this.documents.set(docPath, content);
      }
    } catch (error) {
      // Directory doesn't exist yet, that's fine
    }
  }

  private async shouldExecuteStep(step: RecipeStep): Promise<boolean> {
    const condition = step.condition;
    if (!condition || condition.type === 'always') {
      return true;
    }

    if (condition.type === 'user-decision') {
      const response = await this.askUser(
        condition.check?.prompt || 'Execute this step? (y/n): '
      );
      return response.toLowerCase() === 'y';
    }

    if (condition.type === 'on-success' && condition.check) {
      return await this.checkCondition(condition.check);
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
      // Check if output file exists before reading
      try {
          await readFile(this.outputFile, 'utf-8');
      } catch {
          return false; // Output file doesn't exist yet
      }
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
    if (!condition) return false;

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

    // Load documents from disk before this step
    await this.loadDocuments();

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
      task += '\n\n---\n\n## Reference Documents (Context)\n\n';
      for (const docPath of step.includeDocuments) {
        const docContent = this.documents.get(docPath);
        if (docContent) {
          task += `### Document: \`${docPath}\`\n\n${docContent}\n\n---\n\n`;
          console.log(chalk.green(`   ✓ Included: ${docPath}`));
        } else {
          console.log(chalk.yellow(`   ⚠️  Document not found: ${docPath}`));
        }
      }
      task += '\n---\n\n**Please use the documents above as context for your work.**\n\n';
    }

    // Add output document instruction if specified
    if (step.outputDocument) {
      task += `\n\n---\n\n**IMPORTANT**: Save your complete response to the file: \`${step.outputDocument}\`\n`;
      console.log(chalk.gray(`   📄 Output will be saved to: ${step.outputDocument}`));
    }

    console.log(chalk.gray(`\n${task}\n`));

    // Execute based on tool
    await this.executeWithTool(step.agent, task, step.continueConversation !== false);

    // If this step has an output document, remind user to save it
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
    console.log(chalk.gray(`   Full path: ${join(process.cwd(), docPath)}`));
    console.log(
      chalk.gray('   Ensure the document is created with the full output from the AI assistant.')
    );

    await this.askUser('Press Enter once the document has been saved...');

    // Try to read the document to cache it for future steps
    try {
      const fullPath = join(process.cwd(), docPath);
      const content = await readFile(fullPath, 'utf-8');
      this.documents.set(docPath, content);
      console.log(chalk.green(`   ✓ Document loaded and cached: ${docPath}`));
      console.log(chalk.gray(`   Size: ${content.length} bytes\n`));
    } catch (error) {
      console.log(
        chalk.yellow(
          `   ⚠️  Could not read document: ${docPath}. It will not be available for subsequent steps.`
        )
      );
      console.log(
        chalk.gray(`   Error: ${error instanceof Error ? error.message : String(error)}\n`)
      );
    }
  }
}
