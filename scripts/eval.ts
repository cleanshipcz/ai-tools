#!/usr/bin/env node
import { readFile, readdir } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface EvalSuite {
  suite: string;
  version?: string;
  description?: string;
  targets: Array<{
    type: string;
    id: string;
    dataset: string;
    config?: any;
  }>;
  checks?: Array<{
    name: string;
    type: string;
    [key: string]: any;
  }>;
  budgets?: {
    max_tokens?: number;
    max_cost_usd?: number;
    max_duration_sec?: number;
  };
}

class EvalRunner {
  private suites: Map<string, EvalSuite> = new Map();

  async run(suiteFilter?: string): Promise<boolean> {
    console.log(chalk.blue('🧪 Running evaluations...\n'));

    await this.loadSuites();

    if (this.suites.size === 0) {
      console.log(chalk.yellow('No eval suites found'));
      return true;
    }

    const suitesToRun = suiteFilter
      ? Array.from(this.suites.values()).filter((s) => s.suite === suiteFilter)
      : Array.from(this.suites.values());

    if (suitesToRun.length === 0) {
      console.log(chalk.yellow(`No suite matching "${suiteFilter}" found`));
      return false;
    }

    let allPassed = true;

    for (const suite of suitesToRun) {
      const passed = await this.runSuite(suite);
      if (!passed) {
        allPassed = false;
      }
    }

    if (allPassed) {
      console.log(chalk.green('\n✅ All evaluations passed!'));
    } else {
      console.log(chalk.red('\n❌ Some evaluations failed'));
    }

    return allPassed;
  }

  private async loadSuites(): Promise<void> {
    const suitesDir = join(rootDir, 'evals', 'suites');

    try {
      const files = await this.findYamlFiles(suitesDir);

      for (const file of files) {
        const content = await readFile(file, 'utf-8');
        const suite = loadYaml(content) as EvalSuite;
        this.suites.set(suite.suite, suite);
      }

      console.log(chalk.gray(`  Loaded ${this.suites.size} eval suite(s)`));
    } catch (error) {
      // No suites directory
    }
  }

  private async findYamlFiles(dir: string): Promise<string[]> {
    const files: string[] = [];

    try {
      const entries = await readdir(dir, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = join(dir, entry.name);

        if (entry.isDirectory()) {
          files.push(...(await this.findYamlFiles(fullPath)));
        } else if (
          entry.isFile() &&
          (entry.name.endsWith('.yml') || entry.name.endsWith('.yaml'))
        ) {
          files.push(fullPath);
        }
      }
    } catch (error) {
      // Directory doesn't exist
    }

    return files;
  }

  private async runSuite(suite: EvalSuite): Promise<boolean> {
    console.log(chalk.blue(`\n  Running suite: ${suite.suite}`));
    if (suite.description) {
      console.log(chalk.gray(`  ${suite.description}`));
    }

    // Check if datasets exist
    for (const target of suite.targets) {
      const datasetPath = join(rootDir, 'evals', target.dataset);
      try {
        await readFile(datasetPath, 'utf-8');
        console.log(chalk.gray(`    ✓ Dataset found: ${target.dataset}`));
      } catch (error) {
        console.log(chalk.red(`    ✗ Dataset not found: ${target.dataset}`));
        return false;
      }
    }

    // In a real implementation, this would:
    // 1. Load the dataset
    // 2. Run each test case through the target (prompt/agent/skill)
    // 3. Execute checks on the outputs
    // 4. Track token usage and costs
    // 5. Generate a report

    console.log(chalk.yellow('    ⚠ Eval execution not yet implemented'));
    console.log(chalk.gray('    This would run the tests and checks here'));

    // Check budget constraints
    if (suite.budgets) {
      console.log(chalk.gray('    Budget constraints:'));
      if (suite.budgets.max_tokens) {
        console.log(chalk.gray(`      - Max tokens: ${suite.budgets.max_tokens}`));
      }
      if (suite.budgets.max_cost_usd) {
        console.log(chalk.gray(`      - Max cost: $${suite.budgets.max_cost_usd}`));
      }
      if (suite.budgets.max_duration_sec) {
        console.log(chalk.gray(`      - Max duration: ${suite.budgets.max_duration_sec}s`));
      }
    }

    return true;
  }
}

// Parse command line arguments
const args = process.argv.slice(2);
let suiteFilter: string | undefined;

for (let i = 0; i < args.length; i++) {
  if (args[i] === '--suite' && i + 1 < args.length) {
    suiteFilter = args[i + 1];
    i++;
  }
}

// Main execution
const runner = new EvalRunner();
runner.run(suiteFilter).then((success) => {
  process.exit(success ? 0 : 1);
});
