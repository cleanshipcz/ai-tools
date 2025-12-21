#!/usr/bin/env node
import { Command } from 'commander';
import { buildCommand } from './commands/build.js';
import { generateCommand } from './commands/generate.js';
import { deployCommand } from './commands/deploy.js';
import { createCommand } from './commands/create.js';
import { initCommand } from './commands/init.js';
import { listCommand } from './commands/list.js';
import { externalCommand } from './commands/external.js';
import { ToolRegistry } from '../tools/registry.js';

import { validateCommand } from './commands/validate.js';
import { evalCommand } from './commands/eval.js';
import { diffCommand } from './commands/diff.js';
import { cleanCommand } from './commands/clean.js';
import { featuresCommand } from './commands/features.js';
import { skillsCommand } from './commands/skills.js';
import { docsCommand } from './commands/docs.js';
import { promptsCommand } from './commands/prompts.js';
import { recipesCommand } from './commands/recipes.js';

const program = new Command();

program
  .name('ai-tools')
  .description('AI Tools CLI')
  .version('1.0.0')
  .addCommand(buildCommand)
  .addCommand(generateCommand)
  .addCommand(deployCommand)
  .addCommand(createCommand)
  .addCommand(initCommand)
  .addCommand(listCommand)
  .addCommand(externalCommand)
  .addCommand(validateCommand)
  .addCommand(evalCommand)
  .addCommand(diffCommand)
  .addCommand(cleanCommand)
  .addCommand(featuresCommand)
  .addCommand(skillsCommand)
  .addCommand(docsCommand)
  .addCommand(promptsCommand)
  .addCommand(recipesCommand);

program.parse(process.argv);
