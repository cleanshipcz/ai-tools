import { Feature, Prompt, Agent, Skill, Recipe } from '../../core/models/types';
import { ToolAdapter } from '../common/toolAdapter';
import { FileWriter } from '../common/fileWriter';
import { join } from 'path';
import { FileContent } from '../common/fileContent';
import { Printers } from '../common/printers';
import { Printer } from '../common/printer';

const WINDSURF_DIR = '.github';

export class WindsurfAdapter implements ToolAdapter {
  fileWriter = new FileWriter();
  printers = new Printers();

  exportFeature(projectDirPath: string, feature: Feature): Promise<void> {
    throw new Error('Method not implemented.');
  }
  async exportPrompt(projectDirPath: string, prompt: Prompt): Promise<void> {
    await this.export(
      projectDirPath,
      'rules',
      `prompt-${prompt.id}.md`,
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

  private async export<T>(
    projectDirPath: string,
    subDirectory: string,
    fileName: string,
    printer: Printer<T>,
    entity: T
  ) {
    const path = join(projectDirPath, WINDSURF_DIR, subDirectory, fileName);
    const content = new FileContent();
    content.append('---');
    content.append('trigger: manual');
    content.append('---');
    content.append('');
    printer.print(entity, content);
    await this.fileWriter.writeFile(path, content);
  }
}
