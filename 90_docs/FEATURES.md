# ai-tools TypeScript Build System -- Feature Reference

> Comprehensive documentation of the TypeScript/Node.js build system that transforms unified YAML manifests into tool-specific AI coding assistant configurations.

**Version:** 1.1.0
**Runtime:** Node.js >= 18.0.0, TypeScript ES2022, ESM modules
**Test Framework:** Vitest
**CLI Framework:** Commander.js

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Manifest Types](#manifest-types)
  - [Rulesets](#rulesets)
  - [Agents](#agents)
  - [Prompts](#prompts)
  - [Skills](#skills)
  - [Recipes](#recipes)
  - [Projects](#projects)
  - [Features](#features)
  - [Deploy Configs](#deploy-configs)
  - [Eval Suites](#eval-suites)
- [CLI Commands](#cli-commands)
- [Output Targets (Tool Adapters)](#output-targets-tool-adapters)
  - [Windsurf](#windsurf)
  - [Claude Code](#claude-code)
  - [Cursor](#cursor)
  - [GitHub Copilot](#github-copilot)
  - [Copilot CLI](#copilot-cli)
  - [Codex (OpenAI)](#codex-openai)
- [Build Pipeline](#build-pipeline)
- [Resolver System](#resolver-system)
- [Validation System](#validation-system)
- [Configuration System](#configuration-system)
- [External Project Management](#external-project-management)
- [Prompt Library Generation](#prompt-library-generation)
- [Recipe System](#recipe-system)
- [Feature System](#feature-system)
- [Deployment System](#deployment-system)
- [Testing](#testing)
- [Directory Layout](#directory-layout)

---

## Overview

The ai-tools TypeScript build system is the core engine that reads YAML manifest files (rulesets, agents, prompts, skills, recipes, projects, features) and produces tool-specific configuration artifacts for six different AI coding assistants. The principle is "define once, deploy everywhere": a single set of YAML definitions is transformed into the native configuration format each tool expects.

The system supports:
- **6 output targets:** Windsurf, Claude Code, Cursor, GitHub Copilot, Copilot CLI, Codex
- **9 manifest types:** rulesets, agents, prompts, skills, recipes, projects, features, deploy configs, eval suites
- **9 JSON schemas** for validation
- **15 CLI commands** organized into project management, generation, deployment, validation, and documentation workflows
- **Multi-stack projects** with language-aware ruleset filtering
- **Include/exclude filtering** via regex patterns for agents, prompts, rulesets, and recipes
- **Inheritance** for rulesets via `extends`
- **Interactive recipe execution** with loop support and conversation continuity
- **Security scanning** of manifests for leaked credentials
- **Backup and rollback** during deployments

---

## Architecture

### Core Layers

```
CLI Commands (src/cli/commands/)
    |
Core Services (src/core/services/)
    |
Tool Adapters (src/tools/)
    |
Output Files (.windsurf/, .cursor/, .claude/, .github/, AGENTS.md, etc.)
```

### Key Design Patterns

- **Adapter Pattern:** Each output target implements `ToolAdapter` with `generate()` and `generateGlobal()` methods.
- **Singleton Pattern:** `ConfigService` and `ToolRegistry` use static singleton instances.
- **Registry Pattern:** `ToolRegistry` discovers and manages all tool adapters.
- **Service Layer:** Business logic is encapsulated in services (`ResolverService`, `ValidationService`, `LoaderService`, etc.) and shared across adapters and commands.

### Two Adapter Interfaces

The codebase contains two adapter interface generations:

1. **Legacy adapter interface** (`src/tools/base.ts`): Abstract class with `generate(project, outputDir)` and `generateGlobal(outputDir)`. Used by Cursor, Copilot CLI, Codex, and the backup Windsurf/Claude adapters.
2. **New adapter interface** (`src/tools/common/toolAdapter.ts`): Interface with entity-level export methods (`exportPrompt`, `exportAgent`, `exportFeature`, `exportSkill`, `exportRecipe`, `exportProjectGlobal`). Used by the current Windsurf, Claude, and GitHub Copilot adapters (in their root `adapter.ts` files). This uses a `Printer` pattern with `FileContent` and `FileWriter` utilities for consistent output generation.

### Registered Adapters

The `ToolRegistry` (`src/tools/registry.ts`) registers all six adapters on construction:
- `WindsurfAdapter`
- `CursorAdapter`
- `ClaudeAdapter`
- `GitHubCopilotAdapter`
- `CopilotCLIAdapter`
- `CodexAdapter`

---

## Manifest Types

Each manifest type has a corresponding JSON schema in `10_schemas/` and a TypeScript interface in `src/core/models/types.ts`.

### Rulesets

**Location:** `01_rulesets/`
**Schema:** `ruleset.schema.json`

Rulesets are collections of rules (coding standards, conventions) that can be composed via inheritance.

| Field         | Type                | Description                                                   |
| ------------- | ------------------- | ------------------------------------------------------------- |
| `id`          | string (required)   | Unique kebab-case identifier                                  |
| `version`     | string              | Semver version                                                |
| `description` | string              | Human-readable description                                    |
| `extends`     | string[]            | Parent ruleset IDs for inheritance                            |
| `tags`        | string[]            | Tags used for tech-stack filtering (e.g., `kotlin`, `python`) |
| `rules`       | string[] (required) | The actual rule text entries                                  |
| `metadata`    | object              | Author, dates, tags                                           |

**Key behaviors:**
- Inheritance via `extends` is resolved recursively with cycle detection (visited set).
- Tags drive language-aware filtering: a ruleset tagged `kotlin` is excluded from projects that do not list Kotlin in their tech stack.
- In a multi-stack project, language-specific rulesets are excluded from the "global" context and only included for matching stack contexts.

### Agents

**Location:** `05_agents/`
**Schema:** `agent.schema.json`

Agents are complete AI assistant personas with purpose, system prompts, rulesets, capabilities, and model defaults.

| Field                  | Type              | Description                                       |
| ---------------------- | ----------------- | ------------------------------------------------- |
| `id`                   | string (required) | Unique kebab-case identifier                      |
| `version`              | string            | Semver version                                    |
| `purpose`              | string (required) | Brief description of the agent's role             |
| `description`          | string            | Extended description                              |
| `rulesets`             | string[]          | IDs of rulesets to resolve and attach             |
| `prompt.system`        | string            | System prompt / persona                           |
| `prompt.user_template` | string            | User prompt template                              |
| `defaults.model`       | AIModel           | Default model (e.g., `claude-sonnet-4`)           |
| `defaults.temperature` | number            | Sampling temperature                              |
| `defaults.max_tokens`  | number            | Max output tokens                                 |
| `defaults.style`       | enum              | `terse`, `verbose`, `conversational`, `technical` |
| `capabilities`         | string[]          | Required capabilities                             |
| `tools`                | string[]          | Tool references                                   |
| `constraints`          | string[]          | Operating constraints                             |

**Supported AIModel values:** `claude-sonnet-4.5`, `claude-sonnet-4`, `claude-haiku-4.5`, `gpt-5`, `gpt-5.1`, `gpt-5.1-codex-mini`, `gpt-5.1-codex`

### Prompts

**Location:** `03_prompts/`
**Schema:** `prompt.schema.json`

Prompts are reusable templates with variable substitution and optional system/user prompt separation.

| Field         | Type              | Description             |
| ------------- | ----------------- | ----------------------- |
| `id`          | string (required) | Unique identifier       |
| `version`     | string            | Semver version          |
| `description` | string (required) | What the prompt does    |
| `content`     | string            | Main prompt content     |
| `system`      | string            | System-level prompt     |
| `user`        | string            | User-level prompt       |
| `model`       | AIModel           | Suggested model         |
| `includes`    | string[]          | Paths to included files |
| `rules`       | string[]          | Inline guidelines       |
| `variables`   | PromptVariable[]  | Template variables      |
| `tags`        | string[]          | Categorization tags     |
| `outputs`     | object            | Expected output format  |

**Variable substitution:**
- Simple: `{{variable_name}}` is replaced with the provided value.
- Conditional sections: `{{#variable_name}}content{{/variable_name}}` -- included only when the variable has a value.
- The `shared/` subdirectory is skipped during loading (reserved for include fragments).

### Skills

**Location:** `04_skills/`
**Schema:** `skill.schema.json`

Skills represent executable commands or MCP tools with typed inputs, outputs, timeouts, and retry configuration.

| Field         | Type              | Description                                                     |
| ------------- | ----------------- | --------------------------------------------------------------- |
| `id`          | string (required) | Unique identifier                                               |
| `version`     | string            | Semver version                                                  |
| `description` | string (required) | What the skill does                                             |
| `command`     | object            | CLI command definition (`program`, `args`, `cwd`, `env`)        |
| `mcp_tool`    | string            | MCP tool identifier (alternative to command)                    |
| `timeout_sec` | number            | Execution timeout                                               |
| `inputs`      | array             | Input parameters (`name`, `type`, `required`, `description`)    |
| `outputs`     | object            | Output specification (`exit_code`, `stdout`, `stderr`, `files`) |
| `tags`        | string[]          | Categorization                                                  |

Skills are transformed into Anthropic-compatible `SKILL.md` files by the `SkillService`.

### Recipes

**Location:** `08_recipes/`
**Schema:** `recipe.schema.json`

Recipes are multi-step automated workflows that chain agent tasks, support loops, conditions, and conversation continuity.

| Field                  | Type                    | Description                                                   |
| ---------------------- | ----------------------- | ------------------------------------------------------------- |
| `id`                   | string (required)       | Unique identifier                                             |
| `version`              | string (required)       | Semver version                                                |
| `description`          | string (required)       | What the recipe does                                          |
| `tags`                 | string[]                | Tags                                                          |
| `tools`                | string[]                | Supported tools (`claude-code`, `copilot-cli`, `cursor`)      |
| `conversationStrategy` | string                  | `separate` (default) or `continue`                            |
| `toolOptions`          | object                  | Tool-specific options                                         |
| `variables`            | Record<string, string>  | Recipe-level variables with `{{var}}` interpolation           |
| `model`                | AIModel                 | Default model for all steps                                   |
| `steps`                | RecipeStep[] (required) | Ordered execution steps                                       |
| `loop`                 | object                  | Loop configuration with `steps`, `maxIterations`, `condition` |

**RecipeStep fields:**
- `id`: Step identifier
- `agent`: Agent ID to use
- `task`: Task description (supports variable interpolation)
- `model`: Per-step model override
- `inputs`: Step-specific variable values
- `outputDocument`: Path to save the AI response
- `includeDocuments`: Paths to reference documents for context
- `continueConversation`: Whether to continue in the same conversation
- `waitForConfirmation`: Pause for user confirmation
- `condition`: Execution condition (`always`, `on-success`, `on-failure`, `user-decision`, `file_exists`)

**Loop conditions:**
- `max-iterations`: Stop after N iterations
- `user-decision`: Ask the user whether to continue
- `command`: Run a shell command, continue if exit code is 0

### Projects

**Location:** `09_projects/global/` and `09_projects/local/`
**Schema:** `project.schema.json`

Projects are the central configuration unit that ties together tech stacks, conventions, agent/ruleset/prompt/recipe selection, and deployment targets.

| Field           | Type                           | Description                                                       |
| --------------- | ------------------------------ | ----------------------------------------------------------------- |
| `id`            | string (required)              | Unique kebab-case identifier                                      |
| `version`       | string (required)              | Semver version                                                    |
| `name`          | string (required)              | Display name                                                      |
| `description`   | string (required)              | Project description                                               |
| `context`       | object                         | `overview`, `purpose`                                             |
| `tech_stack`    | TechStack                      | Single tech stack                                                 |
| `tech_stacks`   | Record<string, TechStack>      | Named tech stacks for multi-stack projects                        |
| `documentation` | Record<string, string\|Record> | Documentation links                                               |
| `commands`      | Record<string, string\|Record> | Key commands                                                      |
| `conventions`   | object                         | `naming`, `patterns`, `testing`, `structure`, `custom` arrays     |
| `ai_tools`      | AiToolsConfig                  | `model`, `preferred_agents`, `preferred_rulesets`, `custom_rules` |
| `agents`        | IncludeExcludeConfig           | Regex-based include/exclude for agents                            |
| `prompts`       | IncludeExcludeConfig           | Regex-based include/exclude for prompts                           |
| `rulesets`      | IncludeExcludeConfig           | Regex-based include/exclude for rulesets                          |
| `recipes`       | IncludeExcludeConfig           | Regex-based include/exclude for recipes                           |

**Multi-stack projects:** When `tech_stacks` is defined (e.g., `frontend`, `backend`), the system generates separate suffixed outputs per stack context. For example, agents get a `-frontend` and `-backend` variant, each with language-specific rulesets resolved for that stack's languages.

**TechStack fields:** `languages`, `frontend`, `backend`, `database`, `infrastructure`, `tools` (all string arrays).

### Features

**Location:** `09_projects/<project>/features/<feature>/feature.yml`
**Schema:** `feature.schema.json`

Features are project-level scoped contexts representing specific areas of development (e.g., "auth-module", "payment-integration").

| Field            | Type              | Description                                     |
| ---------------- | ----------------- | ----------------------------------------------- |
| `id`             | string (required) | Unique identifier                               |
| `version`        | string (required) | Semver version                                  |
| `name`           | string (required) | Display name                                    |
| `description`    | string (required) | Feature description                             |
| `model`          | AIModel           | Feature-level model override (highest priority) |
| `context`        | object            | `overview`, `architecture`, `dependencies`      |
| `files.patterns` | string[]          | File glob patterns                              |
| `conventions`    | string[]          | Feature-specific conventions                    |
| `recipe`         | object            | Bound recipe (`id`, `context`, `tools`)         |
| `metadata`       | object            | `status`, `owner`, `created`, `updated`, `tags` |

**Validation:** Feature manifests are checked recursively for backtick characters, which cause bash command substitution errors in generated scripts.

### Deploy Configs

**Location:** `09_projects/<project>/deploy.yml` (with optional `deploy.local.yml` overlay)
**Schema:** `deploy.schema.json`

| Field         | Type                      | Description                                                                                  |
| ------------- | ------------------------- | -------------------------------------------------------------------------------------------- |
| `target`      | string (required)         | Target directory path                                                                        |
| `tools`       | string[] (required)       | Which tools to deploy (`windsurf`, `cursor`, `claude-code`, `github-copilot`, `copilot-cli`) |
| `mode`        | enum (required)           | `local` or `manual`                                                                          |
| `auto_commit` | boolean                   | Auto-commit after deploy (local mode)                                                        |
| `git_branch`  | string                    | Branch for deployment                                                                        |
| `backup`      | boolean                   | Enable backup (default: true)                                                                |
| `ai_tools`    | AiToolsConfig             | Override project AI settings                                                                 |
| `agents`      | IncludeExcludeConfig      | Override agent filters                                                                       |
| `prompts`     | IncludeExcludeConfig      | Override prompt filters                                                                      |
| `rulesets`    | IncludeExcludeConfig      | Override ruleset filters                                                                     |
| `recipes`     | IncludeExcludeConfig      | Override recipe filters                                                                      |
| `tech_stack`  | TechStack                 | Override tech stack                                                                          |
| `tech_stacks` | Record<string, TechStack> | Override tech stacks                                                                         |

Deploy config values take precedence over project manifest values via `applyDeployConfig()`. Local overrides (`deploy.local.yml`) are merged on top, enabling per-developer customization without modifying committed files.

### Eval Suites

**Location:** `20_evals/suites/`
**Schema:** `eval.schema.json`

| Field         | Type              | Description                                      |
| ------------- | ----------------- | ------------------------------------------------ |
| `suite`       | string (required) | Suite name                                       |
| `version`     | string            | Semver version                                   |
| `description` | string            | Suite description                                |
| `targets`     | array (required)  | `type`, `id`, `dataset`, `config`                |
| `checks`      | array             | Named checks with `type`                         |
| `budgets`     | object            | `max_tokens`, `max_cost_usd`, `max_duration_sec` |

Eval execution is currently a structural check (verifying datasets exist) with placeholder logic for actual AI evaluation.

---

## CLI Commands

All commands are invoked via `tsx src/cli/index.ts <command>` or through npm scripts.

### `build`

Generate global adapter configurations for the ai-tools repository itself.

```bash
npm run build
# tsx src/cli/index.ts build
```

Iterates all registered adapters and calls `generateGlobal()` for each, writing output to `adapters/<tool-name>/`.

### `generate [project] [--all]`

Generate project-specific configurations for all registered tools.

```bash
npm run gen-project -- my-project
npm run gen-project -- --all
```

- Loads `project.yml` and optionally `deploy.yml` from the project directory.
- Applies deploy config overrides via `applyDeployConfig()`.
- Calls `generate(project, outputDir)` on all registered adapters.
- Outputs to `.output/<project-id>/<tool-name>/`.

### `deploy project <projectId> [-n] [-f] [-i]`

Deploy generated configurations to a target project directory.

```bash
npm run project:deploy -- my-project
npm run project:deploy -- my-project --dry-run
```

**Steps:**
1. Find project directory (configured sources, then external projects).
2. Load `deploy.yml` (with `deploy.local.yml` overlay).
3. Generate outputs for each configured tool.
4. Generate features and merge Windsurf workflows.
5. Verify target directory exists.
6. Backup existing files (keeps latest 10 backups).
7. Copy generated files to target.
8. Optional auto-commit if `auto_commit: true` and `mode: local`.

**Options:**
- `-n, --dry-run`: Simulate without writing files.
- `-f, --force`: Skip confirmation.
- `-i, --interactive`: Enable interactive confirmation.

### `deploy all [-n] [-f] [-i]`

Deploy all projects that have a `deploy.yml`.

### `deploy rollback <projectId> [timestamp]`

Rollback a project deployment (placeholder -- not yet implemented).

### `validate`

Validate all manifests against schemas and rules.

```bash
npm run validate
```

Runs: schema validation, ID uniqueness checks, kebab-case enforcement, semver validation, reference integrity (rulesets, extends), security scanning, include file existence checks, and feature backtick detection.

### `eval [--suite <name>]`

Run evaluation suites.

```bash
npm run eval
npm run eval -- --suite my-suite
```

Loads suites from `20_evals/suites/`, verifies datasets exist, and reports budget constraints.

### `diff --before <file> --after <file> [--format lines|words]`

Compare two files and display a colored diff with statistics.

```bash
npm run diff -- --before old.md --after new.md
```

Reports additions, deletions, unchanged lines, and change ratio percentage.

### `clean`

Remove all generated artifacts.

```bash
npm run clean
```

Deletes: `adapters/`, `.output/`, `20_evals/reports/`.

### `create <name> [--global] [--local] [-d <desc>]`

Create a new project from the template.

```bash
npm run project:create -- my-new-project --description "My project"
```

- Validates and converts name to kebab-case.
- Copies `project.yml` and `deploy.yml` from the `global/template/` directory.
- Sets project metadata (ID, name, description, dates).

### `init <path> [-a <alias>] [-d <desc>] [--no-register] [--global]`

Initialize AI tools in an external project directory.

```bash
npm run project:init -- /path/to/external-project --alias my-proj
```

- Creates `.cleanship-ai-tools/` directory in the target project.
- Generates `project.yml`, `deploy.yml`, and `README.md` from templates.
- Registers the project in the external projects registry.

### `list`

List all available projects (configured and external).

```bash
npm run project:list
```

### `external list|add|remove`

Manage external project registrations.

```bash
npm run project:external -- list
npm run project:external -- add /path/to/project --alias my-proj --global
npm run project:external -- remove my-proj
```

External projects are tracked in `09_projects/projects.global.yml` and `09_projects/projects.local.yml`.

### `features generate <project-id>`

Generate feature snippets and recipe scripts for a project.

```bash
npm run gen-features -- my-project
```

Generates output for GitHub Copilot (Markdown), Windsurf (workflow files), Claude Code (Markdown), and Cursor (JSON).

### `skills generate`

Generate Anthropic-compatible `SKILL.md` files from skill manifests.

```bash
npm run skills
```

Outputs to `adapters/claude-code/skills/<skill-id>/SKILL.md`.

### `docs generate`

Generate Markdown documentation from all agents, prompts, and skills.

```bash
npm run docs
```

Outputs to `docs/AGENTS.md` with a table of contents, grouped by type and tags.

### `prompts library`

Generate a user-friendly Markdown prompt library.

```bash
npm run prompt-library
```

Outputs `PROMPT_LIBRARY.md` with copy-paste ready prompts, grouped by category, with variable documentation and usage guides.

### `prompts html`

Generate an interactive HTML prompt browser.

```bash
npm run prompt-html
```

Outputs `PROMPT_LIBRARY.html` -- a single-file web application with search, tag filtering, variable filling with live preview, and copy-to-clipboard.

### `prompts use <prompt-id>`

Interactively use a prompt: fill variables via terminal input, get a ready-to-paste output.

```bash
npm run use-prompt -- review-code
```

### `recipes list`

List all available recipes with descriptions, supported tools, and step counts.

### `recipes run <recipe-id> [tool]`

Run a recipe interactively. Default tool is `claude-code`.

```bash
npm run recipe:run -- feature-delivery claude-code
```

The runner:
- Loads the recipe and all agents.
- Executes steps sequentially, supporting conversation continuation.
- Handles conditions (`always`, `on-success`, `on-failure`, `user-decision`, `file_exists`).
- Manages document flow (output documents from one step become input for subsequent steps).
- Supports loop iteration with configurable exit conditions.

### `recipes generate <recipe-id> [tool] [output-path]`

Generate an executable bash script for a recipe.

```bash
npm run recipe:generate -- feature-delivery claude-code
```

---

## Output Targets (Tool Adapters)

### Windsurf

**Adapter:** `src/tools/windsurf/backup/adapter.ts` (extends `ToolAdapter` base)
**Output directory:** `.windsurf/`

**Generated files:**
- `.windsurf/rules/project-context.md` -- Always-on rule with project context, tech stack, commands, conventions, documentation.
- `.windsurf/rules/agent-<id>[<suffix>].md` -- Manual-trigger rules for each agent, with resolved rulesets.
- `.windsurf/rules/prompt-<path-based-id>[<suffix>].md` -- Manual-trigger rules for each prompt.
- `.windsurf/.cs.recipes/<recipe>.sh` -- Executable recipe scripts.
- `.windsurf/workflows/feature-<id>.md` -- Feature workflow files (generated during deploy).

**Format:** Markdown files with YAML frontmatter (`trigger: always_on` or `trigger: manual`).

### Claude Code

**Adapter:** `src/tools/claude/backup/adapter.ts` (extends `ToolAdapter` base)
**Output directory:** `.claude/`

**Generated files:**
- `.claude/project-context.json` -- Project rules and context as JSON.
- `.claude/prompts/<namespaced-id>.json` -- Prompt definitions as JSON.
- `.claude/skills.json` -- All skills as JSON array.
- `.claude/skills/<skill-id>/` -- Skill directories with copied implementation files.
- `.claude/agents/<id>[<suffix>].md` -- Agent definitions as Markdown with YAML frontmatter.
- `.claude/.cs.recipes/<recipe>.sh` -- Executable recipe scripts.

### Cursor

**Adapter:** `src/tools/cursor/adapter.ts` (extends `ToolAdapter` base)
**Output directory:** `.cursor/`

**Generated files:**
- `.cursor/recipes.json` -- Agents converted to Cursor recipe format (with rules appended to prompts).
- `.cursor/project-rules.json` -- Flattened project rules and context.
- `.cursor/.cs.recipes/<recipe>.sh` -- Executable recipe scripts.

### GitHub Copilot

**Adapter:** `src/tools/github-copilot/backend/adapter.ts` (extends `ToolAdapter` base)
**Output directory:** `.github/`

**Generated files:**
- `.github/instructions.md` -- Combined instructions file with base content, agents (with resolved rulesets), and project context.
- `.github/prompts/prompt-<path-based-id>[<suffix>].md` -- Prompt files.
- `.github/prompts/agent-<id>[<suffix>].md` -- Agent files in prompts directory (legacy).
- `.github/instructions/agent-<id>[<suffix>].instructions.md` -- Agent files in instructions directory.
- `.github/instructions/feature-<id>.instructions.md` -- Feature instructions.
- `.github/.cs.recipes/<recipe>.sh` -- Executable recipe scripts.

### Copilot CLI

**Adapter:** `src/tools/copilot-cli/adapter.ts` (extends `ToolAdapter` base)
**Output directory:** project root

**Generated files:**
- `AGENTS.md` -- Comprehensive agents file with base content header, dynamically generated agent sections (with resolved rulesets), and project context.
- `.cs.recipes/<recipe>.sh` -- Executable recipe scripts.

### Codex (OpenAI)

**Adapter:** `src/tools/codex/adapter.ts` (extends `ToolAdapter` base)
**Output directory:** project root + `~/.codex/`

**Generated files:**
- `AGENTS.md` -- Agent configuration with project context, tech stacks, and commands.
- `.codex/prompts/prompt-<path-based-id>[<suffix>].md` -- Prompt files as Markdown.
- `.codex/prompts/agent-<id>[<suffix>].md` -- Agent files as Markdown (with persona, constraints, rules).

**Special deployment behavior:** During deploy, prompts are copied to `~/.codex/prompts` (user home directory).

---

## Build Pipeline

### Global Build (`build` command)

1. `ConfigService` determines root directory and adapter output path.
2. `ToolRegistry` instantiates all six adapters.
3. Each adapter's `generateGlobal(outputDir)` is called.
4. Output lands in `adapters/<tool-name>/`.

### Project Generation (`generate` command)

1. Locate `project.yml` by searching configured project sources.
2. Optionally load `deploy.yml` and merge via `applyDeployConfig()`.
3. For each registered adapter, call `generate(project, outputDir)`.
4. Output lands in `.output/<project-id>/<tool-name>/`.

### Full CI Pipeline

```bash
npm run ci  # validate -> build -> eval -> docs
```

---

## Resolver System

The `ResolverService` (`src/core/services/resolver.service.ts`) handles all dependency resolution and filtering.

### Ruleset Resolution

- Recursively resolves `extends` chains with cycle detection.
- Returns a flat, deduplicated list of rules in inheritance order.
- Filters rulesets based on project `rulesets.include` and `rulesets.exclude` regex patterns.
- **Language-aware filtering:** Rulesets tagged with specific languages (e.g., `kotlin`, `python`) are only included when the current context's languages match. A heuristic list of common languages is used, supplemented by all languages defined in the project's tech stacks.
- **Global context exclusion:** In global (non-stack-specific) contexts, rulesets tagged with any language present in any tech stack are excluded to prevent language pollution.

### Agent Resolution

`resolveAllAgents(project)` produces a list of `{ agent, rules, suffix }` tuples:

1. **Global context** (suffix `""`): All included agents with rules resolved against no language filter (language-specific rulesets excluded).
2. **Per-stack context** (suffix `-<stackName>`): All included agents with rules resolved using that stack's languages.

This ensures that a project with `frontend` (TypeScript) and `backend` (Kotlin) tech stacks gets separate agent variants with appropriate rules.

### Include/Exclude Filtering

All filtering uses `IncludeExcludeConfig` with regex patterns:
- If `include` is specified, the item must match at least one include pattern.
- If `exclude` is specified, the item must not match any exclude pattern.
- Patterns are tested via `new RegExp(pattern).test(value)`.

### Model Resolution Hierarchy

`resolveModel()` priority (highest to lowest):
1. Feature-level model
2. Project-level model (`ai_tools.model`)
3. Agent-level model (`defaults.model`)
4. Prompt-level model

---

## Validation System

The `ValidationService` (`src/core/services/validation.service.ts`) performs comprehensive manifest validation.

### Validation Checks

1. **Schema validation:** Each manifest is validated against its JSON schema using Ajv with format support.
2. **ID uniqueness:** All manifest IDs must be globally unique across all types.
3. **Kebab-case enforcement:** IDs must match `/^[a-z0-9]+(-[a-z0-9]+)*$/`.
4. **Semver validation:** Version strings must match `/^\d+\.\d+\.\d+$/`.
5. **Reference integrity:** Referenced rulesets and extended rulesets must exist.
6. **Security scanning:** Manifests are scanned for potential secrets:
   - API keys (32+ character alphanumeric strings)
   - Passwords and secrets in `key: "value"` format
   - Private keys (PEM format)
   - AWS access keys (`AKIA...`)
   - GitHub tokens (`ghp_...`)
   - Excludes known safe patterns (`example`, `sample`, `test`, `placeholder`).
7. **Include validation:** All referenced include files must exist on disk.
8. **Feature backtick detection:** Feature manifests are recursively checked for backtick characters that would cause bash script errors.

### Collected Manifest Sources

- `01_rulesets/`, `04_skills/`, `03_prompts/`, `05_agents/`, `08_recipes/`, `20_evals/suites/`
- Project manifests from all configured project sources
- Deploy configs (`deploy.yml`)
- Feature manifests (`features/*/feature.yml`)
- Skips: `template/` directories, `deploy.local.yml`, `shared/` directories

---

## Configuration System

### Files

- `15_config/config.yml` -- Base configuration (committed).
- `15_config/config.local.yml` -- Local overrides (gitignored).
- `config.yml` and `config.local.yml` at root -- Legacy location.

### Config Merging

Deep merge with array deduplication: arrays are concatenated and deduplicated; objects are recursively merged with override taking precedence.

### Project Sources

Default project sources:
1. `09_projects/global/`
2. `09_projects/local/`

Additional sources from `config.yml` `project_sources` array (supports absolute and relative paths).

### Scopes

- **Global:** Shared across environments, committed to version control.
- **Local:** Per-developer, gitignored.

---

## External Project Management

The `ExternalProjectService` manages projects that live outside the ai-tools repository.

### Registry Files

- `09_projects/projects.global.yml` -- Global external project registry.
- `09_projects/projects.local.yml` -- Local external project registry.

### External Project Record

```yaml
projects:
  - path: /absolute/path/to/project/.cleanship-ai-tools
    alias: my-project
    addedAt: 2026-01-15T10:00:00.000Z
```

External projects are searched during `deploy`, `generate`, and `list` commands alongside configured project sources.

---

## Prompt Library Generation

The `PromptService` provides three output modes for prompts:

### Markdown Library (`PROMPT_LIBRARY.md`)

- Grouped by category (derived from subdirectory structure).
- Each prompt includes: description, tags, version, variables, guidelines, expected output.
- Expandable sections for prompt text and usage examples.
- Includes a usage guide section.

### HTML Browser (`PROMPT_LIBRARY.html`)

A self-contained single-file web application with:
- Real-time search across names, descriptions, and tags.
- Tag-based filtering with toggle buttons.
- Card grid layout with responsive design.
- Modal viewer with live variable filling and preview.
- Copy-to-clipboard functionality.
- Example data auto-fill for testing.
- Stats counter showing visible/total prompts.

### Interactive CLI (`prompts use <id>`)

- Lists available prompts grouped by category if ID not found.
- Collects variable values via terminal input.
- Validates required variables.
- Outputs the filled prompt ready for copy-paste.

---

## Recipe System

### Recipe Script Generation

Recipes are compiled into executable bash scripts with:

- Project context auto-detection (checks `.claude/`, `.cursor/`, `.windsurf/` for context files).
- Variable interpolation from recipe-level and step-level variables.
- Loop support with configurable iteration count.
- Per-step tool-specific command generation:
  - **Claude Code:** `claude --system-prompt "..." -p "task"` (resolves agent system prompts).
  - **Copilot CLI:** Echo-piped commands.
  - **Windsurf/Cursor:** Manual execution instructions.
- Condition checks for step execution.
- Logging to `.recipe-logs/` with timestamped files.

### Interactive Recipe Runner

The `RecipeRunnerService` provides a live interactive execution mode:

- Sequential step execution with agent loading.
- Variable interpolation in task descriptions.
- Document flow: steps can produce documents (`outputDocument`) and consume them (`includeDocuments`).
- Conversation continuation support for Claude Code (reuses conversation ID).
- User confirmation pauses between steps.
- Loop execution with exit conditions.
- Tool-specific execution guidance (prints commands for the user to run).

### Feature-Bound Recipes

Features can reference a recipe, and the system generates customized recipe scripts with:
- Feature context variables injected.
- Model hierarchy resolution (feature > project > agent).
- Per-tool output directories matching the tool's convention.

---

## Feature System

The `FeatureService` generates tool-specific feature documentation from `feature.yml` manifests.

### Per-Tool Output

| Tool           | Output Format                                             | Location                 |
| -------------- | --------------------------------------------------------- | ------------------------ |
| GitHub Copilot | Markdown (`.md` and `.instructions.md`)                   | `.github/instructions/`  |
| Windsurf       | Markdown with YAML frontmatter (`auto_execution_mode: 3`) | `.windsurf/workflows/`   |
| Claude Code    | Markdown                                                  | Feature output directory |
| Cursor         | JSON (`features.json`)                                    | Feature output directory |

### Feature Content

All formats include: name, description, model (if set), context (overview, architecture, dependencies), conventions, and recipe context (implementation steps, acceptance criteria).

During deployment, Windsurf feature workflows are merged from the features output directory into the main `.windsurf/workflows/` output.

---

## Deployment System

### Deploy Flow

1. **Find project** in configured sources or external registries.
2. **Load deploy config** with local overrides.
3. **Generate outputs** for each configured tool.
4. **Generate features** and merge Windsurf workflows.
5. **Verify target** directory exists.
6. **Backup** existing files (configurable, keeps 10 most recent).
7. **Copy** generated artifacts to target, respecting each tool's directory convention.
8. **Auto-commit** (optional, local mode only).

### Tool-to-Directory Mapping

| Tool             | Target Directory                                      |
| ---------------- | ----------------------------------------------------- |
| `windsurf`       | `.windsurf/`                                          |
| `cursor`         | `.cursor/`                                            |
| `claude-code`    | `.claude/`                                            |
| `github-copilot` | `.github/`                                            |
| `copilot-cli`    | `AGENTS.md`, `.cs.recipes/` (project root)            |
| `codex`          | `AGENTS.md` (project root), `~/.codex/prompts` (home) |

### Backup System

- Backups stored in `.backups/<project-id>/<ISO-timestamp>/`.
- Only backs up directories that will be overwritten.
- Automatic cleanup keeps the 10 most recent backups per project.
- Special handling for Copilot CLI (`AGENTS.md`, `.cs.recipes`) and Codex (`AGENTS.md`, `~/.codex/prompts`).

---

## Testing

**Framework:** Vitest with V8 coverage
**Configuration:** `vitest.config.ts`

- Test files: `src/**/*.test.ts`
- Pool: forks (single fork, no parallelism)
- Coverage thresholds: 60% for lines, functions, branches, statements
- Coverage reporters: text, JSON, HTML, LCOV
- Test timeout: 10 seconds, hook timeout: 30 seconds

### Existing Test Files

| Test                           | Location                                              |
| ------------------------------ | ----------------------------------------------------- |
| Generate command               | `src/cli/commands/generate.test.ts`                   |
| Deploy command                 | `src/cli/commands/deploy.test.ts`                     |
| Cursor adapter                 | `src/tools/cursor/adapter.test.ts`                    |
| Codex adapter                  | `src/tools/codex/adapter.test.ts`                     |
| Windsurf backup adapter        | `src/tools/windsurf/backup/adapter.test.ts`           |
| Claude backup adapter          | `src/tools/claude/backup/adapter.test.ts`             |
| GitHub Copilot backend adapter | `src/tools/github-copilot/backend/adapter.test.ts`    |
| Validation service             | `src/core/services/validation.service.test.ts`        |
| Eval service                   | `src/core/services/eval.service.test.ts`              |
| Resolver patterns              | `src/core/services/resolver.service.patterns.test.ts` |
| Resolver reload                | `src/core/services/resolver.service.reload.test.ts`   |
| Deploy schema regex            | `src/core/schemas/deploy.schema.regex-only.test.ts`   |

---

## Directory Layout

```
ai-tools/
  01_rulesets/           # Ruleset YAML manifests (coding standards)
  04_skills/             # Skill YAML manifests (executable tools)
  03_prompts/            # Prompt YAML manifests (reusable templates)
    shared/              # Include fragments (not standalone prompts)
  05_agents/             # Agent YAML manifests (AI personas)
  08_recipes/            # Recipe YAML manifests (multi-step workflows)
  09_projects/           # Project definitions
    global/              # Shared project configs (committed)
      template/          # Template for new projects
    local/               # Per-developer projects (gitignored)
    projects.global.yml  # Global external project registry
    projects.local.yml   # Local external project registry
  07_mcp/                # MCP server configurations
  10_schemas/            # JSON Schema files for all manifest types
  15_config/             # Configuration files
    config.yml           # Base config
    config.local.yml     # Local overrides
  20_evals/              # Evaluation framework
    suites/              # Eval suite definitions
    datasets/            # Test datasets
  90_docs/               # Documentation
  src/                   # TypeScript source
    cli/                 # CLI entry point and commands
      commands/          # Individual command implementations
      index.ts           # Commander.js program setup
    core/                # Core business logic
      models/            # TypeScript interfaces (types.ts)
      services/          # Service classes
      utils/             # Utility functions
      schemas/           # Schema-related tests
    tools/               # Tool adapter implementations
      base.ts            # Abstract ToolAdapter base class
      registry.ts        # ToolRegistry singleton
      common/            # Shared adapter utilities
        toolAdapter.ts   # New ToolAdapter interface
        fileContent.ts   # FileContent builder
        fileWriter.ts    # FileWriter utility
        printer.ts       # Printer interface
        printers.ts      # Printer factory
        promptPrinter.ts # Prompt-to-markdown printer
      windsurf/          # Windsurf adapter
      claude/            # Claude Code adapter
      cursor/            # Cursor adapter
      github-copilot/    # GitHub Copilot adapter
      copilot-cli/       # Copilot CLI adapter
      codex/             # Codex adapter
    types.d.ts           # Type declarations for external modules
  adapters/              # Generated global adapter output (build artifacts)
  .output/               # Generated project-specific output (staging area)
  .backups/              # Deployment backups
  .recipe-docs/          # Recipe document flow storage
  .recipe-logs/          # Recipe execution logs
  build/                 # Legacy build artifacts
  coverage/              # Test coverage reports
```

---

## Kotlin Migration Status (ai-tools-engine)

> Gap analysis comparing this TypeScript build system with the Kotlin rewrite (`ai-tools-engine/`).
> Last updated: 2026-03-28

### Summary

| Status      | Count   | Percentage |
| ----------- | ------- | ---------- |
| Implemented | 28      | 27%        |
| Partial     | 10      | 10%        |
| Missing     | 65      | 63%        |
| **Total**   | **103** |            |

### Feature Matrix

#### Manifest Types

| Feature                                                                         |  TS   |              Kotlin               | Status |
| ------------------------------------------------------------------------------- | :---: | :-------------------------------: | ------ |
| Ruleset manifest (id, description, rules, metadata)                             |  Yes  |                Yes                | ✅      |
| Ruleset `extends` (inheritance with cycle detection)                            |  Yes  |                No                 | ❌      |
| Ruleset `tags` (language-aware filtering)                                       |  Yes  |                No                 | ❌      |
| Agent manifest (id, description, persona, prompt, rulesets, rules, constraints) |  Yes  |                Yes                | ✅      |
| Agent `defaults` (model, temperature, max_tokens, style)                        |  Yes  |                No                 | ❌      |
| Agent `capabilities`, `tools` fields                                            |  Yes  |                No                 | ❌      |
| Agent `prompt.system` / `prompt.user_template` separation                       |  Yes  |                No                 | ❌      |
| Prompt manifest (id, description, content, variables, rules, rulesets)          |  Yes  |                Yes                | ✅      |
| Prompt `system` / `user` separation                                             |  Yes  |                No                 | ❌      |
| Prompt variable substitution (`{{var}}`, conditional `{{#var}}...{{/var}}`)     |  Yes  |                No                 | ❌      |
| Prompt `shared/` subdirectory skip                                              |  Yes  |                No                 | ❌      |
| Skill manifest                                                                  |  Yes  |                No                 | ❌      |
| Recipe manifest                                                                 |  Yes  |                No                 | ❌      |
| Project manifest (basic)                                                        |  Yes  |                Yes                | 🔶      |
| Project `tech_stacks` (multi-stack)                                             |  Yes  |                No                 | ❌      |
| Project `conventions`                                                           |  Yes  |                No                 | ❌      |
| Project `agents`/`prompts`/`rulesets` include/exclude (regex)                   |  Yes  | Partial (tag/whitelist/blacklist) | 🔶      |
| Project `documentation`                                                         |  Yes  |                Yes                | ✅      |
| Feature manifest (basic)                                                        |  Yes  |                Yes                | 🔶      |
| Feature `model`, `files.patterns`, `recipe` binding                             |  Yes  |                No                 | ❌      |
| Deploy config as separate manifest (`deploy.yml`)                               |  Yes  |      No (inline in project)       | 🔶      |
| Eval suite manifest                                                             |  Yes  |                No                 | ❌      |

#### CLI Commands

| Feature                                             |      TS      | Kotlin  | Status |
| --------------------------------------------------- | :----------: | :-----: | ------ |
| CLI framework                                       | Commander.js |  Clikt  | ✅      |
| `build` (global adapter generation)                 |     Yes      |   No    | ❌      |
| `generate [project] [--all]`                        |     Yes      | Partial | 🔶      |
| `deploy project` / `deploy all` / `deploy rollback` |     Yes      |   No    | ❌      |
| `validate` (schema + rules)                         |     Yes      |   No    | ❌      |
| `eval [--suite]`                                    |     Yes      |   No    | ❌      |
| `diff --before --after`                             |     Yes      |   No    | ❌      |
| `clean`                                             |     Yes      |   No    | ❌      |
| `create` / `init` / `list` / `external`             |     Yes      |   No    | ❌      |
| `docs generate`                                     |     Yes      |   No    | ❌      |
| `prompts library` / `prompts html` / `prompts use`  |     Yes      |   No    | ❌      |
| `recipes list` / `recipes run` / `recipes generate` |     Yes      |   No    | ❌      |
| `--dry-run`, `--force`, `--interactive` options     |     Yes      |   No    | ❌      |

#### Output Targets (Tool Adapters)

| Feature                                 |  TS   | Kotlin | Status |
| --------------------------------------- | :---: | :----: | ------ |
| Windsurf adapter                        |  Yes  |  Yes   | ✅      |
| Claude Code adapter                     |  Yes  |  Yes   | ✅      |
| Cursor adapter                          |  Yes  |  Yes   | ✅      |
| GitHub Copilot adapter                  |  Yes  |  Yes   | ✅      |
| Codex adapter                           |  Yes  |  Yes   | ✅      |
| Copilot CLI adapter                     |  Yes  |   No   | ❌      |
| Antigravity adapter (Kotlin-only)       |  No   |  Yes   | ✅      |
| Recipe script generation (all adapters) |  Yes  |   No   | ❌      |
| Skill export (Claude adapter)           |  Yes  |   No   | ❌      |

#### Build Pipeline & Resolver

| Feature                                                     |  TS   |      Kotlin       | Status |
| ----------------------------------------------------------- | :---: | :---------------: | ------ |
| Global build (all adapters)                                 |  Yes  |        No         | ❌      |
| Staging area (`.output/`)                                   |  Yes  | No (direct write) | ❌      |
| Ruleset inheritance resolution (recursive, cycle detection) |  Yes  |        No         | ❌      |
| Ruleset regex pattern matching                              |  Yes  |        Yes        | ✅      |
| Language-aware ruleset filtering                            |  Yes  |        No         | ❌      |
| Agent resolution per-stack (suffix `-<stackName>`)          |  Yes  |        No         | ❌      |
| Model resolution hierarchy                                  |  Yes  |        No         | ❌      |

#### Validation

| Feature                                         |  TS   | Kotlin | Status |
| ----------------------------------------------- | :---: | :----: | ------ |
| JSON schema validation                          |  Yes  |   No   | ❌      |
| ID uniqueness check                             |  Yes  |   No   | ❌      |
| Kebab-case enforcement                          |  Yes  |   No   | ❌      |
| Reference integrity                             |  Yes  |   No   | ❌      |
| Security scanning (API keys, passwords, tokens) |  Yes  |   No   | ❌      |

#### Deployment

| Feature                                             |  TS   | Kotlin | Status |
| --------------------------------------------------- | :---: | :----: | ------ |
| Deploy pipeline (generate → verify → backup → copy) |  Yes  |   No   | ❌      |
| Backup system (10 most recent per project)          |  Yes  |   No   | ❌      |
| Auto-commit after deploy                            |  Yes  |   No   | ❌      |
| Dry-run mode                                        |  Yes  |   No   | ❌      |

### Kotlin-Only Features (not in TypeScript)

| Feature                  | Description                                                            |
| ------------------------ | ---------------------------------------------------------------------- |
| **Antigravity adapter**  | New tool adapter for `.agent/` directory structure                     |
| **HTTP Server (Ktor)**   | REST API server module (`/`, `/health` endpoints)                      |
| **Telemetry module**     | OpenTelemetry-based tracing with span support                          |
| **Structured logging**   | SLF4J with trace/span context                                          |
| **Multi-module Gradle**  | Separate `engine`, `cli`, `server`, `telemetry`, `utils` modules       |
| **Sealed class filters** | Type-safe filter variants (ByTags, ByWhitelistedIds, ByBlacklistedIds) |

### Implementation Priority

| Phase | Focus                   | Key Items                                                                                                             |
| ----- | ----------------------- | --------------------------------------------------------------------------------------------------------------------- |
| 1     | Core Model Completeness | Ruleset `extends` + inheritance, language-aware filtering, agent extended fields, skill manifest, multi-stack support |
| 2     | Deployment Pipeline     | Separate deploy config, staging area, backup system, deploy commands                                                  |
| 3     | Validation              | Schema validation, uniqueness, kebab-case, reference integrity, security scanning                                     |
| 4     | Recipe System           | Recipe manifest, script generation, adapter export, interactive runner                                                |
| 5     | CLI Commands            | `build`, `create`/`init`, `list`/`external`, `clean`, Copilot CLI adapter                                             |
| 6     | Docs & Prompts          | `docs generate`, prompt library (MD/HTML), `prompts use`, variable substitution                                       |
| 7     | Advanced                | Eval suites, diff command, model resolution hierarchy, config deep merge                                              |

> For the full detailed gap analysis, see [FEATURE_COMPARISON.md](FEATURE_COMPARISON.md).
