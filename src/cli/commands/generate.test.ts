import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { generateForProject } from './generate.js';
import { ConfigService } from '../../core/services/config.service.js';
import { LoaderService } from '../../core/services/loader.service.js';
import { ToolRegistry } from '../../tools/registry.js';
import * as fs from 'fs/promises';
import { join } from 'path';
import { tmpdir } from 'os';
import { mkdtemp } from 'fs/promises';

// Mock RecipeService to avoid dependency issues during test
vi.mock('../../core/services/recipe.service.js', () => {
  return {
    RecipeService: class {
      generateRecipesForTool = vi.fn().mockResolvedValue(undefined);
    },
  };
});

describe('generate command (integration)', () => {
  let tempDir: string;
  let projectsDir: string;
  let outputDir: string;

  beforeEach(async () => {
    // Create a temporary directory for the test
    tempDir = await mkdtemp(join(tmpdir(), 'ai-tools-test-'));
    projectsDir = join(tempDir, 'projects');
    outputDir = join(tempDir, 'output');

    await fs.mkdir(projectsDir, { recursive: true });
    await fs.mkdir(outputDir, { recursive: true });

    // Mock ConfigService to point to temp directories for projects and output
    vi.spyOn(ConfigService.prototype, 'getProjectSources').mockResolvedValue([projectsDir]);
    vi.spyOn(ConfigService.prototype, 'getPath').mockImplementation((...parts: string[]) => {
      // Intercept projects and output directories
      if (parts[0] === '06_projects') {
        return join(projectsDir, ...parts.slice(1));
      }
      if (parts[0] === '.output') {
        return join(outputDir, ...parts.slice(1));
      }
      // For everything else (agents, prompts, etc.), use the real project root
      return join(process.cwd(), ...parts);
    });
  });

  afterEach(async () => {
    // Cleanup
    await fs.rm(tempDir, { recursive: true, force: true });
    vi.restoreAllMocks();
  });

  it('should generate stack-specific agents based on tech_stacks configuration', async () => {
    // 1. Setup Test Project
    const projectId = 'test-stack-integration';
    const projectDir = join(projectsDir, projectId);
    await fs.mkdir(projectDir, { recursive: true });

    const projectYaml = `
id: ${projectId}
version: 1.0.0
name: Test Stack Integration
description: Integration test for tech stacks
tech_stacks:
  backend:
    languages: [python]
  frontend:
    languages: [typescript]
ai_tools:
  whitelist_agents:
    - feature-builder
`;
    await fs.writeFile(join(projectDir, 'project.yml'), projectYaml);

    // 2. Run Generation
    const config = ConfigService.getInstance();
    const loader = new LoaderService();
    const registry = new ToolRegistry(); // This will load real adapters

    // We need to make sure LoaderService can find the rulepacks and agents
    // Since we are running in the real repo, it should find them in the real paths
    // relative to CWD. We might need to adjust if CWD is not root.
    // Assuming test runner runs from root.

    await generateForProject(projectId, config, loader, registry);

    // 3. Verify Output
    const windsurfRulesDir = join(outputDir, projectId, 'windsurf', '.windsurf', 'rules');
    
    // Check Backend Agent (Python)
    const backendAgentPath = join(windsurfRulesDir, 'agent-feature-builder-backend.md');
    expect(await fs.stat(backendAgentPath)).toBeDefined();
    const backendContent = await fs.readFile(backendAgentPath, 'utf-8');
    expect(backendContent).toContain('PEP 8'); // Specific to Python rules
    expect(backendContent).not.toContain('strict TypeScript'); // Should NOT have TypeScript rules

    // Check Frontend Agent (TypeScript)
    const frontendAgentPath = join(windsurfRulesDir, 'agent-feature-builder-frontend.md');
    expect(await fs.stat(frontendAgentPath)).toBeDefined();
    const frontendContent = await fs.readFile(frontendAgentPath, 'utf-8');
    expect(frontendContent).toContain('strict TypeScript'); // Specific to TypeScript rules
    expect(frontendContent).not.toContain('PEP 8'); // Should NOT have Python rules
  });
});
