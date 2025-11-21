import { ToolAdapter } from '../base.js';
import { Project, Agent, Prompt } from '../../core/models/types.js';
import { ConfigService } from '../../core/services/config.service.js';
import { LoaderService } from '../../core/services/loader.service.js';
import { ResolverService } from '../../core/services/resolver.service.js';
import { RecipeService } from '../../core/services/recipe.service.js';
import { join, dirname } from 'path';
import { mkdir, writeFile, copyFile, access, readdir, readFile } from 'fs/promises';

export class ClaudeAdapter extends ToolAdapter {
  name = 'claude-code';
  private config = ConfigService.getInstance();
  private loader = new LoaderService();
  private resolver = new ResolverService();
  private recipeService = new RecipeService();

  async generate(project: Project, outputDir: string): Promise<void> {
    const claudeDir = join(outputDir, '.claude');
    await mkdir(claudeDir, { recursive: true });

    // 1. Generate base adapters (prompts, skills, agents) from source
    // This replaces the dependency on pre-built 'adapters' directory
    
    // Prompts
    const promptsDir = this.config.getPath(this.config.dirs.prompts);
    const promptsMap = new Map<string, string>();
    
    // Clean up destination prompts directory first
    const destPromptsDir = join(claudeDir, 'prompts');
    try {
        const { rm } = await import('fs/promises');
        await rm(destPromptsDir, { recursive: true, force: true });
    } catch {}
    await mkdir(destPromptsDir, { recursive: true });

    try {
      const promptFiles = await this.loader.findYamlFilesRelative(promptsDir);
      for (const relativePath of promptFiles) {
        const fullPath = join(promptsDir, relativePath);
        const prompt = await this.loader.loadYaml<Prompt>(fullPath);
        
        // Determine path-based ID/Name
        const pathWithoutExt = relativePath.replace(/\.ya?ml$/, '').replace(/\\/g, '/');
        promptsMap.set(prompt.id, pathWithoutExt);
        
        if (this.resolver.shouldIncludePrompt(prompt.id, promptsMap, project)) {
           // Generate Claude-specific JSON for prompt
           // Use path-derived ID for consistency with Windsurf
           const namespacedId = pathWithoutExt.split('/').join('-');
           
           const claudePrompt = {
             id: namespacedId, // Use the namespaced ID
             description: prompt.description,
             content: prompt.content,
             system: prompt.system,
             user: prompt.user,
             variables: prompt.variables
           };
           
           // const destDir = join(claudeDir, 'prompts');
           // await mkdir(destDir, { recursive: true });
           
           // Use namespaced ID for filename
           await writeFile(join(destPromptsDir, `${namespacedId}.json`), JSON.stringify(claudePrompt, null, 2));
        }
      }
    } catch {}

    // Skills
    const skillsDir = this.config.getPath(this.config.dirs.skills);
    try {
      const skillFiles = await this.loader.findYamlFiles(skillsDir);
      const skills: any[] = [];
      
      for (const file of skillFiles) {
        const skill = await this.loader.loadYaml<any>(file);
        skills.push(skill);
        
        // Copy skill implementation if needed (e.g. SKILL.md)
        // Legacy script copied the whole directory.
        // Here we might need to be more selective or copy the parent dir of the skill.yaml
        const skillDir = dirname(file);
        const skillDestDir = join(claudeDir, 'skills', skill.id);
        await mkdir(skillDestDir, { recursive: true });
        
        // Copy all files in skill dir
        const entries = await readdir(skillDir);
        for (const entry of entries) {
            await copyFile(join(skillDir, entry), join(skillDestDir, entry));
        }
      }
      
      // Generate skills.json
      await writeFile(join(claudeDir, 'skills.json'), JSON.stringify({ skills }, null, 2));
      
    } catch {}

    // Agents
    const agentsDir = this.config.getPath(this.config.dirs.agents);
    try {
      const agentFiles = await this.loader.findYamlFiles(agentsDir);
      for (const file of agentFiles) {
        const agent = await this.loader.loadYaml<Agent>(file);
        if (this.resolver.shouldIncludeAgent(agent.id, project)) {
           // Generate Claude-specific agent MD
           const destDir = join(claudeDir, 'agents');
           await mkdir(destDir, { recursive: true });
           
           let content = `---
description: ${agent.description}
---

# ${agent.purpose}

`;
           if (agent.prompt?.system) {
               content += `${agent.prompt.system}\n\n`;
           }
           
           if (agent.rulepacks) {
               const rules = await this.resolver.resolveRulepacks(agent.rulepacks);
               if (rules.length > 0) {
                   content += `## Rules\n\n`;
                   rules.forEach(r => content += `- ${r}\n`);
                   content += `\n`;
               }
           }
           
           await writeFile(join(destDir, `${agent.id}.md`), content);
        }
      }
    } catch {}

    // 2. Generate project-context.json
    const rules = await this.buildProjectRules(project);
    const context = {
      project: {
        name: project.name,
        description: project.description,
        rules: rules,
      },
    };

    await writeFile(join(claudeDir, 'project-context.json'), JSON.stringify(context, null, 2));

    // 3. Generate recipes
    await this.recipeService.generateRecipesForTool(this.name, outputDir, project);
  }

  async generateGlobal(outputDir: string): Promise<void> {
    const claudeDir = join(outputDir, '.claude');
    await mkdir(claudeDir, { recursive: true });

    const adapterSrcDir = this.config.getPath(this.config.dirs.adapters, 'claude-code');
    try {
      await this.copyDirectory(adapterSrcDir, claudeDir);
    } catch {
      // Ignore
    }
  }

  private async copyPromptsWithFiltering(src: string, dest: string, project: Project): Promise<void> {
    await mkdir(dest, { recursive: true });
    const entries = await readdir(src, { withFileTypes: true });

    // We need a map of ID -> Path to use resolver.shouldIncludePrompt
    // But here we are copying from already generated adapters which might be JSON files.
    // In gen-project.ts: copyClaudePromptsWithFiltering
    // It iterates and checks filtering.
    
    // For simplicity and to match gen-project.ts logic:
    // It seems gen-project.ts relies on finding the original YAML to check filtering?
    // Or it parses the JSON/MD in the adapter?
    // Let's look at gen-project.ts implementation again.
    // It seems it iterates the source directory (adapters/claude-code/prompts).
    // If it's a directory, recurse.
    // If it's a JSON file (category-prompt-id.json), it extracts ID and checks filtering.
    
    for (const entry of entries) {
      const srcPath = join(src, entry.name);
      const destPath = join(dest, entry.name);

      if (entry.isDirectory()) {
        await this.copyPromptsWithFiltering(srcPath, destPath, project);
      } else if (entry.name.endsWith('.json')) {
        // e.g. "refactor-extract-method.json" -> ID might be "extract-method" or "refactor/extract-method"
        // The ID extraction in gen-project.ts is a bit loose or relies on naming convention.
        // Actually, let's look at how we can check filtering.
        // The resolver needs prompt ID or path.
        // If we can't easily deduce ID, we might need to load the JSON and check 'id' field if present.
        
        const content = await readFile(srcPath, 'utf-8');
        const promptData = JSON.parse(content);
        // Assuming promptData has id. If not, we might skip filtering or include all.
        // In build.ts, we generate these files.
        
        // Let's try to use the ID from the file content if available.
        // If not, maybe we skip filtering?
        // The original code in gen-project.ts:
        // const filenameWithoutExt = entry.name.replace(/\.json$/, '');
        // ... logic to match ID ...
        // It seems complex to reverse engineer ID from filename if it was transformed.
        
        // BETTER APPROACH:
        // The ResolverService.shouldIncludePrompt takes a map of ID -> Path.
        // We can build this map from the source YAMLs (using LoaderService).
        // Then for each file in adapters, we try to match.
        
        // However, since we are in the Adapter, maybe we should just load the source YAMLs directly
        // and regenerate the Claude output?
        // That would be cleaner and less dependent on the 'build' step artifacts.
        // BUT, the requirement says "Polymorphic Export System".
        // And we want to avoid duplication.
        // If we regenerate, we duplicate the logic of "how to convert Prompt YAML to Claude JSON".
        // That logic is currently in `build.ts`.
        
        // Ideally, `build.ts` logic should be moved to `ClaudeAdapter.generateGlobal` (or similar).
        // And `generate` (project-specific) should reuse that logic or use the generated artifacts.
        
        // If we use generated artifacts, we need to filter them.
        // Let's stick to filtering for now to match `gen-project.ts`.
        
        // In `gen-project.ts`:
        // const filenameWithoutExt = entry.name.replace(/\.json$/, '');
        // It iterates over all known prompts (from `this.promptsMap`) and checks if filename ends with that ID (normalized).
        
        // This is inefficient.
        // Let's simplified:
        // If we can't easily filter, maybe we just include all?
        // No, filtering is a requirement.
        
        // Let's try to extract ID from the JSON content.
        if (promptData.id) {
           // We need to construct the "path" for the resolver.
           // The resolver uses path for whitelist matching (e.g. "refactor/extract-method").
           // We might not have the path here.
           // But we can use the ID.
           
           // Let's load all prompts to build the map, similar to gen-project.ts
           const promptsDir = this.config.getPath(this.config.dirs.prompts);
           const promptsMap = new Map<string, string>();
           const promptFiles = await this.loader.findYamlFilesRelative(promptsDir);
           for (const f of promptFiles) {
             const p = await this.loader.loadYaml<Prompt>(join(promptsDir, f));
             promptsMap.set(p.id, f.replace(/\.ya?ml$/, '').replace(/\\/g, '/'));
           }
           
           if (this.resolver.shouldIncludePrompt(promptData.id, promptsMap, project)) {
             await copyFile(srcPath, destPath);
           }
        } else {
          // Fallback: include if no ID found (safe default?) or exclude?
          // Let's include.
          await copyFile(srcPath, destPath);
        }
      }
    }
  }

  private async copyAgentsWithFiltering(src: string, dest: string, project: Project): Promise<void> {
    await mkdir(dest, { recursive: true });
    const entries = await readdir(src, { withFileTypes: true });

    for (const entry of entries) {
      const srcPath = join(src, entry.name);
      const destPath = join(dest, entry.name);

      if (entry.isDirectory()) {
        await this.copyAgentsWithFiltering(srcPath, destPath, project);
      } else if (entry.name.endsWith('.md')) {
        const agentId = entry.name.replace(/\.md$/, '');
        if (this.resolver.shouldIncludeAgent(agentId, project)) {
          await copyFile(srcPath, destPath);
        }
      } else {
        await copyFile(srcPath, destPath);
      }
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

  private async buildProjectRules(project: Project): Promise<string[]> {
    const rules: string[] = [];
    if (project.conventions) {
      if (project.conventions.naming) rules.push(...project.conventions.naming);
      if (project.conventions.patterns) rules.push(...project.conventions.patterns);
      if (project.conventions.testing) rules.push(...project.conventions.testing);
      if (project.conventions.structure) rules.push(...project.conventions.structure);
      if (project.conventions.custom) rules.push(...project.conventions.custom);
    }
    if (project.ai_tools) {
      if (project.ai_tools.preferred_rulepacks) {
        const resolvedRules = await this.resolver.resolveRulepacks(project.ai_tools.preferred_rulepacks, project);
        rules.push(...resolvedRules);
      }
      if (project.ai_tools.custom_rules) {
        rules.push(...project.ai_tools.custom_rules);
      }
    }
    return rules;
  }
}
