import { FileContent } from './fileContent';

export interface Printer<T> {
  print(entity: T, content: FileContent): FileContent;
}
