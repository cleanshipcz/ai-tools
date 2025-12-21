import { Command } from 'commander';
import { ToolRegistry } from '../../tools/registry.js';
import { ConfigService } from '../../core/services/config.service.js';
import chalk from 'chalk';

export const buildCommand = new Command('build')
  .description('Generate global adapters (formerly build.ts)')
  .action(async () => {
    console.log(chalk.blue('🔨 Starting build...'));
    
    const config = ConfigService.getInstance();
    const registry = new ToolRegistry();
    const adaptersDir = config.getPath(config.dirs.adapters);

    for (const adapter of registry.getAll()) {
      console.log(chalk.blue(`  Generating global config for ${adapter.name}...`));
      await adapter.generateGlobal(join(adaptersDir, adapter.name));
    }

    console.log(chalk.green('\n✅ Build complete!'));
  });

import { join } from 'path';
