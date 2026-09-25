# Deployments: Project and User AI Tool Configurations

> **Generate and deploy AI tool configurations, into a project directory or into your own user scope**

This directory holds the deployment manifests of the repository. A directory containing a `project.yml` deploys into
a project directory; a directory containing a `user.yml` deploys into the user scope of each tool it names.

Everything below describes the current Kotlin engine, which is driven by a single command, `./deploy.sh`.
For the field-by-field manifest reference see [../QUICKREF.md](../QUICKREF.md), and for the generated artifacts of each tool see [../README.md](../README.md#tool-output).

---

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Directory Layout](#directory-layout)
- [Configuration](#configuration)
- [Project Manifests](#project-manifests)
- [User Deployments](#user-deployments)
- [Features System](#features-system)
- [Duplicate Ids](#duplicate-ids)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [See Also](#see-also)

---

## Overview

A deployment answers one question: *which of the manifests in this repository land where, for which tools?*

There are two kinds of deployment, and **the filename names the kind while the directory names the instance**:

| File | Kind | Deploys into |
| --- | --- | --- |
| `project.yml` | project deployment | the `deploy.directory` it declares |
| `user.yml` | user deployment | the per-user configuration of each tool it names (`~/.claude/`, `~/.codex/`) |

There is no `type:` discriminator inside the YAML, and no separate list of locations per kind: the engine walks every directory under `locations.deployments` and picks the schema by filename.

Both kinds select their content the same way — filters over `agents`, `prompts`, `rulesets`, `fragments`, and `skills` — and both may narrow themselves to a subset of the tools configured for the run.
Only a project has a `context` block, a `deploy.directory`, and `features/`.

Every deployment manifest found is processed on every `./deploy.sh` run.
There is no command to deploy a single one; narrow the `locations.deployments` list in `config.local.yml` instead.

---

## Quick Start

### 1. Create a deployment

Create a directory named after the deployment, and put one manifest in it:

```bash
# a project deployment
mkdir -p 09_deployments/my-project
$EDITOR 09_deployments/my-project/project.yml

# a user deployment
mkdir -p 09_deployments/my-globals
$EDITOR 09_deployments/my-globals/user.yml
```

Copy the shape from [`ai-tools/project.yml`](ai-tools/project.yml) or [`globals/user.yml`](globals/user.yml), or from the templates in [../QUICKREF.md](../QUICKREF.md).

Manifests are parsed strictly: an unknown key, a missing required field, or a malformed `metadata.version` fails the run naming the offending file.

### 2. Try it out

Validate the manifests first, without writing anything:

```bash
./deploy.sh --dry-run
```

A dry run loads, filters, and renders every manifest exactly as a deploy does, but it creates, deletes, and modifies nothing — not in the project directories, not in your home.
It fails on what a deploy fails on, with one exception: a file a tool cannot write. A dry run finds a broken tool directory only through the MCP config file in it, so a broken `.claude` or `.github`, and any write the file system refuses, such as into a read-only directory, are found only by a real deploy; see [README.md](../README.md#common-workflow).
In a real deploy, such a file fails only that project and tool, and every other tool and project is still deployed. In a user deployment, a file that cannot be written stops the run instead, and the CLI prints `'<path>' cannot be written (<class>[: <reason>])` as one line; the projects and the user-scope files exported before it are already written.
It logs the absolute path of every artifact a deploy would write.

To read the produced files, a user deployment can be pointed at a harmless home instead:

```bash
./deploy.sh --user-home /tmp/try
```

Every argument given to `deploy.sh` is forwarded to the engine.
A project deployment has no equivalent switch — point its `deploy.directory` at a scratch directory while experimenting, or stay with `--dry-run`.

### 3. Deploy

```bash
./deploy.sh
```

Run it from the repository root: the current directory is passed to the engine as `--working-dir`, and that is where `config.yml` is read from.
The exit status is the engine's own, so a manifest that fails to export fails the script too.

---

## Directory Layout

Manifest directories sit **directly** under `09_deployments/`:

```
09_deployments/
├── README.md
├── ai-tools/
│   ├── project.yml            # deploys onto this repository itself
│   └── features/
│       ├── export-global-file.yml
│       └── ruleset-filtering.yml
└── globals/
    └── user.yml               # deploys into ~/.claude and ~/.codex
```

There is no `global/` and `local/` split.
A manifest that should not be versioned here lives in a directory outside this repository, added to `locations.deployments` by the gitignored `config.local.yml` — see [Configuration](#configuration).

The search is recursive, so a machine-private location is free to group its manifests in subdirectories.

---

## Configuration

**Configurable deployment locations** (`config.yml` + `config.local.yml`, in the repository root)

The engine searches for deployments in the directories listed under `locations.deployments`.
A project is any directory containing a `project.yml`, a user deployment any directory containing a `user.yml`; the search is recursive.

**Configuration Files:**

1. **`config.yml`** (versioned, shared) - the deployment locations used by everyone
2. **`config.local.yml`** (gitignored, personal) - your machine-local overrides

Both live in the repository root. There is no `15_config/` directory, and the engine does not read one.

**Default Configuration (`config.yml`):**

```yaml
locations:
  deployments:
    - "09_deployments"
```

**Adding Personal Sources (`config.local.yml`):**

```yaml
locations:
  deployments:
    - "09_deployments"
    - "../ai-tools-projects/projects"
```

This key was named `locations.projects` before the second kind of manifest existed.
A config file still declaring the old name fails the run with a message naming the replacement, rather than having its list dropped as an unknown key — which would deploy nothing while exiting successfully.

**Configuration Merging:**

Each key is **replaced wholesale**, not merged element-wise.
If `config.local.yml` defines `locations.deployments`, that list fully replaces the one in `config.yml` - so you must repeat any default entry you still want, as the example above repeats `09_deployments`.

The same applies to the other `locations` entries and to the `tools` list.
The one key that does merge element-wise is `env_vars`, described below.

**Path Types:**

- **Relative**: resolved against the directory passed to `--working-dir`, i.e. the repository root (e.g. `09_deployments`)
- **Absolute**: used as given (e.g. `/home/user/projects`)

**Path Variables:**

Every entry under `locations`, every project's `deploy.directory`, and the skill-level `source` of a pointer skill may reference a variable as `${NAME}`.
Variables are declared under the top-level `env_vars` key of either config file:

```yaml
# config.local.yml
env_vars:
  PROJECTS_FOLDER: /home/alice/projects
```

```yaml
# project.yml of a project that lives under that folder
deploy:
  directory: "${PROJECTS_FOLDER}/custom-ai-tools"
```

A name is resolved from the `env_vars` of `config.local.yml` first, then from those of `config.yml`, then from the environment of the process.
Unlike the `locations` lists, the two `env_vars` maps merge per variable: a local declaration overrides that one variable, and variables declared in only one of the files survive.

This is what makes a `project.yml` shareable. The absolute base each machine deploys under lives in the gitignored `config.local.yml`, while the versioned manifest names only the variable - so a colleague whose projects sit under a different root runs the same manifest without editing it.

Substitution happens *before* the relative-vs-absolute decision above, so a variable may supply the absolute base of a value whose remainder is written as a relative fragment.

After substitution, a value that is `~` or starts with `~/` resolves against the home directory of the user running the engine, so a variable may also hold a path such as `~/Documents/Projects`. `~user` is not expanded: it is read as a relative path whose first folder is named `~user`. A `~` anywhere but at the start is an ordinary part of the name.

A reference to a variable that is declared nowhere aborts the run with an error naming the variable, and for a location also the config file the offending value came from.
Every project's `deploy.directory` is resolved before any project is deployed, so a typo cannot leave you with some projects deployed and others not, and nothing is ever written to a directory literally named `${...}`.

Values must already be fully expanded: a variable whose own value contains `${...}`, or an expansion that assembles a new reference together with the surrounding text, is an error rather than a second substitution pass.
Groups that are not valid references - `${1ST}`, `${A-B}`, `$NO_BRACES` - pass through as literal text.
A name consists of letters, digits, and underscores, and may not start with a digit.

A user deployment declares no path at all, so nothing in a `user.yml` is substituted.
Its destination comes from `--user-home`, whose `${...}` references are not substituted. A value that is `~` or starts with `~/` resolves against the home directory, as in any declared path; an unquoted one is usually expanded by the shell first anyway.

Configs without `env_vars`, and paths without references, behave exactly as they did before.

**When Configuration Is Used:**

Every deployment found under these locations is processed on each `./deploy.sh` run: a project into its own `deploy.directory`, a user deployment into the user scope of the tools it names.
There is no command to list, generate, or deploy a single one.

---

## Project Manifests

A project deployment is a directory holding a `project.yml`, optionally with a `features/` directory beside it.

```yaml
id: my-project
description: What this project is
context:
  overview: |
    Free-form description injected into every generated context file.
  documentation:
    readme: README.md
deploy:
  directory: "${PROJECTS_FOLDER}/my-app"   # or an absolute path, or "." for this repository
  replace: false
  # tools: [claude, codex]                 # optional; omitted = every configured tool
  agents:
    filter:
      - type: tags
        tags: [development]
  prompts:
    filter:
      - type: whitelist
        ids: [docs-write-readme]
  rulesets: {}
  fragments: {}
  skills: {}
  mcps: {}                                 # MCP servers of 07_mcp/ are opt-in: {} = every one, no block = none
  features: {}
metadata:
  version: 1.0.0
```

Key points, all documented in full in [../QUICKREF.md](../QUICKREF.md#creating-a-project):

- `deploy.directory` decides where the generated files land. A relative value resolves against `--working-dir`, so `.` means this repository's root; a value that is `~` or starts with `~/` resolves against the home directory.
- `deploy.replace: true` wipes the output directories each tool owns inside `deploy.directory` before writing them again: `.claude`, `.windsurf`, and `.agent` as a whole; for Codex only `.codex/skills` and `.codex/features`, for Cursor only `.cursor/rules`, `.cursor/commands`, and `.cursor/features`, and for GitHub Copilot only `.github/prompts`, `.github/instructions`, and `.github/agents`. It never deletes an MCP config file (`.mcp.json`, `.vscode/mcp.json`, `.cursor/mcp.json`, `.codex/config.toml`), whose server entries the engine owns one by one. There is no backup and no auto-commit; files are overwritten in place, each written atomically through a temporary file. The wipe removes a symbolic link inside those directories, or such a directory that is itself a link, as a link and never touches what it leads to, and a run whose wipe would reach the source folder of a pointer skill fails before anything is written; see [04_skills/README.md](../04_skills/README.md#pointer-skill).
- `deploy.tools` narrows the project to a subset of the tools configured for the run. Omitting it means all of them, `[]` means none, and naming a tool the run does not configure is a warning rather than an error.
- Filters fold over a selection that **starts empty**: `tags` and `whitelist` add, `blacklist` subtracts, so `blacklist` must come last and an omitted or empty `filter` lets everything through.
- A ruleset or fragment an agent references must itself survive the project's `rulesets` / `fragments` filter, otherwise that agent fails to export.
- `deploy.mcps` selects the MCP servers of `07_mcp/` that are merged into the MCP config file of each tool. MCP servers are opt-in: omitting the block selects no server, unlike every other kind, and a project that selects none never reads or writes an MCP config file; `mcps: {}` selects every server. See [07_mcp/README.md](../07_mcp/README.md).

[`ai-tools/project.yml`](ai-tools/project.yml) is a worked example: it deploys onto this repository itself with `directory: "."`.

---

## User Deployments

A user deployment is a directory holding a `user.yml`.
It installs a filtered selection of rulesets, agents, prompts, and skills into the per-user configuration of each tool it names, instead of into a project directory.

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

It carries none of the fields a user scope has no answer for:

- no `context` — a user scope has no repository, README, or per-topic documentation to describe
- no `directory` — the destination is each tool's canonical per-user location, under `--user-home`
- no `features` — a feature belongs to the project whose directory it lives under

Everything else behaves as it does in a project manifest: the same three filter types with the same order sensitivity, and the same `tools` semantics.

### Where it lands

Paths are relative to `--user-home`, which defaults to the home of whoever runs the deploy.

| Artifact | `claude` | `codex` |
| --- | --- | --- |
| rulesets → instructions file | `~/.claude/CLAUDE.md` | `~/.codex/AGENTS.md` |
| agents | `~/.claude/agents/<id>.md` | `~/.codex/skills/agent-<id>/SKILL.md` |
| prompts | `~/.claude/commands/<id>.md` | `~/.codex/skills/prompt-<id>/SKILL.md` |
| skills | `~/.claude/skills/<id>/SKILL.md` | `~/.codex/skills/skill-<id>/SKILL.md` |

Codex has one shape for everything it can be asked to do, so its agents and prompts are skill-shaped there too, told apart by the prefix of their directory — the same layout it writes inside a project.
A skill's companion `files` are copied next to the generated `SKILL.md`, exactly as in project scope.

`windsurf`, `antigravity`, `github_copilot`, and `cursor` have no user-scope layout in this engine yet.
A manifest naming one of them is not silently dropped: the run logs that the manifest is not deployed for that tool, and deploys it for the tools that do have a layout.

### The engine owns the instructions file

`~/.claude/CLAUDE.md` and `~/.codex/AGENTS.md` are generated from the manifest — a heading naming the deployment, its `description`, and a `## Rules` list holding the rules of every ruleset the manifest selected.
The engine **owns and overwrites these files on every deploy**, and logs the path each time, as a warning when it is replacing a file that already exists.

Anything written into them by hand is lost on the next deploy.
That includes what Claude Code's `#`-remember shortcut appends and what CLAUDE.md-editing tooling adds.
The workflow is: edit the ruleset YAML, redeploy.
Auto-memory is unaffected — it lives under `~/.claude/projects/.../memory/`.

Two user deployments that select the same tool both claim that tool's single instructions file.
No winner is picked: the file is left alone, the collision is reported naming both manifests, and the run fails — while the artifacts they do not contend for are still deployed.
Give each tool a single deployment, or narrow their `tools` lists.

### What a deploy touches, and what it leaves alone

A user deploy owns the paths of the artifacts it writes and nothing else.
The directories holding them — `~/.claude/skills/`, `~/.claude/agents/`, `~/.claude/commands/`, `~/.codex/skills/` — are shared with everything you installed by hand, so they are created when missing and never deleted wholesale.

With `replace: true`, the directory of each artifact this manifest deploys is deleted and rewritten (for Claude the skill directories, for Codex the skill, agent, and prompt directories), and single-file artifacts are overwritten in place.
A skill you wrote yourself, sitting beside the generated ones, survives every deploy.

Known limitations:

- Removing an artifact from a `user.yml` leaves its previously deployed copy behind in the home until you delete it by hand. The engine keeps no ledger of what it wrote, so it cannot tell a stale artifact from one you installed yourself.
- `replace: true` reaches per-artifact paths only. Parent directories and hand-made neighbours are never touched.
- A directory to be replaced that is a symbolic link is removed as a link when it leads inside the skills folder of the tool or leads nowhere; one leading to an existing folder or file outside that folder, or to that folder itself, fails the run before anything is written.

### Choosing the home

```bash
./deploy.sh --user-home /tmp/try
```

A relative `--user-home` resolves against `--working-dir`, the same base every other declared path of the run uses, rather than against the shell's current directory.
An empty value is rejected instead of resolved.
A home that does not exist yet is allowed — a first deploy onto a fresh machine creates it, and the run says so.

`deploy.sh` forwards every argument to the engine, with one exception: Gradle's `--args` cannot escape a double quote, so an argument containing one is refused rather than delivered as a different, still-plausible path.
Run the CLI directly for that case:

```bash
cd ai-tools-engine && ./gradlew :cli:run --args=...
```

---

## Features System

Features are project-scoped: they describe a slice of one project, and they exist only beside a `project.yml`.
A `user.yml` has no features.

### Creating a feature

Put the manifest in a `features/` directory next to `project.yml`:

```
09_deployments/my-project/
├── project.yml
└── features/
    └── user-authentication.yml
```

```yaml
id: user-authentication
description: JWT-based user authentication and session management
context:
  overview: |
    Handles user login, registration, and session management using JWT tokens.
  architecture: |
    Middleware-based, with token validation on protected routes.
  dependencies:
    - jsonwebtoken
  files:
    - src/auth/index.ts
prompt: |
  Implement the authentication flow described above.
acceptance_criteria:
  - "All existing tests pass"
constraints:
  - "Use JUnit 5 + AssertJ for tests"
metadata:
  version: 1.0.0
  tags:
    - auth
```

`id`, `description`, `prompt`, and `metadata` are required; `context`, `acceptance_criteria`, and `constraints` are optional.

### Deploying features

Features are exported during the normal `./deploy.sh` run, as one workflow or instruction file per tool — see the [tool output table](../README.md#tool-output) for the exact paths.

They are filtered like every other kind, through `deploy.features.filter` in `project.yml`.
Two features of the same project sharing an id keep that project from being exported at all, because a project that does not filter its features deploys all of them.

[`ai-tools/features/`](ai-tools/features/) holds two worked examples.

---

## Duplicate Ids

Manifest ids are the primary key of the whole engine, so two files declaring the same id are never resolved by picking a winner.
One run reports every collision it found, names the id and both files, and exits non-zero.

Ids are indexed **per kind**, so a `project.yml` and a `user.yml` may declare the same id — they are separate manifests of separate scopes.

How much a collision costs depends on the kind:

- Two projects, two user deployments, or two features of the same project sharing an id cost only the deployment(s) that carry them. Those are left unexported, everything else is deployed as usual, and the run still fails at the end.
- Two agents, prompts, rulesets, fragments, or skills sharing an id stop the whole run before anything is written. They are shared by every deployment, and one that does not filter that kind deploys all of it.

---

## Best Practices

1. **Name the directory after the deployment.** The filename says what kind it is; the directory is the only place the instance is named.
2. **Keep machine-specific paths out of the versioned manifests.** Declare the base as an `env_vars` variable in `config.local.yml` and reference it as `${NAME}` from `deploy.directory`.
3. **Try a user deployment against a scratch home first** with `--user-home`, and read the generated instructions file before deploying into your own.
4. **Never hand-edit `~/.claude/CLAUDE.md` or `~/.codex/AGENTS.md`** once a `user.yml` deploys them. Edit the rulesets and redeploy.
5. **Prefer tag filters over whitelists**, so a new manifest is picked up without editing every deployment.
6. **Put `blacklist` last**, or it trims a selection that is still empty and does nothing.
7. **Bump `metadata.version`** when a manifest's behaviour changes, and keep `metadata.updated` current.
8. **Remember that nothing is cleaned up for you** when you drop an artifact from a manifest: the previously deployed copy stays until it is deleted by hand.

---

## Troubleshooting

**`Missing default config file`** — the engine found no `config.yml` in `--working-dir`. Run `./deploy.sh` from the repository root.

**`'locations.projects' ... was renamed to 'locations.deployments'`** — a config file still declares the retired key. Rename it; the run fails rather than silently deploying nothing.

**`Found no deployment manifest under [...]`** — the configured directories hold no `project.yml` and no `user.yml`. The message lists the absolute paths that were searched.

**`Cannot resolve the deploy directory of N project(s)`** — a `${NAME}` in a `deploy.directory` names a variable no `env_vars` map and no environment variable declares. Nothing is deployed until every project's directory resolves.

**`Refusing to deploy: N path(s) the run would write or delete overlap the source folder of a pointer skill`** / **`Failed to load .../04_skills/<id>/skill.yml: ...`** — a skill or a replaced directory of a deployment reaches the source folder of a pointer skill, or a pointer skill cannot be read from its source folder. Both stop the run before anything is written, `--dry-run` included; see [04_skills/README.md](../04_skills/README.md#troubleshooting).

**`Refusing to deploy: N folder(s) inside a directory the run would delete to replace it cannot be read`** — a directory that `replace: true` deletes is or holds a folder the user running the deploy cannot read, so the delete would stop midway. The run stops before anything is written, `--dry-run` included. Make the folder readable and writable, remove it, or turn off `replace` for that deployment. An empty folder that cannot be read is refused too, because the delete never removes a folder it cannot open; remove such a folder yourself. A replaced directory that is itself a symbolic link is not checked for unreadable folders, because the deploy never opens what it leads to. In a project, the deploy unlinks it. In a user deployment, the deploy unlinks it when it leads inside the skills folder of the tool or leads nowhere, and otherwise refuses it before anything is written with `Refusing to replace '...' for '...': it is a symbolic link that leads to '...'`. For such a directory, only the link itself is compared with the source folders of pointer skills: where it sits and where it leads, not the links in the folder it leads to.

**`Cannot replace the <tool> files of project '<id>' in '...': deleting '...' failed`** / **`Cannot replace the <tool> files of user deployment '<id>' under '...': deleting '...' failed`** — a replacing deploy could not delete an entry, typically a file in a folder that is not writable. The message names the deployment, the tool, and the entry, and ends with `The run stopped here; make that path deletable and deploy again.` The run stops at that point and leaves the directory partly deleted. Projects are exported before user deployments: when a user deployment fails, every project of the run, and the user-scope files exported before the failed one, are already written; when a project fails, the projects and tools exported before it are. `--dry-run` does not delete, so it does not report this.

**`Export failed for N manifest(s)`** — the list below the headline names each manifest and every tool it failed for. Everything else was still exported, and the run exits non-zero.

**`No rulesets match pattern 'x'`** — the pattern matched nothing. The message says whether a match exists but was excluded by the deployment's `rulesets` filter.

**`... is deployed by more than one user deployment`** — two `user.yml` manifests claim the same tool's instructions file. Neither writes it. Give each tool one deployment, or narrow their `tools` lists.

**`... has no user-scope layout in this engine`** — a `user.yml` names a tool whose per-user layout is not implemented yet. It still deploys for the tools that have one.

**`Invalid manifest id '...'`** — an id must name a single file or directory: no path separators, no `.` or `..`, not empty. It becomes the name of what the adapters write, so the check runs at load time for every manifest kind, and the run fails naming the file before anything is written.

**`Refusing to replace '...' for '...': it is a symbolic link that leads to '...', which is not inside '...'`** — a user deploy with `replace: true` would rewrite a directory in the home, such as `~/.claude/skills/<id>`, that is a symbolic link leading outside the skills folder of the tool, typically a skill installed by hand as a link to a checkout. The deploy judges the link by where it leads, so it refuses it rather than unlinking it; a link that leads inside the skills folder, or leads nowhere, is unlinked instead. The run fails before anything is written, and `--dry-run` fails the same way. Remove the link, or turn off `replace` for that deployment.

**`Refusing to replace '...' for '...': it is not inside '...'`** — a user deploy with `replace: true` found an artifact path in the home that is not a symbolic link and lies outside the directory it owns, or is that directory itself. Id validation rejects such an id before anything is written, so this points at an id that bypassed it. Give the manifest an id that names a single directory, as the message advises. The run fails before anything is written, and `--dry-run` fails the same way.

**A manifest does not show up in the output** — check the filters of the deployment. A manifest with no matching tag and no whitelist entry is skipped silently, and a `blacklist` placed before the filter it was meant to trim removes nothing.

**A tool stopped receiving files, but its old files are still there** — narrowing `tools` only stops future writes. A de-selected tool never runs, so `replace: true` does not clean up after it either; remove what it left by hand.

---

## See Also

- [../README.md](../README.md) - overview, workflow, tool output, and configuration
- [../QUICKREF.md](../QUICKREF.md) - manifest templates and field reference
- [../90_docs/TOOLS.md](../90_docs/TOOLS.md) - per-tool integration details
- [../PLANNED_FEATURES.md](../PLANNED_FEATURES.md) - what is intended next
