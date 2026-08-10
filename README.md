# AI Tools Repository
> Manifest-driven generator for AI coding assistant configs

This repository is the source of truth for prompts, rulesets, fragments, agents, skills, and deployment manifests.
A Kotlin engine (`ai-tools-engine/`) reads the YAML in this repo and writes tool-specific configuration files for Windsurf, Antigravity, Cursor, Claude Code, GitHub Copilot, and Codex — into each project's deploy directory, and into your own user scope (`~/.claude/`, `~/.codex/`).

## What You Get
- One YAML definition per agent, prompt, ruleset, fragment, and skill, exported to six tools at once
- Per-project filtering by tag, whitelist, or blacklist, declared in `project.yml`
- The same filtering for the tools' per-user configuration, declared in `user.yml` — see [User-Scope Deployments](#user-scope-deployments)
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
- `09_deployments/` – deployment manifests, each a directory containing a `project.yml` (deploys into a project directory) or a `user.yml` (deploys into the user scope of a tool), plus an optional `features/`
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

2) Edit manifests, then edit `09_deployments/<deployment>/project.yml` to set `deploy.directory` and the filters, or `09_deployments/<deployment>/user.yml` to set the filters of a user-scope deployment

3) Generate and deploy

```bash
./deploy.sh
```

`deploy.sh` is a thin wrapper around the CLI:

```bash
cd ai-tools-engine && ./gradlew :cli:run --args="--working-dir <repository root>"
```

The CLI has two options and no subcommands:

- `--working-dir` — the directory containing `config.yml` (and the optional `config.local.yml`). Defaults to `.`, and `deploy.sh` sets it to the directory it was started from.
- `--user-home` — the home directory a `user.yml` deploys under. Defaults to the home of whoever runs the command; see [User-Scope Deployments](#user-scope-deployments).

Every argument you give `deploy.sh` is forwarded to the CLI, so a trial run into a scratch directory is `./deploy.sh --user-home /tmp/try`.
The one argument it cannot forward is one containing a double quote: Gradle's `--args` has no escape mechanism for it, so `deploy.sh` refuses such an argument instead of delivering a different, still-plausible path.
Run the CLI directly for that case.

Every deployment manifest found under the configured `deployments` locations is processed on every run: each `project.yml` is written to its own `deploy.directory`, and each `user.yml` into the user scope of the tools it names.
There is no way to deploy a single manifest from the command line; narrow the `deployments` list in `config.local.yml` instead.

## Projects, Features, and Deployment

A project is a directory under a configured `deployments` location containing `project.yml`.
`deploy.directory` sets where its generated files land.

Relative `deploy.directory` values resolve against `--working-dir`, the same base the `locations` paths of `config.yml` use — so `.` means the root of this repository and the value does not change with the launcher.
Use an absolute path for any project outside this repository.
`deploy.directory` may also reference a variable declared under `env_vars` in the config files, as `${NAME}`; the reference is expanded before the value is judged relative or absolute, so the variable can supply the absolute base — see [Path variables](#path-variables).

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

- Two projects sharing an id, two user deployments sharing an id, or two features of the same project sharing an id, cost only the deployment(s) that carry them. Those are not exported, every other deployment is deployed as usual, and the run still fails at the end.
- Two agents, prompts, rulesets, fragments, or skills sharing an id stop the whole run before anything is written. They are shared by every project, and a project that does not filter that kind deploys all of it, so dropping the colliding pair would silently ship every project without content it never excluded.

Ids are indexed per kind, so a `project.yml` and a `user.yml` are free to declare the same id — they are separate manifests of separate scopes, and nothing ever has to choose between them.

## User-Scope Deployments

A user deployment installs a filtered selection of rulesets, agents, prompts, and skills into a tool's per-user configuration — `~/.claude/`, `~/.codex/` — instead of into a project directory.

It follows the same file convention as a project: **the filename names the kind, the directory names the instance**.
A directory under a configured `deployments` location holding a `project.yml` is a project; one holding a `user.yml` is a user deployment.
There is no `type:` field inside the YAML, and one `locations.deployments` list feeds both kinds.

```
09_deployments/
  ai-tools/
    project.yml          # project-scope deployment
    features/...
  globals/
    user.yml             # user-scope deployment
```

The manifest carries only what a user scope has an answer for: no `context` (there is no repository to describe), no `directory` (the destination is each tool's canonical per-user location), and no `features` (a feature belongs to the project whose directory it lives under).

```yaml
# 09_deployments/globals/user.yml
id: globals
description: My global AI tool setup
tools: [claude, codex]        # optional; omitted means every tool configured for the run
replace: false                # optional, default false
rulesets:
  filter:
    - type: tags
      tags: [global]
agents:
  filter:
    - type: whitelist
      ids: [reviewer-code]
skills:
  filter:
    - type: whitelist
      ids: []                 # an empty whitelist selects nothing
# prompts and fragments are omitted here, which selects all of them
metadata:
  version: 1.0.0
```

Filters, and the `tools` list, behave exactly as they do in a `project.yml`: an omitted or empty `filter` lets everything of that kind through, an empty whitelist selects nothing, and `blacklist` must come last because filters fold over a selection that starts empty.
An empty `tools: []` deploys through no tool at all, while omitting the key means every tool configured for the run.
`09_deployments/globals/user.yml` deploys the rules alone, by declaring an empty whitelist for each of the other four kinds.

### Where it lands

Paths are relative to `--user-home`, which defaults to the home of whoever runs the deploy.

| Artifact | `claude` | `codex` |
| --- | --- | --- |
| rulesets → instructions file | `~/.claude/CLAUDE.md` | `~/.codex/AGENTS.md` |
| agents | `~/.claude/agents/<id>.md` | `~/.codex/skills/agent-<id>/SKILL.md` |
| prompts | `~/.claude/commands/<id>.md` | `~/.codex/skills/prompt-<id>/SKILL.md` |
| skills | `~/.claude/skills/<id>/SKILL.md` | `~/.codex/skills/skill-<id>/SKILL.md` |

Codex has one shape for everything it can be asked to do, so its agents and prompts are skill-shaped there too, told apart by the prefix of their directory — the same layout it uses inside a project.

`windsurf`, `antigravity`, `github_copilot`, and `cursor` have no user-scope layout in this engine yet.
A `user.yml` naming one of them is never silently dropped: the run logs that the manifest is not deployed for that tool, and deploys it for the tools that do have a layout.

### The engine owns the instructions file

`~/.claude/CLAUDE.md` and `~/.codex/AGENTS.md` are generated from the manifest — a heading naming the deployment, its `description`, and a `## Rules` list holding the rules of every ruleset the manifest selected.
The engine **owns and overwrites these files on every deploy**, and the run warns you by path each time it replaces one.

Anything you write into them by hand is lost on the next deploy.
That includes what Claude Code's `#`-remember shortcut appends and what CLAUDE.md-editing tooling adds.
The workflow is: edit the YAML, redeploy.
Auto-memory is unaffected — it lives under `~/.claude/projects/.../memory/`.

Two user deployments that select the same tool both claim that tool's single instructions file.
No winner is picked: the file is left alone, the collision is reported naming both manifests, and the run fails — while the artifacts they do not contend for are still deployed.
Give each tool a single deployment, or narrow their `tools` lists.

### What a deploy touches, and what it leaves alone

A user deploy owns the paths of the artifacts it writes and nothing else.
The directories holding them — `~/.claude/skills/`, `~/.claude/agents/`, `~/.codex/skills/` — are shared with everything you installed by hand, so they are created when missing and never deleted wholesale.

With `replace: true`, the directory of each artifact this manifest deploys is deleted and rewritten (for Claude, that is the skill directories; for Codex, the skill, agent, and prompt directories), and single-file artifacts are overwritten in place.
A skill you wrote yourself, sitting beside the generated ones, survives every deploy.

Known limitations:

- Removing an artifact from a `user.yml` leaves its previously deployed copy behind in the home until you delete it by hand. The engine keeps no ledger of what it wrote, so it cannot tell a stale artifact from one you installed yourself.
- `replace: true` reaches per-artifact paths only. Parent directories and hand-made neighbours are never touched.

### Trying it out

`--user-home` points a run at a directory of its own instead of your real home:

```bash
./deploy.sh --user-home /tmp/try
```

A relative value resolves against `--working-dir` — the same base every other declared path of the run uses — not against the shell's current directory.
An empty value is rejected rather than resolved.
A home that does not exist yet is fine: a first deploy onto a fresh machine creates it, and says so in the log.

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

Verified against the adapters in `ai-tools-engine/engine/.../tools/adapters/`. All paths are relative to the project's `deploy.directory`; for what a `user.yml` writes instead, see [Where it lands](#where-it-lands).

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
Lists are never appended to, so a local `deployments:` list must repeat any default entry you still want.
The `env_vars` map is the exception — it merges per variable, so a local declaration overrides that one variable and leaves the rest of the shared map in place.

```yaml
locations:
  agents:    ["05_agents"]
  prompts:   ["03_prompts"]
  rulesets:  ["01_rulesets"]
  fragments: ["02_fragments"]
  skills:    ["04_skills"]
  deployments: ["09_deployments"]

tools:
  - windsurf
  - antigravity
  - github_copilot
  - claude
  - codex
  - cursor
```

Every entry under `locations` is a list, so you can point at additional directories outside this repository.
Relative location paths resolve against the working directory; absolute paths are used as given — the same rule a project's `deploy.directory` follows.
`locations.deployments` holds both kinds of deployment manifest, since a directory that holds one kind usually holds the other.
It was named `locations.projects` before the second kind existed; a config file still declaring the old key fails the run with a message naming the replacement, rather than having its list silently ignored.
The `tools` list accepts exactly the six keys above, and controls which adapters run.
An individual project can narrow itself down to a subset of them with `deploy.tools` in its `project.yml`, and a user deployment with its top-level `tools` — see [QUICKREF.md](QUICKREF.md).

### Path variables

The optional top-level `env_vars` key declares variables that every `locations` entry and every project's `deploy.directory` may reference as `${NAME}`:

```yaml
env_vars:
  PROJECTS_FOLDER: /home/alice/projects
```

```yaml
# 09_deployments/<deployment>/project.yml
deploy:
  directory: "${PROJECTS_FOLDER}/custom-ai-tools"
```

A name is looked up in the `env_vars` of `config.local.yml` first, then in those of `config.yml`, and finally in the environment of the process.
The typical use is the one above: the machine-specific absolute base lives in the gitignored `config.local.yml`, and the shared `project.yml` files only name the variable, so the same manifest works on a machine whose checkout sits somewhere else.

References are expanded before a path is judged relative or absolute, which is what lets a variable carry an absolute base for a value whose remainder is written as a fragment.

A reference to a variable declared nowhere fails the run, naming the variable — and, for a location, the file that declared the offending value.
Every project's `deploy.directory` is resolved up front, before any project is deployed, so a broken reference cannot leave a run half-deployed, and nothing is ever written to a directory literally named `${...}`.

Variable values must already be fully expanded: a value that itself contains `${...}`, or an expansion that assembles a new reference together with the text around it, is an error rather than a second substitution pass.
A `${...}` group that is not a valid reference — `${1ST}`, `${A-B}`, or a braceless `$NO_BRACES` — is left exactly as written, so a path is free to contain one.
A name is made of letters, digits, and underscores, and may not start with a digit.

Configs without `env_vars`, and paths without references, behave exactly as they did before.

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
- MCP server configuration output, in either scope — a `user.yml` has no `mcps` block
- User-scope deployment for `windsurf`, `antigravity`, `github_copilot`, and `cursor` — a `user.yml` deploys through `claude` and `codex` only, and names the tools it skipped
- A ledger of what a deploy wrote, and with it the removal of artifacts a manifest no longer selects — see [User-Scope Deployments](#user-scope-deployments)
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
