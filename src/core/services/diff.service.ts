import { readFile } from 'fs/promises';
import { diffLines, diffWords, Change } from 'diff';

export interface DiffOptions {
  before: string;
  after: string;
  format?: 'lines' | 'words';
}

export interface DiffResult {
  changes: Change[];
  stats: {
    additions: number;
    deletions: number;
    unchanged: number;
    changeRatio: number;
  };
}

export class DiffService {
  async compare(options: DiffOptions): Promise<DiffResult> {
    let beforeContent: string;
    let afterContent: string;

    try {
      beforeContent = await readFile(options.before, 'utf-8');
    } catch (error) {
      throw new Error(`Failed to read before file: ${options.before}`);
    }

    try {
      afterContent = await readFile(options.after, 'utf-8');
    } catch (error) {
      throw new Error(`Failed to read after file: ${options.after}`);
    }

    const format = options.format || 'lines';
    const changes =
      format === 'words'
        ? diffWords(beforeContent, afterContent)
        : diffLines(beforeContent, afterContent);

    let additions = 0;
    let deletions = 0;
    let unchanged = 0;

    for (const part of changes) {
      const lines = part.value.split('\n').filter((l: string) => l.length > 0);
      if (part.added) {
        additions += lines.length;
      } else if (part.removed) {
        deletions += lines.length;
      } else {
        unchanged += lines.length;
      }
    }

    const totalChanges = additions + deletions;
    const changeRatio = totalChanges === 0 ? 0 : (totalChanges / (totalChanges + unchanged)) * 100;

    return {
      changes,
      stats: {
        additions,
        deletions,
        unchanged,
        changeRatio,
      },
    };
  }
}
