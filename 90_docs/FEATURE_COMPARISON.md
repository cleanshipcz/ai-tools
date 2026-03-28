# Feature Comparison: TypeScript vs Kotlin (ai-tools-engine)

> Comprehensive gap analysis comparing the TypeScript build system with the Kotlin rewrite.
> Generated: 2026-03-28

---

## Summary Statistics

| Status | Count | Percentage |
|--------|-------|------------|
| Implemented | 28 | 27% |
| Partial | 10 | 10% |
| Missing | 65 | 63% |
| **Total** | **103** | |

---

## 1. Feature Matrix

### 1.1 Manifest Types

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| Ruleset manifest (`id`, `description`, `rules`, `metadata`) | Yes | Yes | Implemented |
| Ruleset `extends` (inheritance) | Yes | No | Missing |
| Ruleset `tags` (language-aware filtering) | Yes | No (tags exist on metadata but not used for language filtering) | Missing |
| Agent manifest (`id`, `description`, `persona`, `prompt`, `rulesets`, `rules`, `constraints`) | Yes | Yes | Implemented |
| Agent `defaults` (model, temperature, max_tokens, style) | Yes | No | Missing |
| Agent `capabilities`, `tools` fields | Yes | No | Missing |
| Agent `prompt.system` / `prompt.user_template` separation | Yes | No (single `prompt` string) | Missing |
| Prompt manifest (`id`, `description`, `content`, `variables`, `rules`, `rulesets`) | Yes | Yes | Implemented |
| Prompt `system` / `user` separation | Yes | No | Missing |
| Prompt `model` field | Yes | No | Missing |
| Prompt `includes` (file includes) | Yes | No | Missing |
| Prompt `outputs` (format, examples) | Yes | Yes | Implemented |
| Prompt variable substitution (`{{var}}`, conditional `{{#var}}...{{/var}}`) | Yes | No | Missing |
| Prompt `shared/` subdirectory skip | Yes | No | Missing |
| Skill manifest | Yes | No | Missing |
| Recipe manifest | Yes | No | Missing |
| Project manifest (basic: id, description, context, deploy) | Yes | Yes | Partial |
| Project `tech_stack` / `tech_stacks` (multi-stack) | Yes | No | Missing |
| Project `conventions` (naming, patterns, testing, structure, custom) | Yes | No | Missing |
| Project `ai_tools` config (model, preferred_agents, preferred_rulesets) | Yes | No | Missing |
| Project `agents`/`prompts`/`rulesets`/`recipes` include/exclude (regex) | Yes | Partial (tag/whitelist/blacklist filter, not regex) | Partial |
| Project `commands` | Yes | No | Missing |
| Project `documentation` (readme, per-topic, additional items) | Yes | Yes (readme, per-topic, additional) | Implemented |
| Feature manifest (basic: id, description, context, prompt, constraints) | Yes | Yes | Partial |
| Feature `model` field (highest priority in hierarchy) | Yes | No | Missing |
| Feature `files.patterns` (glob patterns) | Yes | No (`files` is flat list in context) | Partial |
| Feature `recipe` binding | Yes | No | Missing |
| Feature `conventions` | Yes | No (has `constraints` instead) | Partial |
| Feature `metadata.status`/`owner` fields | Yes | No | Missing |
| Deploy config manifest (`deploy.yml`) | Yes | No (deploy info is inline in project manifest) | Partial |
| Deploy config local overlay (`deploy.local.yml`) | Yes | No | Missing |
| Deploy config tool selection | Yes | No | Missing |
| Deploy config `backup`, `auto_commit`, `git_branch` | Yes | No | Missing |
| Deploy config override of project filters/tech-stack | Yes | No | Missing |
| Eval suite manifest | Yes | No | Missing |

### 1.2 CLI Commands

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| CLI framework | Commander.js | Clikt | Implemented |
| `build` (global adapter generation) | Yes | No (engine processes per-project only) | Missing |
| `generate [project] [--all]` | Yes | Partial (engine processes all projects from config) | Partial |
| `deploy project <id> [-n] [-f] [-i]` | Yes | No (no deploy pipeline) | Missing |
| `deploy all` | Yes | No | Missing |
| `deploy rollback` | Yes (placeholder) | No | Missing |
| `validate` (schema + rules validation) | Yes | No | Missing |
| `eval [--suite]` | Yes | No | Missing |
| `diff --before --after` | Yes | No | Missing |
| `clean` | Yes | No | Missing |
| `create <name>` (project from template) | Yes | No | Missing |
| `init <path>` (external project init) | Yes | No | Missing |
| `list` (list projects) | Yes | No | Missing |
| `external list|add|remove` | Yes | No | Missing |
| `features generate <project-id>` | Yes | No (features exported as part of main flow) | Partial |
| `skills generate` | Yes | No | Missing |
| `docs generate` | Yes | No | Missing |
| `prompts library` (Markdown) | Yes | No | Missing |
| `prompts html` (interactive HTML browser) | Yes | No | Missing |
| `prompts use <id>` (interactive fill) | Yes | No | Missing |
| `recipes list` | Yes | No | Missing |
| `recipes run <id>` (interactive runner) | Yes | No | Missing |
| `recipes generate <id>` (bash script) | Yes | No | Missing |
| `--working-dir` option | Yes | Yes | Implemented |
| `--dry-run` option | Yes | No | Missing |
| `--force` option | Yes | No | Missing |
| `--interactive` option | Yes | No | Missing |

### 1.3 Output Targets (Tool Adapters)

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| Windsurf adapter | Yes | Yes | Implemented |
| Claude Code adapter | Yes | Yes | Implemented |
| Cursor adapter | Yes | Yes | Implemented |
| GitHub Copilot adapter | Yes | Yes | Implemented |
| Codex adapter | Yes | Yes | Implemented |
| Copilot CLI adapter | Yes | No (not present) | Missing |
| Antigravity adapter (new, not in TS) | No | Yes | Implemented (Kotlin-only) |
| Windsurf: project-context.md (always_on) | Yes | Yes | Implemented |
| Windsurf: agent files (manual trigger) | Yes | Yes | Implemented |
| Windsurf: prompt files | Yes | Yes | Implemented |
| Windsurf: feature workflow files | Yes | Yes | Implemented |
| Windsurf: recipe scripts (.cs.recipes/) | Yes | No | Missing |
| Claude: CLAUDE.md global context | Yes | Yes | Implemented |
| Claude: agent .md files with YAML frontmatter | Yes | Yes | Implemented |
| Claude: prompt files as commands | Yes | Yes | Implemented |
| Claude: feature workflow files | Yes | Yes | Implemented |
| Claude: skills.json | Yes | No | Missing |
| Claude: skill directories with files | Yes | No | Missing |
| Claude: project-context.json | Yes | No (uses CLAUDE.md instead) | Partial |
| Claude: recipe scripts | Yes | No | Missing |
| Cursor: project rules (.mdc with frontmatter) | Yes | Yes | Implemented |
| Cursor: agent rules (.mdc) | Yes | Yes | Implemented |
| Cursor: prompt commands | Yes | Yes | Implemented |
| Cursor: feature files | Yes | Yes | Implemented |
| Cursor: recipes.json (agent-to-recipe conversion) | Yes | No | Missing |
| Cursor: recipe scripts | Yes | No | Missing |
| GitHub Copilot: instructions.md | Yes | Yes (copilot-instructions.md) | Implemented |
| GitHub Copilot: prompt files | Yes | Yes | Implemented |
| GitHub Copilot: agent instruction files | Yes | Yes | Implemented |
| GitHub Copilot: feature instruction files | Yes | Yes | Implemented |
| GitHub Copilot: recipe scripts | Yes | No | Missing |
| Codex: AGENTS.md | Yes | Yes | Implemented |
| Codex: prompt files as SKILL.md | Yes | Yes | Implemented |
| Codex: agent files as SKILL.md | Yes | Yes | Implemented |
| Codex: feature files | Yes | Yes | Implemented |
| Codex: deploy to ~/.codex/prompts | Yes | No | Missing |

### 1.4 Build Pipeline & Resolver System

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| Global build (all adapters, generateGlobal) | Yes | No | Missing |
| Project generation to staging (.output/) | Yes | No (writes directly to deploy dir) | Missing |
| CI pipeline (validate -> build -> eval -> docs) | Yes | No | Missing |
| Ruleset resolution with `extends` (recursive, cycle detection) | Yes | No | Missing |
| Ruleset resolution with regex pattern matching | Yes | Yes | Implemented |
| Language-aware ruleset filtering (tag-based exclusion) | Yes | No | Missing |
| Global context exclusion (language rulesets excluded from global) | Yes | No | Missing |
| Agent resolution per-stack (suffix `-<stackName>`) | Yes | No | Missing |
| Include/exclude filtering (regex-based) | Yes | No (uses tag/whitelist/blacklist) | Partial |
| Model resolution hierarchy (feature > project > agent > prompt) | Yes | No | Missing |
| Deploy config merging via `applyDeployConfig()` | Yes | No | Missing |

### 1.5 Validation System

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| JSON schema validation (Ajv) | Yes | No | Missing |
| ID uniqueness check | Yes | No | Missing |
| Kebab-case enforcement | Yes | No | Missing |
| Semver validation | Yes | Partial (Version class parses semver) | Partial |
| Reference integrity (rulesets, extends) | Yes | No | Missing |
| Security scanning (API keys, passwords, PEM, AWS, GitHub tokens) | Yes | No | Missing |
| Include file existence validation | Yes | No | Missing |
| Feature backtick detection | Yes | No | Missing |

### 1.6 Configuration System

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| `config.yml` loading | Yes | Yes | Implemented |
| `config.local.yml` overlay | Yes | Yes | Implemented |
| Config deep merge (arrays deduplicated, objects recursive) | Yes | Partial (simple override, not deep merge with array dedup) | Partial |
| Configurable project sources | Yes | Yes | Implemented |
| Global vs local scopes | Yes | No | Missing |

### 1.7 External Project Management

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| External project registry (projects.global.yml / projects.local.yml) | Yes | No | Missing |
| `external add/remove/list` commands | Yes | No | Missing |
| .cleanship-ai-tools/ directory in external projects | Yes | No | Missing |

### 1.8 Prompt Library Generation

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| Markdown prompt library (PROMPT_LIBRARY.md) | Yes | No | Missing |
| HTML prompt browser (PROMPT_LIBRARY.html) | Yes | No | Missing |
| Interactive CLI prompt fill | Yes | No | Missing |

### 1.9 Recipe System

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| Recipe manifest loading | Yes | No | Missing |
| Recipe script generation (bash) | Yes | No | Missing |
| Interactive recipe runner | Yes | No | Missing |
| Recipe variable interpolation | Yes | No | Missing |
| Recipe loop support | Yes | No | Missing |
| Recipe condition checks | Yes | No | Missing |
| Recipe document flow (outputDocument / includeDocuments) | Yes | No | Missing |
| Recipe conversation continuation | Yes | No | Missing |
| Feature-bound recipes | Yes | No | Missing |

### 1.10 Deployment System

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| Deploy pipeline (generate -> verify -> backup -> copy) | Yes | No (direct write only) | Missing |
| Backup system (10 most recent per project) | Yes | No | Missing |
| Backup cleanup | Yes | No | Missing |
| Auto-commit after deploy | Yes | No | Missing |
| Dry-run mode | Yes | No | Missing |
| Tool-to-directory mapping during deploy | Yes | No | Missing |
| Windsurf workflow merging during deploy | Yes | No | Missing |

### 1.11 Testing

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| Test framework | Vitest | JUnit5 + MockK | Implemented |
| Adapter unit tests | Yes (5 adapters) | Yes (6 adapters) | Implemented |
| Service tests | Yes (validation, eval, resolver) | Yes (config, filter, loader, ruleset resolver) | Partial |
| CLI integration tests | Yes (generate, deploy) | Yes (basic) | Partial |
| Coverage thresholds (60%) | Yes | No | Missing |
| Engine integration test | No | Yes | Implemented (Kotlin-only) |

### 1.12 Architecture & Infrastructure

| Feature | TypeScript | Kotlin | Status |
|---------|-----------|--------|--------|
| Multi-module project | No (single project) | Yes (engine, cli, server, telemetry, utils) | Implemented (Kotlin-only) |
| HTTP Server (Ktor) | No | Yes | Implemented (Kotlin-only) |
| Telemetry/Observability (OpenTelemetry) | No | Yes | Implemented (Kotlin-only) |
| Structured logging with trace context | No | Yes | Implemented (Kotlin-only) |
| ToolAdapter interface | Yes (two generations) | Yes (clean single interface) | Implemented |
| ToolRegistry (singleton) | Yes | No (uses ToolFactory object) | Partial |
| Printer pattern (FileContent / FileWriter) | Yes | Yes (Output interface) | Implemented |
| JSON Schema files (10_schemas/) | Yes (9 schemas) | No | Missing |

---

## 2. Detailed Gap Analysis

### 2.1 Manifest Type Gaps

#### Skills (Missing)
- **TypeScript:** Full skill manifest with `command` (program, args, cwd, env), `mcp_tool`, `timeout_sec`, `inputs`, `outputs`, `tags`. Skills are transformed into `SKILL.md` files by SkillService.
- **Kotlin:** No `SkillManifest` data class exists. No skill loading, no skill generation.
- **To build:** Create `SkillManifest` data class, add skill loading to `LoaderService`, add `SkillPrinter`, update adapters to export skills.

#### Recipes (Missing)
- **TypeScript:** Full recipe system with multi-step workflows, agents, variables, loops, conditions, conversation strategies, document flow. Recipes compile to bash scripts and run interactively.
- **Kotlin:** No recipe support whatsoever.
- **To build:** `RecipeManifest` with steps, variables, loop config. `RecipeService` for script generation. `RecipeRunnerService` for interactive execution. CLI commands for list/run/generate.

#### Ruleset Inheritance via `extends` (Missing)
- **TypeScript:** Rulesets can extend other rulesets via `extends` field. Resolution is recursive with cycle detection using a visited set.
- **Kotlin:** `RulesetManifest` has no `extends` field. `RulesetResolver` only does pattern matching, not inheritance resolution.
- **To build:** Add `extends` field to `RulesetManifest`. Implement recursive resolution with cycle detection in `RulesetResolver`.

#### Multi-Stack Projects (Missing)
- **TypeScript:** Projects can define `tech_stacks` (e.g., `frontend`, `backend`), each with different languages. Agents get separate suffixed variants per stack with language-specific rulesets.
- **Kotlin:** `ProjectManifest` has no `tech_stack` or `tech_stacks`. No concept of stack contexts or suffixed outputs.
- **To build:** Add `TechStack` and `tech_stacks` to `ProjectManifest`. Implement per-stack agent resolution with suffix generation in `ToolsEngine`.

#### Agent Defaults and Extended Fields (Missing)
- **TypeScript:** Agents have `defaults` (model, temperature, max_tokens, style), `capabilities`, `tools`, and separated `prompt.system`/`prompt.user_template`.
- **Kotlin:** Agents have a flat `persona` and `prompt` string. No model defaults, no capabilities, no tools.
- **To build:** Add `AgentDefaults` data class, `capabilities`, `tools` fields. Consider splitting prompt into system/user.

#### Deploy Config as Separate Manifest (Missing)
- **TypeScript:** `deploy.yml` is a separate file with its own schema, supporting tool selection, mode (local/manual), backup, auto-commit, git branch, and filter overrides. Has `deploy.local.yml` overlay.
- **Kotlin:** Deploy info is a simple `ProjectDeploy` section within `ProjectManifest` (just `directory`, `replace`, and entity filters).
- **To build:** Create `DeployManifest` as separate file. Support `deploy.local.yml` overlay. Add tool selection, backup, auto-commit fields. Implement `applyDeployConfig()` merging.

### 2.2 CLI Command Gaps

#### Project Management Commands (Missing)
- `create`, `init`, `list`, `external list|add|remove` -- none of these exist in Kotlin.
- **To build:** Add Clikt subcommands for each operation. Implement `ExternalProjectService`.

#### Validation Command (Missing)
- **TypeScript:** Comprehensive validation with 8 check types (schema, uniqueness, kebab-case, semver, references, security, includes, backticks).
- **Kotlin:** No validation command or service.
- **To build:** Create `ValidationService` with all check types. Add `validate` Clikt subcommand.

#### Documentation Commands (Missing)
- `docs generate`, `prompts library`, `prompts html`, `prompts use` -- all missing.
- **To build:** `DocsService` for agent/prompt/skill documentation generation. `PromptService` for library and HTML output.

#### Deploy Commands (Missing)
- `deploy project`, `deploy all`, `deploy rollback` with dry-run, force, interactive options.
- **To build:** Full deploy pipeline with backup, copy, verification, and rollback support.

### 2.3 Adapter Gaps

#### Copilot CLI Adapter (Missing)
- **TypeScript:** Generates `AGENTS.md` at project root and `.cs.recipes/` bash scripts.
- **Kotlin:** Not present. Only 6 adapters exist (Windsurf, Antigravity, GitHub Copilot, Claude, Codex, Cursor).
- **To build:** Create `CopilotCliAdapter` implementing `ToolAdapter`.

#### Recipe Script Generation (Missing across all adapters)
- **TypeScript:** Every adapter generates `.cs.recipes/<recipe>.sh` scripts.
- **Kotlin:** No adapter generates recipe scripts.
- **To build:** Once recipe support exists, add recipe export method to `ToolAdapter` interface and implement in all adapters.

#### Skill Export (Missing in Claude adapter)
- **TypeScript:** Claude adapter generates `skills.json` and skill directories.
- **Kotlin:** Claude adapter has no skill support.
- **To build:** Add skill export once `SkillManifest` exists.

### 2.4 Infrastructure Gaps

#### Staging Area (.output/) (Missing)
- **TypeScript:** Generation writes to `.output/<project-id>/<tool-name>/` first, then deploy copies to target.
- **Kotlin:** Engine writes directly to the deploy directory.
- **To build:** Add intermediate staging to the generation pipeline.

#### Backup and Rollback (Missing)
- **TypeScript:** Backs up existing files before overwriting, keeps 10 most recent per project, supports rollback.
- **Kotlin:** `deploy.replace` just deletes the target directory.
- **To build:** Implement backup service with timestamped directories and cleanup.

#### JSON Schema Validation (Missing)
- **TypeScript:** 9 JSON schemas in `10_schemas/` validated with Ajv.
- **Kotlin:** No schema files, no schema validation. Relies on kotlinx.serialization parsing only.
- **To build:** Either port JSON schemas and use a JVM JSON Schema validator, or implement validation rules in code.

---

## 3. Implementation Priority

Priority is based on: (a) foundational dependencies, (b) feature importance for core workflow, (c) effort.

### Phase 1: Core Model Completeness (Foundation)
1. **Ruleset `extends` and inheritance resolution** -- Many features depend on correct ruleset composition.
2. **Ruleset `tags` and language-aware filtering** -- Required for multi-stack support.
3. **Agent extended fields** (defaults, capabilities, tools, prompt split) -- Aligns agent model with TS.
4. **Skill manifest** -- New manifest type, needed for Claude adapter completeness.
5. **TechStack / multi-stack project support** -- Needed for per-stack agent resolution.

### Phase 2: Deployment Pipeline
6. **Separate deploy config** (`deploy.yml` / `deploy.local.yml`) -- Decouples deploy from project manifest.
7. **Staging area** (`.output/`) -- Generate first, then deploy.
8. **Backup system** -- Safety net before overwriting files.
9. **Deploy commands** (`deploy project`, `deploy all`) with dry-run/force/interactive.

### Phase 3: Validation
10. **Validation service** -- Schema validation, uniqueness, kebab-case, semver, reference integrity, security scanning.
11. **Validate CLI command.**

### Phase 4: Recipe System
12. **Recipe manifest and loading.**
13. **Recipe script generation** (bash scripts).
14. **Recipe export in all adapters** (`.cs.recipes/`).
15. **Interactive recipe runner.**

### Phase 5: CLI Commands & Project Management
16. **`build` command** (global generation).
17. **`create` / `init` commands** (project scaffolding).
18. **`list` / `external` commands** (project discovery).
19. **`clean` command.**
20. **Copilot CLI adapter.**

### Phase 6: Documentation & Prompt Tools
21. **`docs generate`** (agents/prompts/skills documentation).
22. **Prompt library** (Markdown and HTML generation).
23. **`prompts use`** (interactive fill).
24. **Prompt variable substitution** (`{{var}}`, conditional sections).

### Phase 7: Advanced Features
25. **Eval suite support.**
26. **Diff command.**
27. **Model resolution hierarchy.**
28. **Config deep merge with array deduplication.**
29. **Coverage thresholds in build.**

---

## 4. Kotlin-Only Features (Not in TypeScript)

The Kotlin project introduces capabilities that do not exist in the TypeScript version:

| Feature | Description |
|---------|-------------|
| **Antigravity adapter** | New tool adapter for the `.agent/` directory structure |
| **HTTP Server (Ktor)** | REST API server module (`/`, `/health` endpoints) |
| **Telemetry module** | OpenTelemetry-based tracing with span support |
| **Structured logging** | SLF4J with trace/span context (TraceJsonProvider) |
| **Multi-module Gradle** | Separate `engine`, `cli`, `server`, `telemetry`, `utils` modules |
| **Build-logic conventions** | Custom Gradle convention plugins for consistent build config |
| **Regex pattern matching for rulesets** | `RulesetResolver` supports regex patterns (e.g., `coding-.*`), which is more flexible than exact ID matching |
| **Sealed class for project filters** | Type-safe filter variants (ByTags, ByWhitelistedIds, ByBlacklistedIds) instead of regex include/exclude |

---

## 5. Key Architectural Differences

| Aspect | TypeScript | Kotlin |
|--------|-----------|--------|
| **Adapter interface** | Two generations (legacy abstract class + new entity-level interface) | Single clean `ToolAdapter` interface with entity-level exports |
| **Config location** | `15_config/config.yml` | Root `config.yml` (relative to working directory) |
| **Project filters** | Regex include/exclude patterns | Sealed class: ByTags, ByWhitelistedIds, ByBlacklistedIds |
| **Output strategy** | Generate to staging, then deploy | Direct write to deploy directory |
| **Adapter discovery** | ToolRegistry singleton with auto-registration | ToolFactory object with explicit `when` mapping |
| **YAML parsing** | js-yaml | kaml (kotlinx.serialization) |
| **Manifest base** | TypeScript interfaces in `types.ts` | `VersionedManifest` interface + data classes |
| **Deploy info** | Separate `deploy.yml` file | Inline `ProjectDeploy` in project manifest |
| **Server mode** | Not supported | Ktor HTTP server (placeholder) |
| **Telemetry** | Not supported | OpenTelemetry spans via `Telemetry` facade |
