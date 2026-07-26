# AI Tools Repository
> Manifest-driven generator for AI coding assistant configs

This repository is the source of truth for prompts, rulesets, fragments, agents, skills, and project manifests.
A Kotlin engine (`ai-tools-engine/`) reads the YAML in this repo and writes tool-specific configuration files for Windsurf, Antigravity, Cursor, Claude Code, GitHub Copilot, and Codex directly into each project's deploy directory.

## What You Get
- One YAML definition per agent, prompt, ruleset, fragment, and skill, exported to six tools at once
- Per-project filtering by tag, whitelist, or blacklist, declared in `project.yml`
- Reusable rulesets and fragments referenced by regex, so one agent can pick up all `coding-language-*` rules at once
- Feature manifests that emit per-tool feature workflows and instructions
- Strict manifest parsing: an unknown key or an unresolvable ruleset reference fails the run instead of silently producing a wrong artifact

Several capabilities described in earlier revisions of this README no longer exist.
See [Not Yet Implemented](#not-yet-implemented) before looking for them.

## Requirements
- A JDK 17 or newer on `PATH` (the Gradle 9 wrapper needs it to start)
- Network access on the first build, so Gradle can fetch dependencies and, if you have no local JDK 21, provision the JVM 21 toolchain the engine targets
- Node/npm only if you want to build the optional `:server` module, whose frontend is built with Vite

## Repository Layout
- `01_rulesets/` – reusable rule sets, referenced by regex on their `id`
- `02_fragments/` – reusable content blocks shared by agents, prompts, and skills
- `03_prompts/` – prompt manifests
- `04_skills/` – skill manifests, as `<id>.yml` or as a directory containing `skill.yml`
- `05_agents/` – agent manifests
- `09_projects/` – project manifests (`global/` and `local/`), each a directory containing `project.yml` and an optional `features/`
- `ai-tools-engine/` – the Kotlin build engine (Gradle multi-module: `:engine`, `:cli`, `:server`, `:telemetry`)
- `10_schemas/` – JSON schemas; **stale**, they have drifted from the engine's Kotlin models and are not used for validation
- `90_docs/` – reference documentation
- `91_examples/` – worked examples

Source of truth retained, engine support pending:

- `07_mcp/` – MCP servers and presets
- `08_recipes/` – multi-agent workflow recipes
- `20_evals/` – evaluation datasets and suites
- `21_redteam/` – security and jailbreak test cases

The engine does not read any of these four directories.
They are kept so the content is not lost, and are tracked in [PLANNED_FEATURES.md](PLANNED_FEATURES.md).

Left over from the retired TypeScript CLI and no longer written or read by anything:

- `adapters/`, `.output/`, `.backups/`, `docs/` – old generated output

## Common Workflow

1) First-run setup — checks prerequisites and builds the engine

```bash
./setup.sh
```

2) Edit manifests, then edit `09_projects/<scope>/<project>/project.yml` to set `deploy.directory` and the filters

3) Generate and deploy

```bash
./deploy.sh
```

`deploy.sh` is a two-line wrapper around the CLI:

```bash
cd ai-tools-engine && ./gradlew :cli:run --args="--working-dir <repository root>"
```

The CLI has exactly one option, `--working-dir`, which points at the directory containing `config.yml`.
There are no subcommands and no other flags.

Every project manifest found under the configured `projects` locations is processed on every run, and each is written to its own `deploy.directory`.
There is no way to deploy a single project from the command line; narrow the `projects` list in `config.local.yml` instead.

## Projects, Features, and Deployment

A project is a directory under a configured `projects` location containing `project.yml`.
`deploy.directory` sets where its generated files land.

Relative `deploy.directory` values resolve against the JVM working directory of the Gradle `:cli:run` task, which is `ai-tools-engine/cli` — so `../../` means the root of this repository.
Use an absolute path for any project outside this repository.

Filters select which manifests reach a project. Each of `agents`, `prompts`, `rulesets`, `fragments`, `skills`, and `features` accepts a list of filters of type `tags`, `whitelist`, or `blacklist`:

```yaml
deploy:
  directory: "/path/to/your/project"
  agents:
    filter:
      - type: tags
        tags: [development, documentation]
  prompts:
    filter:
      - type: whitelist
        ids: [docs-write-readme, qa-write-tests]
```

Filters are applied by folding over a selection that starts empty: `tags` and `whitelist` add, `blacklist` subtracts from what has been selected so far.
A filter list containing only a `blacklist` therefore matches nothing, so `blacklist` must come last.
An omitted or empty filter lets everything through.

A ruleset or fragment referenced by an agent must itself survive the project's `rulesets` / `fragments` filter, otherwise that agent fails to export.

Features live in `features/<feature>.yml` next to `project.yml` and are exported as per-tool workflow or instruction files.

Set `deploy.replace: true` to wipe a tool's output directory before writing.
Every adapter honours the flag, the GitHub Copilot one included: with `replace: true` it clears `.github/prompts/`, `.github/instructions/`, and `.github/agents/`, which it owns entirely, and with `replace: false` it leaves them alone — so files left there by a retired naming scheme survive and have to be removed by hand.

There is no backup and no auto-commit step.
Generated files are overwritten in place, though each file is written atomically via a temporary file, so an interrupted run cannot leave a half-written artifact.

### Duplicate ids

Manifest ids are the primary key of the whole engine, so two files declaring the same id are never resolved by picking a winner.
One run reports every collision it found, names the id and both files, and exits non-zero.

How much a collision costs depends on the kind of manifest:

- Two projects sharing an id, or two features of the same project sharing an id, cost only the project(s) that carry them. Those projects are not exported, every other project is deployed as usual, and the run still fails at the end.
- Two agents, prompts, rulesets, fragments, or skills sharing an id stop the whole run before anything is written. They are shared by every project, and a project that does not filter that kind deploys all of it, so dropping the colliding pair would silently ship every project without content it never excluded.

## Rulesets and Fragments

Agents, prompts, and skills reference rulesets and fragments by **regular expression matched against the manifest `id`**, not by filename:

```yaml
rulesets:
  - base                  # exact id
  - coding-language-.*    # every coding-language-* ruleset
```

Directory nesting under `01_rulesets/` is purely organisational; only `id` matters.
Filenames need not match the `id` — for example `01_rulesets/coding/languages/coding-kotlin.yml` declares `id: coding-language-kotlin`.

A pattern that matches nothing is an error, and the message tells you whether the ruleset exists but was excluded by the project filter.

## Tool Output

Verified against the adapters in `ai-tools-engine/engine/.../tools/adapters/`. All paths are relative to the project's `deploy.directory`.

| Tool (`config.yml` key) | Output |
| --- | --- |
| `claude` | `CLAUDE.md`, `.claude/agents/<id>.md`, `.claude/commands/<prompt-id>.md`, `.claude/skills/<id>/SKILL.md`, `.claude/workflows/feature-<id>.md` |
| `github_copilot` | `.github/copilot-instructions.md`, `.github/agents/<id>.agent.md`, `.github/prompts/prompt-<id>.prompt.md`, `.github/prompts/skill-<id>.prompt.md`, `.github/instructions/feature-<id>.instructions.md` |
| `codex` | `AGENTS.md`, `.codex/skills/agent-<id>/SKILL.md`, `.codex/skills/prompt-<id>/SKILL.md`, `.codex/skills/skill-<id>/SKILL.md`, `.codex/features/feature-<id>.md` |
| `windsurf` | `.windsurf/rules/project.md`, `.windsurf/rules/agent-<id>.md`, `.windsurf/rules/prompt-<id>.md`, `.windsurf/rules/skill-<id>.md`, `.windsurf/workflows/feature-<id>.md` |
| `antigravity` | `.agent/rules/project.md`, `.agent/rules/agent-<id>.md`, `.agent/rules/prompt-<id>.md`, `.agent/rules/skill-<id>.md`, `.agent/workflows/feature-<id>.md` |
| `cursor` | `.cursor/rules/project.mdc`, `.cursor/rules/agent-<id>.mdc`, `.cursor/commands/prompt-<id>.md`, `.cursor/commands/skill-<id>.md`, `.cursor/features/feature-<id>.md` |

## Configuration

The engine reads `config.yml` from the directory given by `--working-dir` — the repository root — and merges `config.local.yml` over it if present.
`config.local.yml` is gitignored and intended for machine-local overrides.

Merging happens per individual key: each of the six entries under `locations`, and the `tools` list, is replaced wholesale when present in `config.local.yml`.
Lists are never appended to, so a local `projects:` list must repeat any default entry you still want.

```yaml
locations:
  agents:    ["05_agents"]
  prompts:   ["03_prompts"]
  rulesets:  ["01_rulesets"]
  fragments: ["02_fragments"]
  skills:    ["04_skills"]
  projects:  ["09_projects"]

tools:
  - windsurf
  - antigravity
  - github_copilot
  - claude
  - codex
  - cursor
```

Every entry under `locations` is a list, so you can point at additional directories outside this repository.
Relative location paths resolve against the working directory; absolute paths are used as given.
The `tools` list accepts exactly the six keys above, and controls which adapters run.

## Not Yet Implemented

These were documented previously but have no implementation in the current engine.
The CLI accepts no subcommands, so there is no command to run for any of them:

- Project scaffolding and registration (`project:create`, `project:init`, `project:list`)
- Deploying a single named project, dry runs, interactive or forced deploys
- Standalone manifest validation — manifests are validated only as part of a deploy run, by strict YAML decoding
- Prompt library generation (`PROMPT_LIBRARY.md` / `.html`) and the interactive prompt filler
- Recipe listing, running, and script generation
- Documentation generation
- Evaluation suite runner
- `diff` and `clean` utilities
- MCP server configuration output
- Deploy backups and auto-commit

The committed `PROMPT_LIBRARY.md`, `PROMPT_LIBRARY.html`, and `docs/AGENTS.md` are artifacts of the retired TypeScript CLI.
Nothing regenerates or reads them today.

Deployment settings live in the `deploy:` block of `project.yml`.
The separate `deploy.yml` that the TypeScript CLI used has been removed — the engine never read it.

See [PLANNED_FEATURES.md](PLANNED_FEATURES.md) for what is intended next.

## Need Help?
- Manifest field reference and templates: [QUICKREF.md](QUICKREF.md)
- Tool-specific integration details: [90_docs/TOOLS.md](90_docs/TOOLS.md)
- Engine architecture and module layout: [ai-tools-engine/README.md](ai-tools-engine/README.md)
- Worked examples: [91_examples/](91_examples/)
- Open an issue or discussion in the repo if something looks off.
