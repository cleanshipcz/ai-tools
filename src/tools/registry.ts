import { ToolAdapter } from './base.js';
import { WindsurfAdapter } from './windsurf/adapter.js';
import { CursorAdapter } from './cursor/adapter.js';
import { ClaudeAdapter } from './claude/adapter.js';
import { GitHubCopilotAdapter } from './github-copilot/adapter.js';
import { CopilotCLIAdapter } from './copilot-cli/adapter.js';

export class ToolRegistry {
  private static instance: ToolRegistry;
  private adapters = new Map<string, ToolAdapter>();

  static getInstance(): ToolRegistry {
    if (!ToolRegistry.instance) {
      ToolRegistry.instance = new ToolRegistry();
    }
    return ToolRegistry.instance;
  }

  constructor() {
    this.register(new WindsurfAdapter());
    this.register(new CursorAdapter());
    this.register(new ClaudeAdapter());
    this.register(new GitHubCopilotAdapter());
    this.register(new CopilotCLIAdapter());
  }

  register(adapter: ToolAdapter) {
    this.adapters.set(adapter.name, adapter);
  }

  get(name: string): ToolAdapter | undefined {
    return this.adapters.get(name);
  }

  getAdapter(name: string): ToolAdapter | undefined {
    return this.get(name);
  }

  getAll(): ToolAdapter[] {
    return Array.from(this.adapters.values());
  }
}
