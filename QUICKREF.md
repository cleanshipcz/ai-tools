# AI Tools - Quick Reference

Every template below matches the Kotlin data classes in `ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/`, which are the source of truth.
Manifests are parsed in strict mode: **an unknown key fails the run**, so do not add fields that are not listed here.

## Common Commands

```bash
# First-run setup: checks prerequisites, builds the engine
./setup.sh

# Validate every manifest without writing anything: parses, filters, resolves
# every ruleset, fragment, and skill-file reference, and logs what a real run
# would write where
./deploy.sh --dry-run

# Try a run first: user.yml manifests land in a scratch home instead of your own
./deploy.sh --user-home /tmp/try

# Generate and deploy configs for every deployment manifest
# WARNING: with a user.yml in play, this rewrites ~/.claude/CLAUDE.md and
# ~/.codex/AGENTS.md from the manifest, keeping no backup of what they held
./deploy.sh

# What deploy.sh runs under the hood
cd ai-tools-engine && ./gradlew :cli:run --args="--working-dir \"<repository root>\""

# Engine development
cd ai-tools-engine
./gradlew :cli:build     # compile + test + ktlint + detekt for the deploy path
./gradlew build          # everything, including :server (needs Node/npm)
./gradlew ktlintFormat   # auto-fix Kotlin formatting
```

The inner quotes in that snippet survive Gradle's own splitting of `--args`, which is what keeps a repository path containing spaces a single argument.

The CLI has three options besides `--help`, and no subcommands:

- `--dry-run` — load, filter, and render everything exactly as a deploy would, report every failure a deploy would report, and write nothing. This is how manifests are validated.
- `--working-dir` — the directory holding `config.yml`. Defaults to `.`; `deploy.sh` sets it to the directory it was started from.
- `--user-home` — the home a `user.yml` deploys under. Defaults to the home of the current user. A relative value resolves against `--working-dir`, a value that is `~` or starts with `~/` resolves against the home directory, an empty value is rejected, and a home that does not exist yet is created.

There is no way to select a single manifest.
`deploy.sh` forwards every argument to the CLI, except one containing a double quote — Gradle's `--args` cannot escape it, so the script refuses the argument rather than delivering a different path. Run `./gradlew :cli:run` directly for that case.

## File Naming Conventions

- **IDs**: kebab-case (e.g. `reviewer-code`, `docs-summarize-pr`)
- **Versions**: semantic versioning, `MAJOR.MINOR.PATCH` with an optional `-SUFFIX`
- **Files**: the filename does **not** have to match the `id`, and directory nesting is purely organisational — only `id` is ever referenced. For example `01_rulesets/coding/languages/coding-kotlin.yml` declares `id: coding-language-kotlin`.
- **Skills**: either `04_skills/<id>.yml`, or a directory `04_skills/<name>/` containing `skill.yml` plus any files it ships. A pointer skill, `04_skills/<id>/skill.yml`, instead points with `source` at a plain skill folder outside this repository — see [Pointer Skill](#pointer-skill)
- **Deployments**: a directory under a `deployments` location holding a `project.yml` (deploys into a project directory) or a `user.yml` (deploys into the user scope of a tool) — the filename names the kind, the directory names the instance
- **IDs across kinds**: unique per kind, so a `project.yml` and a `user.yml` may share an id, while two `user.yml` files may not

## Shared Fields

Every manifest requires `id`, `description`, and `metadata`.
The exceptions are a pointer skill and a pointer MCP server, which declare `source`: a pointer skill takes its description from the `SKILL.md` in that folder, a pointer MCP server from its `server.json`, and neither may declare one.

```yaml
metadata:
  version: 1.0.0          # required, MAJOR.MINOR.PATCH(-SUFFIX)
  author: "AI Tools Team" # optional
  created: "2026-07-25"   # optional
  updated: "2026-07-25"   # optional
  tags:                   # optional, used by project `tags` filters
    - development
```

## Creating a Ruleset

```yaml
id: my-rules
description: Brief description
rules:
  - "Rule 1"
  - "Rule 2"
metadata:
  version: 1.0.0
  tags:
    - development
```

There is no `extends`. Compose rulesets by referencing several of them from an agent or prompt.

## Creating an Agent

```yaml
id: my-agent
description: What this agent does
persona: |
  You are a senior engineer who...
rulesets:
  - base
  - coding-language-.*
fragments:
  - authoring-engine-models
rules:
  - "An extra rule that applies only to this agent."
prompt: |
  Do the thing.

  OUTPUT FORMAT
  1) ...
constraints:
  - "Never do X."
metadata:
  version: 1.0.0
  tags:
    - development
```

`persona` and `prompt` are both required and are plain strings.
There is no `purpose`, no `capabilities`, and no `defaults` block — model and temperature are not configurable here.

## Creating a Prompt

```yaml
id: my-prompt
description: What this prompt does
variables:
  - name: input
    required: true          # optional, defaults to true
    description: "What this variable is"
rulesets:
  - docs-base
fragments:
  - shared-constraints
rules:
  - "Specific rule for this prompt."
content: |
  Your prompt template using {{input}}
outputs:                    # optional
  format: markdown
  examples:
    - "An example of the expected output"
metadata:
  version: 1.0.0
  tags:
    - docs
```

Tags go in `metadata.tags`, not at the top level.
There is no `includes` field — use `fragments` for shared content.

## Creating a Fragment

```yaml
id: my-fragment
description: Reusable block of content
content: |
  Text injected into any agent, prompt, or skill that references this id.
metadata:
  version: 1.0.0
  tags:
    - documentation
```

## Creating a Skill

```yaml
id: my-skill
description: >-
  What this skill does.
  Use when the user asks for X.
sections:
  - text: |
      Instructions rendered into SKILL.md.
  - ruleset: coding-language-kotlin
  - fragment: my-fragment
files:
  - path: templates/example.txt
  - source: field-defaults.json
    target: defaults.json
metadata:
  version: 1.0.0
  tags:
    - kotlin
```

A skill has only these fields, plus the skill-level `source` of a pointer skill — see [Pointer Skill](#pointer-skill).
There is no `command`, no `timeout_sec`, and no `outputs` — the engine generates documentation, it does not execute anything.

Each entry in `sections` is exactly one of `text`, `ruleset`, or `fragment`.
Each entry in `files` is either `{ path: ... }` or `{ source: ..., target: ... }`.
A relative `path` or `source` of a `files` entry resolves against the directory holding `skill.yml`, so a skill that ships files is a directory `04_skills/<name>/`.
These paths are taken as written: a `${NAME}` in them is not substituted, and a leading `~` is not expanded.
A standalone `04_skills/<id>.yml` can list only absolute paths; a relative one fails that skill's export in every deployment that selects it, the other artifacts are still written, and the run then exits with a failure.
`target` is where the file lands inside the directory of the generated skill — for Claude, `.claude/skills/<id>/`, next to `SKILL.md`.

### Pointer Skill

Some skills must stay a plain Claude Code skill — a folder with a `SKILL.md` and its companion files — because people who do not use ai-tools use them directly.
A *pointer skill* keeps its one copy of the content in that folder: its manifest only points at the folder with the skill-level `source` key (not the `source` of a `files` entry) and adds what ai-tools owns: `id` and `metadata`, whose tags the deployments select on.
Put it in `04_skills/<id>/skill.yml`; a relative `source` resolves against the folder holding the manifest.

```yaml
# 04_skills/jira-ticket/skill.yml
id: jira-ticket
source: ${PROJECTS_FOLDER}/jira-confluence-mcp-server/skills/jira-ticket
metadata:
  version: 2.0.0
  tags:
    - jira
    - ticket
    - atlassian
```

The folder `source` names must hold a `SKILL.md` that starts with a YAML frontmatter:

```markdown
---
name: jira-ticket
description: Create, draft, update, and link Jira tickets. Use when ...
---

# Jira tickets

Instructions ...
```

- `source` may reference the `env_vars` of `config.yml` and `config.local.yml`, or an environment variable, as `${NAME}` — the same way `deploy.directory` does.
- After substitution, a `source` that is `~` or starts with `~/` resolves against the home directory of the user running the engine, again as `deploy.directory` does. `~user` is not expanded: it is read as a relative path whose first folder is named `~user`. A `~` anywhere else is part of the name.
- Any other relative `source` resolves against the folder holding the manifest, not against the directory `deploy.sh` runs from.
- The manifest must not declare `description`, `sections`, or `files` together with `source`; the run fails if it does.
- `name` in the frontmatter must equal the manifest `id`.
- The `description` of the frontmatter becomes the description of the deployed skill.
- Only `name` and `description` are read from the frontmatter. Any other key, such as `allowed-tools`, is not deployed, and the run logs a warning naming it.
- The text after the frontmatter, from its first line that is not empty, is deployed unchanged. Unlike a skill declared with `sections`, it gets no generated `# <id>` heading and no repeated description, so write your own heading.
- Every other file in the folder, including files in subfolders and hidden files, is copied into the directory of the generated skill at the same relative path — for Claude, next to `SKILL.md`. There is no list to maintain.
- A symbolic link inside the folder is followed and deployed as the file it points at. A link that points at nothing fails the run.
- A `SKILL.md` saved with a UTF-8 byte order mark is read like one without it; the mark is not deployed.

What the engine guarantees about the source folder:

> The engine only reads the source folder of a pointer skill. Before a run writes or deletes anything, `--dry-run` included, it compares two kinds of path with the source folder of every pointer skill it loaded, whether a deployment selects that skill or not: every path it would write a skill to, and every directory `replace: true` would delete (in a project, the directories each tool generates: `.claude`, `.windsurf`, `.agent`, for Codex `.codex/skills` and `.codex/features`, for Cursor `.cursor/rules`, `.cursor/commands`, and `.cursor/features`, and for GitHub Copilot `.github/prompts`, `.github/instructions`, and `.github/agents`; in the user scope, the directory of each skill, and for Codex also of each prompt and agent, that it rewrites). The run fails if such a path is, lies inside, or contains a source folder once symbolic links are resolved, or if a symbolic link anywhere below such a path leads to a path that does. Each path is compared as it will be when the run writes or deletes it: a replaced directory that is itself a symbolic link is compared by where it sits and by where it leads, not by the links in the folder it leads to, and every path below a replaced directory of the same deployment and tool is compared by where it lies once that directory is deleted. During export, every companion file is checked once more, and one whose target lands, once links are resolved, in the folder it is copied from or in the source folder of any pointer skill the run loaded fails that skill. A replacing deploy never follows a symbolic link and never opens what one leads to: it removes a link below a replaced directory and a replaced directory that is itself a link, except that a user deployment refuses, before anything is written, a replaced directory that is a link leading to an existing folder or file outside the skills folder of the tool, or to that folder itself; a link leading inside that folder, or leading nowhere, it removes. Other writes are not compared with source folders: the files of agents, prompts, commands, and features, and the instructions files (`CLAUDE.md`, `AGENTS.md`), are written without this check, except where they sit in a directory that `replace: true` deletes. Keep every source folder outside every directory a deployment writes to.

Every skill manifest is loaded on every run, whether a deployment selects it or not.
A missing folder, a missing `SKILL.md`, a frontmatter without `name` or `description`, or a `name` that differs from the `id` therefore fails the whole run, `./deploy.sh --dry-run` included, naming the manifest to fix.
The folder must exist on every machine that runs a deploy: where it is not checked out, no deploy runs until you check it out, point `source` or the variable it uses elsewhere, or remove the manifest.
The messages and their fixes are listed in [04_skills/README.md](04_skills/README.md#troubleshooting).

## Creating an MCP Server

An MCP server manifest lives in `07_mcp/<id>.yml`; its model is `McpServerManifest.kt`.
A project selects it with `deploy.mcps`, and every deploy merges it into `.mcp.json` (Claude Code), `.vscode/mcp.json` (GitHub Copilot), `.cursor/mcp.json` (Cursor), and `.codex/config.toml` (Codex) under its id.
Windsurf and Antigravity get no MCP config file; the run warns that the servers are not deployed for them.
The full reference is [07_mcp/README.md](07_mcp/README.md).

An inline server declares how to start or reach it, and the variables it needs. This is a shortened `07_mcp/atlassian.yml`:

```yaml
id: atlassian
description: Jira and Confluence through the local jira-confluence-mcp-server
transport:
  type: stdio                   # stdio | http
  command: ${PROJECTS_FOLDER}/jira-confluence-mcp-server/.venv/bin/jira-mcp-server
  args: []                      # optional
  env: {}                       # optional fixed values
variables:                      # optional
  - name: JIRA_PAT
    description: Jira personal access token
    secret: true                # required, no default
    required: false             # optional, default true
  - name: JIRA_BASE_URL
    description: Base URL of Jira
    secret: false
    required: false
metadata:
  version: 1.0.0
  tags: [ai-tools]
```

An http server declares `url` and optional `headers` instead of `command`, `args`, and `env`, and declares every variable they reference:

```yaml
transport:
  type: http
  url: https://mcp.example.com/mcp
  headers:
    Authorization: Bearer ${EXAMPLE_TOKEN}
variables:
  - name: EXAMPLE_TOKEN
    description: API token
    secret: true
```

A pointer server takes its description, transport, and variables from a `server.json` of the MCP Registry schema `2025-12-11`, and must not declare `description`, `transport`, or `variables`:

```yaml
# 07_mcp/github.yml
id: github
source: ${PROJECTS_FOLDER}/github-mcp-server   # the server.json, or the folder holding it
select:                         # optional when server.json declares exactly one package or remote
  remote: https://api.githubcopilot.com/mcp/   # or: package: <identifier>
metadata:
  version: 1.0.0
  tags: [ai-tools]
```

- `${NAME}` in `args`, `env`, `url`, and `headers` must name a declared variable. Any other `${` fails loading: an undeclared name, `${NAME:-default}`, `${env:NAME}`, `${input:id}`, or a nested reference.
- `command` is a path, resolved like a pointer `source`: `${NAME}` in it is a variable of the run (`env_vars` or the environment of the run), never a declared variable, and a leading `~/` is the home of the user running the engine, whatever `--user-home` the run uses.
- A stdio server receives every declared variable in its environment under its own name; an http server receives the ones its `url` and `headers` reference.
- A secret variable (`secret: true`) is never read. It is written as `${NAME}` for Claude Code (`${NAME:-}` when optional), `${env:NAME}` for VS Code and Cursor, and in `env_vars`, `bearer_token_env_var`, or `env_http_headers` for Codex.
- The value of a secret must be in the environment of the tool process, such as the shell you start `claude` or `codex` from. `env_vars` of `config.local.yml` is not enough: the engine reads it only while deploying and never reads a secret, so the value never reaches the tool. A deploy does not need the value; every run warns about each secret of a selected server that the environment of the run does not set.
- Because Codex has no `${NAME}` expansion, a secret may appear only in the environment of a stdio server, or as a whole header value `${NAME}` or `Authorization: Bearer ${NAME}` of an http server. Anywhere else fails the run.
- A plain variable (`secret: false`) is resolved at deploy time from `env_vars` of the config files only, never from the environment of the run, and written as its value. A required one `env_vars` does not declare fails the MCP config files of every project that selects the server; an optional one is left out.
- MCP servers are opt-in: a project without a `deploy.mcps` block selects none, unlike every other kind, and a project that selects none never reads or writes any MCP config file. `mcps: {}` selects every MCP server manifest.
- A pointer's `server.json` is data: text holding `${` fails loading, a variable is secret when it or any input around it is `isSecret`, a `runtimeHint` must be the runner of the registry type, and the only runtime argument accepted is `-e NAME` of an `oci` package. The package identifier and version must follow the grammar of their registry, environment variable names that configure the runner or loader (such as `DOCKER_*`, `NPM_*`, `NODE_*`, `UV_*`, `PIP_*`, `PYTHON*`, `LD_*`, `*_PROXY`, `PATH`) and connection-level headers are refused, and a remote url must be https when it sends a secret header and never carry a user or password. Every run logs the names of the variables each pointer passes and sets, never a value; the full lists are in [07_mcp/README.md](07_mcp/README.md#a-pointer-server).
- Every `.yml` and `.yaml` file under `07_mcp/`, at any depth, is loaded as an MCP server manifest.
- In a project that selects servers, the engine owns the entries named after any MCP server manifest of the run. It keeps every other entry, removes an owned entry the project does not select, rewrites an owned entry in full, and never deletes an MCP config file, `replace: true` included.
- Every edit is verified before it is written: the file must parse, foreign content must stay unchanged, and the owned entries must be exactly the selected servers; otherwise the file is left untouched and the project fails for that tool. A rewritten file keeps its permission bits. A file is written only inside the project once links are resolved, a linked parent directory such as `.codex` included; anything else at the path that is not a regular file fails that project and tool without being opened.
- The four MCP config files hold plain values resolved on one machine. They are gitignored in this repository; add them to the `.gitignore` of every other project that selects servers. See [what a deploy lets a tool start](07_mcp/README.md#what-a-deploy-lets-a-tool-start) before you select a server.
- User scope, per-agent attachment, Windsurf, tool allow and deny lists, and a secrets manager are not supported yet; see [PLANNED_FEATURES.md](PLANNED_FEATURES.md#mcps).

## Creating a Project

```yaml
id: my-project
description: What this project is
context:
  overview: |
    Free-form description injected into every generated context file.
  rules:
    - "Project-wide rule."
  documentation:
    readme: README.md
    per_topic:
      main_docs:
        prompts: 03_prompts/README.md
    additional:
      - path: docs/architecture.md
        description: "System design"
deploy:
  directory: "/absolute/path/to/target"   # may reference config env_vars, e.g. "${PROJECTS_FOLDER}/my-app"
  replace: false
  # tools: [claude, cursor]    # optional; omitted here so this example deploys through every configured tool
  agents:
    filter:
      - type: tags
        tags: [development, documentation]
  prompts:
    filter:
      - type: whitelist
        ids: [docs-write-readme]
  rulesets:
    filter:
      - type: tags               # select first...
        tags: [development]
      - type: blacklist          # ...then trim that selection
        ids: [windsurf-defaults]
  fragments: {}
  skills: {}
  mcps: {}                       # MCP servers are opt-in: {} selects every one, no block selects none
  features: {}
metadata:
  version: 1.0.0
  tags:
    - tooling
```

`context.documentation` is required, though every field inside it is optional.
An omitted or empty filter — `fragments: {}` above — lets everything through.

### Filters are order-sensitive

Filter types are exactly `tags`, `whitelist`, and `blacklist`; the discriminator key is `type`.
They are applied by folding over a selection that **starts empty**:

- `tags` adds every manifest carrying any of the listed tags
- `whitelist` adds every manifest whose `id` is listed
- `blacklist` removes listed ids from whatever has been selected so far

So a `filter` list containing **only** a `blacklist` selects nothing at all, and a `blacklist` placed before the `tags` or `whitelist` it is meant to trim has no effect.
Always put `blacklist` last.

This is a common cause of a manifest silently disappearing from a deploy.
It also causes `No rulesets match pattern ... excluded by project filter`: a ruleset an agent references must itself survive the project's `rulesets` filter, or the entire agent fails to export.

Relative `deploy.directory` values resolve against `--working-dir`, which `deploy.sh` sets to this repository's root, so `.` is that root — the same base the `locations` paths of `config.yml` use.
A value that is `~` or starts with `~/` resolves against the home directory of the user running the engine instead. `~user` is not expanded: it is read as a relative path whose first folder is named `~user`.
Prefer an absolute path, or one under `~/`, for anything else.

Any `${NAME}` reference in `deploy.directory` — or in a `locations` entry — is expanded first, from the `env_vars` of `config.local.yml`, then `config.yml`, then the environment. Because expansion precedes the rule above, a variable can supply the absolute base. A variable declared nowhere fails the run before anything is deployed. See [README.md](README.md#path-variables).

### Restricting a project to some tools

`deploy.tools` narrows a project to a subset of the tools configured for the run, using the same six keys: `windsurf`, `antigravity`, `github_copilot`, `cursor`, `claude`, `codex`.
It only ever narrows — it cannot add a tool the run does not configure.
The run's tool list is `tools:` in `config.yml`, unless `config.local.yml` declares its own `tools:`, which replaces that list wholesale rather than merging into it.

- **Omitted** — the project deploys through every configured tool. This is the default.
- **Listed** — the project deploys through the listed tools only, and the other configured tools skip it.
- **`tools: []`** — the project deploys through no tool at all. Emptiness restricts to nothing; only omission means "all".
- **`tools:` with nothing under it** — identical to omitting the key, so the project deploys through every configured tool. Commenting out the last entry under a `tools:` key therefore widens the project back to all tools rather than narrowing it to none.

Note that this is the one list in a project manifest whose emptiness *subtracts*: an empty or omitted `filter` lets everything through, but an empty `deploy.tools` lets nothing through.

Naming a tool the run does not configure is not an error: the project deploys through the tools both lists agree on, and the engine logs a warning naming the project and the unavailable tool.
This keeps one project manifest usable across runs that configure different tools.

Narrowing only stops future writes; it does not retract what the de-selected tools already wrote.
Artifacts a tool generated before it was de-selected stay in the target directory and must be removed by hand, and `replace: true` does not clean them up either — a de-selected tool never runs, so it never gets the chance to delete its own directory.
This is deliberate: narrowing is usually what someone does when another workflow takes ownership of that directory, and deleting it from under them would be the more dangerous default.

## Creating a User Deployment

A `user.yml` deploys into the per-user configuration of a tool (`~/.claude/`, `~/.codex/`) instead of into a project directory.
It lives in its own directory under a configured `deployments` location, exactly like a `project.yml`: **the filename names the kind, the directory names the instance**, and there is no `type:` field.

```yaml
# 09_deployments/globals/user.yml
id: globals
description: My global AI tool setup
tools:                        # optional; omitted = every tool configured for the run
  - claude
  - codex
replace: false                # optional, default false
rulesets:
  filter:
    - type: tags
      tags: [global]
agents:
  filter:
    - type: whitelist
      ids: []                 # an empty whitelist selects nothing
prompts: {}                   # an omitted or empty filter selects everything
skills: {}
fragments: {}
metadata:
  version: 1.0.0
```

A user deployment has **no** `context`, **no** `deploy` block, **no** `directory`, and **no** `features` — the destination is each tool's canonical per-user location, and a feature belongs to the project whose directory it lives under.
Everything else works as it does in a project manifest: the same three filter types with the same order sensitivity, and the same `tools` semantics (omitted means all, `[]` means none, a tool the run does not configure is warned about and narrowed away).

Destinations, relative to `--user-home`:

| Artifact | `claude` | `codex` |
| --- | --- | --- |
| rulesets | `~/.claude/CLAUDE.md` | `~/.codex/AGENTS.md` |
| agents | `~/.claude/agents/<id>.md` | `~/.codex/skills/agent-<id>/SKILL.md` |
| prompts | `~/.claude/commands/<id>.md` | `~/.codex/skills/prompt-<id>/SKILL.md` |
| skills | `~/.claude/skills/<id>/SKILL.md` | `~/.codex/skills/skill-<id>/SKILL.md` |

A skill's companion `files` are copied next to the generated `SKILL.md`, exactly as in project scope.

`windsurf`, `antigravity`, `github_copilot`, and `cursor` have no user-scope layout yet; a manifest naming one is deployed for the other tools, and the run logs the tool it skipped.

**The engine owns the instructions file.**
`~/.claude/CLAUDE.md` and `~/.codex/AGENTS.md` are generated from the manifest — a heading, its `description`, and a `## Rules` list of every selected ruleset's rules — and are overwritten on every deploy.
Hand edits are lost, including what Claude Code's `#`-remember shortcut appends. Edit the YAML and redeploy instead. Auto-memory under `~/.claude/projects/.../memory/` is untouched.

Limitations to know before you rely on it:

- Removing an artifact from the manifest leaves its previously deployed copy in the home until you delete it by hand — there is no ledger of what was written.
- `replace: true` deletes and rewrites the directory of each artifact this manifest deploys (Claude: skills; Codex: skills, agents, prompts) and overwrites single-file artifacts. Parent directories such as `~/.claude/skills/` and hand-made neighbours are never touched.
- A directory to be replaced that is a symbolic link is removed as a link when it leads inside the skills folder of the tool or leads nowhere; one leading to an existing folder or file outside that folder, or to that folder itself, fails the run before anything is written.
- Two user deployments selecting the same tool contend for its one instructions file. Neither writes it, the run fails naming both, and their other artifacts are still deployed. Give each tool a single deployment, or narrow the `tools` lists.

## Creating a Feature

Features live in `features/<name>.yml` beside a project's `project.yml`.

```yaml
id: my-feature
description: What this feature adds
context:
  overview: |
    Why this feature exists.
  architecture: |
    Which components are involved.
  dependencies:
    - "some-library"
  files:
    - "path/to/file.kt"
prompt: |
  Implement the thing.
acceptance_criteria:
  - "All existing tests pass"
constraints:
  - "Use JUnit 5 + AssertJ for tests"
metadata:
  version: 1.0.0
  tags:
    - filtering
```

## Referencing Rulesets and Fragments

`rulesets` and `fragments` entries are **regular expressions matched against the manifest `id`**:

```yaml
rulesets:
  - base                  # exact id
  - coding-language-.*    # every coding-language-* ruleset
```

A pattern matching nothing aborts the export with an error naming the pattern, and telling you whether a match exists but was excluded by the project filter.

## Variable Syntax

Declare variables in `variables`, then reference them as `{{name}}` in `content`:

```yaml
variables:
  - name: diff
    required: true
    description: "The git diff to summarize"
content: |
  Summarize this change:
  {{diff}}
```

The engine does **not** render templates.
`content` is written out verbatim, and the declared variables are listed in a `## Variables` section of the generated file.
The one exception is the GitHub Copilot adapter, which rewrites `{{name}}` into `${input:name:description}` for declared variables only.

Anything else in Mustache-like syntax — conditional blocks such as `{{#optional}}...{{/optional}}`, or partials such as `{{> file}}` — is passed through as literal text.
Use `fragments` for shared content; there is no include mechanism.

## Directory Structure

```
├── 01_rulesets/     # Rulesets (nested freely; only `id` matters)
├── 02_fragments/    # Reusable content blocks
├── 03_prompts/      # Prompts
├── 04_skills/       # Skills (<id>.yml, or <dir>/skill.yml)
├── 05_agents/       # Agents
├── 07_mcp/          # MCP servers (<id>.yml)
├── 09_deployments/  # Deployments: <deployment>/project.yml or user.yml (+ features/)
├── 90_docs/         # Reference documentation
├── ai-tools-engine/ # The Kotlin engine
├── config.yml       # Engine configuration (locations + tools + env_vars)
└── config.local.yml # Machine-local overrides (gitignored)
```

`08_recipes/`, `20_evals/`, and `21_redteam/` hold retained source of truth, but the engine does not read them.

## Common Workflows

### Add a New Agent

1. Create `05_agents/my-agent.yml`
2. Reference existing rulesets and fragments by `id` or pattern
3. Make sure it will pass the target project's filter — add a matching tag, or whitelist the id in `project.yml`
4. Validate with `./deploy.sh --dry-run`
5. Run `./deploy.sh`
6. Check the generated `.claude/agents/my-agent.md` in the project's `deploy.directory`

### Modify a Ruleset

1. Edit the ruleset under `01_rulesets/`
2. Bump `metadata.version` if you change behaviour
3. Validate with `./deploy.sh --dry-run`
4. Run `./deploy.sh` — every agent, prompt, and skill referencing it is rewritten

### Add a Skill That Ships Files

1. Create `04_skills/my-skill/skill.yml`
2. Put the extra files in `04_skills/my-skill/`, and list them under `files`
3. Run `./deploy.sh` — the files are copied next to the generated `SKILL.md`

### Add a Pointer Skill

1. Make sure the folder holds a `SKILL.md` whose frontmatter declares `name` and `description`, and that it lies outside every directory a deployment writes to
2. Create `04_skills/<id>/skill.yml` with `id` equal to that `name`, `source` pointing at the folder, and `metadata` with a version and the tags your deployments select on
3. Validate with `./deploy.sh --dry-run`
4. Run `./deploy.sh` — the body of `SKILL.md` and every other file of the folder are deployed; edit them in their own repository, never in the generated copy

### Add an MCP Server

1. Create `07_mcp/<id>.yml`, inline or as a pointer at a `server.json` — see [Creating an MCP Server](#creating-an-mcp-server)
2. Mark every token, password, and key file `secret: true`; put machine-specific plain values under `env_vars` in `config.local.yml`
3. Give it a tag the `mcps` filter of your project selects — `ai-tools` for this repository
4. Validate with `./deploy.sh --dry-run`; it names each MCP config file it would write and warns about every secret the environment does not set
5. Run `./deploy.sh`, and export the secret variables before starting the tool

### Add a Rule to Every Project You Work On

1. Add the rule to a ruleset that the `rulesets` filter of your `user.yml` selects — for this repository, a ruleset tagged `global`
2. Try it out first with `./deploy.sh --user-home /tmp/try`, and read `/tmp/try/.claude/CLAUDE.md`
3. Run `./deploy.sh` — `~/.claude/CLAUDE.md` and `~/.codex/AGENTS.md` are rewritten from the manifest, so never edit them directly

## Validation

```bash
./deploy.sh --dry-run
```

A dry run does everything a deploy does except write: every manifest is decoded strictly (an unknown key, a missing required field, or a malformed version fails the run naming the file), every ruleset, fragment, and skill-file reference is resolved, every skill `source` folder and every MCP `server.json` is read, the plain variables of every MCP server a project selects are resolved, and the log names each artifact and MCP config file a real run would write and where. A secret MCP variable of a selected server that the environment of the run does not set is warned about, by name only, and does not fail the run.
The exit status is the one a deploy would have had.

A plain `./deploy.sh` validates the same way, but a successful run also deploys into every configured project and into your home. Do not use it just to validate.

There are no JSON schemas: the data classes in `ai-tools-engine/engine/.../models/` are the only schema a manifest has.

## Security

Never commit API keys, passwords, tokens, or PII.
`${VAR}` is interpolated in declared paths — `locations.*` in the config files, `deploy.directory` in `project.yml`, and the `source` of a pointer skill or pointer MCP server — and in the transport of an MCP server manifest. Everywhere else, including all other generated content, a `${VAR}` is emitted literally rather than resolved.
In an MCP server manifest, mark every secret `secret: true`: the engine never reads its value and writes only a reference each tool resolves from its own environment. A variable marked `secret: false` is read from `env_vars` of the config files only, never from the environment of the run, and written into the generated MCP config files as its value. No failure message repeats a value or the content of a file.
Keep machine-local paths and settings in `config.local.yml`, which is gitignored — declare a machine-specific base path as an `env_vars` variable there and reference it from the versioned manifests.

## Getting Help

- [README.md](README.md) - overview, workflow, and what is not implemented
- [90_docs/STYLE_GUIDE.md](90_docs/STYLE_GUIDE.md) - writing prompts and rules
- [90_docs/TOOLS.md](90_docs/TOOLS.md) - per-tool integration details
- [PLANNED_FEATURES.md](PLANNED_FEATURES.md) - what is intended next
- Examples in each numbered directory, and in [91_examples/](91_examples/)

## Troubleshooting

**`Missing default config file`**: the engine could not find `config.yml` in `--working-dir`. Run `./deploy.sh` from the repository root.

**`Unknown key` / `Property 'x' is required`**: the manifest has a field the model does not define, or is missing a required one. Compare against the templates above.

**`Invalid version format`**: `metadata.version` is not `MAJOR.MINOR.PATCH` with an optional `-SUFFIX`. The message names the manifest file that carries it.

**`No rulesets match pattern 'x'`**: the pattern matched nothing. The message lists similar available ids, and flags rulesets excluded by the project's filter.

**`Unresolved variable 'X'`** / **`Cannot resolve the deploy directory of N project(s)`**: a `${X}` reference in a `locations` entry, a `deploy.directory`, or the `source` of a pointer skill or pointer MCP server names a variable no `env_vars` map and no environment variable declares. The message names the variable and where it was read from. Nothing is deployed until every project's directory resolves. For a `source`, the message starts with `Failed to load <manifest>:`, and the run stops before anything is deployed.

**`Refusing to deploy: N path(s) the run would write or delete overlap the source folder of a pointer skill; nothing was written`**: a path the run would write a skill to, or a directory `replace: true` would delete, is, lies inside, or contains the source folder of a pointer skill once symbolic links are resolved, or holds a symbolic link leading to such a path — typically because the generated skill directory, such as `~/.claude/skills/<id>`, is a link to the source folder. Each line below the headline names the pointer skill, its manifest, its source folder, the tool, the deployment, the path, and what the deployment would do to it. Nothing is written, and `--dry-run` fails the same way. Follow the advice at the end of the line: remove the link or folder that leads there, deselect the skill, move the source folder out of the replaced directory, move the directory the deployment deploys to out of the source folder (when the line says the path lies inside the source folder `once the link '<link>' is removed`), or turn off `replace` for that deployment. See [Pointer Skill](#pointer-skill) for what is checked.

**`Refusing to deploy: N folder(s) inside a directory the run would delete to replace it cannot be read; nothing was written`**: a directory that `replace: true` deletes, in a project or in the home, is or holds a folder the user running the deploy cannot read, so the delete would stop midway. Each line below the headline reads `<tool> would delete '<path>' for <deployment> to replace it, but cannot read '<folder>', so the delete would stop midway.` Nothing is written, and `--dry-run` fails the same way. Make that folder readable and writable, remove it, or turn off `replace` for that deployment. An empty folder that cannot be read is refused too, because the delete never removes a folder it cannot open. A replaced directory that is itself a symbolic link is not checked for unreadable folders, because the deploy never opens what it leads to. In a project, the deploy unlinks it. In a user deployment, the deploy unlinks it when it leads inside the skills folder of the tool or leads nowhere, and otherwise refuses it before anything is written with `Refusing to replace '...' for '...': it is a symbolic link that leads to '...'`. For such a directory, only the link itself is compared with the source folders of pointer skills: where it sits and where it leads, not the links in the folder it leads to.

**`Cannot replace the <tool> files of project '<id>' in '<directory>': deleting '<entry>' failed (<exception>)`** / **`Cannot replace the <tool> files of user deployment '<id>' under '<home>': deleting '<entry>' failed (<exception>)`**: a replacing deploy could not delete an entry, typically a file in a folder that is not writable. The message ends with `The run stopped here; make that path deletable and deploy again.` The run stops at that entry and leaves the directory partly deleted. Projects are exported before user deployments: when a user deployment fails, every project of the run, and the user-scope files exported before the failed one, are already written; when a project fails, the projects and tools exported before it are. `--dry-run` deletes nothing, so it does not report this.

**`MCP server '<id>' needs the variable '<NAME>' ...`**: a required plain MCP variable is not declared under `env_vars:`. Declare it in `config.local.yml`, mark it `required: false`, or mark it `secret: true` to pass it from the environment of the tool; exporting it in the shell of the deploy does not help. The MCP config files of the projects selecting that server are not written, everything else is, and the run exits non-zero.

**`MCP server '<id>' references the secret variable '<NAME>' in ...`**: a secret is used where a tool would need its value in the file. See [Creating an MCP Server](#creating-an-mcp-server) for where a secret may appear. It comes from loading, prefixed with `Failed to load <file>:`, and stops the whole run before anything is written. The same refusal ending in `where it would have to be written as a value.` is a safeguard behind loading that a manifest cannot reach; if you see it, report it.

**`... is not valid JSON at offset <n>, so the engine leaves it untouched`** / **`... is not valid TOML at line <n>`** / **`... declares the key '<key>' twice`** / **`... defines the MCP server '<id>' ... other than as a table`**: an existing MCP config file cannot be edited without losing part of it. Fix or remove it, and deploy again. The other artifacts of the run are still written, and the run exits non-zero. More messages are listed in [07_mcp/README.md](07_mcp/README.md#troubleshooting).

**`<tool> has no MCP support in this engine`**: a project selects MCP servers and the run configures `windsurf` or `antigravity`. The servers are deployed for the other tools.

**`Export failed for N manifest(s)`**: N manifests could not be exported; the list below the headline names each one, together with every tool it failed for. The run still exports everything else before reporting, and exits non-zero.

**`'locations.projects' ... was renamed to 'locations.deployments'`**: a config file still declares the retired key. Rename it — those directories now hold both `project.yml` and `user.yml` manifests. The run fails rather than dropping the list silently, which would deploy nothing while exiting successfully.

**`Found no deployment manifest under [...]`**: no `project.yml` and no `user.yml` was found under the `deployments` locations. Nothing was deployed. A directory that does not exist reads the same as an empty one here, so a mistyped path in `config.local.yml` produces this too; check the absolute paths the message lists.

**`... is deployed by more than one user deployment`**: two `user.yml` manifests select the same tool and therefore claim its single instructions file. Neither writes it. Give each tool one deployment, or narrow their `tools` lists.

**`... has no user-scope layout in this engine`**: a `user.yml` names a tool whose per-user layout is not implemented (everything except `claude` and `codex`). The manifest still deploys for the other tools it names.

**`Invalid manifest id '...'`**: an id must name a single file or directory — no path separators, no `.` or `..`, not empty — because it becomes the name of what the adapters write. The check runs at load time, for every manifest kind, so the run fails before anything is written and the message names the file to fix.

**`Refusing to replace '...' for '...': it is a symbolic link that leads to '...', which is not inside '...'`**: a user deploy with `replace: true` would rewrite a directory in the home, such as `~/.claude/skills/<id>`, that is a symbolic link leading outside the skills folder of the tool, typically a skill installed by hand as a link to a checkout. The deploy judges the link by where it leads, so it refuses it rather than unlinking it; a link that leads inside the skills folder, or leads nowhere, is unlinked instead. The run fails before anything is written, and `--dry-run` fails the same way. Remove the link, or turn off `replace` for that deployment.

**`Refusing to replace '...' for '...': it is not inside '...'`**: a user deploy with `replace: true` found an artifact path in the home that is not a symbolic link and lies outside the directory it owns, or is that directory itself. Id validation rejects such an id before anything is written, so this points at an id that bypassed it. Give the manifest an id that names a single directory, as the message advises. The run fails before anything is written, and `--dry-run` fails the same way.

**Manifest changes do not show up**: check the project's filters in `project.yml`, or the filters in `user.yml` for the user scope. A manifest with no matching tag and no whitelist entry is silently skipped.

**`Build was configured to prefer settings repositories over project repositories`**: a global Gradle init script in `~/.gradle/` registers repositories, which this build rejects. `./setup.sh` detects this and works around it temporarily.

## Tips

- Keep IDs stable once published — they are the only thing referenced
- Version semantically, and bump on behaviour changes
- Write clear descriptions; they become the `description` frontmatter every tool shows in its picker
- Use rulesets and fragments to avoid duplication
- Prefer tag filters over whitelists so new manifests are picked up automatically
- Deploy to a scratch directory first when experimenting with a new project manifest
