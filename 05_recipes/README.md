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

## Document Output & Context Maintenance

All recipes now follow a **Document-First Workflow**: analysis and planning steps create documents that are maintained throughout the workflow.

### How It Works

1. **Analysis Step**: First step analyzes the codebase/changes and outputs to a document (e.g., `.recipe-docs/analysis.md`)
2. **Planning Step**: Creates a detailed plan based on analysis, outputs to document (e.g., `.recipe-docs/plan.md`)
3. **Context Maintenance**: All subsequent steps include these documents in their context automatically
4. **Persistent Reference**: Documents serve as the "source of truth" throughout the workflow

### Benefits

- **Consistency**: All agents work from the same analysis and plan
- **Traceability**: Documents provide an audit trail of decisions
- **Quality**: Better implementation by following well-thought-out plans
- **Debugging**: Easy to review what was analyzed and planned vs. what was implemented

### Example Step with Document Output

```yaml
steps:
  - id: analyze
    agent: project-planner
    task: |
      Analyze the codebase for the following feature...
    outputDocument: '.recipe-docs/analysis.md' # AI saves response here

  - id: implement
    agent: feature-builder
    task: |
      Implement the feature...
    includeDocuments: # These docs are included in the task context
      - '.recipe-docs/analysis.md'
      - '.recipe-docs/plan.md'
```

### Document Directory

All recipe documents are saved to `.recipe-docs/` in your project root. This directory is typically gitignored but can be committed if you want to track analysis/planning decisions.

## Creating Custom Recipes

1. Create `recipes/my-recipe.yml`
2. Define workflow steps with agents and tasks
3. **Add analysis and planning steps first** with `outputDocument`
4. Include documents in subsequent steps with `includeDocuments`
5. Add loops as needed (all iterations run automatically)
6. Validate: `npm run validate`
7. Test: `npm run recipe:run my-recipe`

**Conversation Strategy:**

Set `conversationStrategy: separate` (default) for fresh agent context per step, or `conversationStrategy: continue` to maintain context across steps.

**Tool Options:**

Configure tool-specific command-line options for fully automated execution:

```yaml
id: my-recipe
conversationStrategy: separate # Each agent starts fresh (recommended)
# conversationStrategy: continue  # Maintain conversation context (uses more tokens)

# Enable automated tool execution (no prompts)
toolOptions:
  copilot-cli:
    allowAllTools: true # Required for non-interactive execution

loop:
  steps:
    - review
    - refactor
  maxIterations: 3 # Runs all 3 iterations automatically
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

**Flow:** Analyze → Plan → Implement → Review → Refactor (3x) → Verify → Document

**Key Features:**

- **Analysis Step**: Analyzes codebase and documents affected areas (saved to `.recipe-docs/analysis.md`)
- **Planning Step**: Creates detailed implementation plan (saved to `.recipe-docs/plan.md`)
- **Context Maintenance**: Analysis and plan documents are included in all subsequent steps

**Variables:**

- `FEATURE_DESCRIPTION` - Feature requirements
- `ACCEPTANCE_CRITERIA` - Success criteria

**Loop:** Runs 3 review→refactor cycles automatically

**Tools:** claude-code, copilot-cli

**Conversation:** Separate (each agent starts fresh)

**Documents Generated:**

- `.recipe-docs/analysis.md` - Comprehensive codebase analysis
- `.recipe-docs/plan.md` - Detailed implementation plan

---

### code-review-cycle.yml

Iterative code review and improvement until quality standards are met.

**Flow:** Analyze Changes → Create Checklist → Review → Fix → Verify → Loop until quality threshold (8+/10)

**Key Features:**

- **Analysis Step**: Analyzes all changes and their impact (saved to `.recipe-docs/changes-analysis.md`)
- **Checklist Step**: Creates custom review checklist based on changes (saved to `.recipe-docs/review-checklist.md`)
- **Context Maintenance**: Analysis and checklist guide all review and fix iterations

**Tools:** claude-code, copilot-cli, cursor

**Documents Generated:**

- `.recipe-docs/changes-analysis.md` - Comprehensive change analysis
- `.recipe-docs/review-checklist.md` - Custom review checklist

---

### bug-fix-workflow.yml

Systematic bug fixing with investigation, testing, and verification.

**Flow:** Analyze Bug → Plan Fix → Investigate → Implement → Test → Review → Document

**Key Features:**

- **Analysis Step**: Comprehensive bug analysis with root cause investigation (saved to `.recipe-docs/bug-analysis.md`)
- **Planning Step**: Detailed fix plan with test cases (saved to `.recipe-docs/fix-plan.md`)
- **Context Maintenance**: Analysis and plan documents guide all fix implementation steps

**Variables:**

- `BUG_DESCRIPTION` - Bug details
- `REPRODUCTION_STEPS` - How to reproduce

**Tools:** claude-code, copilot-cli

**Documents Generated:**

- `.recipe-docs/bug-analysis.md` - Comprehensive bug analysis
- `.recipe-docs/fix-plan.md` - Detailed fix plan with tests

## Schema Reference

All recipes must conform to [`schemas/recipe.schema.json`](../schemas/recipe.schema.json).

**Required fields:**

- `id` - Unique kebab-case identifier
- `version` - Semantic version
- `description` - What the recipe accomplishes
- `steps` - Array of workflow steps with agents and tasks

**Optional recipe-level fields:**

- `conversationStrategy` - `'separate'` (default) or `'continue'`
- `toolOptions` - Tool-specific command-line options (e.g., `allowAllTools` for copilot-cli)
- `loop` - Define iterative workflows with `steps` and `maxIterations`
- `variables` - Define inputs for the recipe

**Step fields:**

- `id` - Unique step identifier (required)
- `agent` - Agent ID to use (required)
- `task` - Task description or prompt (required)
- `outputDocument` - Path where AI response should be saved (e.g., `.recipe-docs/analysis.md`)
- `includeDocuments` - Array of document paths to include in step context
- `continueConversation` - Whether to continue same conversation (default: true)
- `waitForConfirmation` - Wait for user confirmation before continuing (default: false)
- `condition` - Conditional execution logic
