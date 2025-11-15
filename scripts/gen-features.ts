#!/usr/bin/env node
import { readFile, writeFile, mkdir, readdir, access } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface Feature {
  id: string;
  version: string;
  name: string;
  description: string;
  context?: {
    overview?: string;
    architecture?: string;
    dependencies?: string[];
  };
  files?: {
    entry_points?: string[];
    key_files?: string[];
    patterns?: string[];
  };
  snippets?: Array<{
    id: string;
    title: string;
    description?: string;
    content: string;
    language?: string;
  }>;
  conventions?: string[];
  metadata?: {
    status?: 'draft' | 'active' | 'deprecated' | 'archived';
    owner?: string;
    created?: string;
    updated?: string;
    tags?: string[];
  };
}

export class FeatureGenerator {
  async generateFeatures(projectId: string): Promise<void> {
    console.log(chalk.blue(`\n🎯 Generating features for project: ${projectId}\n`));

    // Find project directory
    const projectDir = await this.findProjectDir(projectId);
    if (!projectDir) {
      throw new Error(`Project not found: ${projectId}`);
    }

    // Find features directory
    const featuresDir = join(projectDir, 'features');
    try {
      await access(featuresDir);
    } catch {
      console.log(chalk.yellow('  No features directory found'));
      return;
    }

    // Load all features
    const features = await this.loadFeatures(featuresDir);

    if (features.length === 0) {
      console.log(chalk.yellow('  No features found'));
      return;
    }

    console.log(chalk.gray(`  Found ${features.length} feature(s)\n`));

    // Generate output for each tool
    const outputDir = join(rootDir, '.output', projectId, 'features');
    await mkdir(outputDir, { recursive: true });

    await this.generateGitHubCopilotFeatures(features, outputDir);
    await this.generateWindsurfFeatures(features, outputDir);
    await this.generateClaudeCodeFeatures(features, outputDir);
    await this.generateCursorFeatures(features, outputDir);

    console.log(chalk.green(`\n✅ Generated feature snippets in: ${outputDir}\n`));
  }

  private async findProjectDir(projectId: string): Promise<string | null> {
    // Check global
    let projectDir = join(rootDir, 'projects', 'global', projectId);
    try {
      await access(projectDir);
      return projectDir;
    } catch {
      // Try local
      projectDir = join(rootDir, 'projects', 'local', projectId);
      try {
        await access(projectDir);
        return projectDir;
      } catch {
        return null;
      }
    }
  }

  private async loadFeatures(featuresDir: string): Promise<Feature[]> {
    const features: Feature[] = [];
    const entries = await readdir(featuresDir, { withFileTypes: true });

    for (const entry of entries) {
      if (entry.isDirectory()) {
        const featurePath = join(featuresDir, entry.name, 'feature.yml');
        try {
          await access(featurePath);
          const content = await readFile(featurePath, 'utf-8');
          const feature = loadYaml(content) as Feature;
          features.push(feature);
          console.log(chalk.gray(`    Loaded: ${feature.name}`));
        } catch {
          // No feature.yml in this directory
        }
      }
    }

    return features;
  }

  private async generateGitHubCopilotFeatures(
    features: Feature[],
    outputDir: string
  ): Promise<void> {
    const copilotDir = join(outputDir, 'github-copilot');
    await mkdir(copilotDir, { recursive: true });

    for (const feature of features) {
      const content: string[] = [];

      content.push(`# Feature: ${feature.name}`);
      content.push('');
      content.push(feature.description);
      content.push('');

      if (feature.context?.overview) {
        content.push('## Overview');
        content.push('');
        content.push(feature.context.overview);
        content.push('');
      }

      if (feature.context?.architecture) {
        content.push('## Architecture');
        content.push('');
        content.push(feature.context.architecture);
        content.push('');
      }

      if (feature.files) {
        content.push('## Related Files');
        content.push('');
        if (feature.files.entry_points?.length) {
          content.push('**Entry Points:**');
          for (const file of feature.files.entry_points) {
            content.push(`- \`${file}\``);
          }
          content.push('');
        }
        if (feature.files.key_files?.length) {
          content.push('**Key Files:**');
          for (const file of feature.files.key_files) {
            content.push(`- \`${file}\``);
          }
          content.push('');
        }
      }

      if (feature.conventions?.length) {
        content.push('## Conventions');
        content.push('');
        for (const convention of feature.conventions) {
          content.push(`- ${convention}`);
        }
        content.push('');
      }

      if (feature.snippets?.length) {
        content.push('## Code Snippets');
        content.push('');
        for (const snippet of feature.snippets) {
          content.push(`### ${snippet.title}`);
          if (snippet.description) {
            content.push('');
            content.push(snippet.description);
          }
          content.push('');
          content.push('```' + (snippet.language || ''));
          content.push(snippet.content);
          content.push('```');
          content.push('');
        }
      }

      await writeFile(join(copilotDir, `feature-${feature.id}.md`), content.join('\n'), 'utf-8');
    }

    console.log(chalk.gray(`    Generated ${features.length} GitHub Copilot feature files`));
  }

  private async generateWindsurfFeatures(features: Feature[], outputDir: string): Promise<void> {
    const windsurfDir = join(outputDir, 'windsurf');
    await mkdir(windsurfDir, { recursive: true });

    for (const feature of features) {
      const config = {
        name: feature.name,
        description: feature.description,
        context: feature.context || {},
        files: feature.files || {},
        conventions: feature.conventions || [],
        snippets: feature.snippets || [],
      };

      await writeFile(
        join(windsurfDir, `feature-${feature.id}.json`),
        JSON.stringify(config, null, 2),
        'utf-8'
      );
    }

    console.log(chalk.gray(`    Generated ${features.length} Windsurf feature files`));
  }

  private async generateClaudeCodeFeatures(features: Feature[], outputDir: string): Promise<void> {
    const claudeDir = join(outputDir, 'claude-code');
    await mkdir(claudeDir, { recursive: true });

    for (const feature of features) {
      const content: string[] = [];

      content.push(`# ${feature.name}`);
      content.push('');
      content.push(feature.description);
      content.push('');

      if (feature.context) {
        if (feature.context.overview) {
          content.push(feature.context.overview);
          content.push('');
        }
        if (feature.context.architecture) {
          content.push('## Architecture');
          content.push('');
          content.push(feature.context.architecture);
          content.push('');
        }
      }

      if (feature.snippets?.length) {
        content.push('## Examples');
        content.push('');
        for (const snippet of feature.snippets) {
          content.push(`**${snippet.title}**`);
          if (snippet.description) {
            content.push(snippet.description);
          }
          content.push('');
          content.push('```' + (snippet.language || ''));
          content.push(snippet.content);
          content.push('```');
          content.push('');
        }
      }

      await writeFile(join(claudeDir, `feature-${feature.id}.md`), content.join('\n'), 'utf-8');
    }

    console.log(chalk.gray(`    Generated ${features.length} Claude Code feature files`));
  }

  private async generateCursorFeatures(features: Feature[], outputDir: string): Promise<void> {
    const cursorDir = join(outputDir, 'cursor');
    await mkdir(cursorDir, { recursive: true });

    const allFeatures = features.map((feature) => ({
      id: feature.id,
      name: feature.name,
      description: feature.description,
      context: feature.context || {},
      files: feature.files || {},
      conventions: feature.conventions || [],
      snippets: feature.snippets || [],
    }));

    await writeFile(
      join(cursorDir, 'features.json'),
      JSON.stringify({ features: allFeatures }, null, 2),
      'utf-8'
    );

    console.log(chalk.gray(`    Generated Cursor features.json`));
  }
}

// CLI
if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2);
  const projectId = args[0];

  if (!projectId) {
    console.error(chalk.red('Error: Project ID required'));
    console.log('\nUsage: npm run project:generate-features <project-id>');
    process.exit(1);
  }

  const generator = new FeatureGenerator();
  generator.generateFeatures(projectId).catch((error) => {
    console.error(chalk.red('\n❌ Error:'), error.message);
    process.exit(1);
  });
}
