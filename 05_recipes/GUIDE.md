# Recipe Guide

Complete reference for creating and using multi-agent workflow recipes.

## Table of Contents

- [Core Concepts](#core-concepts)
- [Recipe Structure](#recipe-structure)
- [Workflow Control](#workflow-control)
- [Advanced Features](#advanced-features)
- [Creating Custom Recipes](#creating-custom-recipes)
- [Testing and Debugging](#testing-and-debugging)
- [Best Practices](#best-practices)

## Core Concepts

### What Are Recipes?

Recipes are YAML manifests that define multi-agent workflows. They get compiled into bash scripts that orchestrate multiple AI agents to complete complex tasks.

**Key Principles:**

1. **Automated Execution** - Scripts run fully unattended, no user prompts
2. **Agent Orchestration** - Chain multiple specialized agents together
3. **Context Management** - Control conversation flow between agents
4. **Deployment Model** - Scripts deploy to YOUR project, run on YOUR code

### How Recipes Work

```
┌─────────────────────────────────────────────────────────┐
│  1. Define Recipe (YAML)                                │
│     recipes/my-workflow.yml                             │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  2. Deploy to Project                                   │
│     npm run project:deploy my-app                       │
│     → Generates bash scripts in your app directory      │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  3. Run in Your Project                                 │
│     cd /path/to/your-app                                │
│     ./.cs.recipes/my-workflow.sh                  │
│     → Executes agents on YOUR code                      │
└─────────────────────────────────────────────────────────┘
```

## Recipe Structure

### Minimal Recipe

```yaml
id: simple-review
version: 1.0.0
description: Quick code review
tools:
  - copilot-cli

steps:
  - id: review
    agent: code-reviewer
    task: Review the current changes and provide feedback
```

### Complete Recipe

```yaml
id: feature-delivery
version: 1.0.0
description: Complete feature implementation with review cycles
tags:
  - workflow
  - feature
tools:
  - claude-code
  - copilot-cli

conversationStrategy: separate  # or 'continue'

toolOptions:
  copilot-cli:
    allowAllTools: true  # Enable automated tool execution

variables:
  feature_description: "{{FEATURE_DESCRIPTION}}"
  acceptance_criteria: "{{ACCEPTANCE_CRITERIA}}"

steps:
  - id: implement
    agent: feature-builder
    task: |
      Implement the following feature:
      
      Feature: {{feature_description}}
      
      Acceptance Criteria:
      {{acceptance_criteria}}
    continueConversation: false

  - id: review
    agent: code-reviewer
    task: Review the implementation and provide feedback
    continueConversation: true

  - id: refactor
    agent: feature-builder
    task: Address the review feedback
    continueConversation: true

loop:
  steps:
    - review
    - refactor
  maxIterations: 3

metadata:
  author: your-team
  created: "2025-01-15"
```

### Required Fields

- `id` - Unique kebab-case identifier
- `version` - Semantic version (e.g., "1.0.0")
- `description` - Brief description of what the recipe does (10-500 chars)
- `steps` - Array of workflow steps

### Optional Fields

- `tags` - Array of tags for categorization
- `tools` - Supported tools (claude-code, copilot-cli, cursor)
- `conversationStrategy` - How to handle conversation context
- `variables` - Input variables for the recipe
- `loop` - Loop configuration for iterative workflows
- `metadata` - Additional information (author, created date, etc.)

## Workflow Control

### Conversation Strategies

Control how agents share context:

```yaml
# Each agent starts fresh (default, recommended)
conversationStrategy: separate

# Agents maintain conversation context
conversationStrategy: continue
```

**When to use `separate`:**
- Avoid context window bloat
- Each agent needs fresh perspective
- Working with large codebases
- **Recommended for most workflows**

**When to use `continue`:**
- Agents need to reference previous discussions
- Building complex multi-step reasoning
- Short, tightly coupled workflows

### Loops

Execute steps repeatedly:

```yaml
loop:
  steps:
    - review      # Step IDs to repeat
    - refactor
  maxIterations: 3  # Max times to loop
```

**Important:** All iterations run automatically. No user prompts between iterations.

**Example workflow:**
```
Iteration 1: review → refactor
Iteration 2: review → refactor
Iteration 3: review → refactor
Done
```

### Step Configuration

```yaml
steps:
  - id: step-name
    agent: agent-id              # Which agent to use
    task: Task description       # What the agent should do
    outputDocument: ".recipe-docs/output.md"  # Save AI response to file
    includeDocuments:            # Include these docs in context
      - ".recipe-docs/analysis.md"
      - ".recipe-docs/plan.md"
    continueConversation: true   # Continue from previous step
    waitForConfirmation: false   # Wait for user (not implemented)
```

**`outputDocument`:**
- Path where the AI's complete response should be saved
- Typically in `.recipe-docs/` directory
- Used for analysis, plans, checklists, etc.
- Document is cached and available to subsequent steps

**`includeDocuments`:**
- Array of document paths to include in the step's context
- Documents are appended to the task description
- Ensures all agents work from the same analysis/plan
- Essential for maintaining consistency across steps

**`continueConversation`:**
- Only matters when `conversationStrategy: continue`
- `true` - Continue conversation from previous step
- `false` - Start fresh conversation
- First step always starts fresh

### Variables

Define reusable inputs:

```yaml
variables:
  feature_name: "{{FEATURE_NAME}}"
  file_path: "{{FILE_PATH}}"
  
steps:
  - id: implement
    task: Implement {{feature_name}} in {{file_path}}
```

**Usage in scripts:**
```bash
FEATURE_NAME="Authentication" \
FILE_PATH="src/auth.ts" \
./.cs.recipes/my-recipe.sh
```

**With feature binding:**
```yaml
# features/auth/feature.yml
recipe:
  context:
    feature_name: "User Authentication"
    file_path: "src/auth.ts"
```

Variables are automatically pre-filled in feature-bound scripts.

## Advanced Features

### Conditions (Schema Support Only)

**Note:** Condition checking is defined in the schema but basic implementation only. Advanced condition types are not fully implemented yet.

```yaml
steps:
  - id: quality-check
    agent: code-reviewer
    task: Final review. Respond "APPROVED" if quality standards met.
    condition:
      type: on-success
      check:
        type: contains
        value: "APPROVED"
```

Currently implemented:
- Basic `contains` check on response output
- Script exits with error if condition not met

### Tool Options

Configure tool-specific command-line options for automated execution.

**Note:** Currently only `copilot-cli` supports tool options. Claude Code and Cursor use project-level configuration files or interactive prompts instead.

```yaml
toolOptions:
  copilot-cli:
    allowAllTools: true        # --allow-all-tools (recommended for automation)
    allowAllPaths: true         # --allow-all-paths (careful with security)
    disallowTempDir: false      # --disallow-temp-dir
    addDirs:                    # --add-dir (multiple)
      - /path/to/project
      - /path/to/workspace
    allowTools:                 # --allow-tool (specific tools only)
      - "write"
      - "shell(git:*)"
    denyTools:                  # --deny-tool (takes precedence)
      - "shell(rm *)"
```

**Common configurations:**

Fully automated (no prompts):
```yaml
toolOptions:
  copilot-cli:
    allowAllTools: true
```

Restricted automation (only file editing):
```yaml
toolOptions:
  copilot-cli:
    allowTools:
      - "write"
      - "read"
```

Project-specific paths:
```yaml
toolOptions:
  copilot-cli:
    allowAllTools: true
    addDirs:
      - /home/user/my-project
```

**Security note:** Use `allowAllTools: true` only for trusted recipes running on trusted codebases. Consider using `allowTools` to whitelist specific operations for better security.

### Tool-Specific Behavior

**copilot-cli:**

```bash
# Separate conversations
echo "@agent-name task" | copilot

# Continue conversation
echo "@agent-name task" | copilot --continue

# With tool options (generated automatically)
echo "@agent-name task" | copilot --allow-all-tools --add-dir "/path"
```

**claude-code:**

```bash
# Separate conversations
claude --agent agent-name "task"

# Continue conversation
claude -c $CONVERSATION_ID --agent agent-name "task"
```

**cursor:**

- Manual execution required
- Scripts generate comments with instructions
- Less automation support

## Creating Custom Recipes

### Step 1: Create Recipe Manifest

```bash
# Create file
vim recipes/my-workflow.yml
```

**Follow the document-first pattern:**

```yaml
id: my-workflow
version: 1.0.0
description: Brief description of what this does
tools:
  - copilot-cli

steps:
  # 1. Analysis step - understand the problem
  - id: analyze
    agent: project-planner
    task: |
      Analyze the codebase to understand...
      Document all relevant areas, dependencies, and considerations.
    outputDocument: ".recipe-docs/analysis.md"
    
  # 2. Planning step - create detailed plan
  - id: plan
    agent: project-planner
    task: |
      Create a detailed action plan based on the analysis.
      Include step-by-step approach and expected outcomes.
    outputDocument: ".recipe-docs/plan.md"
    includeDocuments:
      - ".recipe-docs/analysis.md"
  
  # 3. Implementation - execute the plan
  - id: implement
    agent: feature-builder
    task: |
      Implement according to the plan.
      Follow the analysis and plan documents carefully.
    includeDocuments:
      - ".recipe-docs/analysis.md"
      - ".recipe-docs/plan.md"
      
  # 4. Review - verify against plan
  - id: review
    agent: code-reviewer
    task: |
      Review the implementation.
      Verify it matches the plan and addresses the analysis.
    includeDocuments:
      - ".recipe-docs/analysis.md"
      - ".recipe-docs/plan.md"
```

### Step 2: Validate

```bash
npm run validate
```

This checks:
- Schema compliance
- Agent references exist
- Variable syntax is correct
- Step IDs are unique

### Step 3: Test

```bash
# Test in ai-tools repo (for development only)
npm run recipe:run my-workflow copilot-cli

# Real usage: deploy to project
npm run project:deploy my-project
cd /path/to/my-project
./.cs.recipes/my-workflow.sh
```

### Step 4: Deploy

Add to your project configuration and deploy:

```bash
npm run project:deploy my-project
```

Scripts are generated in your project's `.cs.recipes/` directory.

## Testing and Debugging

### List Available Recipes

```bash
npm run recipe:list
```

### Test Recipe Execution

```bash
# Run in ai-tools repo (for testing only)
npm run recipe:run feature-delivery copilot-cli
```

**Warning:** This runs on the ai-tools repo itself. For real usage, deploy to your project.

### Debug Generated Scripts

```bash
# View generated script
cat /path/to/your-project/.cs.recipes/my-recipe.sh

# Run with bash tracing
bash -x ./.cs.recipes/my-recipe.sh

# Check for errors
set -e  # Already in generated scripts
```

### Common Issues

**Script not found:**
```bash
# Did you deploy?
npm run project:deploy my-project

# Check project's .cs.recipes/ directory
ls -la /path/to/your-project/.cs.recipes/
```

**Variables not interpolating:**
```bash
# Export variables before running
export FEATURE_DESCRIPTION="My feature"
./.cs.recipes/feature-delivery.sh

# Or inline
FEATURE_DESCRIPTION="My feature" ./.cs.recipes/feature-delivery.sh
```

**Agent not found:**
```bash
# Verify agent exists
cat agents/my-agent.yml

# Check agent ID matches recipe reference
grep "agent:" recipes/my-recipe.yml
```

## Best Practices

### Recipe Design

1. **Document-First Workflow** - Start with analysis and planning steps that output to documents
2. **Single Responsibility** - Each recipe should accomplish one clear goal
3. **Atomic Steps** - Break work into discrete, verifiable steps
4. **Clear Tasks** - Be specific about what each agent should do
5. **Appropriate Loops** - Use loops for review cycles, not general iteration
6. **Separate Conversations** - Default to `conversationStrategy: separate`
7. **Context Maintenance** - Include analysis/plan documents in all subsequent steps

### Document-First Workflow Pattern

**Every recipe should follow this pattern:**

1. **Analysis Step** - Understand the problem/codebase
   - Agent: Usually `project-planner`, `bug-fixer`, or `code-reviewer`
   - Output: `.recipe-docs/analysis.md` or `.recipe-docs/changes-analysis.md`
   - Purpose: Comprehensive understanding before action

2. **Planning Step** - Create detailed action plan
   - Agent: Usually `project-planner`, `bug-fixer`, or `code-reviewer`
   - Output: `.recipe-docs/plan.md` or `.recipe-docs/fix-plan.md`
   - Input: Include the analysis document
   - Purpose: Structured approach before implementation

3. **Implementation Steps** - Execute the plan
   - Agent: Usually `feature-builder`, `refactoring-specialist`, etc.
   - Input: Include both analysis and plan documents
   - Purpose: Implement according to well-thought-out plan

4. **Review/Verification Steps** - Ensure quality
   - Agent: Usually `code-reviewer`, `tdd-navigator`, etc.
   - Input: Include analysis and plan documents
   - Purpose: Verify implementation matches plan and requirements

**Example Pattern:**

```yaml
steps:
  - id: analyze
    agent: project-planner
    task: Analyze the codebase for implementing {{feature_name}}
    outputDocument: ".recipe-docs/analysis.md"
    
  - id: plan
    agent: project-planner
    task: Create detailed implementation plan for {{feature_name}}
    outputDocument: ".recipe-docs/plan.md"
    includeDocuments:
      - ".recipe-docs/analysis.md"
    
  - id: implement
    agent: feature-builder
    task: Implement {{feature_name}} following the plan
    includeDocuments:
      - ".recipe-docs/analysis.md"
      - ".recipe-docs/plan.md"
    
  - id: review
    agent: code-reviewer
    task: Review implementation against the plan
    includeDocuments:
      - ".recipe-docs/analysis.md"
      - ".recipe-docs/plan.md"
```

**Benefits:**
- **Consistency** - All agents reference the same source of truth
- **Quality** - Better implementations from well-researched plans
- **Traceability** - Clear record of what was analyzed vs. implemented
- **Debugging** - Easy to see where implementation deviated from plan

### Naming Conventions

- Recipe IDs: `kebab-case` (e.g., `feature-delivery`, `bug-fix-workflow`)
- Step IDs: `kebab-case` (e.g., `implement`, `review`, `quality-check`)
- Variables: `snake_case` (e.g., `feature_description`, `file_path`)

### Task Writing

**Good tasks:**
```yaml
task: |
  Review the authentication implementation in src/auth/.
  
  Focus on:
  - Password hashing security
  - JWT token expiration
  - Input validation
  
  Provide specific, actionable feedback.
```

**Poor tasks:**
```yaml
task: Review the code  # Too vague
```

### Loop Guidelines

- **Max 3-5 iterations** - Prevent infinite loops
- **Clear exit criteria** - Define what "done" means
- **Progress tracking** - Each iteration should make progress
- **Use for refinement** - Not for discovery or implementation

### Variable Guidelines

- **Use descriptive names** - `feature_description` not `desc`
- **Provide defaults** - When sensible
- **Document expected format** - In recipe description
- **Use feature binding** - For predefined workflows

## Schema Reference

Full schema: [`../schemas/recipe.schema.json`](../schemas/recipe.schema.json)

### Top Level

```typescript
{
  id: string                    // Required: kebab-case
  version: string                // Required: semver
  description: string            // Required: 10-500 chars
  tags?: string[]
  tools?: string[]
  conversationStrategy?: 'separate' | 'continue'
  toolOptions?: {
    'copilot-cli'?: {
      allowAllTools?: boolean
      allowAllPaths?: boolean
      addDirs?: string[]
      allowTools?: string[]
      denyTools?: string[]
      disallowTempDir?: boolean
    }
    // Note: claude-code and cursor don't support tool options
  }
  variables?: Record<string, string>
  steps: Step[]                  // Required: at least 1 step
  loop?: LoopConfig
  metadata?: Record<string, any>
}
```

### Step

```typescript
{
  id: string                     // Required: kebab-case
  agent: string                  // Required: agent ID
  task: string                   // Required: task description
  outputDocument?: string        // Save AI response to this file path
  includeDocuments?: string[]    // Include these docs in step context
  continueConversation?: boolean // Default: true
  waitForConfirmation?: boolean  // Not fully implemented
  condition?: Condition          // Basic support
}
```

### Loop

```typescript
{
  steps: string[]                // Required: step IDs to loop
  maxIterations?: number         // Default: 3
  condition?: LoopCondition      // Optional
}
```

## Examples

See these recipes for reference:

- [`feature-delivery.yml`](feature-delivery.yml) - Complete feature workflow with review cycles
- [`code-review-cycle.yml`](code-review-cycle.yml) - Iterative quality improvement
- [`bug-fix-workflow.yml`](bug-fix-workflow.yml) - Systematic bug fixing

## See Also

- [README.md](README.md) - Quick start and basic usage
- [../schemas/recipe.schema.json](../schemas/recipe.schema.json) - Complete schema definition
- [../agents/](../agents/) - Available agents for recipes
- [../docs/STYLE_GUIDE.md](../docs/STYLE_GUIDE.md) - Project conventions
