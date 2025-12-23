import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ResolverService } from './resolver.service.js';
import { Project } from '../models/types.js';

describe('ResolverService pattern-based filters', () => {
  let resolver: ResolverService;

  beforeEach(() => {
    resolver = new ResolverService();
  });

  it('should include/exclude agents using allow/deny patterns', () => {
    const project: Project = {
      id: 'p',
      version: '1.0.0',
      name: 'Test',
      description: 'desc',
      agents: { include: ['^code-.*'] },
    };

    expect(resolver.shouldIncludeAgent('code-reviewer', project)).toBe(true);
    expect(resolver.shouldIncludeAgent('feature-builder', project)).toBe(false);

    project.agents!.exclude = ['reviewer$'];
    expect(resolver.shouldIncludeAgent('code-reviewer', project)).toBe(false);
  });

  it('should include/exclude prompts using allow/deny patterns against prompt paths', () => {
    const promptsMap = new Map<string, string>([
      ['refactor/extract-method', '03_prompts/refactor/extract-method.yml'],
      ['docs/create-tutorial', '03_prompts/docs/create-tutorial.yml'],
    ]);

    const project: Project = {
      id: 'p',
      version: '1.0.0',
      name: 'Test',
      description: 'desc',
      prompts: { include: ['^refactor/'] },
    };

    expect(resolver.shouldIncludePrompt('refactor/extract-method', promptsMap, project)).toBe(true);
    expect(resolver.shouldIncludePrompt('docs/create-tutorial', promptsMap, project)).toBe(false);

    project.prompts!.exclude = ['tutorial'];
    expect(resolver.shouldIncludePrompt('docs/create-tutorial', promptsMap, project)).toBe(false);
  });

  it('should include/exclude rulesets using allow/deny patterns', async () => {
    vi.spyOn(resolver as any, 'loadRuleset').mockImplementation(async () => ({ rules: [], tags: [] }));

    const project: Project = {
      id: 'p',
      version: '1.0.0',
      name: 'Test',
      description: 'desc',
      rulesets: { include: ['typescript'] },
    };

    await expect(resolver.shouldIncludeRuleset('coding-typescript', project)).resolves.toBe(true);
    await expect(resolver.shouldIncludeRuleset('coding-python', project)).resolves.toBe(false);

    project.rulesets!.exclude = ['typescript'];
    await expect(resolver.shouldIncludeRuleset('coding-typescript', project)).resolves.toBe(false);
  });

  it('should include/exclude recipes using allow/deny patterns', () => {
    const project: Project = {
      id: 'p',
      version: '1.0.0',
      name: 'Test',
      description: 'desc',
      recipes: { include: ['^feature-'] },
    };

    expect(resolver.shouldIncludeRecipe('feature-delivery', project)).toBe(true);
    expect(resolver.shouldIncludeRecipe('major-refactoring-cycle', project)).toBe(false);

    project.recipes!.exclude = ['delivery'];
    expect(resolver.shouldIncludeRecipe('feature-delivery', project)).toBe(false);
  });
});
