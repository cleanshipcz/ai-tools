# Delivery Plan — Project Analysis & Improvements

Branch: `refactor/project-improvements` (off `fix/copilot-adapter-export`)
Run slug: `project-improvements`

## 1. Context

Two analysts surveyed the repo. Findings are in:
- `tmp/analysis/analyst-engine-codebase.md`
- `tmp/analysis/analyst-repo-structure.md`

Root cause of nearly every repo-level finding: the TypeScript→Kotlin engine migration replaced the
build engine and updated almost nothing around it. The manifest layer itself is healthy
(130 manifests, zero duplicate IDs, zero unresolved ruleset references). The scaffolding around it is stale.

Baseline established before any change: `./gradlew clean build` in `ai-tools-engine/` is **green**.

## 2. Scope (user-approved)

Approved: "Fix docs + remove dead weight" plus low-risk engine cleanups.
Approved: leave `CLAUDE.md` / `AGENTS.md` duplication alone.

### In scope

**A. Correctness (engine)**
- A1. `ToolsEngine` swallows `RulesetResolvingException` / `FragmentResolvingException`, logs ERROR, and the
  process still **exits 0**. Combined with `ExportService` writing directly to the target stream, a failure
  mid-write leaves a **truncated file on disk** while the run reports success. Fix: write atomically
  (temp file + move on success) and propagate failure to a non-zero exit code.
- A2. `LoaderService.loadAll` uses `associateBy`, so two manifests sharing an id silently drop one. Fix: detect and fail loudly.

**B. Dead weight removal**
- B1. `:utils` module — `Calculator.add()` template scaffolding. Referenced by nothing; `engine`, `server` and `cli` all declare a dependency on it.
- B2. `ManifestService` — zero references; `listFeatures()` reads `02_features/`, which does not exist.
- B3. `engine`'s unreachable `main()` (`ToolsApplication.kt`) — `engine` does not apply the `application` plugin. Duplicate of the CLI startup path.
- B4. `ToolsEngineTest.kt` — 126 lines of commented-out code referencing a long-gone API. Replaced by a real test (see A1).
- B5. `LoaderService` — dead `filter` parameter never passed by any caller; commented-out `strictMode` with a misleading comment.
- B6. `new_files.txt` — tracked, 280 lines of legacy paths, referenced by nothing.
- B7. `15_config/` — legacy `project_sources` schema. `ConfigService` reads **only** `<workingDir>/config.yml` + `config.local.yml`. Nothing reads `15_config/`; its example files are referenced by nothing.
- B8. `09_projects/global/ai-tools/deploy.yml` — never read by the engine (deployment now lives in `project.yml`'s `deploy:` block); names 6 agents that no longer exist.

**C. Documentation truth**
- C1. `setup.sh` — every command it runs (`npm install`, `npm run validate|build|docs`) fails; there is no `package.json`. Rewrite for the real Gradle workflow.
- C2. `README.md` — documents an npm CLI that no longer exists, references a non-existent `12_templates/`, misstates config location, and never mentions `ai-tools-engine/` (the actual build engine) or `deploy.sh`.
- C3. `QUICKREF.md` — every manifest template contradicts the real schema (`version:` vs `metadata.version`, agent `purpose:` vs `description`, wrong directory names). Highest-value doc fix: agents author manifests from this file.
- C4. `09_projects/global/ai-tools/project.yml` — `ocs-summarize-pr` typo silently drops a prompt (real id `docs-summarize-pr`); `deploy.directory` hardcodes `/home/blaha/...` in a tracked file.

**D. Build & CI hygiene**
- D1. `.github/workflows/ci.yml` is the only workflow and runs npm — so **no Kotlin build, ktlint or detekt runs in CI**, despite `CLAUDE.md` naming that as the verification rule.
- D2. `sonar-project.properties` `projectKey` points at `cleanshipcz_bootstrap-kotlin` — the template repo. Analysis is published under the wrong project and the quality gate is meaningless.
- D3. `.pre-commit-config.yaml` runs `npm run validate` (ENOENT).
- D4. `.gitignore` gaps: `build/`, `.gradle/`, `.test-output/`, `.delivery/`, `ai-tools-projects/` not ignored; `.gradle/ideaInitScripts/ijtgtmapper.gradle` is tracked and hardcodes a machine-local JDK path.

### Explicitly OUT of scope — with justification

- **`07_mcp/github/` (4.4 MB, 438 of 807 tracked files).** The analyst rated removal HIGH. **Overruled by the lead:**
  git history shows commits `fc08d7d "Forking github mcp"` and `22e0a11 "Adding Github MCP"`. The vendoring is
  deliberate, not accidental. Removing a deliberate fork is the owner's call, not a refactor. Raised in the report instead.
- **`21_redteam/`, `91_examples/`, `20_evals/`, `08_recipes/`, `07_mcp/` presets.** Zero or near-zero inbound
  references, but `PLANNED_FEATURES.md` lists MCP and prompt-webpage support as pending. These are awaiting
  re-implementation, not dead. Deleting them would destroy authored source of truth.
- **JSON-schema realignment + Gradle validation wiring (analyst item 1b).** Real and valuable — `skill.schema.json`
  is entirely divorced from `SkillManifest.kt` — but effort M–L and it changes the authoring contract. Deserves its own
  reviewed change, not a tail-end item in a broad sweep.
- **Adapter frontmatter centralisation.** User selected the scope tier that excludes it. Latent only: no current
  manifest description contains YAML-hostile characters (verified).
- **`ToolsEngine` structural refactor (C1/C2), resolver deduplication (T2), server HTTP tests (T11).** Sound proposals,
  but they are behaviour-preserving restructures whose safety net (a real `ToolsEngine` test) does not exist yet.
  A1/B4 create that net; the restructure should follow it, not precede it.
- **`detekt.yml` relaxations.** Deliberate and documented with rationale. Not a defect.
- **Ruleset filename/ID mismatches (13 renames).** Cosmetic, and renames churn history for every consumer.

## 3. Acceptance criteria

1. `./gradlew clean build` in `ai-tools-engine/` passes, including ktlint and detekt, with `maxIssues: 0`.
2. No production behaviour change beyond the two deliberate correctness fixes (A1, A2).
3. A malformed ruleset pattern causes a **non-zero exit** and leaves **no truncated output file**. Covered by a new automated test.
4. Duplicate manifest ids fail loudly. Covered by a new automated test.
5. Every command in `setup.sh` and `README.md` executes successfully against the current repo.
6. Nothing deleted is referenced anywhere — confirmed per item by grep before removal.
7. One commit per discrete change. Nothing pushed.

## 4. Risks

| Risk | Mitigation |
| --- | --- |
| A1 changes exit-code behaviour; a previously "passing" deploy may now fail | That is the intent — it currently ships broken artifacts silently. Called out prominently in the report. |
| Deleting `:utils` breaks an unseen consumer | Grep confirmed zero usages outside the module; build gate catches it. |
| Deleting `15_config/` removes a file someone edits by habit | Confirmed unread by the engine; README updated in the same delivery to point at the real location. |
| Parallel agents racing on the git index | Developer agents do **not** commit. The lead stages and commits each change. |
| Doc rewrites drift from reality again | Every documented command is executed before the change is accepted. |

## 5. Staffing decisions

| Agent | Engaged? | Justification |
| --- | --- | --- |
| `analyst-codebase` ×2 | **Engaged** | Repo spans a Kotlin multi-module build plus a large YAML manifest tree; two disjoint domains, parallelised. |
| `developer-refactoring` | **Engaged** | Stream A/B is behaviour-preserving removal plus two scoped correctness fixes — exactly this agent's remit. |
| `developer-feature` | **Skipped** | No new feature. All work is removal, correction, or documentation. |
| `developer-bugfix` | **Skipped** | A1/A2 are folded into the refactoring stream; splitting them across agents would fragment one coherent diff of `ToolsEngine`/`LoaderService`. |
| `documenter-project` | **Engaged** | Stream C is a documentation-accuracy rewrite of README/QUICKREF/setup.sh — its remit, and disjoint from the engine diff so it runs in parallel. |
| `documenter-code` | **Skipped** | No public code API surface changes; deletions only. |
| `documenter-rest` | **Skipped** | Server HTTP surface untouched. |
| `reviewer-code` | **Engaged** | Mandatory spine. |
| `reviewer-security` | **Skipped** | No external input parsing, auth, secrets or network surface added. The one input-validation note (`skillFile.target` path traversal) is on repo-local manifests and is out of scope this run. |
| `reviewer-architecture` | **Skipped** | Deliberately deferred the structural refactors; module boundaries are unchanged by this delivery. |
| `reviewer-api` | **Skipped** | No API design change. |
| `reviewer-documentation` | **Engaged** | Stream C is the largest surface by line count and its whole purpose is accuracy — it needs an independent correctness check. |
| `analyst-security`, `analyst-performance` | **Skipped** | No threat-surface or performance-sensitive change in scope. |

## 6. Execution order

1. Parallel: Stream A/B (`developer-refactoring`, engine) ‖ Stream C (`documenter-project`, docs) ‖ Stream D (build/CI hygiene).
   Streams touch disjoint paths. No agent commits.
2. Lead stages and commits each discrete change.
3. `reviewer-code` + `reviewer-documentation` on the full diff.
4. Verify: `./gradlew clean build` (init-script workaround required — see below).
5. Report.

**Environment note:** `~/.gradle/init.gradle.kts` (work Artifactory mirror) conflicts with this project's
`RepositoriesMode.FAIL_ON_PROJECT_REPOS`. It must be moved aside for the build and restored afterwards.
