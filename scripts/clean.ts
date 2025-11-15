#!/usr/bin/env node
import { rm } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

class Cleaner {
  async clean(): Promise<void> {
    console.log(chalk.blue('🧹 Cleaning generated files...\n'));

    const pathsToClean = [
      join(rootDir, 'adapters'),
      join(rootDir, '.output'),
      join(rootDir, 'evals', 'reports'),
    ];

    let cleaned = 0;

    for (const path of pathsToClean) {
      try {
        await rm(path, { recursive: true, force: true });
        console.log(chalk.gray(`  Cleaned: ${path}`));
        cleaned++;
      } catch (error: any) {
        if (error.code !== 'ENOENT') {
          console.log(chalk.yellow(`  Failed to clean: ${path}`));
        }
      }
    }

    console.log(chalk.green(`\n✅ Cleaned ${cleaned} directories\n`));
  }
}

// CLI
if (import.meta.url === `file://${process.argv[1]}`) {
  const cleaner = new Cleaner();
  cleaner.clean().catch((error) => {
    console.error(chalk.red('Error:'), error.message);
    process.exit(1);
  });
}
