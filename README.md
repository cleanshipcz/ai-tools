# AI Tools Repository
> Manifest-driven generator for AI coding assistant configs

This repository is the source of truth for prompts, rulesets, agents, skills, recipes, and project manifests. The CLI reads the YAML in this repo and produces tool-specific outputs for Windsurf, Cursor, Claude Code, GitHub Copilot, Copilot CLI, and Codex. Deployments copy the generated files into your target project with backups and optional auto-commit.

## What You Get
- Schema validation for all manifests (`npm run validate`)
- Global adapter build (`npm run build`) for sharing prompts/agents across tools
- Project generation and deployment with filtering from `deploy.yml` (includes backup + optional commit)
- Feature manifests that emit per-tool snippets/workflows and recipe scripts
- Prompt library generation (Markdown + interactive HTML) and an interactive prompt filler (`npm run use-prompt <id>`)
- Recipe runner and script generator for multi-agent workflows
- Anthropic-compatible skill generation (`npm run skills generate`)
- Docs generator (`npm run docs generate`), diff/clean utilities, and optional eval runner

## Repository Layout
- `01_rulesets/` – reusable rule sets
- `02_skills/` – skill manifests (converted to `SKILL.md`)
- `03_prompts/` – prompt manifests
- `04_agents/` – agent manifests
- `05_recipes/` – multi-agent workflow recipes
- `09_projects/` – project manifests (`global/` + `local/` + templates)
- `07_mcp/` – MCP servers and presets
- `10_schemas/` – JSON schemas for validation
- `12_templates/` – scaffolding used by project commands
- `15_config/` – repo configuration (`config.yml`, `config.local.yml`)
- `adapters/` – global build output (`npm run build`)
- `.output/` – per-project generated output (`project:generate`/`project:deploy`)
- `.backups/` – deploy backups per project
- `docs/` / `90_docs/` – generated and reference documentation

## Common Workflow
1) Install dependencies  
`npm install`

2) Validate manifests  
`npm run validate`

3) Build global adapters (optional but useful for quick sharing)  
`npm run build`

4) Create or register a project  
- Create managed project: `npm run project:create my-app -- --local -d "My app"`  
- Initialize external project: `npm run project:init /path/to/app --alias my-app`

5) Edit `09_projects/.../project.yml` and `deploy.yml` (set `target`, `tools`, filters)

6) Generate and deploy  
`npm run project:deploy my-app`  
Use `--dry-run`, `--force`, or `--interactive` to control deployment. Outputs are staged in `.output/my-app/` and copied to the deploy target with backups in `.backups/my-app/`.

## Projects, Features, and Deployment
- `project:deploy <id>`: loads `project.yml` + `deploy.yml`, applies include/exclude filters, generates per-tool outputs, creates backups, merges feature workflows into Windsurf, and optionally commits when `auto_commit` is enabled.
- `project:deploy all`: deploys every project that has a `deploy.yml` (including registered externals).
- `project:list`: shows managed + external projects.
- `project:init`: scaffolds `.cleanship-ai-tools` inside an external repo and optionally registers it.
- Features: place `features/<feature>/feature.yml` under a project. `project:deploy` (or `npm run features generate <project>`) emits per-tool feature snippets and recipe scripts into `.output/<project>/features/` and merges Windsurf workflows automatically.

## Prompts and Agents
- Generate libraries: `npm run prompt-library` (Markdown) and `npm run prompt-html` (interactive browser).
- Interactive fill/copy: `npm run use-prompt <prompt-id>`.
- Agents, prompts, and rulesets are filtered per project using include/exclude rules in `project.yml` / `deploy.yml`.

## Recipes
- Discover: `npm run recipe:list`
- Run interactively: `npm run recipe:run <recipe-id> [claude-code|copilot-cli|cursor]`
- Generate scripts: `npm run recipe:generate <recipe-id> [tool] [output]`

Recipes are emitted to `.cs.recipes/` inside each tool’s output directory so you can execute them directly from your project.

## Skills and Docs
- Skills: `npm run skills generate` converts `02_skills` into Anthropic `SKILL.md` folders under `adapters/claude-code/skills`.
- Docs: `npm run docs generate` builds `docs/AGENTS.md` from manifests.

## Utilities
- `npm run diff -- --before <file> --after <file> [--format lines|words]`
- `npm run clean` removes generated artifacts (`.output`, adapters, prompt libraries, recipe docs/logs).
- `npm run eval -- --suite <name>` runs evaluation suites when configured.
- Testing: `npm test`, `npm run test:watch`, `npm run test:coverage`.

## Tool Output (generated per project)
| Tool           | Staged Output (under `.output/<project>`) | Contains                                                                |
| -------------- | ----------------------------------------- | ----------------------------------------------------------------------- |
| Windsurf       | `.windsurf/`                              | Project context, agent/prompt rules, recipes, feature workflows         |
| Cursor         | `.cursor/`                                | `recipes.json`, `project-rules.json`, recipes                           |
| Claude Code    | `.claude/`                                | Prompts (JSON), skills, agents, project context, recipes                |
| GitHub Copilot | `.github/`                                | `instructions.md`, prompt/agent markdown, recipes                       |
| Copilot CLI    | `AGENTS.md` + `.cs.recipes/`              | Agent catalog plus runnable recipes                                     |
| Codex          | `AGENTS.md` + `.codex/prompts/`           | Agent catalog and prompts (deploy copies prompts to `~/.codex/prompts`) |

Deploy copies these staged files into the `target` from `deploy.yml` and keeps timestamped backups in `.backups/<project>/`.

## Configuration Notes
- Repo-level config: `15_config/config.yml` (override with `config.local.yml`).
- Project sources: defaults to `09_projects/global` and `09_projects/local`; add more via `project_sources` in config.
- Model preference priority: Feature → Project (`ai_tools.model`) → Agent defaults → Prompt.

## Need Help?
- Recipe and feature examples live in `05_recipes/` and `09_projects/*/features/`.
- Tool-specific integration details are in `90_docs/TOOLS.md`.
- Open an issue or discussion in the repo if something looks off.
