import { readFile, stat } from 'fs/promises';
import { join } from 'path';
import { ConfigService } from './config.service.js';
import { LoaderService } from './loader.service.js';

export interface EvalSuite {
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

export interface EvalResult {
  success: boolean;
  suitesRun: number;
  suitesPassed: number;
  suitesFailed: number;
  details: string[];
}

export class EvalService {
  private config = ConfigService.getInstance();
  private loader = new LoaderService();
  private suites = new Map<string, EvalSuite>();

  async run(suiteFilter?: string): Promise<EvalResult> {
    const result: EvalResult = {
      success: true,
      suitesRun: 0,
      suitesPassed: 0,
      suitesFailed: 0,
      details: [],
    };

    await this.loadSuites();

    if (this.suites.size === 0) {
      result.details.push('No eval suites found');
      return result;
    }

    const suitesToRun = suiteFilter
      ? Array.from(this.suites.values()).filter((s) => s.suite === suiteFilter)
      : Array.from(this.suites.values());

    if (suitesToRun.length === 0) {
      result.details.push(`No suite matching "${suiteFilter}" found`);
      result.success = false;
      return result;
    }

    for (const suite of suitesToRun) {
      result.suitesRun++;
      const passed = await this.runSuite(suite, result);
      if (passed) {
        result.suitesPassed++;
      } else {
        result.suitesFailed++;
        result.success = false;
      }
    }

    return result;
  }

  private async loadSuites(): Promise<void> {
    const suitesDir = join(this.config.getPath(this.config.dirs.evals), 'suites');

    try {
      const files = await this.loader.findYamlFiles(suitesDir);

      for (const file of files) {
        const suite = await this.loader.loadYaml<EvalSuite>(file);
        this.suites.set(suite.suite, suite);
      }
    } catch (error) {
      // No suites directory or error loading
    }
  }

  private async runSuite(suite: EvalSuite, result: EvalResult): Promise<boolean> {
    result.details.push(`Running suite: ${suite.suite}`);
    if (suite.description) {
      result.details.push(`  ${suite.description}`);
    }

    // Check if datasets exist
    for (const target of suite.targets) {
      const datasetPath = join(this.config.getPath(this.config.dirs.evals), target.dataset);
      try {
        await readFile(datasetPath, 'utf-8');
        result.details.push(`    ✓ Dataset found: ${target.dataset}`);
      } catch (error) {
        result.details.push(`    ✗ Dataset not found: ${target.dataset}`);
        return false;
      }
    }

    // Placeholder for actual execution
    result.details.push('    ⚠ Eval execution not yet implemented');
    result.details.push('    This would run the tests and checks here');

    // Check budget constraints
    if (suite.budgets) {
      result.details.push('    Budget constraints:');
      if (suite.budgets.max_tokens) {
        result.details.push(`      - Max tokens: ${suite.budgets.max_tokens}`);
      }
      if (suite.budgets.max_cost_usd) {
        result.details.push(`      - Max cost: $${suite.budgets.max_cost_usd}`);
      }
      if (suite.budgets.max_duration_sec) {
        result.details.push(`      - Max duration: ${suite.budgets.max_duration_sec}s`);
      }
    }

    return true;
  }
}
