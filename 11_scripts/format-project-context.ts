#!/usr/bin/env node
/**
 * Helper script to load and format project.yml context for injection into AI prompts
 * Called from generated recipe bash scripts at runtime
 */

import { ProjectContextLoader } from './project-context.js';

async function main() {
  const loader = new ProjectContextLoader(process.cwd());
  const project = await loader.loadProject();

  if (project) {
    const context = loader.getContextForSystemPrompt();
    console.log(context);
  }
  // If no project found, output nothing (empty string)
}

main().catch((error) => {
  // Fail silently - if we can't load context, just continue without it
  process.stderr.write(`Warning: Could not load project context: ${error.message}\n`);
});
