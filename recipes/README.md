# Recipes

Multi-agent workflow recipes for orchestrating complex tasks.

## Table of Contents

- [Quick Start](#quick-start)
- [How to Use](#how-to-use)
- [Using with Features](#using-with-features)
- [Creating Custom Recipes](#creating-custom-recipes)
- [Available Recipes](#available-recipes)
- [Schema Reference](#schema-reference)

## Quick Start

1. Deploy recipes to your project: `npm run project:deploy my-project`
2. Go to your project: `cd /path/to/your-actual-app`
3. Run the recipe: `./.cs.recipes/feature-delivery.sh`

The script runs multi-agent workflows on YOUR code, completely unattended.

## How to Use

### 1. Deploy to Your Project

```bash
# In ai-tools repo
cd ~/ai-tools

# Create project config (if not done already)
npm run project:create my-app -- --local

# Edit deploy.yml to point to your app
vim projects/local/my-app/deploy.yml
# Set target: /path/to/your-actual-app

# Deploy
npm run project:deploy my-app
```

### 2. Run Recipes in Your Project

```bash
# Go to YOUR actual project (where your code is)
cd /path/to/your-actual-app

# Run recipes - they'll modify YOUR code automatically
./.cs.recipes/feature-delivery.sh
./.cs.recipes/bug-fix-workflow.sh
./.cs.recipes/code-review-cycle.sh

# Or for Claude Code:
./.claude/.cs.recipes/feature-delivery.sh
```

**Note:** Recipes run completely unattended. No prompts, no user interaction. Just set it and walk away.

## Using with Features

If you have a feature defined in `projects/local/my-app/features/user-auth/feature.yml`:

```yaml
# features/user-auth/feature.yml
id: user-auth
name: 'User Authentication'
description: 'JWT-based authentication system'

recipe:
  id: feature-delivery
  context:
    feature_description: |
      Implement JWT authentication with:
      - User registration endpoint
      - Login with token generation
      - Password hashing with bcrypt
    acceptance_criteria: |
      - Users can register and login
      - Passwords are securely hashed
      - JWT tokens expire after 24h
  tools:
    - copilot-cli
```

Then deploy and a **feature-specific script** is generated:

```bash
# In ai-tools
npm run project:deploy my-app

# In YOUR project
cd /path/to/your-actual-app
ls -la .cs.recipes/
# feature-user-auth.sh ← Generated with context pre-filled!

# Run it (fully automated, no interaction needed)
./.cs.recipes/feature-user-auth.sh
# All variables from feature.yml are already set
```

## Creating Custom Recipes

1. Create `recipes/my-recipe.yml`
2. Define workflow steps with agents and tasks
3. Add loops as needed (all iterations run automatically)
4. Validate: `npm run validate`
5. Test: `npm run recipe:run my-recipe`

**Conversation Strategy:**

Set `conversationStrategy: separate` (default) for fresh agent context per step, or `conversationStrategy: continue` to maintain context across steps.

**Tool Options:**

Configure tool-specific command-line options for fully automated execution:

```yaml
id: my-recipe
conversationStrategy: separate  # Each agent starts fresh (recommended)
# conversationStrategy: continue  # Maintain conversation context (uses more tokens)

# Enable automated tool execution (no prompts)
toolOptions:
  copilot-cli:
    allowAllTools: true  # Required for non-interactive execution

loop:
  steps:
    - review
    - refactor
  maxIterations: 3  # Runs all 3 iterations automatically
```

**For advanced topics**, see [GUIDE.md](GUIDE.md) for:
- Detailed recipe structure
- Workflow control patterns
- Creating custom recipes
- Testing and debugging
- Best practices

**For developers testing recipes:**

```bash
# List available recipes
npm run recipe:list

# Test recipe execution (runs in ai-tools repo)
npm run recipe:run feature-delivery copilot-cli

# ⚠️ For testing only! Real usage: deploy to your project
```

## Available Recipes

### feature-delivery.yml

Complete feature implementation workflow with iterative review cycles.

**Flow:** Implement → Review → Refactor (3x) → Verify → Document

**Variables:**
- `FEATURE_DESCRIPTION` - Feature requirements
- `ACCEPTANCE_CRITERIA` - Success criteria

**Loop:** Runs 3 review→refactor cycles automatically

**Tools:** claude-code, copilot-cli

**Conversation:** Separate (each agent starts fresh)

---

### code-review-cycle.yml

Iterative code review and improvement until quality standards are met.

**Flow:** Review → Fix → Verify → Loop until quality threshold (8+/10)

**Tools:** claude-code, copilot-cli, cursor

---

### bug-fix-workflow.yml

Systematic bug fixing with investigation, testing, and verification.

**Flow:** Investigate → Fix → Test → Review → Document

**Variables:**
- `BUG_DESCRIPTION` - Bug details
- `REPRODUCTION_STEPS` - How to reproduce

**Tools:** claude-code, copilot-cli

## Schema Reference

All recipes must conform to [`schemas/recipe.schema.json`](../schemas/recipe.schema.json).

Required fields:
- `id` - Unique kebab-case identifier
- `version` - Semantic version
- `description` - What the recipe accomplishes
- `steps` - Array of workflow steps with agents and tasks

Optional fields:

- `conversationStrategy` - `'separate'` (default) or `'continue'`
- `toolOptions` - Tool-specific command-line options (e.g., `allowAllTools` for copilot-cli)
- `loop` - Define iterative workflows with `steps` and `maxIterations`
- `variables` - Define inputs for the recipe
