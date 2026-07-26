# Delivery Report — Project Analysis & Improvements

Branch: `refactor/project-improvements` (off `fix/copilot-adapter-export`)
Status: delivered on the branch. **Nothing pushed. Nothing merged.**

## 1. Outcome

16 commits, one per discrete change. Working tree clean.

Gate results:

| Gate | Result | Evidence |
| --- | --- | --- |
| Build | PASS | `./gradlew clean build` in `ai-tools-engine/`, `EXIT=0` |
| ktlint | PASS | wired into `check` by the convention plugin; `maxIssues: 0` |
| detekt | PASS | same |
| Tests | PASS | 235 tests, 0 failures/errors (219 before the review fixes) |
| Review (code) | PASS — SHIP-WITH-FIXES, fixes applied | `tmp/reviews/reviewer-code-improvements.md` |
| Review (docs) | NOT COMPLETED — see §5 | reviewer never reported; areas covered by the code reviewer instead |
| Deploy (real run) | **BLOCKED** — see §5 | duplicate `xbid` id in the user's separate projects repo |

Metrics:

| | Before | After |
| --- | --- | --- |
| Tracked files | 808 | 797 |
| Gradle modules | 5 | 4 |
| Kotlin main files | 64 | 61 |
| Kotlin test files | 35 | 35 (CalculatorTest removed, ExportServiceTest added) |

Net diff: 36 files changed, +1466 / −1448.

## 2. Root cause behind most findings

The TypeScript→Kotlin engine migration replaced the build engine and updated almost nothing around it.
The manifest layer itself is healthy — 130 manifests, zero duplicate ids, zero unresolved ruleset
references, confirmed by the repo analyst. Everything *around* it had rotted.

## 3. What was delivered

### Correctness (the two real bugs)

1. **`a703a90` Write exported artifacts atomically.** Content resolution happens inside the printer, so an
   unresolvable reference could throw mid-write. The stream was opened directly on the target file, so `use`
   closed it and left a truncated artifact on disk — frontmatter present, body missing. Now writes to a temp
   file in the target directory and moves it into place only on success.
2. **`83c2f20` Fail the run when a manifest cannot be exported.** The resolve exception was caught, logged,
   and ignored: `process` returned normally and the CLI **exited 0**, so a failed export reported success.
   The catch also wrapped the whole adapter, so one broken agent silently skipped every remaining prompt,
   feature and skill for that tool. Now each manifest is isolated, failures are collected across all
   projects and adapters, and one aggregated `ExportFailedException` surfaces via `CliktError` with a
   non-zero exit.
3. **`a3b5932` Fail on duplicate manifest ids.** `associateBy { it.id }` silently discarded one of any two
   manifests sharing an id. Now raises `DuplicateManifestIdException` naming both absolute paths. Files
   reachable through overlapping configured locations are de-duplicated by canonical path first, so
   overlapping config is not mistaken for a duplicate.

### Dead weight removed (each confirmed unreferenced by grep before removal)

- `1bb198f` `:utils` module — `Calculator.add()` template scaffolding; three modules depended on it, nothing used it.
- `dad710f` `ManifestService` (no callers; `listFeatures()` read a `02_features/` directory that does not exist) and `ToolsApplication` (a `main()` in a module that does not apply the `application` plugin, therefore unreachable).
- `fff0ecc` `15_config/` — `ConfigService` reads only root `config.yml`/`config.local.yml`. Its `config.yml` used a `project_sources:` key absent from the engine's schema, and config parsing runs with `strictMode` disabled, so edits there had no effect and produced no warning.
- `eb215e5` `new_files.txt` — 280 lines of legacy paths, referenced by nothing.
- `098ee29` `09_projects/global/ai-tools/deploy.yml` — never read; 6 of its 11 agent ids no longer existed.
- `643c68f` `.gradle/ideaInitScripts/ijtgtmapper.gradle` untracked (hardcoded a machine-local JDK path). Left on disk.

### Documentation truth

- `efab7cc` `setup.sh` + `README.md` + `ai-tools-engine/README.md`. `setup.sh` previously failed on its first
  command (`npm install`; there is no `package.json`). It now checks prerequisites and builds the engine, and
  handles the global-Gradle-init-script conflict by detecting it from Gradle's own error output, retrying with
  the script moved aside, and always restoring it — including on interrupt. README documents the real layout
  and commands, drops the phantom `12_templates/`, corrects the config location, and lists capabilities with no
  implementation as *not implemented* rather than as working commands.
- `f6ec14a` `QUICKREF.md`. Every manifest template contradicted the engine's data classes. Since this repo's
  product *is* agent context, a wrong template here produces broken manifests on every run. Templates are now
  derived from the Kotlin models and cross-checked against committed manifests, with non-existent fields called
  out explicitly so they are not reintroduced.
- `project.yml`: `ocs-summarize-pr` → `docs-summarize-pr` (the typo silently dropped that prompt from
  every deploy), and the hardcoded `/home/blaha/...` deploy path made portable — first as `../../`, later
  as `"."` once the resolution base was fixed (see §5b).
- `098ee29` also fixed `03_prompts/manifests/create-project.yml`, which instructed agents to author `deploy.yml`
  files for the retired TypeScript CLI — it would have generated dead config on every run.

### Build & CI

- `.github/workflows/ci.yml` ran only npm commands, so **nothing was verified in CI at all**, despite
  `CLAUDE.md` naming `./gradlew clean build` with ktlint and detekt as the project's rule. Now builds the engine,
  on every branch rather than only `main`.
- `sonar.projectKey` was `cleanshipcz_bootstrap-kotlin` — the *template* repo. Also removed a
  "wrapper validation" step whose command was `echo "Running wrapper validation"`.
  **See the correction in §5a: this fix is inert until the workflow is relocated.**
- `8f143e7` pre-commit hooks that could not run.
- `6a39bf0` `.gitignore` gaps (`build/`, `.gradle/`, `.test-output/`, `ai-tools-projects/`).

## 4. Verification notes

- The deploy path was **verified empirically, not assumed**, at both stages. First, running `:cli:run` with no
  args made the CLI report the absolute path it searched, revealing the run working directory as
  `<repo>/ai-tools-engine/cli/`, which is what made `../../` correct at the time. Later, once the resolution base
  was fixed, `"."` was proved by running the `installDist` binary from several different process working
  directories including `/`, and confirming the output follows `--working-dir` in every case.
- **Caveat on the earlier build evidence in this report:** several of the "build green" runs completed in 2-3
  seconds, i.e. mostly from the Gradle build cache, which is weak evidence that anything actually ran. The final
  state was re-verified with `--no-build-cache`: 65 of 71 tasks genuinely executed, 255 tests, 0 failures,
  0 skipped.
- `.gitignore` rules were checked with `git check-ignore -v` to confirm no tracked source file became ignored
  and that `.delivery/` remains committed.
- The untracked IDE init script was confirmed still present on disk after untracking.

## 5a. Corrections — things this report got WRONG before the CI agent's report arrived

The `dev-ci` agent's report was delayed (its plain-text output never reached the lead; it re-sent via
SendMessage). It contained three findings that corrected me. All three are verified.

**CORRECTION 1 — the SonarCloud workflow has never run at all.**
I originally wrote that analysis "was published under the template's project and the quality gate was
meaningless". That is wrong. `sonarcloud.yml` and `codeql.yml` live in `ai-tools-engine/.github/workflows/`,
but `ai-tools-engine` is a **plain subdirectory, not a submodule** (verified: no `.gitmodules`, no nested
`.git`, 148 of its files tracked by the root repo). GitHub Actions only reads workflows from
`.github/workflows/` **at the repository root**. So both workflows are **dormant and always have been** —
no analysis was ever published anywhere.

Consequence: correcting `sonar.projectKey` makes the file right but changes nothing operationally. Sonar and
CodeQL stay dead until the workflows are moved to the repository root. That relocation was deliberately NOT
done: with neither the SonarCloud project nor `SONAR_TOKEN` verifiable from this environment, moving them
would likely convert a dormant workflow into a failing check on every pull request. **Owner decision.**
The commit message for that change was reworded to state this accurately.

**CORRECTION 2 — a real coverage gap was opened, not closed.**
The old `ci.yml` ran `npm run validate` for manifest validation. There is no Gradle equivalent, so replacing
the npm pipeline means **manifest validation is now enforced nowhere**. It was already broken (no
`package.json`, so the step always failed), meaning validation has not actually run for some time — but the
new CI does not restore it either. The old release job was dropped for the same reason: it read the version
from `package.json`, and no version is declared in `gradle.properties` or `build.gradle.kts`.
This is the strongest argument for the schema-realignment work in §7, which would make validation
mechanisable.

**CORRECTION 3 — my `.gitignore` verification method was weak.**
I originally verified ignore rules with plain `git check-ignore`. That consults the index and reports every
*tracked* file as "not ignored" regardless of the patterns, so it could not have detected the problem it was
meant to detect. Re-verified properly with `--no-index`: piping all tracked files through
`git check-ignore -v --no-index --stdin` yields exactly one hit, `90_docs/AGENTS.md`, matched by the
**pre-existing** `AGENTS.md` pattern at `.gitignore:77` — not by any rule added here. `.delivery/` confirmed
not ignored. The conclusion stands; the original method did not justify it.

Minor: `90_docs/AGENTS.md` being tracked while matching an ignore pattern is a pre-existing oddity (ignore
rules never untrack). Not introduced or changed here; worth a decision at some point.

Also worth recording: the TruffleHog secret-scan step in the old `ci.yml` never executed either — it sat
behind an unguarded `npm install` in the same job, so the job always died before reaching it. The repo has
had no working secret scanning. Raising that separately is reasonable; nothing was lost by removing it.

## 5. Review findings

**Status: the code review gate COMPLETED. Verdict SHIP-WITH-FIXES. All in-repo fixes are applied.**
Full review transcribed to `tmp/reviews/reviewer-code-improvements.md`.

**Why it looked like it had failed, and the process lesson:** subagent plain-text output is not delivered to the
lead. Every agent on this delivery was briefed to "write a report file and return findings as your final
response" — a channel that does not work. Three agents' reports were lost that way and only arrived after being
re-requested over `SendMessage`. `reviewer-code` additionally could not write a findings file at all, because
its own operating instructions prohibit it, so the directory I created for it was never the blocker. The lead's
instruction was the defect, not the agents. **Brief agents to report via `SendMessage`.**

`reviewer-documentation` never reported and its findings are unknown; however `reviewer-code` independently
covered the documentation areas and verified QUICKREF field by field (see "verified clean" in the review).

### BLOCKING — not fixable inside this repository

**A duplicate project id in the user's separate `ai-tools-projects` repository now stops every deploy.**

`config.local.yml:14` includes `../ai-tools-projects/projects`, where `xbid/project.yml` and
`xbid copy/project.yml` both declare `id: xbid` while deploying to different directories.

Confirmed end to end by the lead by running the real `./deploy.sh`:

    Duplicate manifest id 'xbid' declared in both .../projects/xbid/project.yml
      and .../projects/xbid copy/project.yml. Manifest ids must be unique - rename one of them.
    deploy.sh EXIT=1        files exported: 0

Before this delivery, `associateBy` was last-wins: one xbid won arbitrarily and the other 25 projects deployed
normally. The detection is correct — silently dropping a project *is* a bug, and that configuration is genuinely
broken — but the **blast radius contradicts the policy adopted one commit earlier**, where a bad manifest is
isolated so everything else still exports.

Not touched: separate repository, user's private data. Two ways forward, not mutually exclusive:
1. Rename the id in `xbid copy/project.yml`. Trivial, unblocks immediately.
2. Scope the loader failure per-project like the export failure, so one duplicate fails that project rather than
   the whole run. Arguably the more correct fix, and a deliberate design change rather than a patch.

### Fixed in response to the review (5 commits)

| Finding | Fix |
| --- | --- |
| **HIGH — `deploy.sh` exited 0 even when the engine failed** (trailing `cd ..`), defeating the entire non-zero-exit commit at the one command the docs tell users to run | Engine runs in a subshell that is the last command; `set -euo pipefail`; working directory quoted. Verified: failing run exits 1, successful run exits 0, path with spaces survives. |
| **MEDIUM — non-resolver export failures aborted the run** (`IllegalArgumentException` for a standalone skill with a relative `files:` entry, `NoSuchFileException` for a missing companion file) | Both raise a named `SkillFileResolvingException`, now collected. Catch list kept to a small named set so permission/OOM/programming faults still abort loudly. |
| **MEDIUM — `QUICKREF.md:335` was factually false** about version errors naming the file | Made the claim true rather than watering it down: `InvalidVersionException` → `ManifestLoadingException` naming the file → caught by the CLI. Plus two Troubleshooting entries. |
| **MEDIUM — `setup.sh` INT trap did not exit**, so Ctrl-C started a *second* build | Signals detach traps, restore, and re-raise (128+n). HUP/QUIT trapped too; stale backup refused rather than clobbered. Verified by signalling real runs; init-script checksum unchanged throughout. |
| **LOW — failure count counted manifest×adapter pairs** ("6 manifest(s)" for one broken agent) | Headline counts distinct manifests; per-adapter detail retained. |

Tests: **219 → 235** (+16), all written red-first and confirmed failing for the right reason before each fix.
No existing test weakened, skipped or deleted. Build green.

### Accepted from the review, NOT fixed — deliberate

- ~~**`deploy.directory` resolves against the JVM working directory, not `--working-dir`**~~ — **FIXED after
  this report was first written, at the owner's request.** Recorded here because the reasoning is still useful.
  Every `locations.*` path resolved against `--working-dir` while `deploy.directory` resolved against the JVM
  working directory: two bases in one config surface. The `../../` value was correct for `:cli:run` but
  launcher-dependent — under `installDist` or `java -jar` from the repo root it resolved to
  `/home/blaha/Documents/`, and `prepare()` deletes directories under whatever it resolves to. Corroborating
  smell at the time: `<repo>/ai-tools-projects/projects/` exists and is empty, consistent with a relative path
  created at a wrong base. Both call sites now share one `File.resolveDeclaredPath`, and the manifest moved to
  `directory: "."`. Absolute values are returned exactly as declared, so no existing project changed behaviour.
  Proved by running the `installDist` binary from several process working directories, including `/`, and
  confirming the output follows `--working-dir` and never the process directory.
- `ATOMIC_MOVE` and `fsync` on the export write; `README.md:111`'s "written atomically" is overbroad because
  `copySkillFiles` uses non-atomic `copyTo`.
- A JVM kill between temp-file creation and the move strands a `*.tmp` in the output directory.
- Test gaps: duplicate-id detection untested for *features*; `ToolsEngineTest` uses one adapter and one project,
  so "collected across all projects and adapters" is untested, as is `FragmentResolvingException`.
- CI: actions pinned to mutable major tags; Gradle caching configured twice; `push` + `pull_request` double-runs
  PR branches; `clean build` pulls `:server`, which shells out to `npm ci` with no `setup-node` — an undeclared
  network dependency `setup.sh` sidesteps by using `:cli:build`.
- Stale `15_config/` references remain in `90_docs/` (scoped out as legacy), but `README.md:197` and
  `QUICKREF.md:350` link `90_docs/TOOLS.md` as authoritative while it still documents `.output/`, `.backups/`
  and `deploy.yml`. The scope-out is defensible; the link from a corrected doc to a stale one is not.
- **Pre-existing, not introduced here:** `prepare()` swallows failure in all six adapters — each only calls
  `deleteRecursively()`, whose Boolean result is discarded and which does not throw. With `deploy.replace: true`
  a permission error during cleanup is silently ignored while the run reports success. This is the one genuinely
  swallowed failure left in the export path.

What *was* verified objectively, and is not in doubt:

- `./gradlew clean build` green, including ktlint and detekt at `maxIssues: 0`.
- 219 tests, 0 failures, run with `--rerun-tasks` so no result came from cache.
- Every deletion grep-verified as unreferenced before removal.
- The `../../` deploy path verified empirically by probing the CLI's actual run working directory.
- `.gitignore` rules verified with `git check-ignore -v`; no tracked source file became ignored and
  `.delivery/` remains committed.
- The untracked IDE init script confirmed still present on disk.

Review performed by the lead in place of the missing gate (not a substitute for independent review):

- **`ExportService`** — temp file is created in the *target directory*, so the move is a same-filesystem
  rename; `delete()` in `finally` is correctly a no-op after a successful move. Sound.
  - *LOW, not fixed:* a hard kill (SIGKILL, power loss) mid-write leaves a `*.tmp` file in the output
    directory. Previously it left a truncated real file, so this is still an improvement, but the litter is new.
  - *LOW, not fixed:* the KDoc says "atomically"; `Files.move` without `ATOMIC_MOVE` may in principle fall back
    to copy+delete. Same-directory rename makes this practically moot, and forcing `ATOMIC_MOVE` alongside
    `REPLACE_EXISTING` risks `AtomicMoveNotSupportedException` on some platforms. Current form is the safer choice.
  - *Theoretical only:* `File.createTempFile` requires a prefix of ≥3 characters. The prefix is
    `"${targetFile.name}."`, and every generated filename (`<id>.md`, `CLAUDE.md`, `SKILL.md`) exceeds that.
    Not reachable with current naming.
- **`ToolsEngine`** — `adapter.prepare(...)` is deliberately *outside* the per-manifest try, so a failure to
  prepare the output directory still aborts that adapter rather than being collected. That is defensible
  (prepare failure is environmental, not an authoring error) but it is an asymmetry with the new
  collect-all-then-fail policy and is **not covered by a test**. Worth a second opinion.
- **`LoaderService`** — `distinctBy { it.canonicalPath }` resolves symlinks, which is the right key for
  "same file reached two ways". On a case-insensitive filesystem two ids differing only by case would still
  be treated as distinct manifests but could collide on export; not a regression introduced here.
- **Regression risk of the duplicate-id check:** any repository that *currently* has two manifests sharing an
  id has been silently losing one. Such a repo will now fail to build until the collision is resolved. The
  repo analyst verified this repo has **zero** duplicate ids across all 130 manifests, so this repo is unaffected.

**Recommendation:** re-run `reviewer-code` over `git diff 0e7293e..HEAD` before merging, focusing on
`ToolsEngine.kt`'s failure policy and the `prepare` asymmetry noted above.

## 5b. Follow-up work, after the report was first written

The owner reviewed the findings above and asked for three of the deferred items to be done. Each went through a
developer agent, was verified, and is committed on this branch. Tests went 235 → 255 across them.

1. **Duplicate manifest ids scoped to what they affect.** The loader threw on the first collision, so one bad
   manifest stopped every project from deploying — inconsistent with the per-manifest isolation used for
   exports. Two projects sharing an id are now both dropped and every other project still exports; two features
   of one project sharing an id fail that project only. Duplicates in the global kinds (agents, prompts,
   rulesets, fragments, skills) still stop the run **deliberately**: `FilterService` returns every manifest when
   a project declares no filter, and a whitelist naming a missing id yields fewer manifests rather than an
   error, so dropping a colliding pair would silently export projects without it. Reporting improved regardless
   — every collision in a run is listed at once. *An earlier draft of this plan preferred the graceful option;
   the agent checked `FilterService` first and showed it could not be made safe without the resolver refactor
   that was out of scope. The stricter choice is the correct one.*
2. **`deploy.directory` resolved against `--working-dir`** — see the struck-through entry in §5.
3. **`GitHubCopilotAdapter` honours `deploy.replace`.** It cleared three `.github` directories on every export
   regardless of the flag. One flag now decides for every tool whether a deploy may delete. The original
   rationale is preserved in the code comment along with the accepted consequence: with `replace: false`,
   output from a retired naming scheme survives and needs removing by hand.

Also fixed in the same pass, from the review: `deploy.sh` exited 0 even when the engine failed; skill-file
authoring errors aborted the run instead of being collected; a malformed `version:` produced no filename; and
`setup.sh`'s INT trap started a second build instead of aborting.

## 6. Deliberately NOT done — and why

| Item | Reason |
| --- | --- |
| **Remove `07_mcp/github/`** (4.4 MB, 438 of 807 tracked files) | The analyst rated this HIGH for removal as "unreferenced". **Overruled by the lead:** git history shows `fc08d7d "Forking github mcp"` and `22e0a11 "Adding Github MCP"`. The vendoring is deliberate. Removing a deliberate fork is the owner's decision, not a refactor. **Raised for your decision.** |
| Delete `21_redteam/`, `91_examples/`, `20_evals/`, `08_recipes/` | Zero or near-zero inbound references, but `PLANNED_FEATURES.md` lists MCP and prompt-webpage support as pending. These hold authored source of truth awaiting re-implementation. |
| Realign `10_schemas/*.json` with the Kotlin models | Real and valuable — `skill.schema.json` is entirely divorced from `SkillManifest.kt` (it defines `command`, `timeout_sec`, `retry`; the model has `sections` and `files`). But this changes the authoring contract and deserves its own reviewed change. README now flags the schemas as stale. |
| Centralise adapter YAML frontmatter emission | Hand-rolled across all six adapters with only `description.replace("\n"," ")` as escaping. Verified **latent**: no current manifest description contains YAML-hostile characters. Outside the approved scope tier. |
| `ToolsEngine`/resolver structural refactors (analyst C1/C2/T2) | Behaviour-preserving restructures that need a safety net. This delivery *created* that net (a real `ToolsEngineTest`); the restructure should follow it, not precede it. |
| `detekt.yml` relaxations | Deliberate and documented with rationale. Not a defect. |
| Ruleset filename/ID mismatches (13 renames) | Cosmetic; churns history for every consumer. |

## 7. Recommended next actions

0. ~~**UNBLOCK YOUR DEPLOYS FIRST**~~ — **DONE.** The `xbid` collision was resolved by the owner: `xbid copy`
   now declares `id: xbid-copy`, so all 26 projects load. Note the consequence — `/Projects/xbid` had been
   silently losing every config update since 9 April, because `associateBy` was last-wins and `xbid-copy` kept
   winning. Its next deploy is its first in over three months, and with `replace: true` it clears and
   regenerates. The loader failure has also been scoped per-project since (§5b).
1. **Decide what to do about the dormant workflows** (see §5a, correction 1). `sonarcloud.yml` and
   `codeql.yml` have never run because they are not at the repository root. Either move them to
   `.github/workflows/` — after confirming the SonarCloud project and `SONAR_TOKEN` exist, or the result is a
   red check on every PR — or delete them and stop implying the project has Sonar and CodeQL coverage.
   This is a bigger finding than the project key it was discovered alongside.
2. **Restore manifest validation** (see §5a, correction 2). Nothing validates manifests today. The natural
   fix is to realign `10_schemas/*.json` with the Kotlin models and wire validation into the Gradle build, so
   it is enforced by the same gate as everything else.
3. **Decide on `07_mcp/github/`** — keep the vendored fork, convert it to a git submodule, or remove it.
4. **`prepare()` swallows cleanup failures** — all six adapters discard the `Boolean` from
   `deleteRecursively()`, so with `replace: true` a permission error during cleanup is ignored and stale files
   survive while the run reports success. The last genuinely swallowed failure in the export path.
5. **Tidy-up left to the owner:** `<repo>/ai-tools-projects/projects/` still exists and is empty — the stray
   directory that pointed at the wrong-base bug. Nothing creates it now; deleting it is a manual call.
   Same for the literal `~` directory at the repository root.

Note the **behaviour change** throughout: a broken ruleset reference, a duplicate id, or an unusable skill file
now fails the run with a non-zero exit where it previously exited 0 or silently dropped work. That is the intent,
but any automation depending on the old always-succeeds behaviour will now correctly fail.

## 8. Environment note

`~/.gradle/init.gradle.kts` (work Artifactory mirror) conflicts with this project's
`RepositoriesMode.FAIL_ON_PROJECT_REPOS`. It must be moved aside for a local build and restored afterwards.
`tmp/verify-build.sh` does this safely with a trap. `setup.sh` now handles the same conflict for new contributors.
