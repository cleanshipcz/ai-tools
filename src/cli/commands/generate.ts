import { Command } from 'commander';
import { ToolRegistry } from '../../tools/registry.js';
import { ConfigService } from '../../core/services/config.service.js';
import { LoaderService } from '../../core/services/loader.service.js';
import { Project } from '../../core/models/types.js';
import chalk from 'chalk';
import { join } from 'path';
import { readdir, stat } from 'fs/promises';

export const generateCommand = new Command('generate')
  .description('Generate project configuration')
  .argument('[project]', 'Project alias or path')
  .option('--all', 'Generate for all projects')
  .action(async (projectArg, options) => {
    const config = ConfigService.getInstance();
    const loader = new LoaderService();
    const registry = new ToolRegistry();

    if (options.all) {
      console.log(chalk.blue('Generating for all projects...'));
      // Logic to find all projects and generate
      const projectSources = await config.getProjectSources();
      for (const source of projectSources) {
        try {
          const entries = await readdir(source, { withFileTypes: true });
          for (const entry of entries) {
            if (entry.isDirectory() && 
                entry.name !== 'template' && 
                entry.name !== '.gitkeep' && 
                !entry.name.startsWith('.')) {
               
               // Check for project.yml
               try {
                 await stat(join(source, entry.name, 'project.yml'));
                 await generateForProject(entry.name, config, loader, registry);
               } catch {}
            }
          }
        } catch {}
      }
    } else if (projectArg) {
      await generateForProject(projectArg, config, loader, registry);
    } else {
      console.log(chalk.red('Please specify a project or use --all'));
    }
  });

async function generateForProject(projectId: string, config: ConfigService, loader: LoaderService, registry: ToolRegistry) {
  console.log(chalk.blue(`\nGenerating for project: ${projectId}`));

  // 1. Load project manifest
  // Search in global and local directories
  // Search in configured project sources
  const projectSources = await config.getProjectSources();
  let projectPath: string | null = null;

  for (const source of projectSources) {
    const candidate = join(source, projectId, 'project.yml');
    try {
      await stat(candidate);
      projectPath = candidate;
      break;
    } catch {}
  }

  if (!projectPath) {
    // Try flat structure in root of projects dir (legacy fallback)
    const flatPath = config.getPath(config.dirs.projects, projectId, 'project.yml');
    try {
      await stat(flatPath);
      projectPath = flatPath;
    } catch {}
  }

  if (!projectPath) {
    console.error(chalk.red(`Project not found: ${projectId}`));
    return;
  }

  let outputDir = config.getPath(config.dirs.output, projectId);

  const project = await loader.loadYaml<Project>(projectPath);

  // 2. Generate for each tool
  for (const adapter of registry.getAll()) {
    console.log(chalk.gray(`  Generating ${adapter.name}...`));
    const toolOutputDir = join(outputDir, adapter.name);
    const { mkdir } = await import('fs/promises');
    await mkdir(toolOutputDir, { recursive: true });
    await adapter.generate(project, toolOutputDir);
  }
  
  console.log(chalk.green(`✅ Generated ${projectId}`));
}
