# AI Tools - Quick Reference

Every template below matches the Kotlin data classes in `ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/`, which are the source of truth.
Manifests are parsed in strict mode: **an unknown key fails the run**, so do not add fields that are not listed here.

## Common Commands

```bash
# First-run setup: checks prerequisites, builds the engine
./setup.sh

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

The CLI has two options besides `--help`, and no subcommands:

- `--working-dir` — the directory holding `config.yml`. Defaults to `.`; `deploy.sh` sets it to the directory it was started from.
- `--user-home` — the home a `user.yml` deploys under. Defaults to the home of the current user. A relative value resolves against `--working-dir`, an empty value is rejected, and a home that does not exist yet is created.

There is no `--dry-run` and no way to select a single manifest.
`deploy.sh` forwards every argument to the CLI, except one containing a double quote — Gradle's `--args` cannot escape it, so the script refuses the argument rather than delivering a different path. Run `./gradlew :cli:run` directly for that case.

## File Naming Conventions

- **IDs**: kebab-case (e.g. `reviewer-code`, `docs-summarize-pr`)
- **Versions**: semantic versioning, `MAJOR.MINOR.PATCH` with an optional `-SUFFIX`
- **Files**: the filename does **not** have to match the `id`, and directory nesting is purely organisational — only `id` is ever referenced. For example `01_rulesets/coding/languages/coding-kotlin.yml` declares `id: coding-language-kotlin`.
- **Skills**: either `04_skills/<id>.yml`, or a directory `04_skills/<name>/` containing `skill.yml` plus any files it ships
- **Deployments**: a directory under a `deployments` location holding a `project.yml` (deploys into a project directory) or a `user.yml` (deploys into the user scope of a tool) — the filename names the kind, the directory names the instance
- **IDs across kinds**: unique per kind, so a `project.yml` and a `user.yml` may share an id, while two `user.yml` files may not

## Shared Fields

Every manifest requires `id`, `description`, and `metadata`:

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
  - confluence-mcp-tools
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

A skill has only these fields.
There is no `command`, no `timeout_sec`, and no `outputs` — the engine generates documentation, it does not execute anything.

Each entry in `sections` is exactly one of `text`, `ruleset`, or `fragment`.
Each entry in `files` is either `{ path: ... }` or `{ source: ..., target: ... }`, resolved relative to the skill's own directory.

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
Prefer an absolute path for anything else.

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
├── 09_deployments/  # Deployments: <deployment>/project.yml or user.yml (+ features/)
├── 10_schemas/      # JSON schemas - STALE, not used for validation
├── 90_docs/         # Reference documentation
├── ai-tools-engine/ # The Kotlin engine
├── config.yml       # Engine configuration (locations + tools + env_vars)
└── config.local.yml # Machine-local overrides (gitignored)
```

`07_mcp/`, `08_recipes/`, `20_evals/`, and `21_redteam/` hold retained source of truth, but the engine does not read them.

## Common Workflows

### Add a New Agent

1. Create `05_agents/my-agent.yml`
2. Reference existing rulesets and fragments by `id` or pattern
3. Make sure it will pass the target project's filter — add a matching tag, or whitelist the id in `project.yml`
4. Run `./deploy.sh`
5. Check the generated `.claude/agents/my-agent.md` in the project's `deploy.directory`

### Modify a Ruleset

1. Edit the ruleset under `01_rulesets/`
2. Bump `metadata.version` if you change behaviour
3. Run `./deploy.sh` — every agent, prompt, and skill referencing it is rewritten

### Add a Skill That Ships Files

1. Create `04_skills/my-skill/skill.yml`
2. Put the extra files in `04_skills/my-skill/`, and list them under `files`
3. Run `./deploy.sh` — the files are copied next to the generated `SKILL.md`

### Add a Rule to Every Project You Work On

1. Add the rule to a ruleset that the `rulesets` filter of your `user.yml` selects — for this repository, a ruleset tagged `global`
2. Try it out first with `./deploy.sh --user-home /tmp/try`, and read `/tmp/try/.claude/CLAUDE.md`
3. Run `./deploy.sh` — `~/.claude/CLAUDE.md` and `~/.codex/AGENTS.md` are rewritten from the manifest, so never edit them directly

## Validation

There is no standalone validation command.
Manifests are validated when `./deploy.sh` runs, by strict YAML decoding: an unknown key, a missing required field, or a malformed version aborts the run with the offending file path.

The JSON schemas in `10_schemas/` have drifted from the Kotlin models and are **not** used by the engine.
Do not treat them as authoritative — the data classes in `ai-tools-engine/engine/.../models/` are.

## Security

Never commit API keys, passwords, tokens, or PII.
`${VAR}` is interpolated in declared paths only — `locations.*` in the config files, and `deploy.directory` in `project.yml`. Everywhere else, including all generated content, a `${VAR}` is emitted literally rather than resolved, so it will not keep a secret out of the generated files.
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

**`Unresolved variable 'X'`** / **`Cannot resolve the deploy directory of N project(s)`**: a `${X}` reference in a `locations` entry or a `deploy.directory` names a variable no `env_vars` map and no environment variable declares. The message names the variable and where it was read from. Nothing is deployed until every project's directory resolves.

**`Export failed for N manifest(s)`**: N manifests could not be exported; the list below the headline names each one, together with every tool it failed for. The run still exports everything else before reporting, and exits non-zero.

**`'locations.projects' ... was renamed to 'locations.deployments'`**: a config file still declares the retired key. Rename it — those directories now hold both `project.yml` and `user.yml` manifests. The run fails rather than dropping the list silently, which would deploy nothing while exiting successfully.

**`Found no deployment manifest under [...]`**: no `project.yml` and no `user.yml` was found under the `deployments` locations. Nothing was deployed. A directory that does not exist reads the same as an empty one here, so a mistyped path in `config.local.yml` produces this too; check the absolute paths the message lists.

**`... is deployed by more than one user deployment`**: two `user.yml` manifests select the same tool and therefore claim its single instructions file. Neither writes it. Give each tool one deployment, or narrow their `tools` lists.

**`... has no user-scope layout in this engine`**: a `user.yml` names a tool whose per-user layout is not implemented (everything except `claude` and `codex`). The manifest still deploys for the other tools it names.

**`Invalid manifest id '...'`**: an id must name a single file or directory — no path separators, no `.` or `..`, not empty — because it becomes the name of what the adapters write. The check runs at load time, for every manifest kind, so the run fails before anything is written and the message names the file to fix.

**`Refusing to replace '...': it is not inside '...'`**: a user deploy with `replace: true` found that one of its artifact paths in the home resolves outside the directory it owns — in practice a directory that has been replaced by a symlink pointing elsewhere. The run aborts; nothing was deleted by that check. Inspect the named path in the home, not the manifest.

**Manifest changes do not show up**: check the project's filters in `project.yml`, or the filters in `user.yml` for the user scope. A manifest with no matching tag and no whitelist entry is silently skipped.

**`Build was configured to prefer settings repositories over project repositories`**: a global Gradle init script in `~/.gradle/` registers repositories, which this build rejects. `./setup.sh` detects this and works around it temporarily.

## Tips

- Keep IDs stable once published — they are the only thing referenced
- Version semantically, and bump on behaviour changes
- Write clear descriptions; they become the `description` frontmatter every tool shows in its picker
- Use rulesets and fragments to avoid duplication
- Prefer tag filters over whitelists so new manifests are picked up automatically
- Deploy to a scratch directory first when experimenting with a new project manifest
