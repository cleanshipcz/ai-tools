import { Agent, Feature, Prompt, Recipe, Skill } from '../../core/models/types';

export interface ToolAdapter {
  exportFeature(projectDirPath: string, feature: Feature): Promise<void>;
  exportPrompt(projectDirPath: string, prompt: Prompt): Promise<void>;
  exportAgent(projectDirPath: string, agent: Agent): Promise<void>;
  exportSkill(projectDirPath: string, skill: Skill): Promise<void>;
  exportRecipe(projectDirPath: string, recipe: Recipe): Promise<void>;
  exportProjectGlobal(projectDirPath: string): Promise<void>; // TODO
}
