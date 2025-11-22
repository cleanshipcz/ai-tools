import { LoaderService } from './loader.service.js';
import { ConfigService } from './config.service.js';
import { Rulepack, Project, AIModel, Agent, Prompt } from '../models/types.js';
import { join } from 'path';

export class ResolverService {
  private loader: LoaderService;
  private config: ConfigService;
  private rulepacksCache = new Map<string, Rulepack>();

  constructor() {
    this.loader = new LoaderService();
    this.config = ConfigService.getInstance();
  }

  /**
   * Resolve rulepacks into a flat list of rules, handling inheritance (extends).
   * Optionally filters rulepacks based on project configuration (whitelist/blacklist/tech-stack).
   */
  async resolveRulepacks(rulepackIds: string[], project?: Project, techStackContext?: { languages?: string[] }): Promise<string[]> {
    const resolved: string[] = [];
    const visited = new Set<string>();

    const resolve = async (id: string) => {
      if (visited.has(id)) return;
      visited.add(id);

      // Check if we should include this rulepack based on project config
      if (project && !(await this.shouldIncludeRulepack(id, project, techStackContext))) {
        return;
      }

      const rulepack = await this.loadRulepack(id);
      if (!rulepack) {
        console.warn(`Warning: Rulepack "${id}" not found`);
        return;
      }

      // Resolve parent rulepacks first
      if (rulepack.extends) {
        for (const parentId of rulepack.extends) {
          await resolve(parentId);
        }
      }

      // Add this rulepack's rules
      resolved.push(...rulepack.rules);
    };

    for (const id of rulepackIds) {
      await resolve(id);
    }

    return resolved;
  }

  async loadRulepack(rulepackId: string): Promise<Rulepack | null> {
    if (this.rulepacksCache.has(rulepackId)) {
      return this.rulepacksCache.get(rulepackId)!;
    }

    try {
      const rulepackPath = this.config.getPath(this.config.dirs.rulepacks, `${rulepackId}.yml`);
      const rulepack = await this.loader.loadYaml<Rulepack>(rulepackPath);
      this.rulepacksCache.set(rulepackId, rulepack);
      return rulepack;
    } catch {
      return null;
    }
  }

  async shouldIncludeRulepack(rulepackId: string, project: Project, techStackContext?: { languages?: string[] }): Promise<boolean> {
    if (!project.ai_tools) return true;

    const { whitelist_rulepacks, blacklist_rulepacks } = project.ai_tools;

    // Whitelist takes precedence for INCLUSION, but we still might want to filter by tech stack
    // If it's in the whitelist, we proceed to tech stack checks instead of returning true immediately.
    // If it's NOT in the whitelist, we return false (if whitelist exists).
    if (whitelist_rulepacks && whitelist_rulepacks.length > 0) {
      if (!whitelist_rulepacks.includes(rulepackId)) {
        return false;
      }
      // It is whitelisted, but we still check tech stack constraints below
    }

    // Blacklist
    if (blacklist_rulepacks && blacklist_rulepacks.length > 0) {
      return !blacklist_rulepacks.includes(rulepackId);
    }

    // Tech-stack filtering
    // Use specific context if provided, otherwise fall back to global project tech stack
    const languages = techStackContext?.languages || project.tech_stack?.languages;

    const rulepack = await this.loadRulepack(rulepackId);
    // Check tags at root level (as per YAMLs) or in metadata (legacy/fallback)
    const tags = rulepack?.tags || rulepack?.metadata?.tags;

    if (tags) {
      const rulepackTags = tags.map((tag) => tag.toLowerCase());

      if (languages && languages.length > 0) {
        // We have a specific language context
        const techStackLanguages = languages.map((lang) => lang.toLowerCase());
        const hasLanguageMatch = rulepackTags.some((tag) => techStackLanguages.includes(tag));

        // Check if rulepack is language-specific
        // We use a heuristic list of common languages, OR we check against ALL languages defined in the project
        const allProjectLanguages = this.getAllProjectLanguages(project);
        const isLanguageSpecific = rulepackTags.some((tag) => 
          [
            'java', 'kotlin', 'python', 'typescript', 'javascript', 'go', 'rust', 'c++', 'c#', 'ruby', 'php', 'swift'
          ].includes(tag) || allProjectLanguages.includes(tag)
        );

        if (isLanguageSpecific && !hasLanguageMatch) {
          return false;
        }
      } else {
        // No language context (Global context)
        // If the rulepack is tagged with ANY language that is used in the project's tech stacks, exclude it.
        // This prevents "python" rules from leaking into the global agent when "python" is only in the "backend" stack.
        const allProjectLanguages = this.getAllProjectLanguages(project);
        const isSpecificToSomeStack = rulepackTags.some(tag => allProjectLanguages.includes(tag));
        
        if (isSpecificToSomeStack) {
           return false;
        }
      }
    }

    return true;
  }

  private getAllProjectLanguages(project: Project): string[] {
    const languages = new Set<string>();
    
    if (project.tech_stack?.languages) {
      project.tech_stack.languages.forEach(l => languages.add(l.toLowerCase()));
    }

    if (project.tech_stacks) {
      Object.values(project.tech_stacks).forEach(stack => {
        if (stack.languages) {
          stack.languages.forEach(l => languages.add(l.toLowerCase()));
        }
      });
    }

    return Array.from(languages);
  }

  /**
   * Resolves all agents for a project, including global and stack-specific versions.
   * This provides a generic way for all adapters to generate agents.
   */
  async resolveAllAgents(project: Project): Promise<{ agent: Agent; rules: string[]; suffix: string }[]> {
    const results: { agent: Agent; rules: string[]; suffix: string }[] = [];
    const agentsDir = this.config.getPath(this.config.dirs.agents);
    let agentFiles: string[] = [];
    
    try {
      agentFiles = await this.loader.findYamlFiles(agentsDir);
    } catch {
      return [];
    }

    // Helper to process agents for a context
    const processContext = async (suffix: string, context?: { languages?: string[] }) => {
      for (const file of agentFiles) {
        const agent = await this.loader.loadYaml<Agent>(file);
        if (this.shouldIncludeAgent(agent.id, project)) {
          const rules = agent.rulepacks ? await this.resolveRulepacks(agent.rulepacks, project, context) : [];
          results.push({ agent, rules, suffix });
        }
      }
    };

    // 1. Global Context
    // Only generate global context if we don't have specific tech stacks OR if we explicitly want global fallback?
    // Usually we always want global agents (e.g. for general tasks).
    // Our updated shouldIncludeRulepack will ensure they don't have language-specific pollution.
    await processContext('');

    // 2. Stack-specific Contexts
    if (project.tech_stacks) {
      for (const [stackName, stackContext] of Object.entries(project.tech_stacks)) {
        await processContext(`-${stackName}`, stackContext);
      }
    }

    return results;
  }

  shouldIncludeAgent(agentId: string, project?: Project): boolean {
    if (!project?.ai_tools) return true;

    const { whitelist_agents, blacklist_agents } = project.ai_tools;

    if (whitelist_agents && whitelist_agents.length > 0) {
      return whitelist_agents.includes(agentId);
    }

    if (blacklist_agents && blacklist_agents.length > 0) {
      return !blacklist_agents.includes(agentId);
    }

    return true;
  }

  shouldIncludePrompt(promptIdOrPath: string, promptsMap: Map<string, string>, project?: Project): boolean {
    if (!project?.ai_tools) return true;

    const { whitelist_prompts, blacklist_prompts } = project.ai_tools;
    const promptPath = promptsMap.get(promptIdOrPath) || promptIdOrPath;

    if (whitelist_prompts && whitelist_prompts.length > 0) {
      return whitelist_prompts.some((pattern) => {
        return (
          promptPath === pattern || promptPath.endsWith(`/${pattern}`) || promptIdOrPath === pattern
        );
      });
    }

    if (blacklist_prompts && blacklist_prompts.length > 0) {
      return !blacklist_prompts.some((pattern) => {
        return (
          promptPath === pattern || promptPath.endsWith(`/${pattern}`) || promptIdOrPath === pattern
        );
      });
    }

    return true;
  }

  shouldIncludeRecipe(recipeId: string, project?: Project): boolean {
    if (!project?.ai_tools) return true;

    const { whitelist_recipes, blacklist_recipes } = project.ai_tools;

    if (whitelist_recipes && whitelist_recipes.length > 0) {
      return whitelist_recipes.includes(recipeId);
    }

    if (blacklist_recipes && blacklist_recipes.length > 0) {
      return !blacklist_recipes.includes(recipeId);
    }

    return true;
  }

  /**
   * Resolve the effective model based on hierarchy:
   * feature > project > agent > prompt (lowest priority)
   */
  resolveModel(
    prompt?: Prompt,
    agent?: Agent,
    project?: Project,
    feature?: { model?: AIModel }
  ): AIModel | undefined {
    return feature?.model || project?.ai_tools?.model || agent?.defaults?.model || prompt?.model;
  }
}
