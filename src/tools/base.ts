import { Project } from '../core/models/types.js';

export interface GlobalContext {
  // Context available when generating global configuration (not project-specific)
  // e.g., generating adapters for the repo itself
}

export abstract class ToolAdapter {
  /**
   * The unique identifier for the tool (e.g., 'windsurf', 'cursor')
   */
  abstract name: string;

  /**
   * Generate configuration for a specific project.
   * @param project The project configuration
   * @param outputDir The directory where output should be generated
   */
  abstract generate(project: Project, outputDir: string): Promise<void>;

  /**
   * Generate global configuration (e.g., for the ai-tools repo itself).
   * This corresponds to the old 'build' script functionality.
   * @param outputDir The directory where output should be generated
   */
  abstract generateGlobal(outputDir: string): Promise<void>;
}
