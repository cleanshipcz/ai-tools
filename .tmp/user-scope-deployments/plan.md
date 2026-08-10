# Delivery plan: user-scope deployments (`UserDeploymentManifest`)

Run slug: `user-scope-deployments`
Branch: `feature/user-scope-deployments`
Specification: `.tmp/plans/analyst-user-scope-deployments-design.md` (approved design, 2026-08-10)

## Scope

Implement the approved design end-to-end:

1. New manifest kind `UserDeploymentManifest`, loaded from `user.yml` files (filename selects the schema, directory names the instance) alongside `project.yml`.
2. Config key rename `locations.projects` → `locations.deployments`; both kinds flow through it; duplicate-id detection scoped per kind.
3. Second export loop in `ToolsEngine` after projects: adapter selection via `tools` with the existing warning semantics, artifact selection via `FilterService`, failure isolation mirroring the project loop (collect-all-then-fail).
4. User-scope layouts in the Claude and Codex adapters via a shared project-layout/user-layout abstraction; instructions file generated from filtered rulesets plus a header naming the manifest, engine-owned and overwritten on every deploy; adapters without user-scope support log that they skip the manifest.
5. Replace semantics: engine owns specific paths, never parent directories; `replace: true` deletes and rewrites per-artifact paths only; hand-made neighbors survive.
6. Injectable user home: `--user-home` CLI option and engine constructor parameter defaulting to `System.getProperty("user.home")`; tests only ever use a temp home.
7. Repository migration: `09_projects/` → `09_deployments/` flattened (no `global/`/`local/` split), `config.yml` and on-disk `config.local.yml` updated, directory README rewritten, maintainer's hand-written `~/.claude/CLAUDE.md` rules captured as ruleset manifests wired into a `globals` user deployment.
8. Documentation: README, QUICKREF, `09_deployments/README.md`, `PLANNED_FEATURES.md` per the spec's Documentation section.

Out of scope (per spec non-goals): MCP support, deployed-files ledger/cleanup, system-wide scope, Copilot/Windsurf/Antigravity/Cursor user scope (v1 = Claude + Codex).

## Acceptance criteria

- AC1: A `user.yml` under a configured `locations.deployments` directory loads as `UserDeploymentManifest` with the spec schema (`id`, `description`, optional `tools`, optional `replace` default false, per-kind `filter` blocks reusing `ProjectFilter`, `metadata`); no `type:` discriminator; no `context`/`directory`/`features` fields accepted (strict decoding rejects unknown fields with a named origin).
- AC2: `locations.projects` no longer exists; `locations.deployments` feeds both kinds; two `user.yml` manifests with the same id conflict, a `user.yml` and a `project.yml` sharing an id do not.
- AC3: The engine exports each user deployment through the adapters its `tools` list selects, with the same unavailable-tool and empty-selection warnings projects get; one broken manifest or artifact collects a failure without stopping the run; collected failures fail the run at the end.
- AC4: Claude user scope writes `<home>/.claude/CLAUDE.md` (rulesets + generated header), `<home>/.claude/skills/<id>/`, `<home>/.claude/agents/<id>.md`, `<home>/.claude/commands/<id>.md`; Codex writes `<home>/.codex/AGENTS.md`, `<home>/.codex/skills/skill-<id>/`, with agents and prompts skills-shaped as in project scope.
- AC5: Adapters without user-scope support (Windsurf, Antigravity, Copilot, Cursor) log that they skip the manifest; a declared tool is never silently dropped.
- AC6: With `replace: true` the engine deletes and rewrites only per-artifact paths and overwrites the instructions file; parent directories are never deleted; a hand-made neighbor file/dir under `<home>/.claude/skills/` survives a deploy (proven by test).
- AC7: `--user-home` overrides the home base end-to-end (CLI → engine → adapters); default is `System.getProperty("user.home")`; no test touches the real home.
- AC8: Repository migrated: `09_deployments/` flattened with `ai-tools/project.yml` directly under it; `config.yml` and on-disk `config.local.yml` use `locations.deployments`; the maintainer's global code-style and question-behavior rules exist as ruleset manifests selected by `09_deployments/globals/user.yml`; a repo-wide reference sweep finds no stale `09_projects` / `locations.projects` references in live code or config (historical docs excluded).
- AC9: Tests per the spec Testing section exist and pass (loader discovery/rejection/merging/per-kind duplicate scoping; engine filter assembly, tools selection incl. warnings, failure isolation, `--user-home`; Claude+Codex destination mapping, instructions-file generation, replace semantics, neighbor survival) following TDD with JUnit5 + MockK + AssertJ.
- AC10: `./gradlew clean build` in `ai-tools-engine/` passes, including ktlint and detekt.
- AC11: README, QUICKREF, `09_deployments/README.md`, and `PLANNED_FEATURES.md` updated per the spec's Documentation section.

## Risks

- R1: Destructive file operations under the user's home. Mitigation: injectable home, per-artifact-path ownership, neighbor-survival tests, dedicated security review.
- R2: This machine's `~/.gradle/init.gradle.kts` (Artifactory mirror) breaks the Gradle build; it must be moved aside for every build and restored afterwards. `gradlew` lives in `ai-tools-engine/`, not the repo root.
- R3: The `locations.projects` rename can break unswept consumers (`deploy.sh`, server module, docs, examples, `projects.global.yml`/`projects.local.yml` legacy files in `09_projects/`). Mitigation: mandatory repo-wide reference sweep in the developer brief.
- R4: `config.local.yml` is untracked; forgetting the on-disk edit silently breaks local runs. Mitigation: explicit brief item; noted in delivery report.
- R5: Engine ownership of `~/.claude/CLAUDE.md` destroys hand edits. Accepted in the design; mitigated by capturing current content as rulesets before any real deploy (AC8) and documenting the limitation.

## Staffing decisions (one line each, engaged or skipped)

- ENGAGED developer-feature: new feature with mandated TDD; single developer because the engine change and the repo migration are coupled by the `locations` key rename.
- ENGAGED reviewer-code: mandatory gate on the full diff.
- ENGAGED reviewer-security: the engine deletes and overwrites paths under `$HOME`; destructive-file-op and path-handling audit is warranted.
- ENGAGED documenter-project: the spec mandates README/QUICKREF/directory-README/PLANNED_FEATURES updates — project-level docs are this agent's specialty.
- ENGAGED reviewer-documentation: user-facing docs describe destructive behavior (file ownership, replace semantics); accuracy review is cheap insurance.
- SKIPPED analyst-codebase: the approved design already encodes the codebase analysis, and I surveyed `ToolsEngine`, models, loader, and adapters directly during intake.
- SKIPPED analyst-security: no untrusted input or new attack surface analysis needed; the concrete risk (destructive ops) is covered by reviewer-security on the actual diff.
- SKIPPED analyst-performance: batch file-generation tool with no performance-sensitive paths touched.
- SKIPPED reviewer-api: no HTTP/REST surface changes (server module untouched).
- SKIPPED reviewer-architecture: the architecture is fixed by the approved design; conformance to it is checked by reviewer-code against the spec.
- SKIPPED documenter-code: KDoc is written inline by the developer per the existing codebase style (heavily documented sources).
- SKIPPED documenter-rest: no REST endpoints changed.

## Execution order

1. developer-feature implements engine + migration (TDD, build must pass locally with the R2 workaround).
2. reviewer-code and reviewer-security review the diff in parallel; findings routed to a developer agent; max 3 iterations.
3. Verify: `./gradlew clean build` in `ai-tools-engine/` (ktlint + detekt included).
4. documenter-project updates docs; reviewer-documentation checks them; findings routed back.
5. Delivery report, final commit on the feature branch. No merge, no push.

## Gate policy for this run

Plan approval was not requested by the user or the spec, so implementation proceeds immediately. Gates: review findings resolved or waived with rationale in `.tmp/user-scope-deployments/`, verification build passing.
