# Planned features

## ✅ Implemented

### Miscs (v1.1.0)

- ✅ clean - Cleans generated files and rebuilds them (`npm run clean`)
- ✅ project - Deploy all available projects at once (`npm run project:deploy-all`)

### Large features (v1.1.0)

- ✅ Feature-specific context system in `06_projects/<global|local>/project-name/features/feature-name`
- ✅ Feature manifests with snippets and conventions
- ✅ Generates feature-specific context for all tools (GitHub Copilot, Windsurf, Claude Code, Cursor)
- ✅ Feature generation integrated into project generation workflow

### Project specific (v1.1.0)

- ✅ Support for external projects via `.cleanship-ai-tools` folders
- ✅ Project registry system with `projects.global.yml` (versioned) and `projects.local.yml` (gitignored)
- ✅ External project management commands (`npm run project:external`)
- ✅ External projects integrated into all project commands (list, generate, deploy)

### Copilot CLI support

✅ **Fully Supported**

GitHub Copilot CLI (standalone `copilot` command) is now a fully supported tool with native custom instructions support via `AGENTS.md`.

**Generated artifacts:**

- `AGENTS.md` - Complete file with all agents, prompts, rules, and project-specific context

**Usage:**

```bash
# Build adapters
npm run build

# Generate for project
npm run project:generate <project-id> copilot-cli

# Copy to your project and use
cp .output/<project-id>/copilot-cli/AGENTS.md /path/to/project/
cd /path/to/project
copilot  # Automatically loads AGENTS.md
```

See [`docs/TOOLS.md`](TOOLS.md) for details.

### Multi-agent support (v1.2.0)

✅ **Fully Implemented**

Multi-agent recipes enable orchestrating multiple AI agents in sequence to accomplish complex workflows.

**Key features:**

- Recipe manifests define multi-step workflows with agents, tasks, and conditions
- **Fully automated execution** - Generate and run scripts with single command
- **Interactive mode** - Manual control with quality gates and loops
- Tool integration with Claude Code, Copilot CLI, and Cursor
- Built-in recipes: feature-delivery, code-review-cycle, bug-fix-workflow
- CI/CD compatible with generated bash scripts

**Generated artifacts:**

- `05_recipes/*.yml` - Recipe manifest definitions
- `10_schemas/recipe.schema.json` - JSON Schema for validation
- `.output/scripts/*.sh` - Auto-generated executable scripts

**Usage (Automated):**

```bash
# Generate executable script
npm run recipe:generate feature-delivery claude-code

# Run with variables
FEATURE_DESCRIPTION="Add auth" \
.output/scripts/feature-delivery-claude-code.sh
```

**Usage (Interactive):**

```bash
# Step-by-step guidance
npm run recipe:run feature-delivery claude-code
```

See [`05_recipes/README.md`](../05_recipes/README.md) for complete documentation.

### Misc

- [x] Rename cleanship-recipes to .cs.recipes
- [x] switch --no-confirm to --interactive for step-by-step with confirmation (by default doesn't require confirmation)
- [x] For all recipes, the first step needs to be: analysis of the relevant content with output to a document, then detailed plan of the change with output to a document; these documents then need to be included in context and maintained
- [x] Organization agent -> good in organizing files, folders, documents, topics, etc.
- [x] Organize this repository -> add enumerated prefix for folders (e.g. 01_rulepacks, 02_skills, ..., 90_docs)

### Model selection (v1.3.0)

✅ **Fully Implemented**

Hierarchical model configuration with priority-based resolution.

**Key features:**

- Prompts can define default model
- Agents can override prompt defaults
- Projects can override agent defaults
- Features have highest priority (override everything)
- Supported models: claude-sonnet-4.5, claude-sonnet-4, claude-haiku-4.5, gpt-5, gpt-5.1, gpt-5.1-codex-mini, gpt-5.1-codex

**Priority Hierarchy (highest to lowest):**

1. Feature model (feature.yml)
2. Project model (project.yml → ai_tools.model)
3. Agent model (agent.yml → defaults.model)
4. Prompt model (prompt.yml → model)

**Usage:**

```yaml
# In a prompt
model: claude-sonnet-4

# In an agent
defaults:
  model: claude-sonnet-4.5

# In a project
ai_tools:
  model: gpt-5.1

# In a feature (highest priority)
model: gpt-5.1-codex
```

The effective model is displayed in generated outputs for all tools (GitHub Copilot, Windsurf, Claude Code, Cursor).

## Fully automated tests

- unit tests
- integration tests
- end-to-end (deployment) tests

## Other features NOT NOW

- [ ] More tool adapters (Cody, Neovim, JetBrains AI)
- [ ] Prompt marketplace/sharing platform
- [ ] Advanced eval metrics (BLEU, ROUGE, custom)
- [ ] Cost analytics dashboard
- [ ] Version control integration (Git hooks for prompt changes)
- [ ] Prompt optimization suggestions (A/B testing automation)
