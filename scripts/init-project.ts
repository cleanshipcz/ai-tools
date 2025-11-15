#!/usr/bin/env node
import { readFile, writeFile, mkdir, copyFile, access } from 'fs/promises';
import { join, dirname, basename, resolve } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml, dump as dumpYaml } from 'js-yaml';
import chalk from 'chalk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface InitOptions {
  path: string;
  alias?: string;
  description?: string;
  register?: 'global' | 'local' | 'none';
}

class ProjectInitializer {
  async init(options: InitOptions): Promise<void> {
    const { path: projectPath, alias, description, register } = options;

    console.log(chalk.blue(`\n🚀 Initializing AI tools in external project\n`));

    // Resolve and validate path
    const resolvedPath = resolve(projectPath);

    try {
      await access(resolvedPath);
    } catch {
      throw new Error(`Path does not exist: ${resolvedPath}`);
    }

    console.log(chalk.gray(`  Target: ${resolvedPath}`));

    // Determine project name/alias
    const projectName = alias || basename(resolvedPath);
    const projectId = this.toKebabCase(projectName);

    console.log(chalk.gray(`  Project ID: ${projectId}`));

    // Create .cleanship-ai-tools directory
    const aiToolsDir = join(resolvedPath, '.cleanship-ai-tools');

    try {
      await access(aiToolsDir);
      throw new Error(`.cleanship-ai-tools folder already exists at ${aiToolsDir}`);
    } catch (error: any) {
      if (error.code !== 'ENOENT') throw error;
    }

    await mkdir(aiToolsDir, { recursive: true });
    console.log(chalk.gray(`  Created: .cleanship-ai-tools/`));

    // Copy and customize project.yml from template
    const templateDir = join(rootDir, 'projects', 'global', 'template');
    const projectYmlPath = join(templateDir, 'project.yml');
    const projectYmlContent = await readFile(projectYmlPath, 'utf-8');
    const projectManifest = loadYaml(projectYmlContent) as any;

    // Customize the manifest
    projectManifest.id = projectId;
    projectManifest.name = this.toTitleCase(projectName);
    if (description) {
      projectManifest.description = description;
    } else {
      projectManifest.description = `${this.toTitleCase(projectName)} project configuration`;
    }
    projectManifest.metadata = projectManifest.metadata || {};
    projectManifest.metadata.created = new Date().toISOString().split('T')[0];
    projectManifest.metadata.updated = new Date().toISOString().split('T')[0];

    await writeFile(
      join(aiToolsDir, 'project.yml'),
      dumpYaml(projectManifest, { lineWidth: -1, noRefs: true }),
      'utf-8'
    );
    console.log(chalk.gray(`  Created: project.yml`));

    // Copy deploy.yml
    const deployYmlPath = join(templateDir, 'deploy.yml');
    let deployYmlContent = await readFile(deployYmlPath, 'utf-8');

    // Update target to point to parent directory (the project root)
    deployYmlContent = deployYmlContent.replace(
      /target: .*/,
      'target: ".."  # Deploy to project root (parent of .cleanship-ai-tools)'
    );

    await writeFile(join(aiToolsDir, 'deploy.yml'), deployYmlContent, 'utf-8');
    console.log(chalk.gray(`  Created: deploy.yml`));

    // Create features directory with example
    const featuresDir = join(aiToolsDir, 'features');
    await mkdir(featuresDir, { recursive: true });

    // Copy example feature
    const exampleFeatureDir = join(templateDir, 'features', 'example-feature');
    try {
      await access(exampleFeatureDir);
      const exampleFeatureTargetDir = join(featuresDir, 'example-feature');
      await mkdir(exampleFeatureTargetDir, { recursive: true });

      const featureYmlPath = join(exampleFeatureDir, 'feature.yml');
      await copyFile(featureYmlPath, join(exampleFeatureTargetDir, 'feature.yml'));
      console.log(chalk.gray(`  Created: features/example-feature/ (template)`));
    } catch {
      // No example feature in template, skip
    }

    // Create README for the .cleanship-ai-tools folder
    const readmeContent = `# AI Tools Configuration

This directory contains AI tool configurations for ${projectManifest.name}.

## Files

- \`project.yml\` - Project manifest (tech stack, conventions, context)
- \`deploy.yml\` - Deployment configuration
- \`features/\` - Feature-specific context and snippets (optional)

## Usage

### Generate configurations

From the ai-tools repository:

\`\`\`bash
npm run project:generate ${projectId}
\`\`\`

### Deploy to project

\`\`\`bash
npm run project:deploy ${projectId}
\`\`\`

### Edit project manifest

Edit \`project.yml\` to customize:
- Tech stack
- Commands
- Conventions
- Documentation links
- AI tool preferences

### Add features

Create feature-specific context:

\`\`\`bash
mkdir -p features/my-feature
# Create features/my-feature/feature.yml
\`\`\`

## Documentation

See [ai-tools Projects Guide](https://github.com/cleanshipcz/ai-tools/blob/main/projects/README.md) for full documentation.
`;

    await writeFile(join(aiToolsDir, 'README.md'), readmeContent, 'utf-8');
    console.log(chalk.gray(`  Created: README.md`));

    console.log(chalk.green(`\n✅ Initialized successfully!\n`));

    // Register in external projects if requested
    if (register && register !== 'none') {
      console.log(chalk.blue(`📝 Registering in ${register} external projects...\n`));

      try {
        const { ExternalProjectManager } = await import('./external-projects.js');
        const manager = new ExternalProjectManager();
        await manager.add(aiToolsDir, {
          global: register === 'global',
          alias: projectId,
        });
        console.log(chalk.green(`✅ Registered as: ${projectId}\n`));
      } catch (error: any) {
        console.log(chalk.yellow(`⚠️  Failed to auto-register: ${error.message}`));
        console.log(chalk.gray(`   You can register manually with:`));
        console.log(
          chalk.gray(`   npm run project:external add ${aiToolsDir} --alias ${projectId}\n`)
        );
      }
    }

    // Show next steps
    console.log(chalk.bold('Next steps:'));
    console.log(chalk.gray(`  1. Edit .cleanship-ai-tools/project.yml with your project details`));
    console.log(chalk.gray(`  2. Customize .cleanship-ai-tools/deploy.yml if needed`));

    if (!register || register === 'none') {
      console.log(chalk.gray(`  3. Register the project:`));
      console.log(
        chalk.gray(`     npm run project:external add ${aiToolsDir} --alias ${projectId}`)
      );
      console.log(chalk.gray(`  4. Generate and deploy:`));
    } else {
      console.log(chalk.gray(`  3. Generate and deploy:`));
    }

    console.log(chalk.gray(`     npm run project:deploy ${projectId}`));
    console.log('');
  }

  private toKebabCase(str: string): string {
    return str
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  private toTitleCase(str: string): string {
    return str
      .split(/[-_\s]+/)
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }
}

// CLI
if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2);

  // Parse arguments
  let path = '';
  let alias = '';
  let description = '';
  let register: 'global' | 'local' | 'none' = 'local';

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];

    if (arg === '--alias' || arg === '-a') {
      alias = args[++i] || '';
    } else if (arg === '--description' || arg === '-d') {
      description = args[++i] || '';
    } else if (arg === '--register-global') {
      register = 'global';
    } else if (arg === '--register-local') {
      register = 'local';
    } else if (arg === '--no-register') {
      register = 'none';
    } else if (!path) {
      path = arg;
    }
  }

  if (!path) {
    console.error(chalk.red('Error: Project path required'));
    console.log('\nUsage: npm run project:init <path> [options]');
    console.log('\nOptions:');
    console.log('  --alias, -a <name>      Project alias/nickname (default: directory name)');
    console.log('  --description, -d <desc> Project description');
    console.log('  --register-local        Register in local external projects (default)');
    console.log('  --register-global       Register in global external projects');
    console.log('  --no-register           Do not auto-register');
    console.log('\nExamples:');
    console.log('  npm run project:init /path/to/my-project');
    console.log('  npm run project:init ~/projects/my-app --alias my-app');
    console.log('  npm run project:init ../sibling-project -a sibling -d "Sibling project"');
    console.log('  npm run project:init /opt/company/api --register-global');
    process.exit(1);
  }

  const initializer = new ProjectInitializer();
  initializer.init({ path, alias, description, register }).catch((error) => {
    console.error(chalk.red('\n❌ Error:'), error.message);
    process.exit(1);
  });
}
