#!/usr/bin/env node
import { readFile } from 'fs/promises';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import chalk from 'chalk';
import { diffLines, diffWords } from 'diff';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface DiffOptions {
  before: string;
  after: string;
  format?: 'lines' | 'words';
}

class DiffTool {
  async compare(options: DiffOptions): Promise<void> {
    console.log(chalk.blue('🔍 Comparing outputs...\n'));

    let beforeContent: string;
    let afterContent: string;

    try {
      beforeContent = await readFile(options.before, 'utf-8');
    } catch (error) {
      console.error(chalk.red(`Failed to read before file: ${options.before}`));
      process.exit(1);
    }

    try {
      afterContent = await readFile(options.after, 'utf-8');
    } catch (error) {
      console.error(chalk.red(`Failed to read after file: ${options.after}`));
      process.exit(1);
    }

    const format = options.format || 'lines';
    const changes =
      format === 'words'
        ? diffWords(beforeContent, afterContent)
        : diffLines(beforeContent, afterContent);

    let additions = 0;
    let deletions = 0;
    let unchanged = 0;

    console.log(chalk.bold('Diff:\n'));

    for (const part of changes) {
      const lines = part.value.split('\n').filter((l) => l.length > 0);

      if (part.added) {
        additions += lines.length;
        for (const line of lines) {
          console.log(chalk.green(`+ ${line}`));
        }
      } else if (part.removed) {
        deletions += lines.length;
        for (const line of lines) {
          console.log(chalk.red(`- ${line}`));
        }
      } else {
        unchanged += lines.length;
        // Show a few lines of context
        const contextLines = lines.slice(0, 2);
        for (const line of contextLines) {
          console.log(chalk.gray(`  ${line}`));
        }
        if (lines.length > 2) {
          console.log(chalk.gray(`  ... (${lines.length - 2} more unchanged lines)`));
        }
      }
    }

    console.log(chalk.bold('\nSummary:'));
    console.log(chalk.green(`  Additions: ${additions}`));
    console.log(chalk.red(`  Deletions: ${deletions}`));
    console.log(chalk.gray(`  Unchanged: ${unchanged}`));

    const totalChanges = additions + deletions;
    if (totalChanges === 0) {
      console.log(chalk.yellow('\n  No changes detected'));
    } else {
      const changePercent = ((totalChanges / (totalChanges + unchanged)) * 100).toFixed(1);
      console.log(chalk.blue(`\n  Change ratio: ${changePercent}%`));
    }
  }

  showUsage(): void {
    console.log('Usage: npm run diff -- --before <file> --after <file> [--format lines|words]');
    console.log('\nOptions:');
    console.log('  --before    Path to the before/baseline file');
    console.log('  --after     Path to the after/current file');
    console.log('  --format    Diff format: "lines" (default) or "words"');
    console.log('\nExample:');
    console.log('  npm run diff -- --before baseline.txt --after current.txt');
  }
}

// Parse command line arguments
const args = process.argv.slice(2);
const options: Partial<DiffOptions> = {};

for (let i = 0; i < args.length; i++) {
  switch (args[i]) {
    case '--before':
      if (i + 1 < args.length) {
        options.before = args[i + 1];
        i++;
      }
      break;
    case '--after':
      if (i + 1 < args.length) {
        options.after = args[i + 1];
        i++;
      }
      break;
    case '--format':
      if (i + 1 < args.length) {
        options.format = args[i + 1] as 'lines' | 'words';
        i++;
      }
      break;
    case '--help':
    case '-h':
      new DiffTool().showUsage();
      process.exit(0);
  }
}

// Main execution
const tool = new DiffTool();

if (!options.before || !options.after) {
  tool.showUsage();
  process.exit(1);
}

tool.compare(options as DiffOptions).catch((error) => {
  console.error(chalk.red('Diff failed:'), error);
  process.exit(1);
});
