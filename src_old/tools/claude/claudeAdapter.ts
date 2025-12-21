import { Feature, Prompt, Agent, Skill, Recipe } from '../../core/models/types';
import { ToolAdapter } from '../common/toolAdapter';
import { FileWriter } from '../common/fileWriter';
import { join } from 'path';
import { FileContent } from '../common/fileContent';
import { Printers } from '../common/printers';
import { Printer } from '../common/printer';

const CLAUDE_DIR = '.claude';

export class ClaudeAdapter implements ToolAdapter {
  fileWriter = new FileWriter();
  printers = new Printers();

  exportFeature(projectDirPath: string, feature: Feature): Promise<void> {
    throw new Error('Method not implemented.');
  }
  async exportPrompt(projectDirPath: string, prompt: Prompt): Promise<void> {
    await this.export(
      projectDirPath,
      'prompts',
      `${prompt.id}.md`,
      this.printers.getPromptPrinter(),
      prompt
    );
  }
  exportAgent(projectDirPath: string, agent: Agent): Promise<void> {
    throw new Error('Method not implemented.');
  }
  exportSkill(projectDirPath: string, skill: Skill): Promise<void> {
    throw new Error('Method not implemented.');
  }
  exportRecipe(projectDirPath: string, recipe: Recipe): Promise<void> {
    throw new Error('Method not implemented.');
  }
  exportProjectGlobal(projectDirPath: string): Promise<void> {
    throw new Error('Method not implemented.');
  }

  private filePath(projectDirPath: string, subDirectory: string, fileName: string): string {
    return join(projectDirPath, CLAUDE_DIR, subDirectory, fileName);
  }

  private async export<T>(
    projectDirPath: string,
    subDirectory: string,
    fileName: string,
    printer: Printer<T>,
    entity: T
  ) {
    const path = this.filePath(projectDirPath, subDirectory, fileName);
    const content = new FileContent();
    printer.print(entity, content);
    await this.fileWriter.writeFile(path, content);
  }
}
