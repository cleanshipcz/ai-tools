import { writeFile } from 'fs/promises';
import { FileContent } from './fileContent';

export class FileWriter {
  async writeFile(path: string, content: FileContent) {
    await writeFile(path, content.toString(), 'utf-8');
  }
}
