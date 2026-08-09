# Plan: Environment-variable substitution for declared paths

Run slug: `env-var-substitution` · Branch: `feature/env-var-substitution` · Date: 2026-08-09

## Specification (as received)

Add support for env variables to define an absolute base folder, so projects can reference that variable. Variables can be defined in `config.yml` / `config.local.yml` (pattern to be defined by us), then `project.yml` can use e.g. `directory: '${PROJECTS_FOLDER}/custom-ai-tools'`.

## Scope

### Chosen pattern (design decision)

`env_vars` is a **YAML map** (not a list of `KEY=VALUE` strings) — idiomatic YAML, no `=`-parsing edge cases, natural per-key override in `config.local.yml`:

```yaml
# config.yml or config.local.yml
env_vars:
  PROJECTS_FOLDER: /home/blaha/Documents/Projects
```

```yaml
# project.yml
deploy:
  directory: "${PROJECTS_FOLDER}/custom-ai-tools"
```

### Behavior contract

1. Reference syntax: `${NAME}` where NAME matches `[A-Za-z_][A-Za-z0-9_]*`. Multiple references per value are allowed.
2. Resolution precedence: `config.local.yml` `env_vars` > `config.yml` `env_vars` > real process environment variables. Config merge is per-key (consistent with the existing wholesale-per-key merge of `locations`/`tools`).
3. Substitution applies to declared path fields: `deploy.directory` in `project.yml` and the `locations.*` lists in config. Substitution happens BEFORE path resolution (`resolveDeclaredPath`), so a variable may expand to an absolute base.
4. A reference to a variable defined nowhere (config or process env) MUST fail the run with an error naming the variable and the file it was referenced from — never silently deploy to a literal `${...}` path.
5. Values without references pass through unchanged; existing configs and manifests remain valid (backward compatible).

### Out of scope

- Substitution inside prompt/agent/skill body text (a separate mechanism, `{{...}}`, already exists for GitHub input placeholders).
- Recursive/nested variable expansion (a variable value containing another `${...}`) — error or literal pass-through, developer picks the simpler behavior and documents it; MUST NOT loop infinitely.
- A `config.schema.json` (none exists today; not created in this run).

## Acceptance criteria

1. `env_vars` map parses from `config.yml` and `config.local.yml`; local overrides default per individual key.
2. `deploy.directory: "${PROJECTS_FOLDER}/custom-ai-tools"` deploys to the expanded absolute path when `PROJECTS_FOLDER` is defined in config.
3. When a referenced variable is absent from both config files, the real process environment supplies it; config always wins over process env.
4. An unresolvable reference fails with a clear error containing the variable name and the referencing file path.
5. `locations.*` entries in config support the same `${...}` references.
6. Unit tests cover: map parsing, per-key local override, precedence over process env, multi-reference values, unresolved-variable error, unchanged behavior for values without references.
7. `./gradlew clean build` passes in `ai-tools-engine/` including ktlint and detekt (project verification rule).
8. Documentation updated: `README.md` (Configuration section), `09_projects/README.md` (path handling), `QUICKREF.md`, and `10_schemas/project.schema.json` `directory` description mentions variable references.

## Risks

- **Merge-semantics drift**: `env_vars` must follow the established per-key override style; a wholesale-replace of the whole map would surprise users. Mitigated by explicit acceptance criterion 1 and tests.
- **Loader strictness split**: `ConfigService` uses lenient kaml, `LoaderService` strict — substitution must sit at a layer that serves both consumers of declared paths. Constraint stated in briefs; implementation layer is the developer's call.
- **Gradle environment**: `ai-tools-engine` build fails if `~/.gradle/init.gradle.kts` is present (known local conflict) — verification must move it aside and restore it.
- **Detekt/ktlint gates**: new code must pass static analysis; budgeted inside the review loop.

## Staffing decisions (every optional agent, one line each)

| Agent | Decision | Justification |
|---|---|---|
| scout (Explore) | engaged (done) | mapped config/project loading, substitution sites, tests, docs before planning |
| analyst-codebase | skipped | scout survey already produced the architecture map this change needs |
| analyst-security | skipped | feature reads local config and process env only; no new external input or trust boundary |
| analyst-performance | skipped | string substitution at startup on a handful of values; no performance dimension |
| developer-feature | engaged (mandatory) | implements the feature with tests |
| reviewer-code | engaged (mandatory) | reviews the diff |
| reviewer-security | skipped | no secrets handling, no injection surface beyond local machine trust already assumed |
| reviewer-api | skipped | no HTTP/REST surface touched |
| reviewer-architecture | skipped | localized change inside existing loading pipeline; no structural decisions |
| documenter-project | engaged | user-facing config schema changed; README/QUICKREF/09_projects docs must teach the new key |
| reviewer-documentation | skipped | doc delta is small and concrete; reviewer-code reviews the full diff including docs |

All subagents run on the Opus model (user directive).

## Process

1. developer-feature implements behavior contract + tests (criteria 1-6).
2. reviewer-code reviews diff; findings routed back to developer (max 3 iterations).
3. Verify: `./gradlew clean build` in `ai-tools-engine/` (init-script moved aside), ktlint + detekt.
4. documenter-project updates the four documentation targets (criterion 8).
5. Delivery report to `.tmp/env-var-substitution/report.md`; stop at committed feature branch — no merge, no push.
