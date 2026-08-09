# Delivery report: env-var substitution for declared paths

Run slug: `env-var-substitution` · Branch: `feature/env-var-substitution` · Delivered: 2026-08-09
Status: **DELIVERED** — all gates passed. Branch left committed; not merged, not pushed.

## What was delivered

Environment-variable substitution for declared paths in the ai-tools engine:

- New top-level `env_vars` key (YAML map) in `config.yml` / `config.local.yml`; merged PER KEY, local wins.
- `${NAME}` references (`[A-Za-z_][A-Za-z0-9_]*`) expand in every `locations.*` entry and in `deploy.directory` of `project.yml`, before relative/absolute path resolution — so `directory: "${PROJECTS_FOLDER}/custom-ai-tools"` works.
- Precedence: `config.local.yml` > `config.yml` > real process environment (environment access via injectable `EnvironmentSource`, so tests never touch real env).
- Safety: every project's `deploy.directory` is validated up-front before ANY adapter runs — a run never partially deploys and never writes/deletes under a literal `${...}` path; all unresolved names are reported in one failure. Nested/assembled references are errors (single-pass, cannot loop). Malformed groups (`${1ST}`, `$NO_BRACES`) pass through literally.
- Backward compatible: configs without `env_vars` and values without references behave exactly as before; no existing test weakened.
- `VariableResolver` `toString` prints variable names only, so `env_vars` values cannot leak into logs.

### Commits (on top of main)

| Commit | Content |
|---|---|
| e1da846 | plan |
| 4b08b12 | feature implementation + tests (13 files, +757/−21) |
| 57dc376 | review round-2 fixes: atomic up-front deploy.directory validation, assembled-reference guard, per-file origin naming, equality/defensive-copy, test gaps (8 files, +392/−87) |
| 422fb09 | review round-3 fixes: hand-written VariableResolver equality over the declared copy, order-independent atomicity regression test (3 files, +64/−10) |
| 654e743 | documentation: README, QUICKREF, 09_projects/README, project.schema.json, STYLE_GUIDE (5 files, +77/−5) |

Key code: `engine/env/VariableResolver.kt` (new), `ConfigService.kt`, `ToolsEngine.kt`, `ConfigManifest.kt`, `EngineConfig.kt`, CLI error translation in `AiToolsCli.kt`. ~25 new test methods across VariableResolverTest (new), ConfigServiceTest, ToolsEngineTest, AiToolsCliIntegrationTest.

## Gate outcomes

| Gate | Outcome |
|---|---|
| Plan | Approved by user interactively (pattern choice: `env_vars` map, config > process-env precedence, hard error on unresolved) |
| Review | APPROVE after 3 iterations (reviewer-code, Opus). Iteration 1: REQUEST_CHANGES, 10 findings (1 MAJOR: partial-deploy before abort). Iteration 2: all routed fixes confirmed, APPROVE with 2 residual MINORs. Iteration 3: targeted re-check, APPROVE, zero remaining findings. Full ledger: `reviewer-code-findings.md` |
| Verify | `./gradlew clean build --rerun-tasks` run independently by team lead: BUILD SUCCESSFUL, 71/71 tasks executed, ktlint + detekt included, 321 tests green (`~/.gradle/init.gradle.kts` moved aside and restored) |
| Document | 4 planned targets + 1 approved extension (STYLE_GUIDE clause); documenter verified docs against the implementation, not just the brief |

## Waived findings (with rationale)

| Finding | Rationale |
|---|---|
| 1.9 Merged manifest carries `emptyMap()` where siblings preserve null | No consumer observes the difference (sole consumer calls `orEmpty()`); internal asymmetry only |
| 1.10 Server prints raw stack trace for the new exception (CLI translates it) | Pre-existing server error-handling gap (same for FileNotFoundException); out of run scope; follow-up candidate |
| 2.3 `UnexpandedReferenceException` names the surviving reference, not the declaring variable | Documented deliberate trade-off; message quotes original value + substituted result; no single culprit exists in the boundary case |
| 2.4 Lambdas vs property references in ConfigService | Pure style; equally readable |
| 2.5 Failures logged and repeated in aggregate message | Matches existing house style (`ExportFailedException`); diverging only the new path would be inconsistent |
| 3.1 toString label `variables=` could imply full state | Cosmetic; redaction reasoning documented in KDoc |

## Staffing decisions taken

All subagents ran on Opus (user directive). Engaged: Explore scout (pre-plan code map), developer-feature (dev-envvars, 3 rounds), reviewer-code (rev-code-envvars, 3 iterations), documenter-project (doc-envvars). Skipped with justification (see plan.md): analyst-codebase, analyst-security, analyst-performance, reviewer-security, reviewer-api, reviewer-architecture, reviewer-documentation.

## Notable behavior change (intentional, reviewer-flagged)

A run configuring no tools now fails on an unresolved `deploy.directory` it previously never inspected, because validation is up-front and covers every loaded project. Accepted as the more correct behavior.

## Follow-up candidates (not part of this delivery)

1. Server module: translate `VariableSubstitutionException` / `DeployDirectoryResolvingException` into friendly errors like the CLI does (pre-existing gap, widened by any new failure mode).
2. `10_schemas/deploy.schema.json` is stale legacy (describes the removed deploy.yml) — delete or update.
3. `90_docs/STYLE_GUIDE.md` env-var advice got a qualifying note; a fuller security-guidance pass could unify placeholder semantics across docs.

## Pre-existing working-tree items left untouched

`PLANNED_FEATURES.md` (modified before this run) and untracked `.github/agents/` were present before branching and are NOT part of this delivery; they remain uncommitted.
