# Documentation review — user-scope deployments (commit `1c29a20`)

Reviewer: reviewer-documentation. Date: 2026-08-10. Branch: `feature/user-scope-deployments`.

Scope: `README.md`, `QUICKREF.md`, `09_deployments/README.md`, `PLANNED_FEATURES.md` as changed by `1c29a20`.

## How this was verified

Not by reading alone. I built a classpath from the module jars in `ai-tools-engine/*/build/libs/` (built 15:55, same session as the commit) plus the Gradle dependency cache, and ran `cz.cleanship.aitools.cli.AiToolsCliKt` on JDK 21 against seven purpose-built scratch fixtures under the session scratchpad. Nothing in the repository and no real home directory was touched. The probes were:

1. The QUICKREF `user.yml` example (QUICKREF.md:269-290) copied verbatim into a fixture — decodes, deploys, and produces every documented path.
2. The README `user.yml` example (README.md:150-170) copied verbatim — same, plus the agent destinations the first probe could not reach.
3. Two `user.yml` manifests contending for `~/.claude/CLAUDE.md`, one of them naming no `tools` — produces both the skipped-tool warnings and the contention failure.
4. `replace: true` against a hand-made neighbour skill and an artifact dropped from the manifest.
5. `--user-home ""`.
6. A manifest id containing a path separator, and a skill id containing one.
7. An artifact directory in the target home replaced by a symlink pointing outside the owned directory.

Plus `./deploy.sh --user-home 'a"b'` (which exits before it reaches Gradle, so it deployed nothing) and a machine-checked pass over every internal link and heading anchor in the four files.

## Accuracy

Accuracy is the dimension this review was asked to weight highest, and it is where the commit is strongest. Every load-bearing claim checks out against the engine. The findings below are the exceptions; the confirmations are listed under "What is correct" so the fixer knows what not to touch.

### DOC-1 — MAJOR — the ownership warning is absent from the two places a reader actually runs the deploy

**Where:** `README.md:57-63` (Common Workflow steps 2 and 3), `QUICKREF.md:12-16` (Common Commands).

**What the docs say.** README step 2 introduces `user.yml` ("or `09_deployments/<deployment>/user.yml` to set the filters of a user-scope deployment"), and step 3 is a bare `./deploy.sh` with no caution and no link. QUICKREF's Common Commands lists `./deploy.sh` first, with `./deploy.sh --user-home /tmp/try` offered afterwards as a convenience rather than as the safe first move.

**What the code does.** This repository ships `09_deployments/globals/user.yml` with `tools: [claude, codex]`. A `./deploy.sh` run from those instructions therefore rewrites the reader's real `~/.claude/CLAUDE.md` and `~/.codex/AGENTS.md` from the manifest — verified: `ToolsEngine.instructionsExport` warns and then `ClaudeUserScopeExporter.export` writes the file whether or not `replace` is set. Anything hand-written in those files, including everything the `#`-remember shortcut appended, is gone, and there is no backup (`PLANNED_FEATURES.md:73`+ confirms deploy backups do not exist).

**Why this matters.** The commit message states the case better than I can: this is "the one thing a reader must know before deploying". The commit delivers that warning very well in three places — `README.md:181-190`, `QUICKREF.md:306-308`, `09_deployments/README.md:302-310` — and `09_deployments/README.md:72` even opens its Quick Start with "A user deployment writes into your own home, so try it somewhere harmless first". The root README's three-step workflow is the highest-traffic path in the repository and the one a new contributor follows without reading further, and it is the only one of the four entry points that says nothing. The result is a documented quickstart that causes irreversible loss of the reader's own file.

**Fix.** One clause in each place, e.g. at README step 3: "If a `user.yml` is in play, this rewrites `~/.claude/CLAUDE.md` and `~/.codex/AGENTS.md` from the manifest — try `./deploy.sh --user-home /tmp/try` first, and see [User-Scope Deployments](#user-scope-deployments)." And in QUICKREF, move the `--user-home /tmp/try` line above the bare `./deploy.sh` or annotate the latter.

### DOC-2 — MINOR — the `Refusing to replace` entry blames a cause that load-time validation now prevents, and the error a bad id really produces is undocumented

**Where:** `QUICKREF.md:467`, `09_deployments/README.md:446`.

**What the docs say.** "**`Refusing to replace '...': it is not inside '...'`** — a manifest id would steer a replacing deploy out of the directory it owns."

**What the code does.** `LoaderService.requireSingleSegmentId` (`LoaderService.kt:209-220`) rejects such an id at load time, for every manifest kind, before anything is exported. Probe 6 confirms it: a `user.yml` with `id: ../escape` fails with

```
Failed to load .../09_deployments/globals/user.yml: Invalid manifest id '../escape': it contains a path separator. An id must name a single file or directory.
```

and a skill manifest with the same id shape fails identically. An id therefore cannot reach the containment check. The documented message is still reachable — `deleteArtifactDirectoryWithin` compares canonical paths, so an artifact directory in the target that is a symlink out of the owned directory triggers it (probe 7):

```
Refusing to replace '.../home/.claude/skills/my-skill' for 'skill 'my-skill'': it is not inside '.../home/.claude/skills'.
```

**Impact.** A reader who mistypes an id gets a message that appears in neither troubleshooting list and will search for it in vain; a reader who does hit `Refusing to replace` is sent to inspect an id that is fine. The rule the entry states ("no path separators, no `.` or `..`") is correct and worth keeping — it is only the message it is filed under that is wrong.

**Fix.** Split it: file the id rule under `Invalid manifest id '...'` (naming load time, so the reader knows nothing was written), and re-file `Refusing to replace` under its real cause — a path in the target that resolves outside the directory the deploy owns, which in practice means a symlink.

### DOC-3 — MINOR — `Found no deployment manifest` is described as a directory that exists

**Where:** `QUICKREF.md:461`.

**What the doc says.** "the `deployments` locations **exist but** hold no `project.yml` and no `user.yml`."

**What the code does.** `ToolsEngine.warnWhenNothingToDeploy` (`ToolsEngine.kt:166-177`) fires on `allData.projects.isEmpty() && allData.userDeployments.isEmpty()`, with no existence check anywhere in the path — `LoaderService.findYamlFiles` walks a non-existent directory to an empty result. A typo in a `config.local.yml` path produces exactly this warning, which is the likelier cause of the two.

**Impact.** Small, because the same sentence ends "check the paths the message lists", which points the reader the right way anyway. Worth correcting because the clause asserts something the reader can act on wrongly (looking inside a directory that is not the one they think). The sibling entry at `09_deployments/README.md:434` ("the configured directories hold no `project.yml` and no `user.yml`") does not carry the false implication and needs no change.

### DOC-4 — NIT — "two options and no subcommands"

**Where:** `README.md:71`, `QUICKREF.md:28`.

Clikt adds `-h, --help`, and the CLI's own `--help` output lists three options. The count is a convention many CLIs use and the two documented options are the two that matter, so this only rates a mention because the sentence is precise enough to invite the comparison. "Two options besides `--help`" costs three words.

### DOC-5 — NIT — the "under the hood" snippet omits the quoting `deploy.sh` actually emits

**Where:** `README.md:68`, `QUICKREF.md:19`.

The docs show `./gradlew :cli:run --args="--working-dir <repository root>"`. `deploy.sh:44` emits `--args="--working-dir \"$CUR_DIR\"$FORWARDED_ARGS"`, and the inner quotes are what survive Gradle's own splitting so that a repository path containing spaces stays one argument. A reader who copies the documented form for a spaced path hits the failure that both files explain two paragraphs later. Adding the inner quotes to the snippet makes it copy-safe.

### DOC-6 — NIT — the opening sentence attaches six tools to both destinations

**Where:** `README.md:5`.

"writes tool-specific configuration files for Windsurf, Antigravity, Cursor, Claude Code, GitHub Copilot, and Codex — into each project's deploy directory, and into your own user scope (`~/.claude/`, `~/.codex/`)" can be read as six tools deploying into a user scope; only `claude` and `codex` do. The parenthetical is what disambiguates, and it is right there, which is why this is a NIT rather than a finding — but this is the lede, and the rest of the document works hard to keep exactly this straight.

## Completeness

No gaps beyond the two folded into DOC-1 and DOC-2 above. Specifically checked and found present:

- The `user.yml` schema is documented field by field in all three reader-facing files, including which fields are absent and why.
- Both filter-fold order sensitivity and `tools` emptiness-vs-omission are carried into the user-scope sections rather than left to the project sections.
- The three limitations the task named — no stale-artifact cleanup, per-artifact-only replace, contended instructions files failing the run — appear in all three files.
- The `deploy.sh` double-quote refusal is documented in all three, with the workaround.
- Every `PLANNED_FEATURES.md` follow-up carries its SEC number, so a future reader can find the reasoning in `.tmp/user-scope-deployments/reviewer-security-user-scope-deployments.md`.

## Clarity

Strong throughout, and the three files are genuinely differentiated rather than duplicated: README explains, QUICKREF tabulates, `09_deployments/README.md` walks. Terminology is consistent ("user deployment", "instructions file", "the filename names the kind, the directory names the instance" repeated verbatim in all three). The `09_deployments/README.md` rewrite is a large net improvement — it replaces 1391 lines describing a retired TypeScript CLI behind an "outdated" banner with 460 lines describing the engine that exists.

One observation rather than a finding: the same destination table and the same limitations list appear in all three files. That is the right call for a table a reader must not have to go looking for, but it is now three places to edit when `windsurf` or `github_copilot` gains a user scope. Worth a note in whichever issue tracks that work.

## What is correct — verified, do not "fix"

Every item below was checked against code or a live run, and several read as though they might be approximations but are exact.

- **The destination table is exact**, in all three files, including the Codex `skill-` / `agent-` / `prompt-` directory prefixes and the skills-shaped agents and prompts. Probes 1 and 2 produced precisely `.claude/CLAUDE.md`, `.claude/agents/reviewer-code.md`, `.claude/commands/my-prompt.md`, `.claude/skills/my-skill/SKILL.md`, `.codex/AGENTS.md`, `.codex/skills/agent-reviewer-code/SKILL.md`, `.codex/skills/prompt-my-prompt/SKILL.md`, `.codex/skills/skill-my-skill/SKILL.md`.
- **Both `user.yml` examples load and deploy verbatim**, `prompts: {}` and the empty whitelists included. This was the checklist's first item and it holds.
- **The instructions-file description is exact.** `UserInstructionsPrinter` emits `# <id>`, the description, then `## Rules` — probe 1's generated `CLAUDE.md` matches the documented shape line for line.
- **The replace semantics are exact.** Probe 4: with `replace: true` and the manifest switched from `my-skill` to `other-skill`, the hand-written `skills/hand-written/` survived, the dropped `skills/my-skill/` survived whole (including a file added by hand inside it), and the instructions file was replaced with the documented warning naming its path. This is the most consequential safety claim in the docs and it is accurate in every particular, down to "hand-made neighbours are never touched".
- **The contention behaviour is exact.** Probe 3: `.claude/CLAUDE.md` was not written, the error named both manifest ids, `.codex/AGENTS.md` (claimed by only one) was written, the agents and prompts of both were deployed, and the run failed with `Export failed for 2 manifest(s)`. "Neither writes it, the run fails naming both, and their other artifacts are still deployed" is precisely what happens.
- **The skipped-tool message matches the documented string** verbatim: `globals: windsurf has no user-scope layout in this engine, so the manifest is not deployed for it.`
- **`--user-home` semantics are exact.** Relative resolves against `--working-dir` (probe 1 created the home under the fixture root, not under the JVM's cwd); empty is rejected with `--user-home must name a directory, not an empty path` (probe 5); a missing home is created and announced with the documented log line.
- **`deploy.sh` refuses a double-quoted argument** with exit 2 and the documented pointer, before reaching Gradle.
- **No stale claims survive.** The "CLI has exactly one option" line is gone from both files; `09_deployments/README.md` carries no External Projects registry, no `projects.global.yml`, no `deploy.yml`, no npm commands, no `global/`/`local/` split. The only `locations.projects` mentions left are the four that deliberately document the rename (`README.md:292`, `QUICKREF.md:459`, `09_deployments/README.md:146` and `:432`), and `09_deployments/README.md:143`'s `../ai-tools-projects/projects` example matches the real `config.local.yml`, which comments that the directory keeps its on-disk name.
- **Every `PLANNED_FEATURES.md` item is accurately worded.** I checked each against the security review: SEC-4 (project `prepare()` deletes the tool directory wholesale — confirmed at `ClaudeAdapter.kt:26-30`), SEC-16 residual (`ExportService.warnWhenSourceComesFromOutside` returns early on `sourceDir == null` — confirmed), SEC-7 (provenance), SEC-10 (`findYamlFiles` uses `walkTopDown` — confirmed at `LoaderService.kt:179-182`), SEC-11 (`config.yml:24` really is `HOME_FOLDER: "~"`). The DONE/open split is right, and the MCP note matches the model, which has no `mcps` field.
- **Every internal link and heading anchor in the four files resolves**, checked mechanically across all four files and their targets.
- `QUICKREF.md:419`'s "for this repository, a ruleset tagged `global`" is real: `01_rulesets/global/global-code-style.yml` and `global-questions.yml` both carry the tag.

## Files reviewed

- `/home/blaha/Documents/Projects/ai-tools/README.md`
- `/home/blaha/Documents/Projects/ai-tools/QUICKREF.md`
- `/home/blaha/Documents/Projects/ai-tools/09_deployments/README.md`
- `/home/blaha/Documents/Projects/ai-tools/PLANNED_FEATURES.md`

Ground truth consulted: `/home/blaha/Documents/Projects/ai-tools/.tmp/plans/analyst-user-scope-deployments-design.md`, `/home/blaha/Documents/Projects/ai-tools/.tmp/user-scope-deployments/reviewer-security-user-scope-deployments.md`, `/home/blaha/Documents/Projects/ai-tools/09_deployments/globals/user.yml`, `/home/blaha/Documents/Projects/ai-tools/09_deployments/ai-tools/project.yml`, `/home/blaha/Documents/Projects/ai-tools/config.yml`, `/home/blaha/Documents/Projects/ai-tools/config.local.yml`, `/home/blaha/Documents/Projects/ai-tools/deploy.sh`, and under `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/`: `cli/src/main/kotlin/cz/cleanship/aitools/cli/AiToolsCli.kt`, `engine/src/main/kotlin/cz/cleanship/aitools/engine/ToolsEngine.kt`, `.../services/LoaderService.kt`, `.../services/ConfigService.kt`, `.../services/FilterService.kt`, `.../services/ExportService.kt`, `.../models/UserDeploymentManifest.kt`, `.../models/ProjectManifest.kt`, `.../models/ManifestMetadata.kt`, `.../models/ToolType.kt`, `.../io/DeclaredPaths.kt`, `.../io/ArtifactPaths.kt`, `.../tools/ToolAdapter.kt`, `.../tools/UserInstructionsPrinter.kt`, `.../tools/adapters/claude/{ClaudeAdapter,ClaudeLayout}.kt`, `.../tools/adapters/codex/{CodexAdapter,CodexLayout}.kt`.

## Gaps — documentation that should exist and does not

1. **The `Invalid manifest id '...'` error** is undocumented in both troubleshooting lists (see DOC-2). It is the message a mistyped id actually produces, in every scope and every manifest kind.
2. **Companion-file behaviour in user scope** is documented only in `09_deployments/README.md:297` ("A skill's companion `files` are copied next to the generated `SKILL.md`, exactly as in project scope") — correct and verified, but README and QUICKREF's user-scope sections say nothing about it, so a reader of either comes away thinking a user-scope skill is a lone `SKILL.md`.
3. **`~/.claude/commands/`** is missing from the "directories that are created but never deleted" list at `09_deployments/README.md:319` and `README.md:194`, which name `skills/`, `agents/` and `.codex/skills/`. The behaviour is the same for commands; only the list is short.

None of the three is blocking.

## Verdict

**REQUEST CHANGES** — blocking: **DOC-1**.

Accuracy, the dimension this review was asked to weight highest, is clean: I could not find a single incorrect path, field, default, filter shape, message string, or behavioural claim about the user-scope feature, and I tried hard to, running the engine rather than reading it. DOC-2 through DOC-6 are all non-blocking and can be batched with the gaps above.

DOC-1 blocks only because of what it costs the reader: the README's three-step quickstart tells someone to run `./deploy.sh` without mentioning that, with the `user.yml` this repository ships, the run overwrites their `~/.claude/CLAUDE.md` with no backup. The commit already argues that this is the one fact a reader must have before deploying, and delivers it well everywhere else. It needs one more clause, in the one place a first-time reader will not have read the rest.

---

# Spot-check — fix commit `70f9e8e`

Scope: this commit only (`README.md`, `QUICKREF.md`, `09_deployments/README.md`; `PLANNED_FEATURES.md` untouched, correctly — no finding concerned it), limited to DOC-1 through DOC-6 and gaps 2 and 3. Re-ran the link/anchor checker over all four files: 0 problems. Working tree clean.

## Per-finding status

**DOC-1 — CLOSED.** `README.md:66-67` puts the warning immediately after the `./deploy.sh` block of step 3, names the manifest that makes it bite (`09_deployments/globals/user.yml`, which really does declare `tools: [claude, codex]`), states that no backup is kept, and routes the reader to `--user-home /tmp/try` and the full section. `QUICKREF.md:12-18` reorders Common Commands so the scratch-home run comes first and puts a two-line `# WARNING:` above the bare `./deploy.sh`. Both new claims are accurate: nothing in the export path writes a backup, and the shipped manifest names both tools.

**DOC-2 — CLOSED.** Split as suggested, in both files (`QUICKREF.md:467-469`, `09_deployments/README.md:446-448`). The new `Invalid manifest id '...'` entry states the rule exactly as `requireSingleSegmentId` implements it — separators, `.`/`..`, and empty, which is the complete list of rejections — and correctly says the check runs at load time for every manifest kind and fails before anything is written. The re-filed `Refusing to replace` entry now names the real cause. I verified its central claim rather than taking it on trust: `deleteArtifactDirectoryWithin` has exactly two call sites, `ClaudeAdapter.kt:130` and `CodexAdapter.kt:159`, both inside the user-scope exporters, so "a user deploy with `replace: true`" is precise — the six project-scope `prepare()` deletes use `deleteRecursively()` and produce no such message. "The run aborts and that check deletes nothing" also holds: the exception is thrown before the walk, and `exportOrCollectFailure` does not catch it.

**DOC-3 — CLOSED.** `QUICKREF.md:467` drops the "exist but" implication and adds the case I flagged: "A directory that does not exist reads the same as an empty one here, so a mistyped path in `config.local.yml` produces this too."

**DOC-4 — CLOSED.** "two options besides `--help`, and no subcommands" in both `README.md:77` and `QUICKREF.md:32`.

**DOC-5 — CLOSED.** Both snippets now carry the inner quotes (`--args="--working-dir \"<repository root>\""`), matching `deploy.sh:44`, and each is followed by a sentence explaining what they are for. Typed into bash as written, the escaped quotes reach Gradle's splitter exactly as the script delivers them.

**DOC-6 — CLOSED.** `README.md:5-6` splits the lede: six tools into the project deploy directory, then "Claude Code and Codex can also be configured for your user account instead of for a project, in `~/.claude/` and `~/.codex/`." No reading left in which the other four have a user scope.

**Gap 2 — CLOSED.** The companion-files sentence is now in all three files (`README.md:197`, `QUICKREF.md:308`, and the pre-existing `09_deployments/README.md:297`). Accurate — probe 1 copied `templates/example.txt` into both `.claude/skills/my-skill/templates/` and `.codex/skills/skill-my-skill/templates/`.

**Gap 3 — CLOSED.** `~/.claude/commands/` added to the never-deleted-wholesale lists in `README.md:219` and `09_deployments/README.md:319`. Correct per `ClaudeLayout.promptFile`, and the directory is indeed only ever created, never removed in user scope.

## New inaccuracies introduced

None. Every added sentence was checked against the engine.

One non-blocking observation, recorded rather than raised as a finding: `QUICKREF.md:12`'s "Try a run first" comment scopes itself correctly to "user.yml manifests land in a scratch home instead of your own", so it says nothing false — but unlike `09_deployments/README.md:79` it does not add that a `project.yml` has no equivalent switch and still deploys to its real `deploy.directory` during that run. In this repository that means the try-run still writes into the repository itself (`directory: "."`). Worth half a line if the file is touched again; not worth a commit of its own.

## Spot-check verdict

**APPROVE.** All six findings and both actionable gaps are closed as intended, the two claims most easily gotten wrong (which scope raises `Refusing to replace`, and what the id rule actually rejects) are stated exactly right, and no new inaccuracy was introduced. Gap 1 from the original report is closed by DOC-2's new entry. The documentation of this feature is now accurate throughout, and the warning a reader must have arrives before the command that needs it.
