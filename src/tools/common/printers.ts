import { PromptPrinter } from './promptPrinter';
import { Prompt } from '../../core/models/types';
import { Printer } from './printer';

export class Printers {
  promptPrinter = new PromptPrinter();

  getPromptPrinter() : Printer<Prompt> {
    return this.promptPrinter;
  }
}
