import { Command } from 'commander';
import { ConfigService } from '../../core/services/config.service.js';
import { LoaderService } from '../../core/services/loader.service.js';
import { ExternalProjectService } from '../../core/services/external-project.service.js';
import { ToolRegistry } from '../../tools/registry.js';
import { Project } from '../../core/models/types.js';
import { join, resolve, dirname } from 'path';
import { readFile, access, mkdir, readdir, copyFile } from 'fs/promises';
import { load as loadYaml } from 'js-yaml';
import chalk from 'chalk';
import { execSync } from 'child_process';
import { FeatureService } from '../../core/services/feature.service.js';

interface DeploymentConfig {
  target: string;
  tools: string[];
  mode: 'local' | 'manual';
  auto_commit?: boolean;
  git_branch?: string;
  backup?: boolean;
}

export const deployCommand = new Command('deploy')
    .description('Deploy project configurations');

deployCommand
    .command('project <projectId>')
    .description('Deploy a specific project')
    .option('-n, --dry-run', 'Dry run (do not modify files)')
    .option('-f, --force', 'Force deployment without confirmation')
    .option('-i, --interactive', 'Interactive mode')
    .action(async (projectId, options) => {
      await deployProject(projectId, options);
    });

  deployCommand
    .command('all')
    .description('Deploy all projects')
    .option('-n, --dry-run', 'Dry run')
    .option('-f, --force', 'Force deployment')
    .option('-i, --interactive', 'Interactive mode')
    .action(async (options) => {
      await deployAllProjects(options);
    });

  deployCommand
    .command('rollback <projectId> [timestamp]')
    .description('Rollback a project deployment')
    .action(async (projectId, timestamp) => {
      await rollbackProject(projectId, timestamp);
    });

async function deployProject(projectId: string, options: any) {
  const config = ConfigService.getInstance();
  const loader = new LoaderService();
  const externalService = new ExternalProjectService();
  const registry = ToolRegistry.getInstance();
  const featureService = new FeatureService();

  console.log(chalk.blue(`\n🚀 ${options.dryRun ? '[DRY RUN] ' : ''}Deploying project: ${projectId}\n`));

  // 1. Find project directory
  let projectDir: string | null = null;
  
  // Check configured sources
  const projectSources = await config.getProjectSources();
  for (const source of projectSources) {
    const candidate = join(source, projectId);
    try {
      await access(candidate);
      projectDir = candidate;
      break;
    } catch {}
  }

  if (!projectDir) {
    // Check external
    const externalProjects = await externalService.getAllProjects();
    const found = externalProjects.find(p => p.alias === projectId);
    if (found) {
      projectDir = found.path;
    }
  }

  if (!projectDir) {
    console.error(chalk.red(`Project not found: ${projectId}`));
    process.exit(1);
  }

  // 2. Load deployment config
  const deployConfigPath = join(projectDir, 'deploy.yml');
  let deployConfig: DeploymentConfig;
  try {
    const content = await readFile(deployConfigPath, 'utf-8');
    deployConfig = loadYaml(content) as DeploymentConfig;
  } catch {
    console.error(chalk.red(`No deploy.yml found in ${projectDir}`));
    process.exit(1);
  }

  // Merge local overrides
  try {
    const localDeployPath = join(projectDir, 'deploy.local.yml');
    const localContent = await readFile(localDeployPath, 'utf-8');
    const localConfig = loadYaml(localContent) as Partial<DeploymentConfig>;
    deployConfig = { ...deployConfig, ...localConfig };
  } catch {
    // Ignore
  }

  console.log(chalk.bold(`\n📦 Deploying: ${projectId}`));
  console.log(chalk.gray(`  Target: ${deployConfig.target}`));
  console.log(chalk.gray(`  Tools: ${deployConfig.tools.join(', ')}`));

  // Confirmation
  if (!options.force && !options.dryRun && options.interactive) {
    // ... (interactive confirmation logic)
  }

  // 3. Generate outputs
  console.log(chalk.blue('\n  Step 1: Generating project outputs...'));
  if (!options.dryRun) {
    // Load project manifest
    const projectPath = join(projectDir, 'project.yml');
    const project = await loader.loadYaml<Project>(projectPath);
    const outputDir = config.getPath(config.dirs.output, projectId);

    // Generate for each tool
    for (const toolName of deployConfig.tools) {
      const adapter = registry.getAdapter(toolName);
      if (adapter) {
        console.log(chalk.gray(`    Generating ${toolName}...`));
        await adapter.generate(project, outputDir);
      } else {
        console.warn(chalk.yellow(`    Unknown tool: ${toolName}`));
      }
    }

    // Generate features
    console.log(chalk.gray(`    Generating features...`));
    await featureService.generateFeatures(projectId);

    // Merge feature workflows into Windsurf output if Windsurf is enabled
    if (deployConfig.tools.includes('windsurf')) {
      const featuresOutputDir = join(outputDir, 'features', '.windsurf', 'workflows');
      const windsurfOutputDir = join(outputDir, '.windsurf', 'workflows');
      
      try {
        await access(featuresOutputDir);
        await mkdir(windsurfOutputDir, { recursive: true });
        
        const entries = await readdir(featuresOutputDir);
        for (const entry of entries) {
          const srcPath = join(featuresOutputDir, entry);
          const destPath = join(windsurfOutputDir, entry);
          await copyFile(srcPath, destPath);
        }
        console.log(chalk.gray(`    Merged ${entries.length} feature workflow(s) into Windsurf output`));
      } catch {
        // No features or workflows, skip
      }
    }
  }

  // 4. Verify target
  const targetPath = resolve(deployConfig.target);
  console.log(chalk.blue('\n  Step 2: Verifying target directory...'));
  try {
    await access(targetPath);
    console.log(chalk.green('    ✓ Target directory exists'));
  } catch {
    console.error(chalk.red(`    Target directory does not exist: ${targetPath}`));
    process.exit(1);
  }

  // 5. Backup
  if (deployConfig.backup !== false) {
    console.log(chalk.blue('\n  Step 3: Backing up existing files...'));
    if (!options.dryRun) {
      await backupExisting(projectId, deployConfig, targetPath);
    }
  }

  // 6. Copy to target
  console.log(chalk.blue('\n  Step 4: Copying files to target...'));
  if (!options.dryRun) {
    await copyToTarget(projectId, deployConfig, targetPath);
  }

  // 7. Git commit
  if (deployConfig.auto_commit && deployConfig.mode === 'local') {
    console.log(chalk.blue('\n  Step 5: Committing changes...'));
    if (!options.dryRun) {
      await gitCommit(projectId, deployConfig, targetPath);
    }
  }

  console.log(chalk.green(`\n✅ Deployed ${projectId}`));
}

async function deployAllProjects(options: any) {
  const config = ConfigService.getInstance();
  const externalService = new ExternalProjectService();
  
  const projectIds: string[] = [];

  // Find all projects with deploy.yml
  // Configured sources
  const projectSources = await config.getProjectSources();
  for (const source of projectSources) {
    try {
      const entries = await readdir(source, { withFileTypes: true });
      for (const entry of entries) {
        if (entry.isDirectory()) {
          try {
            await access(join(source, entry.name, 'deploy.yml'));
            projectIds.push(entry.name);
          } catch {}
        }
      }
    } catch {}
  }

  // External
  const externalProjects = await externalService.getAllProjects();
  for (const p of externalProjects) {
    try {
      await access(join(p.path, 'deploy.yml'));
      projectIds.push(p.alias);
    } catch {}
  }

  console.log(chalk.blue(`\n🚀 ${options.dryRun ? '[DRY RUN] ' : ''}Deploying all projects\n`));
  console.log(chalk.gray(`  Found ${projectIds.length} project(s) to deploy`));

  for (const projectId of projectIds) {
    try {
      await deployProject(projectId, options);
    } catch (e: any) {
      console.error(chalk.red(`Failed to deploy ${projectId}: ${e.message}`));
    }
  }
}

async function rollbackProject(projectId: string, timestamp?: string) {
  console.log(chalk.yellow('Rollback functionality not yet implemented in new CLI'));
}

export async function backupExisting(projectId: string, config: DeploymentConfig, targetPath: string) {
  const configService = ConfigService.getInstance();
  // Use configured backups directory (defaults to .backups)
  const backupsRoot = configService.getPath(configService.dirs.backups, projectId);
  const backupDir = join(backupsRoot, new Date().toISOString().replace(/[:.]/g, '-'));
  
  await mkdir(backupDir, { recursive: true });
  
  // Logic to backup only what we are about to overwrite
  for (const tool of config.tools) {
    let dirName = tool;
    if (tool === 'claude-code') dirName = '.claude';
    else if (tool === 'cursor') dirName = '.cursor';
    else if (tool === 'windsurf') dirName = '.windsurf';
    else if (tool === 'github-copilot') dirName = '.github';
    else if (tool === 'copilot-cli') {
        // Backup specific files for copilot-cli
        const files = ['AGENTS.md', '.cs.recipes'];
        for (const file of files) {
            const src = join(targetPath, file);
            const dest = join(backupDir, file);
            try {
                await access(src);
                await copyFileOrDir(src, dest);
                console.log(chalk.gray(`    Backed up ${file}`));
            } catch {}
        }
        continue;
    }
    
    const targetToolPath = join(targetPath, dirName);
    try {
      await access(targetToolPath);
      await copyDirectory(targetToolPath, join(backupDir, dirName));
      console.log(chalk.gray(`    Backed up ${dirName}`));
    } catch {}
  }

  // Clean up old backups (keep latest 10)
  try {
    const entries = await readdir(backupsRoot, { withFileTypes: true });
    const backups = entries
        .filter(e => e.isDirectory())
        .map(e => e.name)
        .sort() // ISO strings sort chronologically
        .reverse(); // Newest first

    if (backups.length > 10) {
        const toDelete = backups.slice(10);
        const { rm } = await import('fs/promises');
        for (const dir of toDelete) {
            const pathToDelete = join(backupsRoot, dir);
            await rm(pathToDelete, { recursive: true, force: true });
            console.log(chalk.gray(`    Removed old backup: ${dir}`));
        }
    }
  } catch (e) {
    console.warn(chalk.yellow(`    Failed to clean up old backups: ${e}`));
  }
}

async function copyToTarget(projectId: string, config: DeploymentConfig, targetPath: string) {
  const configService = ConfigService.getInstance();
  const outputDir = configService.getPath(configService.dirs.output, projectId);

  for (const tool of config.tools) {
    const toolOutputDir = join(outputDir, tool); // This is where generate() puts it
    
    // But wait, generate() puts things in .windsurf, .cursor etc INSIDE outputDir/tool?
    // Let's check adapter implementations.
    // WindsurfAdapter: join(outputDir, '.windsurf')
    // CursorAdapter: join(outputDir, '.cursor')
    // ClaudeAdapter: join(outputDir, '.claude')
    // GitHubCopilotAdapter: join(outputDir, '.github')
    // CopilotCLIAdapter: join(outputDir, 'AGENTS.md') and .cs.recipes
    
    // The adapters generate directly into outputDir! 
    // So if we call generate(project, outputDir), it populates outputDir with .windsurf, .cursor, etc.
    // We don't need to look into outputDir/tool. We look into outputDir.
    
    // Wait, the original deploy script iterated over tools and looked for `join(outputDir, tool)`.
    // But the new adapters seem to write to `outputDir/.toolname`.
    // Let's verify where adapters write.
    
    // WindsurfAdapter: `const windsurfDir = join(outputDir, '.windsurf');`
    // So if outputDir is `.output/ai-tools`, it writes to `.output/ai-tools/.windsurf`.
    
    // So we just need to copy the relevant directories from outputDir to targetPath.
    
    let dirName = tool;
    if (tool === 'claude-code') dirName = '.claude';
    else if (tool === 'cursor') dirName = '.cursor';
    else if (tool === 'windsurf') dirName = '.windsurf';
    else if (tool === 'github-copilot') dirName = '.github';
    else if (tool === 'copilot-cli') {
        // Copilot CLI is special, it generates files in root of output
        // AGENTS.md, .cs.recipes
        // We need to copy these specifically.
        const files = ['AGENTS.md', '.cs.recipes'];
        for (const file of files) {
            const src = join(outputDir, file);
            const dest = join(targetPath, file);
            try {
                await access(src);
                await copyFileOrDir(src, dest);
                console.log(chalk.green(`    ✓ Copied ${file}`));
            } catch {}
        }
        continue;
    }

    const srcPath = join(outputDir, dirName);
    const destPath = join(targetPath, dirName);
    
    try {
      await access(srcPath);
      await copyDirectory(srcPath, destPath);
      console.log(chalk.green(`    ✓ Copied ${dirName}`));
    } catch {
        console.warn(chalk.yellow(`    Warning: Output for ${tool} not found at ${srcPath}`));
    }
  }
}

async function copyFileOrDir(src: string, dest: string) {
    const stats = await import('fs/promises').then(fs => fs.stat(src));
    if (stats.isDirectory()) {
        await copyDirectory(src, dest);
    } else {
        await mkdir(dirname(dest), { recursive: true });
        await copyFile(src, dest);
    }
}

async function copyDirectory(src: string, dest: string) {
  await mkdir(dest, { recursive: true });
  const entries = await readdir(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = join(src, entry.name);
    const destPath = join(dest, entry.name);
    if (entry.isDirectory()) {
      await copyDirectory(srcPath, destPath);
    } else {
      await copyFile(srcPath, destPath);
    }
  }
}

async function gitCommit(projectId: string, config: DeploymentConfig, targetPath: string) {
    try {
        execSync('git add .', { cwd: targetPath, stdio: 'ignore' });
        execSync(`git commit -m "chore: update AI tool configurations for ${projectId}"`, { cwd: targetPath, stdio: 'ignore' });
        console.log(chalk.green('    ✓ Changes committed'));
    } catch {
        console.log(chalk.yellow('    ! Git commit failed (no changes or not a git repo)'));
    }
}
