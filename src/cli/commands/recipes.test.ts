import { describe, it, expect, vi, beforeEach } from 'vitest';
import { recipesCommand, listRecipes, runRecipe, generateRecipe } from './recipes.js';
import { RecipeService } from '../../core/services/recipe.service.js';
import { RecipeRunnerService } from '../../core/services/recipe-runner.service.js';

// Mock RecipeService
vi.mock('../../core/services/recipe.service.js', () => {
  const MockRecipeService = vi.fn();
  MockRecipeService.prototype.listRecipes = vi.fn().mockResolvedValue([
    { id: 'test-recipe', description: 'Test Recipe', steps: [] }
  ]);
  MockRecipeService.prototype.loadRecipe = vi.fn().mockResolvedValue({
    id: 'test-recipe',
    description: 'Test Recipe',
    steps: []
  });
  MockRecipeService.prototype.generateRecipeScript = vi.fn();
  return { RecipeService: MockRecipeService };
});

// Mock RecipeRunnerService
vi.mock('../../core/services/recipe-runner.service.js', () => {
  const MockRunner = vi.fn();
  MockRunner.prototype.run = vi.fn();
  return { RecipeRunnerService: MockRunner };
});

describe('recipes command', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should list recipes', async () => {
    const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    
    await listRecipes();

    expect(RecipeService.prototype.listRecipes).toHaveBeenCalled();
    expect(consoleSpy).toHaveBeenCalledWith(expect.stringContaining('test-recipe'));
    
    consoleSpy.mockRestore();
  });

  it('should run a recipe', async () => {
    await runRecipe('test-recipe', 'claude-code');

    expect(RecipeService.prototype.loadRecipe).toHaveBeenCalledWith('test-recipe');
    expect(RecipeRunnerService).toHaveBeenCalled();
    expect(RecipeRunnerService.prototype.run).toHaveBeenCalled();
  });

  it('should generate a recipe script', async () => {
    await generateRecipe('test-recipe', 'claude-code', 'output.sh');

    expect(RecipeService.prototype.loadRecipe).toHaveBeenCalledWith('test-recipe');
    expect(RecipeService.prototype.generateRecipeScript).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'test-recipe' }),
      'claude-code',
      'output.sh'
    );
  });
});
