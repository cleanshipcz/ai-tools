# Project Specification (Current Feature Set)
Comprehensive description of the AI Tools repository as it exists today. Use this as a reference if rewriting the system from scratch.

## Purpose and Scope
- Single source of truth in YAML for prompts, agents, rulepacks, skills, recipes, projects, and features.
- CLI generates tool-specific outputs for Windsurf, Cursor, Claude Code, GitHub Copilot, Copilot CLI, and Codex.
- Deploy pipeline copies staged outputs into target projects with backups and optional auto-commit.
- Validation, doc generation, prompt libraries, skill generation, recipe runners, and utilities (diff/clean/eval) are built-in.

## High-Level Architecture
- **Manifests (YAML):** Located under numbered directories (`01_rulepacks`, `02_skills`, `03_prompts`, `04_agents`, `05_recipes`, `06_projects`, `07_mcp`).
- **CLI (TypeScript):** Entry `src/cli/index.ts` wiring commander subcommands to services.
- **Core Services:** Config, loader, resolver, validation, deployment, feature generation, recipe generation, prompt library generation, docs generation, skill generation, diff/clean/eval utilities.
- **Tool Adapters:** Per-tool generators in `src/tools/` producing staged outputs under `.output/<project>/...`.
- **Deployment:** Copies staged outputs to `deploy.yml` target with backups in `.backups/<project>/`.
- **Tests:** Vitest-based suite (see scripts and services tests).

## Data Model (types)
- **Rulepack:** `id`, `rules[]`, optional `extends`, `tags`, `version`, `metadata`.
-, **Agent:** `id`, `purpose`, `rulepacks[]`, `prompt.system`, defaults (model/temperature), `constraints`, `capabilities`, `tools`.
- **Prompt:** `id`, `description`, `content/system/user`, `variables[]`, `rules[]`, `tags`.
- **Skill:** `id`, `description`, `command`/`mcp_tool`, `inputs`/`outputs`, `tags`.
- **Project:** `id`, `name`, `description`, `context`, `tech_stack`/`tech_stacks`, `commands`, `conventions`, `ai_tools`, include/exclude filters for prompts/agents/rulepacks/recipes.
- **Feature:** `id`, `name`, `description`, `context`, `conventions`, `model`, `recipes[]`, `snippets`, `variables` (see feature service expectations).
- **DeployConfig:** `target`, `tools[]`, `mode`, `backup`, `auto_commit`, `ai_tools`, include/exclude filters, stack overrides.
- **Recipe:** `id`, `description`, `steps[]`, `tools[]`, `loop`, `variables`, `toolOptions`, `conversationStrategy`.

## CLI Surface (scripts map to commands)
- Validation: `validate` → schema + security + reference checks.
- Build: `build` → global adapters (non-project-specific) using ToolRegistry.
- Generate: `generate [--all|<project>]` → stage outputs under `.output/<project>/` for all adapters.
- Deploy: `deploy project <id>` / `deploy all` → generate + copy to target with backups, feature merge for Windsurf, optional auto-commit.
- Create: `create <name>` → scaffold managed project from template into `06_projects/local|global`.
- Init: `init <path>` → scaffold `.cleanship-ai-tools` in external repo and optionally register.
- List: `list` → list managed + external projects.
- External: `external list|add|remove` → manage external project registry.
- Diff: `diff --before --after [--format lines|words]` → human-friendly diff.
- Clean: `clean` → remove generated artifacts (`.output`, adapters, prompt libs, recipe docs/logs).
- Docs: `docs generate` → build `docs/AGENTS.md` from manifests.
- Prompts: `prompts library` (Markdown), `prompts html` (interactive browser), `prompts use <id>` (interactive filler).
- Skills: `skills generate` → Anthropic `SKILL.md` generation into `adapters/claude-code/skills`.
- Recipes: `recipes list|run|generate` → enumerate, run interactively, or produce bash scripts.
- Features: `features generate <project>` → emit feature snippets/workflows/scripts (also run inside deploy).
- Eval: `eval [--suite]` → run evaluation suites if configured.

## Core Services (behavioral summary)
- **ConfigService:** Resolves repo root, directory names, config merge (`config.yml` + `config.local.yml`), project sources default to `06_projects/global` and `06_projects/local`, provides path helpers.
- **LoaderService:** YAML loading, directory scanning for manifests (used throughout).
- **ResolverService:** Include/exclude filtering for prompts/agents/rulepacks/recipes against project/deploy config and tech stacks; resolves rulepack inheritance.
- **ValidationService:** Loads JSON Schemas from `10_schemas`, validates manifests, enforces semver and kebab-case IDs, reference checks (rulepacks exist, etc.), security regex scan for secrets, includes validation for features/deploy/project references.
- **DocsService:** Generates `docs/AGENTS.md` from loaded agents/prompts/skills.
- **PromptService:** Builds Markdown and HTML prompt libraries, interactive prompt filler (`use`), groups by category inferred from path, supports variable substitution.
- **SkillService:** Converts `02_skills` YAML into Anthropic `SKILL.md` folders under `adapters/claude-code/skills`, with inputs/outputs/env/timeout notes.
- **FeatureService:** Loads feature manifests under a project, resolves model priority (feature > project > agent), generates per-tool feature outputs, merges Windsurf workflows into main output during deploy, binds features to recipes.
- **RecipeService / RecipeRunnerService:** Loads recipes (`05_recipes`), filters by tool, generates `.cs.recipes/*.sh` scripts per tool and per tech stack suffix, supports interactive run with tool selection (claude-code/copilot-cli/cursor), supports loops/variables/tool options.
- **ExternalProjectService:** Registers external project paths (global/local registries), used by `init`, `deploy`, `list`.
- **DiffService / CleanService / EvalService:** Utility functions for diffing, cleaning generated files, and running evaluation suites.

## Tool Adapters (staged outputs)
- **WindsurfAdapter:** `.windsurf/` with `project-context.md`, agent rules (resolved rulepacks), prompt rules (path-aware filenames), stack-specific suffixes, recipes via `.cs.recipes/`, feature workflows merged during deploy.
- **CursorAdapter:** `.cursor/recipes.json` (agents as recipes), `.cursor/project-rules.json` (conventions + ai_tools rules), `.cs.recipes/`.
- **ClaudeAdapter (claude-code):** `.claude/` with prompts as JSON (path-derived IDs), skills copied from `02_skills`, agents as markdown with resolved rules, `project-context.json`, `.cs.recipes/`.
- **GitHubCopilotAdapter:** `.github/` with `instructions.md` (agents + project context), prompts and agents as markdown, `.cs.recipes/`.
- **CopilotCLIAdapter:** Root `AGENTS.md` (agents + project context + rules), `.cs.recipes/`.
- **CodexAdapter:** Root `AGENTS.md` and `.codex/prompts/`; deploy copies prompts to `~/.codex/prompts`.

## Project and Deploy Flow
1. **Load project manifest** (`project.yml`) from configured sources; optional merge of `deploy.yml` overrides via `applyDeployConfig`.
2. **Filtering:** Include/exclude filters for prompts, agents, rulepacks, recipes; tech stack contexts (global + `tech_stacks` with suffixes) applied when generating prompts/recipes.
3. **Generation:** For each selected tool, adapters write staged outputs into `.output/<project>/...`.
4. **Features:** FeatureService generates feature snippets/workflows/scripts into `.output/<project>/features/...`; Windsurf workflows are merged into main `.windsurf/workflows` during deploy if tool enabled.
5. **Deployment:** `copyToTarget` maps tool names to final paths (`.windsurf`, `.cursor`, `.claude`, `.github`, `AGENTS.md`, `.cs.recipes`, `.codex/prompts`) and copies to `target` with recursive directory copy. Backups stored under `.backups/<project>/<timestamp>/` before overwrite; old backups pruned to keep latest 10. Codex deploy additionally syncs `~/.codex/prompts`.
6. **Auto-commit:** If `auto_commit` and `mode=local`, runs `git add .` + commit in target.

## Prompt and Agent Handling
- Agents resolved via ResolverService (include/exclude + rulepack resolution). Rulepacks flatten inheritance for adapters.
- Prompts filtered by project rules using path map; stack-specific variants generated with suffixes.
- Prompt library generation skips shared snippets and groups by category (first path segment).

## Feature System
- Feature manifests live under `06_projects/<scope>/<project>/features/<feature>/feature.yml`.
- Generation outputs:
  - GitHub Copilot feature markdown (`feature-<id>.md`).
  - Windsurf workflows with metadata header (auto_execution_mode, description) and feature context sections.
  - Claude Code/Cursor feature data (models, context) included via recipes/snippets; scripts created via RecipeService with feature bindings.
- Model resolution order: feature → project.ai_tools.model → agent defaults → prompt.

## Recipe System
- Recipes defined in `05_recipes/*.yml`; include steps with agents, task text, optional model overrides, variables, conversation strategy, and loops.
- Script generation targets tool-specific paths:
  - claude-code → `.claude/.cs.recipes/*.sh`
  - copilot-cli → `.cs.recipes/*.sh`
  - cursor → `.cursor/.cs.recipes/*.sh`
  - github-copilot → `.github/.cs.recipes/*.sh`
  - windsurf → `.windsurf/.cs.recipes/*.sh`
- Scripts load project context from tool outputs (`project-context.json`, `project-rules.json`, `project-context.md`) and support suffix variants per tech stack.

## Validation and Security
- JSON Schema enforcement for all manifest types; semver and kebab-case checks.
- Reference checks ensure linked rulepacks/agents/prompts/recipes exist.
- Security scan for common secret patterns across manifests.
- Feature content checks to ensure expected structure.

## Configuration and Sources
- Config files in `15_config/` (`config.yml`, `config.local.yml`) merged by ConfigService.
- `project_sources` config augments default sources (`06_projects/global`, `06_projects/local`).
- Deploy targets are absolute or relative paths resolved from repo root; `deploy.local.yml` is ignored by validators and merging.

## Generated Artifacts and Cleanup
- Staged outputs live in `.output/<project>/`; global adapters in `adapters/`.
- Prompt libraries generated at repo root (`PROMPT_LIBRARY.md`, `PROMPT_LIBRARY.html`).
- Recipe run logs stored in `.recipe-logs/` (created by runner scripts).
- Cleanup command removes staged outputs, adapters, prompt libraries, recipe docs/logs.

## Testing and Tooling
- Tests: Vitest (`npm test`, `test:watch`, `test:coverage`), coverage via `@vitest/coverage-v8`.
- TypeScript project (`tsconfig.json`), ES modules.
- Formatting: Prettier (`lint`, `format` scripts).

## Rebuild-from-Scratch Checklist
1. Recreate manifest schemas and loaders (YAML parsing, path scanning, schema validation, secret scan).
2. Implement ConfigService with merged config and project source resolution.
3. Implement ResolverService for include/exclude filters and rulepack inheritance.
4. Port core services: Validation, Docs, Prompt library (md/html + filler), Skills generator, Feature generator, Recipe generator/runner, Diff/Clean/Eval utilities.
5. Implement ToolRegistry and adapters for Windsurf, Cursor, Claude Code, GitHub Copilot, Copilot CLI, Codex with correct output shapes and suffix handling.
6. Implement CLI commands (commander) mirroring current surface and options.
7. Implement deploy flow: generate → verify target → backup → copy → optional commit; handle Codex home-copy and Windsurf feature merge.
8. Preserve model priority (feature → project → agent → prompt) and filtering semantics.
9. Restore directory conventions (`01_`…`15_`, `.output`, `.backups`, `.recipe-*`, `adapters`).
10. Recreate tests for services, generators, and CLI behaviors.***
