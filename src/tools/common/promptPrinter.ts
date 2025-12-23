import { Prompt } from '../../core/models/types';
import { FileContent } from './fileContent';
import { Printer } from './printer';

export class PromptPrinter implements Printer<Prompt> {
  print(entity: Prompt, content: FileContent): FileContent {
    content.append(`# ${entity.id}`);
    content.append('');
    content.append(entity.description);
    content.append('');

    if (entity.variables && entity.variables.length > 0) {
      content.append('## Variables');
      content.append('');
      for (const variable of entity.variables) {
        const required = variable.required ? ' (required)' : '';
        content.append(`- \`{{${variable.name}}}\`${required}: ${variable.description || ''}`);
      }
      content.append('');
    }

    if (entity.content) {
      content.append('## Prompt');
      content.append('');
      content.append(entity.content);
      content.append('');
    }

    if (entity.system) {
      content.append('## System Prompt');
      content.append('');
      content.append(entity.system);
      content.append('');
    }

    if (entity.user) {
      content.append('## User Prompt');
      content.append('');
      content.append(entity.user);
      content.append('');
    }
    return content;
  }
}
