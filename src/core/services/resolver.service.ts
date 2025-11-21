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
  async resolveRulepacks(rulepackIds: string[], project?: Project): Promise<string[]> {
    const resolved: string[] = [];
    const visited = new Set<string>();

    const resolve = async (id: string) => {
      if (visited.has(id)) return;
      visited.add(id);

      // Check if we should include this rulepack based on project config
      if (project && !(await this.shouldIncludeRulepack(id, project))) {
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

  async shouldIncludeRulepack(rulepackId: string, project: Project): Promise<boolean> {
    if (!project.ai_tools) return true;

    const { whitelist_rulepacks, blacklist_rulepacks } = project.ai_tools;

    // Whitelist takes precedence
    if (whitelist_rulepacks && whitelist_rulepacks.length > 0) {
      return whitelist_rulepacks.includes(rulepackId);
    }

    // Blacklist
    if (blacklist_rulepacks && blacklist_rulepacks.length > 0) {
      return !blacklist_rulepacks.includes(rulepackId);
    }

    // Tech-stack filtering
    if (project.tech_stack?.languages && project.tech_stack.languages.length > 0) {
      const rulepack = await this.loadRulepack(rulepackId);
      if (rulepack?.metadata?.tags) {
        const techStackLanguages = project.tech_stack.languages.map((lang) =>
          lang.toLowerCase()
        );
        const rulepackTags = rulepack.metadata.tags.map((tag) => tag.toLowerCase());

        const hasLanguageMatch = rulepackTags.some((tag) => techStackLanguages.includes(tag));

        // Check if rulepack is language-specific
        const isLanguageSpecific = rulepackTags.some((tag) =>
          [
            'java', 'kotlin', 'python', 'typescript', 'javascript', 'go', 'rust', 'c++', 'c#'
          ].includes(tag)
        );

        if (isLanguageSpecific && !hasLanguageMatch) {
          return false;
        }
      }
    }

    return true;
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
