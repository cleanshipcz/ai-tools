import { DeployConfig, Project } from '../models/types.js';

/**
 * Merge deployment-specific configuration into the base project manifest.
 * Deploy settings take precedence when present, but the merge is shallow to keep behavior predictable.
 */
export function applyDeployConfig(project: Project, deployConfig?: DeployConfig): Project {
  if (!deployConfig) {
    return project;
  }

  const mergedAiTools =
    deployConfig.ai_tools || project.ai_tools
      ? { ...(project.ai_tools || {}), ...(deployConfig.ai_tools || {}) }
      : undefined;

  return {
    ...project,
    tech_stack: deployConfig.tech_stack ?? project.tech_stack,
    tech_stacks: deployConfig.tech_stacks ?? project.tech_stacks,
    agents: deployConfig.agents ?? project.agents,
    prompts: deployConfig.prompts ?? project.prompts,
    rulepacks: deployConfig.rulepacks ?? project.rulepacks,
    recipes: deployConfig.recipes ?? project.recipes,
    ai_tools: mergedAiTools,
  };
}
