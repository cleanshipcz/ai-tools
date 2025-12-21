export class FileContent {
  content: string[] = [];

  append(line: string) {
    this.content.push(line);
  }

  toString(): string {
    return this.content.join('\n');
  }
}
