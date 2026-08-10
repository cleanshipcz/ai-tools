# Code review: user-scope deployments (`UserDeploymentManifest`)

Reviewer: reviewer-code
Date: 2026-08-10
Branch: `feature/user-scope-deployments` (7 commits, `main..HEAD`, 53 files, ~2000 insertions)
Specification: `.tmp/plans/analyst-user-scope-deployments-design.md`
Acceptance criteria: `.tmp/user-scope-deployments/plan.md` (AC1-AC11)

Verification performed: `./gradlew clean build` in `ai-tools-engine/` — BUILD SUCCESSFUL, ktlint and detekt included (`~/.gradle/init.gradle.kts` moved aside for the run and restored afterwards; confirmed restored). Every finding below was read out of the actual sources, not inferred from the diff summary.

---

## Findings

### CR-1 — MAJOR — Two user deployments targeting one tool silently overwrite each other's instructions file

`ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/ToolsEngine.kt:271-276`, with `.../adapters/claude/ClaudeLayout.kt:36-39` and `.../adapters/codex/CodexLayout.kt:39-42`

The instructions file is a fixed path per tool per home, so every user deployment selected for a tool writes the same `<home>/.claude/CLAUDE.md`, and the last one processed wins without a warning.

Failure scenario: the maintainer keeps `09_deployments/globals/user.yml` with personal rules and adds `09_deployments/work/user.yml` with employer rules, both declaring `tools: [claude]`. After one `./deploy.sh`, `~/.claude/CLAUDE.md` contains the rules of exactly one of them — whichever the loader walked last, which is filesystem-listing order — and the run exits zero. Nothing in the log says a second manifest claimed the same file. The engine already refuses to pick a winner for a far less destructive ambiguity (`indexByUniqueId` in `LoaderService.kt:217-233` drops both manifests of a contested id rather than choosing), so silently choosing one here is inconsistent with the codebase's own stated policy.

Suggested fix: before the export loop, group the selected user deployments by tool and, where more than one owns the instructions file, either collect a `DuplicateManifestId`-shaped failure or emit a warning naming both manifests and the path they contend for. A warning is enough to close the "silent" part; failing is more consistent with the duplicate-id policy.

### CR-2 — MAJOR — `ArtifactPathException` escapes the CLI as a raw stack trace

`ai-tools-engine/cli/src/main/kotlin/cz/cleanship/aitools/cli/AiToolsCli.kt:44-59`; thrown at `ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/io/ArtifactPaths.kt:21-26`

The new exception is caused by manifest content — an id carrying path segments — but it is neither collected by `exportOrCollectFailure` (`ToolsEngine.kt:450-467` catches only `RulesetResolvingException`, `FragmentResolvingException`, `SkillFileResolvingException`) nor translated into a `CliktError`, so it propagates out of `main` as an unhandled JVM exception.

Failure scenario: a skill manifest is given `id: my/skill` and a user deployment with `replace: true` selects it. `./deploy.sh` aborts with a Gradle "Execution failed for task ':cli:run'" block and a Kotlin stack trace, discarding the carefully worded message the exception composes ("Refusing to replace '...': it is not inside '...'. Give the manifest an id that names a single directory."). The `@throws` list on `ToolsEngine.process` (`ToolsEngine.kt:89-95`) also does not mention it, so nothing warns a caller it is reachable.

Suggested fix: add `catch (ex: ArtifactPathException) { throw CliktError(ex.message, ex) }` alongside the six existing catches, and add the `@throws` line to `ToolsEngine.process`. Deliberately aborting the run rather than collecting the failure is the right call and is well argued in the exception's KDoc — only the reporting is missing.

### CR-3 — MAJOR — `deploy.sh` documents `--user-home` but discards it, deploying into the real home

`deploy.sh:11-12` versus `deploy.sh:25`

The script header says "Pass `--user-home` to the engine to deploy the user scope somewhere else, which is how a run is tried out without touching your own configuration", but line 25 builds the argument list from `"$CUR_DIR"` alone and never references `"$@"`, so every argument passed to `deploy.sh` is silently discarded.

Failure scenario: a user reads that comment, runs `./deploy.sh --user-home /tmp/try`, and the engine deploys into their real `~/.claude` and `~/.codex` — overwriting `~/.claude/CLAUDE.md`, which is precisely the irreversible accident the option exists to prevent (plan risks R1 and R5). The run reports success, so nothing signals the flag was ignored.

Suggested fix: forward the caller's arguments, keeping the existing quoting comment honest — `./gradlew :cli:run --args="--working-dir \"$CUR_DIR\" $*"` — or, if argument forwarding is out of scope, reword the comment to point at the direct `./gradlew :cli:run --args="..."` invocation that README already documents.

### CR-4 — MAJOR — Only deletes are containment-checked; the KDoc claims writes are too

`ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/io/ArtifactPaths.kt:30-36`; `ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/ExportService.kt:48-60`

`ArtifactPathException`'s KDoc states it is "Thrown when an artifact would be written to **or** removed from a path outside the directory its deploy owns". Only `deleteArtifactDirectoryWithin` performs the check. `ExportService.export` does `targetDir.mkdirs()` and `Files.move(..., REPLACE_EXISTING)` on whatever path the layout produced, and `ExportService.copySkillFiles` resolves the author-supplied `files[].target` against the target directory with no normalisation at all (`SkillFile.target` is free-form, `SkillManifest.kt:30-34`).

Failure scenario: a skill manifest declaring `files: [{source: helper.md, target: ../../../../.bashrc}]`, selected by a user deployment, overwrites `~/.bashrc` via `copyTo(overwrite = true)`. Generated-artifact paths are at least suffix-constrained (`$id.md`, `SKILL.md`), but `files[].target` is not constrained at all.

This hole is shared with project scope and therefore not introduced by this diff — what *is* introduced is that the base of that resolution is now `$HOME` rather than a repository checkout, and a KDoc that promises protection the code does not deliver. Minimum fix: correct the KDoc to describe deletes only. Better fix: extract the containment predicate out of `deleteArtifactDirectoryWithin` and apply it to `copySkillFiles`' resolved target (and, cheaply, to the generated-artifact paths), so the sentence becomes true. Flagging for reviewer-security to arbitrate the scope.

### CR-5 — MAJOR — A stale `locations.projects` deploys nothing and reports success

`ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/ConfigService.kt:28` (`strictMode = false`), `.../models/ConfigManifest.kt:20-28`, `ToolsEngine.kt:122-124`

The config decoder ignores unknown keys, so the renamed `locations.projects` is dropped rather than reported; `locations.deployments` then resolves to an empty list and the whole run exports nothing while exiting zero.

Failure scenario: a colleague (or this machine after a `git stash`) pulls the branch with a gitignored `config.local.yml` that still says `locations: projects: [...]`. `./deploy.sh` prints `Loaded 40 agents, ... 0 projects, 0 user deployments` at INFO, buried in Gradle output, and succeeds. They conclude the deploy ran. This is exactly risks R3/R4 from the plan; the chosen mitigation (a repo-wide reference sweep, AC8, which does pass — see below) cannot reach an untracked file on another machine.

The asymmetry is what makes this a finding rather than a preference: `ToolsEngine.kt:122-124` already warns loudly when the run configures no tools, with a comment explaining that leaving a run "silent and successful" is unacceptable. The mirror case — no deployment locations, or locations that yielded no manifests — has no such warning.

Suggested fix: warn when `locations.deployments` is empty or when zero deployment manifests were found, in the same voice as the no-tools warning. Optionally keep a `projects: List<String>? = null` field on `LocationsConfig` purely to fail with "`locations.projects` was renamed to `locations.deployments`".

### CR-6 — MINOR — README still states the CLI has exactly one option

`README.md:70-71`

"The CLI has exactly one option, `--working-dir`, which points at the directory containing `config.yml`. There are no subcommands and no other flags." is falsified by `--user-home`, which this diff adds. `README.md` was edited by this diff (`09_projects` → `09_deployments` in four places), so the sentence was passed over rather than absent from the change set.

Failure scenario: a reader looking for a safe way to trial the feature concludes no such option exists and either skips it or points `deploy.directory` at a scratch path — the unsupported workaround the spec set out to retire. AC11 assigns documentation to a later step; recording the exact line so documenter-project does not have to rediscover it.

### CR-7 — MINOR — User-scope log lines name tools as Kotlin constants, contradicting the adjacent convention

`ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/ToolsEngine.kt:261` and `:264`

`selectAdapters` deliberately logs `it.serialName` and documents why at `ToolsEngine.kt:352-353` ("The warning quotes the unavailable tools in the spelling a manifest writes them, not as Kotlin constants, so that an author can search their own YAML"). The two user-scope lines interpolate `adapter.toolType` instead, which renders as `GITHUB_COPILOT` / `CODEX`.

Failure scenario: a `user.yml` declaring `tools: [github_copilot]` produces `globals: GITHUB_COPILOT has no user-scope layout in this engine, ...`. The author greps their manifests for `GITHUB_COPILOT` and finds nothing. `ToolsEngineTest.kt:825` asserts the uppercase spelling, so the inconsistency is currently pinned by a test and both would need changing together.

### CR-8 — MINOR — `--user-home` accepts a non-existent path, unlike `--working-dir`

`ai-tools-engine/cli/src/main/kotlin/cz/cleanship/aitools/cli/AiToolsCli.kt:33-38`

The option sets `canBeFile = false` but not `mustExist = true`, while `--working-dir` two declarations above (`:20-26`) does set it.

Failure scenario: `--user-home /home/blaha2` (a typo for `/home/blaha`) creates `/home/blaha2/.claude/CLAUDE.md`, `/home/blaha2/.claude/skills/...` and reports success; the user believes their configuration was updated and finds nothing changed.

Counter-argument worth weighing before applying `mustExist`: a first-ever deploy into a home that does not yet exist is legitimate, and the tests rely on `@TempDir`-derived subdirectories that do not exist yet (`ToolsEngineTest.kt:51`, `ClaudeAdapterTest.kt:186`). If `mustExist` is rejected for that reason, the cheaper alternative is a log line when the home directory had to be created.

### CR-9 — MINOR — A test proves reachability only through unspecified directory-listing order

`ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/ToolsEngineTest.kt:846-860`

`should deploy the unaffected user deployment when another one is broken` writes two user deployments (`with-agents` in directory `with-agents`, `without-agents` in directory `second`), both defaulting to `tools: [claude]`, then asserts `<home>/.claude/CLAUDE.md` contains `# without-agents`. Because both write that same path (CR-1), the assertion holds only if the loader walked `second/` last. `LoaderService.findYamlFiles` (`LoaderService.kt:173-177`) uses `walkTopDown`, whose ordering is `File.listFiles()` ordering and is explicitly unspecified.

Failure scenario: the identical commit passes on ext4 (hash order happens to put `second` last) and fails on a filesystem that lists in creation order, where `with-agents` is created first and therefore deployed last, leaving `# with-agents` in the file.

Suggested fix: make the second deployment produce an artifact the first cannot — give it a whitelisted prompt and assert `<home>/.claude/commands/<id>.md` exists — so the test's own claim ("the run reaching the second one is what is proven here") is what the assertion actually checks.

### CR-10 — MINOR — `09_deployments/README.md` still documents the deleted project registry

`09_deployments/README.md:1027-1055`

This diff deletes `09_projects/projects.global.yml` and drops `09_projects/projects.local.yml` from `.gitignore`, but the directory README still documents both as live features under "External Projects", and the file's own status banner says "The `user.yml` kind is not documented here yet".

Failure scenario: AC8's sweep passes for live code and config — I confirmed no `09_projects` or `locations.projects` reference survives outside `90_docs/` and `.delivery/` except these README lines — yet the one directory README that AC11 names still sends readers to registry files that no longer exist. Documentation is step 4 of the delivery plan; this is the concrete backlog entry, together with `90_docs/PLANNED_FEATURES.md`, which is untouched.

### CR-11 — NIT — `ofUser` resolves the tool directory twice

`ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/claude/ClaudeLayout.kt:36-39`, `.../codex/CodexLayout.kt:39-42`

`userHome.resolve(CLAUDE_DIR)` is computed once for `toolDir` and again for `instructionsFile`. `val toolDir = userHome.resolve(CLAUDE_DIR)` followed by `ClaudeLayout(toolDir, toolDir.resolve(INSTRUCTIONS_FILE))` reads as the sentence the KDoc already tells ("the instructions file sits inside `.claude` in the user scope"). Behaviour is identical either way.

### CR-12 — NIT — The filename-selects-schema rule is depth-insensitive

`ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/LoaderService.kt:92-98` with `:173-177`

`findYamlFiles` walks the entire tree under each `locations.deployments` entry and the kind is chosen by basename alone, so a `user.yml` nested anywhere — including `09_deployments/ai-tools/features/user.yml` — is decoded as a user deployment.

Failure scenario: an author names a feature file `user.yml`; the run fails with `Unknown property 'prompt'` against a file they consider a feature manifest. Very unlikely and arguably fine, but the same walk already special-cases `features/` for projects (`LoaderService.kt:99-102`), so the two rules meet at different depths and it is worth a deliberate decision rather than an accident.

---

## Positive observations

- **Layout abstraction is the right seam.** `ClaudeLayout` / `CodexLayout` isolate destination from rendering exactly as the spec asked (design §"Adapter user-scope layouts"): `exportPrompt`, `exportAgent`, `exportSkill` are each written once and take a layout, so project and user scope cannot drift in content. `CodexLayout`'s KDoc explaining that agents and prompts are skill-shaped, told apart by directory prefix, is the kind of note that saves the next reader a git-blame.
- **Replace semantics match the spec precisely.** `ClaudeUserScopeExporter.export(skillContext)` deletes only `skills/<id>/`; per-artifact files (`agents/<id>.md`, `commands/<id>.md`) are overwritten rather than deleted, and `skillsDir` itself is never removed — exactly "the engine owns specific paths, never directories". `CodexUserScopeExporter.replaceIfRequested` applies the same rule uniformly to all three skill-shaped kinds.
- **`deleteArtifactDirectoryWithin` is genuinely well built.** Canonical-path containment catches symlink escapes as well as `..` segments, the `artifactPath == ownedPath` guard blocks deleting the parent itself, and the KDoc explains *why* this is the one place the engine removes files it did not write. Both adapter test suites prove it with a real escaping id and assert the neighbour survives.
- **Neighbour-survival tests do the real thing.** `ClaudeAdapterTest.kt:296-325` plants a hand-made skill, agent, command and an unrelated `projects/` directory, runs a full replacing deploy of all four artifact kinds, and asserts each survives with its original content — no mocks between the assertion and the filesystem. AC6 is genuinely proven, not simulated.
- **No test can reach the real home.** Every user-scope test derives its home from `@TempDir` (`ToolsEngineTest.kt:51`, `ClaudeAdapterTest.kt:186`, `CodexAdapterTest.kt:190`, `UserScopeSupportTest.kt:17-18`), the two "should write nothing outside the home it was given" tests assert the temp root contains only `home`, and the one test that touches `System.getProperty("user.home")` (`AiToolsCliIntegrationTest`, default-value test) uses a stub runner that writes nothing. AC7 holds.
- **`ToolAdapter.userScope` returning nullable, overridden explicitly by all six adapters, is a better design than a default.** The KDoc's reasoning — "so that a tool gaining a user scope is a decision someone made about that tool rather than something a default quietly decided" — is correct, and `UserScopeSupportTest` pins the current answer per tool including that nothing is written while finding out.
- **Per-kind duplicate scoping is implemented and tested end to end.** `LoaderService.loadAll` indexes `project.yml` and `user.yml` separately, and `should load both when a user deployment and a project share an id` proves the spec's rule directly.
- **Strict decoding is verified against the fields that must not exist.** `UserDeploymentManifestTest`'s parameterised rejection of `context`, `directory`, `features`, `deploy` and `type` is the right way to prove a schema by what it refuses, and it decodes through the same kaml configuration `LoaderService` uses rather than a convenient one.
- **The `tools` tri-state is handled and tested for both kinds.** Omitted key, valueless key and explicit `[]` are distinguished, with the "commenting out the last entry" rationale spelled out — a subtlety that would otherwise be rediscovered by accident.
- **The rules migration is faithful.** `01_rulesets/global/global-code-style.yml` and `global-questions.yml` carry the maintainer's `~/.claude/CLAUDE.md` text verbatim; I confirmed only those two rulesets carry the `global` tag and that no agent wildcard (`coding-language-.*`, `security-language-.*`, `testing-language-.*`) matches `global-*`, so nothing leaks into unrelated artifacts. The developer's real trial run wrote into a scratch `fake-home`, and `~/.claude/CLAUDE.md` is untouched (mtime predates this work).

---

## Acceptance criteria assessment

| AC | Verdict | Note |
| --- | --- | --- |
| AC1 schema, strict decoding | Met | `UserDeploymentManifest.kt`; `UserDeploymentManifestTest` |
| AC2 `locations.deployments`, per-kind duplicate scoping | Met in code | See CR-5 for the silent-failure mode of the rename |
| AC3 tool selection, warnings, failure isolation | Met | Shared `selectAdapters`; `ToolsEngineTest.UserDeployments` |
| AC4 Claude/Codex destinations | Met | Verified against both adapter suites and a real trial run |
| AC5 adapters without user scope log a skip | Met | See CR-7 for the spelling of the log line |
| AC6 replace semantics, neighbours survive | Met | Strongest part of the suite |
| AC7 `--user-home` end to end, no real home touched | Met | See CR-3 (unreachable via `deploy.sh`) and CR-8 |
| AC8 repository migrated, sweep clean | Met | Only `09_deployments/README.md` retains stale registry text — CR-10 |
| AC9 tests per the spec's Testing section | Met | With CR-9 on one test's robustness |
| AC10 `./gradlew clean build` | Met | Verified this session |
| AC11 documentation | Not yet | Step 4 of the plan; CR-6 and CR-10 are the concrete items |

---

## Verdict (review cycle 1 — superseded by the Re-review section at the end of this file)

**REQUEST CHANGES.**

Blocking: **CR-1, CR-2, CR-3, CR-4, CR-5.**

CR-3 is the one to fix first: it is a two-word change to a shell script, and until it lands the documented way to try this feature safely does the opposite of what it says, writing into the user's real home. CR-1 and CR-5 are both instances of the same gap — the engine is careful to speak up about ambiguity and misconfiguration everywhere else, and these two paths stay silent. CR-2 is small and mechanical. CR-4 needs a decision on scope: at minimum correct the KDoc so it stops promising a guarantee the code does not provide.

Non-blocking: CR-6 through CR-12. CR-6 and CR-10 belong to the documentation step already scheduled; CR-9 is worth fixing in the same pass as CR-1, since the finding is what makes the test ambiguous.

The core of the feature — the layout abstraction, the containment-checked delete, per-artifact replace semantics, and adapter tests that exercise the real filesystem — is well built and matches the approved design. Every blocking finding is at the edges: reporting, the shell wrapper, and one unhandled multiplicity.

---

## Files reviewed

Engine, main:
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/ToolsEngine.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/io/ArtifactPaths.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/UserDeploymentManifest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/UserDeployment.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/AllManifests.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/EngineConfig.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/ConfigManifest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/ProjectManifest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/SkillManifest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/ToolType.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/LoaderService.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/ConfigService.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/ExportService.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/ToolAdapter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/UserScopeExporter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/UserInstructionsPrinter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/Printers.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/GlobalFilePrinter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/AgentPrinter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/RulesetPrinter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/io/Output.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/claude/ClaudeAdapter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/claude/ClaudeLayout.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/codex/CodexAdapter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/codex/CodexLayout.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/{windsurf,antigravity,cursor,github}/*Adapter.kt`

CLI, main:
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/cli/src/main/kotlin/cz/cleanship/aitools/cli/AiToolsCli.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/cli/src/main/kotlin/cz/cleanship/aitools/cli/ToolsApplicationRunner.kt`

Tests:
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/ToolsEngineTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/models/UserDeploymentManifestTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/services/LoaderServiceIntegrationTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/tools/UserInstructionsPrinterTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/tools/UserScopeSupportTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/tools/adapters/claude/ClaudeAdapterTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/tools/adapters/codex/CodexAdapterTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/data/TestData.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/cli/src/test/kotlin/cz/cleanship/aitools/cli/AiToolsCliIntegrationTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/cli/src/test/kotlin/cz/cleanship/aitools/cli/DefaultToolsApplicationRunnerTest.kt`

Repository, config and docs:
- `/home/blaha/Documents/Projects/ai-tools/config.yml`, `/home/blaha/Documents/Projects/ai-tools/config.local.yml` (untracked, on disk), `/home/blaha/Documents/Projects/ai-tools/.gitignore`
- `/home/blaha/Documents/Projects/ai-tools/deploy.sh`, `/home/blaha/Documents/Projects/ai-tools/setup.sh`
- `/home/blaha/Documents/Projects/ai-tools/README.md`, `/home/blaha/Documents/Projects/ai-tools/QUICKREF.md`, `/home/blaha/Documents/Projects/ai-tools/09_deployments/README.md`
- `/home/blaha/Documents/Projects/ai-tools/09_deployments/globals/user.yml`, `/home/blaha/Documents/Projects/ai-tools/09_deployments/ai-tools/project.yml`
- `/home/blaha/Documents/Projects/ai-tools/01_rulesets/global/global-code-style.yml`, `/home/blaha/Documents/Projects/ai-tools/01_rulesets/global/global-questions.yml`
- `/home/blaha/Documents/Projects/ai-tools/01_rulesets/authoring/authoring-manifests.yml`, `/home/blaha/Documents/Projects/ai-tools/03_prompts/manifests/create-feature.yml`, `/home/blaha/Documents/Projects/ai-tools/05_agents/manifest-builder.yml`

---
---

# Re-review — fix cycle 1

Date: 2026-08-10
Commits reviewed: `730c995..HEAD` — `01eaa7b` (containment on both halves), `beb3a2c` (reporting), `a0458c4` (containment KDoc). 20 files, +933/-57.

Verification performed this round:
- `./gradlew clean build` in `ai-tools-engine/` — BUILD SUCCESSFUL with ktlint and detekt; init script moved aside and restored (verified present afterwards). The new suites ran and passed: `ArtifactPathsTest` and both contention tests are present in `engine/build/test-results/test/` with zero failures.
- CR-3 verified **empirically end to end**: I replicated `deploy.sh`'s forwarding loop verbatim and fed the resulting string to `./gradlew :cli:run --args=...` against an empty working directory. The engine reported `invalid value for --user-home: directory ".../argcheck/a b.txt" is a file` — the forwarded option and its space-containing value arrive intact. Option parsing fails before `run()`, so no deploy occurred during the check.
- Confirmed no manifest id anywhere in the repository, or in the machine-private `../ai-tools-projects/projects` location, violates the new single-segment rule, so the load-time validation is not a latent break for existing content.

## Per-finding outcome

### CR-1 — CLOSED

`ToolsEngine.exportUserDeployments` (`:251-292`) now selects adapters for every manifest first, builds a `UserScopeTarget` per (manifest, tool), and calls `reportContendedInstructionsFiles` (`:319-329`) before a single artifact is written. The resolution matches what I asked for and improves on it: no winner is picked, one ERROR is logged per contended file naming every claimant, and each claimant's instructions export is replaced by a lambda that throws `ContendedInstructionsFileException` (`instructionsExport`, `:339-360`), collected in `exportOrCollectFailure` (`:580-584`) so the run cannot end successfully having written neither.

Three details I checked specifically because they are where this kind of fix usually goes wrong:
- **Interplay with failure isolation is right.** The contention catch deliberately does not log — the comment says so, and `reportContendedInstructionsFiles` already reported once per file — so a run does not emit one error per claimant per tool. The failure still reaches `ExportFailedException` and therefore `CliktError`, so it is never a raw stack trace.
- **Non-contended artifacts still deploy.** `ownsInstructionsFile` gates only the instructions entry of the `buildList`; agents, prompts and skills are untouched. `should deploy neither instructions file when two deployments contend for one` asserts exactly that pairing — `CLAUDE.md` absent, `commands/good-prompt.md` present.
- **Grouping is keyed on `exporter.instructionsFile.absoluteFile` and filtered with `distinctBy { it.manifest.id }`.** That `distinctBy` is what stops a config listing the same tool twice from making one manifest contend with itself — a real possibility, since `DefaultToolAdapterFactory` maps a `List<ToolType>` with no de-duplication.

`should leave an existing instructions file untouched when two deployments contend for it` closes the destructive half: a file from an earlier deploy survives the contended run byte for byte.

### CR-2 — CLOSED

`AiToolsCli` now catches `ArtifactPathException` and `RetiredConfigKeyException` and rethrows both as `CliktError` (`:69-73`), and `ToolsEngine.process` documents `ArtifactPathException` in its `@throws` block (`:94-97`). `ContendedInstructionsFileException` needs no catch of its own because it is collected rather than thrown out. The new CLI test asserts the remediation sentence survives into the `CliktError` message and that the status code is non-zero.

### CR-3 — CLOSED

`deploy.sh:23-26` builds `FORWARDED_ARGS` from `"$@"` and appends it to the `--args` string. Verified empirically as described above. The header comment now shows the concrete invocation rather than an instruction the script could not honour. One residual overclaim in that comment is recorded as CR-13 below — it does not reopen this finding, because the flag is no longer dropped.

### CR-4 — CLOSED

The fix is stronger than what I proposed, and closes the hole at the source rather than at each write site:
- `LoaderService.requireSingleSegmentId` (`:194-219`) runs inside `Yaml.load`, so **every** kind — agents, prompts, rulesets, fragments, features, skills, projects, user deployments — is validated before anything is written, in both scopes. It rejects an empty id, `.`, `..`, and any id containing `/` or `\`, and the rule is deliberately narrow so the dots, dashes, underscores and case already in use keep loading (proven by the parameterised `xbid.bobcat` / `coding-kotlin` / `agent_1` / `UPPER` case).
- `ExportService.resolveTarget` (`:64-83`) contains `files[].target`, rejecting a climb out of the skill directory while still permitting the `templates/example.txt` nesting existing manifests rely on — both directions are tested, including the `~/.bashrc` scenario from my original finding, which now asserts the victim file is untouched.
- The `ArtifactPathException` KDoc now describes deletes only and points at the validation that backs the writes, so the sentence is true.

Bonus hardening I did not ask for and which is well judged: `deleteArtifactDirectoryWithin` no longer uses `deleteRecursively` but a `Files.walkFileTree` visitor that does not follow symbolic links, so a corpus linked into a skill bundle loses the link and keeps its contents. `ArtifactPathsTest` covers directory links, file links, the owned directory itself, and the `skills-evil` sibling-prefix case that a naive `startsWith` on strings would have let through.

### CR-5 — CLOSED

Both halves landed. `ToolsEngine.warnWhenNothingToDeploy` (`:166-172`) distinguishes "no locations configured" from "locations configured but no manifest found", in the same voice as the existing no-tools warning. `ConfigService.rejectRetiredKeys` (`:66-88`) turns a lingering `locations.projects` into a named failure, and `LocationsConfig` keeps the retired field for the sole purpose of making that possible — the KDoc explains that `strictMode = false` is what would otherwise have swallowed it. Tests cover the key in `config.yml` and in `config.local.yml`, asserting the file is named in each case.

### CR-7 — CLOSED

Both user-scope log lines now use `toolType.serialName` (`:304`, `:381`). The old assertion on `WINDSURF` was replaced by one on "no user-scope layout" — so the original test kept its own claim — and a new dedicated test asserts the lowercase `windsurf` appears and the Kotlin constant does not.

### CR-8 — CLOSED

`--user-home` now resolves a relative value against `--working-dir` via the same `resolveDeclaredPath` every other declared path uses, rejects an empty value with a `check`, and the engine logs the resolved absolute home plus a line when it has to be created. Keeping a non-existent home legal is the right call and is now argued in the KDoc rather than left implicit.

I checked the default did not regress: `System.getProperty("user.home")` is absolute, and `resolveDeclaredPath` returns an absolute path unchanged, so the pre-existing `should run under the home of this user when the option is not given` test still holds without modification — and it passes in the green build.

### CR-9 — CLOSED

The test now gives the two deployments different tools (`claude` and `codex`), so each owns its own instructions file, and asserts `.codex/skills/prompt-good-prompt/SKILL.md` — an artifact only the second deployment produces. Order-independent, and it still proves the original claim that the run reaches the second manifest after the first one fails.

### CR-11 — CLOSED

`ClaudeLayout.ofUser` and `CodexLayout.ofUser` compute the tool directory once.

### CR-12 — CLOSED (accepted, documented)

`LoaderService.loadAll`'s KDoc (`:71-76`) now states that the filename decides the kind at any depth on purpose, names the one shape it admits, and explains why strict decoding makes that shape a loud failure rather than a silent mis-deploy. That is the deliberate decision I asked for.

### CR-6, CR-10 — deferred

Documentation step, as agreed. Not counted against this cycle.

## New findings

### CR-13 — MINOR — `deploy.sh`'s quote-escaping comment describes behaviour Gradle does not have

`deploy.sh:19-26`

The comment says "every forwarded argument is quoted here and any quote inside it escaped", and the loop does emit `\"` for an embedded quote. Gradle's `--args` splitter has no backslash handling, so the escape does not survive.

Verified: forwarding a path containing a literal `"` makes Gradle abort with `unbalanced quotes in --working-dir "..." "--user-home" ".../q\"uote.txt"` before any task runs.

Why this is minor and not a reopening of CR-3: the failure is loud and immediate, nothing is deployed, and there is no fallback to the real home — the dangerous mode is absent. What remains is a comment that claims a guarantee the toolchain does not provide, and the inability to pass a path containing `"`. Suggested fix: drop the escape and say plainly that arguments must not contain double quotes, or leave the escape and change the sentence to match what it actually achieves.

### CR-14 — NIT — "The home is created by this deploy" can be false

`ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/ToolsEngine.kt:262-265`

The two INFO lines are emitted once `selections` is non-empty, which is before `targets` is computed, so they announce a deploy that may not happen. The suite already exercises the case: `should warn and deploy nothing for a configured tool that has no user scope` runs a Windsurf-only engine, logs "The home '...' does not exist yet and is created by this deploy", writes nothing, and then asserts `userHome` does not exist. The all-contended run has the same shape when the deployments carry nothing but their instructions files.

Suggested fix: move the two lines below the `targets`/`contendedInstructions` computation, gated on there being at least one target, so the log describes what the run is actually about to do.

### CR-15 — NIT — "Found no deployment manifest" also fires when manifests were found and dropped

`ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/ToolsEngine.kt:166-172`

`warnWhenNothingToDeploy` reads `allData.projects` and `allData.userDeployments`, which already exclude everything a duplicate id or a colliding feature removed. A run whose only manifests collide therefore gets "Found no deployment manifest under [...], so nothing is deployed. A project is a directory holding a project.yml..." — an explanation of manifest layout offered to someone whose manifests were found and rejected.

The preceding `LOG.error` per duplicate does say what really happened, so this is redundancy rather than misdirection. Suggested fix: skip the warning when `allData.duplicates` is non-empty, or word it as "nothing left to deploy".

## Re-review verdict

**APPROVE.**

All five blocking findings (CR-1 through CR-5) are closed, each verified against the code rather than against the description of the change, with CR-3 additionally confirmed by running the real forwarding logic through Gradle. The four non-blocking ones I raised (CR-7, CR-8, CR-9, CR-11) and the documentation decision (CR-12) are closed too. No regression surfaced: the pre-existing default-home test still holds unmodified, the rewritten tests kept their original claims while removing their dependence on walk order, and `./gradlew clean build` is green including ktlint and detekt.

CR-13, CR-14 and CR-15 are new but non-blocking — one comment that overstates what Gradle does, and two log lines that describe a deploy more confidently than the code guarantees. None of them can write, delete or drop anything; they are worth a cleanup pass whenever the documentation step touches these files, and they should not hold the branch.

## Spot-check — cleanup commit `0220926`

Scope: this commit only, as briefed. Build re-run: `./gradlew clean build` BUILD SUCCESSFUL with ktlint and detekt, `failures="0"` across every engine and cli suite; all six new tests present in the results. Init script moved aside and restored (verified).

- **CR-13 — CLOSED.** `deploy.sh:19-34` now refuses an argument containing `"` instead of emitting an escape Gradle cannot read, and the comment states what is actually supported. Verified with a probe copy of the real script (the `./gradlew` line replaced by an echo, everything else verbatim): no args forwards cleanly, `--user-home /tmp/try` and `--user-home "/tmp/a b"` both forward intact, `--user-home '/tmp/q"uote'` prints the two-line explanation and exits 2 *before* the subshell that would run Gradle, and `'/tmp/$(touch /tmp/pwned-probe)x'` reaches the `--args` string as literal text with no command substitution executed.
- **CR-14 — CLOSED.** The two INFO lines moved into `announceHome` (`ToolsEngine.kt:300-315`), called after `targets` and `contendedInstructions` are known. I checked the gate rather than trusting it: `writesSomething` is true when any target's instructions file is uncontended **or** the deployment has artifacts, and `planned` only holds deployments that have at least one target — so the Windsurf-only run announces nothing, an all-contended run carrying nothing else announces nothing, and the two mixed cases (contended instructions but real artifacts; uncontended instructions but no artifacts) both still announce. Two new tests pin the first two. Hoisting `assembleUserDeployment` above the export loop is safe: it is pure `FilterService` work with no side effects, as the added comment says.
- **CR-15 — CLOSED.** `warnWhenNothingToDeploy` now suppresses the layout explanation when `allData.duplicates` is non-empty, leaving the collision error to speak. The new test builds a location whose only two manifests collide and asserts the warning is absent while the error naming the id is present.

### CR-16 — MINOR (new; belongs to SEC-16, not blocking) — the out-of-directory warning is skipped for a standalone skill

`ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/ExportService.kt:65-84` with `:99-110`

`warnWhenSourceComesFromOutside` opens with `val owned = sourceDir?...?: return`, so a `null` source directory silently disables it. A standalone skill YAML (`04_skills/<id>.yml`, no directory of its own) has exactly that: `resolveSource` returns early for an absolute `source` before the null check, and its own error message advertises "provide an absolute path" as the supported way to write one. So the single shape SEC-16 names first — an absolute source — is the one shape that still copies without a word, provided the skill is standalone.

Failure scenario: `04_skills/briefing.yml` declares `files: [{source: /home/user/private/notes.md, target: notes.md}]`; a user deployment selects it; the file lands in `~/.claude/skills/briefing/` and nothing in the transcript says where it came from. Suggested fix: warn unconditionally when `sourceDir == null`, since for a standalone skill every source is by definition from outside.

Everything else in the commit is clean. The six adapters all pass the skill id through, so the `skillId: String = ""` default is reachable only from tests; no production call site can produce the empty-id log line.

### Spot-check verdict

**APPROVE.** CR-13, CR-14 and CR-15 are closed and verified against the code and, for CR-13, against the running script. No defect was introduced by the commit. CR-16 is a residue of the security reviewer's SEC-16 rather than of my findings, is a visibility gap with no effect on what is written, and should not hold the branch.

---

Two remarks for the record. First, the CR-4 resolution moved the guarantee from "the delete is contained" to "no id that could escape ever loads", which is the better place for it, but it also means a single malformed id now aborts a whole run at load time where a non-replacing deploy previously proceeded. That is consistent with how a malformed `metadata.version` already behaves and is argued in the KDoc, so I read it as intended rather than as a side effect — flagging it only so the trade-off is a recorded decision. Second, `ExportService.resolveTarget` compares normalized rather than canonical paths, which the KDoc justifies (nothing on disk to canonicalize yet); a symbolic link already sitting inside a skill directory could therefore still redirect a companion-file write. That is a narrower residue of CR-4 than what was closed, and it belongs to reviewer-security's judgement rather than mine.
