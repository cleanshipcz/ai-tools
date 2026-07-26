# AI Tools - Quick Reference

Every template below matches the Kotlin data classes in `ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/`, which are the source of truth.
Manifests are parsed in strict mode: **an unknown key fails the run**, so do not add fields that are not listed here.

## Common Commands

```bash
# First-run setup: checks prerequisites, builds the engine
./setup.sh

# Generate and deploy configs for every project manifest
./deploy.sh

# What deploy.sh runs under the hood
cd ai-tools-engine && ./gradlew :cli:run --args="--working-dir <repository root>"

# Engine development
cd ai-tools-engine
./gradlew :cli:build     # compile + test + ktlint + detekt for the deploy path
./gradlew build          # everything, including :server (needs Node/npm)
./gradlew ktlintFormat   # auto-fix Kotlin formatting
```

The CLI has exactly one option, `--working-dir`.
There are no subcommands, no `--dry-run`, and no way to select a single project.

## File Naming Conventions

- **IDs**: kebab-case (e.g. `reviewer-code`, `docs-summarize-pr`)
- **Versions**: semantic versioning, `MAJOR.MINOR.PATCH` with an optional `-SUFFIX`
- **Files**: the filename does **not** have to match the `id`, and directory nesting is purely organisational — only `id` is ever referenced. For example `01_rulesets/coding/languages/coding-kotlin.yml` declares `id: coding-language-kotlin`.
- **Skills**: either `04_skills/<id>.yml`, or a directory `04_skills/<name>/` containing `skill.yml` plus any files it ships

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
  directory: "/absolute/path/to/target"
  replace: false
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

Relative `deploy.directory` values resolve against `ai-tools-engine/cli`, the working directory of the Gradle `:cli:run` task, so `../../` is this repository's root.
Prefer an absolute path for anything else.

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
├── 09_projects/     # Projects: <scope>/<project>/project.yml (+ features/)
├── 10_schemas/      # JSON schemas - STALE, not used for validation
├── 90_docs/         # Reference documentation
├── ai-tools-engine/ # The Kotlin engine
├── config.yml       # Engine configuration (locations + tools)
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

## Validation

There is no standalone validation command.
Manifests are validated when `./deploy.sh` runs, by strict YAML decoding: an unknown key, a missing required field, or a malformed version aborts the run with the offending file path.

The JSON schemas in `10_schemas/` have drifted from the Kotlin models and are **not** used by the engine.
Do not treat them as authoritative — the data classes in `ai-tools-engine/engine/.../models/` are.

## Security

Never commit API keys, passwords, tokens, or PII.
The engine performs no variable interpolation, so a `${VAR}` written into a manifest is emitted literally rather than resolved from the environment — it will not keep a secret out of the generated files.
Keep machine-local paths and settings in `config.local.yml`, which is gitignored.

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

**`Export failed for N manifest(s)`**: N manifests could not be exported; the list below the headline names each one, together with every tool it failed for. The run still exports everything else before reporting, and exits non-zero.

**Manifest changes do not show up**: check the project's filters in `project.yml`. A manifest with no matching tag and no whitelist entry is silently skipped.

**`Build was configured to prefer settings repositories over project repositories`**: a global Gradle init script in `~/.gradle/` registers repositories, which this build rejects. `./setup.sh` detects this and works around it temporarily.

## Tips

- Keep IDs stable once published — they are the only thing referenced
- Version semantically, and bump on behaviour changes
- Write clear descriptions; they become the `description` frontmatter every tool shows in its picker
- Use rulesets and fragments to avoid duplication
- Prefer tag filters over whitelists so new manifests are picked up automatically
- Deploy to a scratch directory first when experimenting with a new project manifest
