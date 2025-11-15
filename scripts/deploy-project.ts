#!/usr/bin/env node
import { readFile, writeFile, mkdir, readdir, copyFile, access, rm } from 'fs/promises';
import { join, dirname, resolve } from 'path';
import { fileURLToPath } from 'url';
import { load as loadYaml } from 'js-yaml';
import chalk from 'chalk';
import { execSync } from 'child_process';
import { ProjectGenerator } from './gen-project.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

interface DeploymentConfig {
  target: string;
  tools: string[];
  mode: 'local' | 'manual';
  auto_commit?: boolean;
  git_branch?: string;
  backup?: boolean;
}

class ProjectDeployer {
  private dryRun: boolean = false;
  private force: boolean = false;
  private confirm: boolean = true;
  private backupDir: string = '.backups';

  async deploy(
    projectId: string,
    options: { dryRun?: boolean; force?: boolean; confirm?: boolean } = {}
  ): Promise<void> {
    this.dryRun = options.dryRun || false;
    this.force = options.force || false;
    this.confirm = options.confirm !== false;

    console.log(
      chalk.blue(`\n🚀 ${this.dryRun ? '[DRY RUN] ' : ''}Deploying project: ${projectId}\n`)
    );

    // Load project deployment config
    const config = await this.loadDeployConfig(projectId);

    await this.deployOne(projectId, config);

    console.log(chalk.green('\n✅ Deployment complete!\n'));
  }

  async deployAll(
    options: { dryRun?: boolean; force?: boolean; confirm?: boolean } = {}
  ): Promise<void> {
    this.dryRun = options.dryRun || false;
    this.force = options.force || false;
    this.confirm = options.confirm !== false;

    console.log(chalk.blue(`\n🚀 ${this.dryRun ? '[DRY RUN] ' : ''}Deploying all projects\n`));

    // Get all project IDs
    const projectIds: string[] = [];

    // Check global projects
    const globalDir = join(rootDir, 'projects', 'global');
    try {
      const globalEntries = await readdir(globalDir, { withFileTypes: true });
      for (const entry of globalEntries) {
        if (entry.isDirectory() && entry.name !== 'template') {
          const deployPath = join(globalDir, entry.name, 'deploy.yml');
          try {
            await access(deployPath);
            projectIds.push(entry.name);
          } catch {
            // No deploy.yml, skip
          }
        }
      }
    } catch {
      // No global directory
    }

    // Check local projects
    const localDir = join(rootDir, 'projects', 'local');
    try {
      const localEntries = await readdir(localDir, { withFileTypes: true });
      for (const entry of localEntries) {
        if (entry.isDirectory() && entry.name !== 'README.md' && !entry.name.startsWith('.')) {
          const deployPath = join(localDir, entry.name, 'deploy.yml');
          try {
            await access(deployPath);
            projectIds.push(entry.name);
          } catch {
            // No deploy.yml, skip
          }
        }
      }
    } catch {
      // No local directory
    }

    // Check external projects
    try {
      const { ExternalProjectManager } = await import('./external-projects.js');
      const manager = new ExternalProjectManager();
      const externalProjects = await manager.getAllProjects();
      for (const project of externalProjects) {
        const deployPath = join(project.path, 'deploy.yml');
        try {
          await access(deployPath);
          projectIds.push(project.alias);
        } catch {
          // No deploy.yml, skip
        }
      }
    } catch {
      // No external projects
    }

    if (projectIds.length === 0) {
      console.log(chalk.yellow('No projects with deploy.yml found'));
      return;
    }

    console.log(chalk.gray(`  Found ${projectIds.length} project(s) to deploy:\n`));
    for (const id of projectIds) {
      console.log(chalk.gray(`    - ${id}`));
    }
    console.log('');

    // Confirm if needed
    if (!this.force && !this.dryRun && this.confirm) {
      const readline = await import('readline');
      const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
      });

      const answer = await new Promise<string>((resolve) => {
        rl.question(chalk.yellow('  Continue with all deployments? (y/N): '), resolve);
      });

      rl.close();

      if (answer.toLowerCase() !== 'y' && answer.toLowerCase() !== 'yes') {
        console.log(chalk.yellow('  Cancelled'));
        return;
      }
    }

    // Deploy each project
    let succeeded = 0;
    let failed = 0;

    for (const projectId of projectIds) {
      try {
        const config = await this.loadDeployConfig(projectId);
        await this.deployOne(projectId, config);
        succeeded++;
      } catch (error: any) {
        console.error(chalk.red(`  ❌ Failed to deploy ${projectId}: ${error.message}`));
        failed++;
      }
      console.log(''); // Separator between projects
    }

    console.log(chalk.green(`\n✅ Deployed ${succeeded} project(s)`));
    if (failed > 0) {
      console.log(chalk.red(`   Failed: ${failed} project(s)`));
    }
    console.log('');
  }

  private async loadDeployConfig(projectId: string): Promise<DeploymentConfig> {
    // Find project directory (check global first, then local, then external)
    let projectDir: string | null = null;

    const globalProjectDir = join(rootDir, 'projects', 'global', projectId);
    const localProjectDir = join(rootDir, 'projects', 'local', projectId);

    try {
      await access(globalProjectDir);
      projectDir = globalProjectDir;
    } catch {
      try {
        await access(localProjectDir);
        projectDir = localProjectDir;
      } catch {
        // Check external projects
        try {
          const { ExternalProjectManager } = await import('./external-projects.js');
          const manager = new ExternalProjectManager();
          const projects = await manager.getAllProjects();
          const found = projects.find((p) => p.alias === projectId);
          if (found) {
            projectDir = found.path;
          }
        } catch {
          // No external projects
        }

        if (!projectDir) {
          throw new Error(`Project not found: ${projectId}`);
        }
      }
    }

    // Load deploy.yml
    const deployPath = join(projectDir, 'deploy.yml');
    let config: DeploymentConfig;

    try {
      const content = await readFile(deployPath, 'utf-8');
      config = loadYaml(content) as DeploymentConfig;
      console.log(chalk.gray('  Loaded deployment configuration'));
    } catch {
      throw new Error(
        `No deploy.yml found in project: ${projectId}\nCreate ${deployPath} with deployment configuration.`
      );
    }

    // Load deploy.local.yml if exists and merge
    const localDeployPath = join(projectDir, 'deploy.local.yml');

    try {
      await access(localDeployPath);
      const localContent = await readFile(localDeployPath, 'utf-8');
      const localConfig = loadYaml(localContent) as Partial<DeploymentConfig>;
      config = { ...config, ...localConfig };
      console.log(chalk.gray('  Merged local deployment overrides'));
    } catch {
      // No local config, that's fine
    }

    return config;
  }

  private async deployOne(projectId: string, deployment: DeploymentConfig): Promise<void> {
    console.log(chalk.bold(`\n📦 Deploying: ${projectId}`));
    console.log(chalk.gray(`  Target: ${deployment.target}`));
    console.log(chalk.gray(`  Tools: ${deployment.tools.join(', ')}`));
    console.log(chalk.gray(`  Mode: ${deployment.mode}`));

    // Confirm if needed
    if (!this.force && !this.dryRun && this.confirm) {
      const readline = await import('readline');
      const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
      });

      const answer = await new Promise<string>((resolve) => {
        rl.question(chalk.yellow('\n  Continue? (y/N): '), resolve);
      });

      rl.close();

      if (answer.toLowerCase() !== 'y' && answer.toLowerCase() !== 'yes') {
        console.log(chalk.yellow('  Skipped'));
        return;
      }
    }

    // Step 1: Generate project outputs
    console.log(chalk.blue('\n  Step 1: Generating project outputs...'));
    if (!this.dryRun) {
      try {
        const generator = new ProjectGenerator();
        await generator.generate(projectId, deployment.tools);
      } catch (error) {
        throw new Error(`Failed to generate project outputs: ${error}`);
      }
    } else {
      console.log(chalk.gray('    [DRY RUN] Would generate outputs'));
    }

    // Step 2: Verify target exists
    const targetPath = resolve(deployment.target);
    console.log(chalk.blue('\n  Step 2: Verifying target directory...'));

    try {
      await access(targetPath);
      console.log(chalk.green('    ✓ Target directory exists'));
    } catch {
      throw new Error(`Target directory does not exist: ${targetPath}`);
    }

    // Step 3: Backup existing files
    if (deployment.backup !== false) {
      console.log(chalk.blue('\n  Step 3: Backing up existing files...'));
      if (!this.dryRun) {
        await this.backupExisting(projectId, deployment, targetPath);
      } else {
        console.log(chalk.gray('    [DRY RUN] Would backup existing files'));
      }
    } else {
      console.log(chalk.gray('\n  Step 3: Backup disabled'));
    }

    // Step 4: Copy files to target
    console.log(chalk.blue('\n  Step 4: Copying files to target...'));
    if (!this.dryRun) {
      await this.copyToTarget(projectId, deployment, targetPath);
    } else {
      console.log(chalk.gray('    [DRY RUN] Would copy files'));
      await this.listFilesToCopy(projectId, deployment);
    }

    // Step 5: Git commit (if enabled)
    if (deployment.auto_commit && deployment.mode === 'local') {
      console.log(chalk.blue('\n  Step 5: Committing changes...'));
      if (!this.dryRun) {
        await this.gitCommit(projectId, deployment, targetPath);
      } else {
        console.log(chalk.gray('    [DRY RUN] Would commit changes'));
      }
    } else {
      console.log(chalk.gray('\n  Step 5: Auto-commit disabled'));
    }

    // Manual mode instructions
    if (deployment.mode === 'manual') {
      console.log(chalk.yellow('\n  📝 Manual deployment:'));
      console.log(
        chalk.yellow(`     Copy files from the output directory to your project manually.`)
      );
    }

    console.log(chalk.green(`\n  ✓ Deployed ${projectId}`));
  }

  private async backupExisting(
    projectId: string,
    deployment: DeploymentConfig,
    targetPath: string
  ): Promise<void> {
    const backupDir = join(rootDir, this.backupDir);
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const projectBackupDir = join(backupDir, projectId, timestamp);

    await mkdir(projectBackupDir, { recursive: true });

    let fileCount = 0;

    // Backup based on what will be deployed from the output
    const outputDir = await this.findOutputDir(projectId);

    for (const tool of deployment.tools) {
      const toolOutputPath = join(outputDir, tool);

      try {
        await access(toolOutputPath);

        // Read what directories would be copied and backup those from target
        const entries = await readdir(toolOutputPath, { withFileTypes: true });

        for (const entry of entries) {
          if (entry.isDirectory()) {
            const targetToolPath = join(targetPath, entry.name);

            try {
              await access(targetToolPath);
              // Directory exists in target, backup it
              await this.copyDirectory(targetToolPath, join(projectBackupDir, entry.name));
              fileCount++;
            } catch {
              // Directory doesn't exist in target, skip
            }
          }
        }
      } catch {
        // Tool output doesn't exist, skip
      }
    }

    if (fileCount > 0) {
      console.log(chalk.green(`    ✓ Backed up ${fileCount} tool(s) to: ${projectBackupDir}`));
    } else {
      console.log(chalk.gray('    No existing files to backup'));
    }
  }

  private async copyToTarget(
    projectId: string,
    deployment: DeploymentConfig,
    targetPath: string
  ): Promise<void> {
    // Find output directory
    const outputDir = await this.findOutputDir(projectId);

    let fileCount = 0;

    for (const tool of deployment.tools) {
      const toolOutputPath = join(outputDir, tool);

      try {
        await access(toolOutputPath);

        // Copy the contents of toolOutputPath to targetPath
        const entries = await readdir(toolOutputPath, { withFileTypes: true });

        for (const entry of entries) {
          const srcPath = join(toolOutputPath, entry.name);
          const destPath = join(targetPath, entry.name);

          if (entry.isDirectory()) {
            await this.copyDirectory(srcPath, destPath);
          } else {
            await mkdir(dirname(destPath), { recursive: true });
            await copyFile(srcPath, destPath);
          }
        }

        fileCount++;
        console.log(chalk.green(`    ✓ Copied ${tool}`));
      } catch {
        console.log(chalk.yellow(`    ! ${tool} output not found, skipping`));
      }
    }

    console.log(chalk.green(`    ✓ Copied ${fileCount} tool(s)`));
  }

  private async listFilesToCopy(projectId: string, deployment: DeploymentConfig): Promise<void> {
    const outputDir = await this.findOutputDir(projectId);

    for (const tool of deployment.tools) {
      const toolOutputPath = join(outputDir, tool);

      try {
        await access(toolOutputPath);
        const files = await this.listFilesRecursive(toolOutputPath);
        console.log(chalk.gray(`    ${tool}: ${files.length} file(s)`));
        for (const file of files) {
          console.log(chalk.gray(`      - ${file}`));
        }
      } catch {
        console.log(chalk.yellow(`    ${tool}: not found`));
      }
    }
  }

  private async findOutputDir(projectId: string): Promise<string> {
    const outputDir = join(rootDir, '.output', projectId);

    try {
      await access(outputDir);
      return outputDir;
    } catch {
      throw new Error(`No output found for project: ${projectId}. Run project:generate first.`);
    }
  }

  private async copyDirectory(src: string, dest: string): Promise<void> {
    await mkdir(dest, { recursive: true });

    const entries = await readdir(src, { withFileTypes: true });

    for (const entry of entries) {
      const srcPath = join(src, entry.name);
      const destPath = join(dest, entry.name);

      if (entry.isDirectory()) {
        await this.copyDirectory(srcPath, destPath);
      } else {
        await copyFile(srcPath, destPath);
      }
    }
  }

  private async listFilesRecursive(dir: string, basePath: string = ''): Promise<string[]> {
    const files: string[] = [];
    const entries = await readdir(dir, { withFileTypes: true });

    for (const entry of entries) {
      const relativePath = join(basePath, entry.name);

      if (entry.isDirectory()) {
        files.push(...(await this.listFilesRecursive(join(dir, entry.name), relativePath)));
      } else {
        files.push(relativePath);
      }
    }

    return files;
  }

  private async gitCommit(
    projectId: string,
    deployment: DeploymentConfig,
    targetPath: string
  ): Promise<void> {
    try {
      // Check if it's a git repository
      execSync('git rev-parse --git-dir', { cwd: targetPath, stdio: 'ignore' });

      // Create branch if specified
      if (deployment.git_branch) {
        try {
          execSync(`git checkout -b ${deployment.git_branch}`, {
            cwd: targetPath,
            stdio: 'ignore',
          });
        } catch {
          // Branch might already exist
          execSync(`git checkout ${deployment.git_branch}`, { cwd: targetPath, stdio: 'ignore' });
        }
      }

      // Stage changes - add common AI tool directories
      const dirsToAdd = ['.github', '.windsurf', '.cursor', '.claude'];
      for (const dir of dirsToAdd) {
        try {
          execSync(`git add ${dir}`, { cwd: targetPath, stdio: 'ignore' });
        } catch {
          // Directory might not exist, skip
        }
      }

      // Commit
      execSync(`git commit -m "chore: update AI tool configurations"`, {
        cwd: targetPath,
        stdio: 'ignore',
      });

      console.log(chalk.green('    ✓ Changes committed'));
    } catch (error) {
      console.log(chalk.yellow('    ! Git commit failed (is it a git repository?)'));
    }
  }

  async rollback(projectId: string, timestamp?: string): Promise<void> {
    console.log(chalk.blue(`\n⏮️  Rolling back: ${projectId}\n`));

    const backupDir = join(rootDir, this.backupDir, projectId);

    // List available backups
    try {
      const backups = await readdir(backupDir);

      if (backups.length === 0) {
        console.log(chalk.yellow('No backups found'));
        return;
      }

      const selectedBackup = timestamp || backups[backups.length - 1];
      const backupPath = join(backupDir, selectedBackup);

      console.log(chalk.gray(`  Using backup: ${selectedBackup}`));
      console.log(chalk.yellow('  (Rollback functionality not fully implemented yet)'));
      console.log(chalk.gray(`  Backup location: ${backupPath}`));
    } catch {
      console.log(chalk.yellow('No backups found'));
    }
  }
}

// CLI
const args = process.argv.slice(2);
const command = args[0];
const projectId = args[1];
const flags = {
  dryRun: args.includes('--dry-run') || args.includes('-n'),
  force: args.includes('--force') || args.includes('-f'),
  confirm: !args.includes('--no-confirm'),
};

const deployer = new ProjectDeployer();

if (command === 'deploy') {
  if (!projectId) {
    console.error(chalk.red('Error: Project ID required'));
    console.log('\nUsage: deploy <project-id> [--dry-run] [--force] [--no-confirm]');
    process.exit(1);
  }

  deployer.deploy(projectId, flags).catch((error) => {
    console.error(chalk.red('\n❌ Error:'), error.message);
    process.exit(1);
  });
} else if (command === 'deploy-all') {
  deployer.deployAll(flags).catch((error) => {
    console.error(chalk.red('\n❌ Error:'), error.message);
    process.exit(1);
  });
} else if (command === 'rollback') {
  if (!projectId) {
    console.error(chalk.red('Error: Project ID required for rollback'));
    process.exit(1);
  }

  const timestamp = args[2];
  deployer.rollback(projectId, timestamp).catch((error) => {
    console.error(chalk.red('\n❌ Error:'), error.message);
    process.exit(1);
  });
} else {
  console.error(chalk.red(`Unknown command: ${command || '(none)'}`));
  console.log('\nAvailable commands:');
  console.log('  deploy <project-id> [--dry-run] [--force] [--no-confirm]');
  console.log('  deploy-all [--dry-run] [--force] [--no-confirm]');
  console.log('  rollback <project-id> [timestamp]');
  process.exit(1);
}
