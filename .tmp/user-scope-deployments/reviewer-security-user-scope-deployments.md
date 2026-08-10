# Security review — user-scope deployments

Branch `feature/user-scope-deployments` (`git diff main..HEAD`), reviewed against `.tmp/plans/analyst-user-scope-deployments-design.md`.
Reviewer: reviewer-security. Date: 2026-08-10.

Threat model used, as instructed: manifests are authored by the repository owner, so the primary risk is accidental self-damage, with a secondary supply-chain risk from third-party manifest directories added to `locations.deployments` / `locations.*` (which `config.local.yml` already does — it lists `../ai-tools-projects/projects`, a directory of another repository). Findings are rated for that model, not for a hostile-multi-user-host model.

Every finding below was verified against the code. Where the behaviour depended on JVM or Kotlin stdlib semantics I ran the real code path rather than reasoning from memory; those probes are noted inline. I did **not** run `./gradlew clean build` — the code-review agent is working the same tree and the build requires moving the shared `~/.gradle/init.gradle.kts` aside, which two concurrent agents cannot do safely. Nothing in this report depends on the build passing.

---

## Findings

### SEC-1 — MAJOR — `deleteArtifactDirectoryWithin` checks containment only at the top of the tree; the recursion below it follows symlinks

**Where:** `ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/io/ArtifactPaths.kt:18-28` (the guard), `:27` (the delete), and the claim it makes at `:13-15`.

**Defect.** The guard canonicalises the artifact directory and `owned`, and rejects anything that is not strictly inside `owned`. That part is correct. It then calls `deleteRecursively()` — `kotlin.io.FilesKt.deleteRecursively`, which walks with `FileTreeWalk`. `FileTreeWalk` decides "is this a directory?" with `File.isDirectory`, which *follows* symlinks, and then lists and deletes the link target's children. So the containment decision is made once, for the root of the tree, and every symlink underneath it is silently followed out of the owned directory.

The KDoc at `:13-15` overstates what was achieved: *"The containment is decided on the canonical paths, so a symbolic link pointing out of `owned` is caught as well rather than only the `..` segments that are visible in the path."* That is true of the artifact directory itself and false of everything inside it.

**Verified.** I ran the real `kotlin.io.FilesKt.deleteRecursively` (kotlin-stdlib 2.2.20, the version in `gradle/libs.versions.toml:6`) against a tree shaped like the user scope:

```
home/.claude/skills/my-skill/SKILL.md
home/.claude/skills/my-skill/data -> ../../../../Documents     (symlink)
Documents/thesis.txt
```

Result: the containment check passes (`my-skill` really is inside `skills`), and after the delete `Documents/thesis.txt` no longer exists. The `Documents` directory itself survives — only its *contents* are destroyed, which makes the damage quieter, not smaller.

**Damage scenario.** A user who keeps a large reference corpus outside the config directory symlinks it in — `~/.claude/skills/research-notes/corpus -> ~/Documents/research` — which is an ordinary thing to do with Claude Code skill bundles. A manifest declares a skill with id `research-notes` and `replace: true`. The next `./deploy.sh` deletes the contents of `~/Documents/research`. No warning names that path; the log line only says the deployment is replacing artifacts under `<home>`. There is no undo.

**Fix direction.** Delete with a walk that does not follow symlinks — `Files.walkFileTree` with an empty `FileVisitOption` set (the default is not to follow links) removing each entry, or `kotlin.io.path.deleteRecursively()`, which is specified to delete a symlink as a link rather than descending into it. A symlink inside the owned directory should be unlinked, never traversed. Then correct the KDoc at `:13-15` to claim only what the guard does.

---

### SEC-2 — MAJOR (blocking) — the containment guard is gated on `replace`, so the default configuration checks nothing, and the write half escapes the owned directory in every configuration

**Where:** guard call sites `ClaudeAdapter.kt:126-134` (gated at `:127`) and `CodexAdapter.kt:150-159` (gated at `:156`); unguarded write paths `ClaudeAdapter.kt:122` (prompt), `:124` (agent), `ClaudeLayout.kt:22,24,26`, `CodexLayout.kt:22-26`, and `ExportService.kt:55` (`targetDir.resolve(skillFile.target)`). `UserDeploymentManifest.kt:27` sets `replace = false` as the default. Nothing anywhere validates a manifest `id` — the only `Regex` in `models/` is the semver check in `Version.kt:40`.

**Defect.** Two gaps compound:

1. **The guard only runs when `replace: true`.** `replace` defaults to `false`, and the repository's own `09_deployments/globals/user.yml` does not set it. In that default configuration no path in the user scope is checked at all — the delete does not happen, and the write is unguarded, so an escaping id simply writes outside the owned directory with nothing raising an objection.
2. **The write half is unguarded even when `replace: true`.** For Claude, `export(agentContext)` and `export(promptContext)` in the user-scope exporter do not go through `replaceIfRequested` at all (unlike Codex, where agents and prompts are skill-shaped and do); they are single-file overwrites via `Files.move(…, REPLACE_EXISTING)` with no containment check in any configuration.

**Verified.** Replaying the exact primitives the adapters use (`ClaudeLayout` path construction via the real `kotlin.io.FilesKt.resolve`, then `ExportService`'s `File.createTempFile` + `Files.move(REPLACE_EXISTING)`; and `Files.copy(REPLACE_EXISTING)` for skill files) against a temp home:

- agent id `../CLAUDE` → wrote to `<home>/.claude/agents/../CLAUDE.md`, i.e. it **clobbered the engine-owned `~/.claude/CLAUDE.md`**, the global instructions file. Confirmed: the file's contents were replaced by the agent body.
- skill `files: [{source: payload.sh, target: ../../../../outside/victim.sh}]` → wrote arbitrary content to an arbitrary path **outside the home entirely**. Confirmed by reading the clobbered file back.

One caveat that bounds, but does not remove, the id-based case: the escape needs the intermediate directory to exist, because `File.createTempFile` resolves `..` through the real filesystem. On a first-ever deploy into an empty home the write fails with `IOException`; on any subsequent deploy — or in the same run once a well-formed artifact of that kind has been written — `~/.claude/agents` exists and the escape succeeds. The `files[].target` case has no such constraint beyond its own parent, which `ExportService.kt:56` creates.

**Damage scenario.** Two, of different severity:

- *Accidental (the likely one).* An author names an agent or prompt with a leading `../` by copy-paste from a path, or gives a skill a `files` entry whose `target` starts with `../` to "share" a file between two skills. The deploy silently writes outside its directory. The `.md` suffix that the Claude agent/prompt layout appends limits this to Markdown targets — enough to destroy a `README.md`, a note, or the generated `CLAUDE.md`, not enough to plant an executable.
- *Supply chain (the serious one).* `skillFile.target` has no suffix constraint and `skillFile.source` may be absolute (`ExportService.kt:79-80`), so a skill manifest from a third-party directory added to `locations.skills` is an arbitrary-file-write primitive with arbitrary content, aimed at `$HOME` by default now that the user scope exists. `~/.bashrc`, `~/.config/autostart/*.desktop`, `~/.ssh/authorized_keys` are all reachable. This half is pre-existing project-scope behaviour, but before this branch it could only reach a directory the author had explicitly typed into `deploy.directory`; now it reaches the home with no directory declared anywhere.

**Assessment of the known lead.** The developer's summary is accurate as far as it goes, but the mitigation as shipped is incomplete in a way that is worth blocking on: the guard protects the *rare* operation (a delete that only happens under `replace: true`) and leaves the *always-on* operation (the write) open, while its own KDoc at `ArtifactPaths.kt:9-11` frames the problem as "nothing validates that id today". A reader of that comment would reasonably conclude ids are now handled. They are not.

**Fix direction.** Validate the id once, at load time, where it is cheap and total: reject any id that is not a single path segment (no separator, no `..`, no `.`, not empty) for every `VersionedManifest`, failing the manifest with a named origin the way malformed YAML already does. That closes the delete side, both write sides, every adapter, and both scopes in one place, and makes the per-adapter guard a defence-in-depth assertion rather than the only line. Separately, constrain `skillFile.target` to stay within the skill directory (normalise and verify containment in `copySkillFiles`), since it is attacker-controlled independently of the id.

---

### SEC-3 — MAJOR — `./deploy.sh` now writes the real home unconditionally, and the documented way to avoid that does not work

**Where:** `deploy.sh:25` (the only invocation), `deploy.sh:11-12` (the comment describing the escape hatch), `09_deployments/globals/user.yml:1-5` (committed, `tools: [claude, codex]`), `AiToolsCli.kt:33-38` (the default).

**Defect.** `09_deployments/globals/user.yml` is committed to the repository and is picked up by `config.yml`'s `deployments: ["09_deployments"]`. `deploy.sh:25` invokes the CLI with `--args="--working-dir \"$CUR_DIR\""` and nothing else — it does not forward `"$@"`. So anyone who clones this repository and runs `./deploy.sh`, which `setup.sh` and `README.md` both tell them to do as the next step, has their personal `~/.claude/CLAUDE.md` and `~/.codex/AGENTS.md` overwritten with the repository owner's rules. There is no prompt, no dry run, and no `--user-home` on the command line.

The header comment at `deploy.sh:11-12` says *"Pass `--user-home` to the engine to deploy the user scope somewhere else, which is how a run is tried out without touching your own configuration."* That is the only documented mitigation for the whole feature, and it is unreachable through this script: extra arguments to `deploy.sh` are ignored. The option works only if you bypass `deploy.sh` and call `./gradlew :cli:run --args=…` yourself.

**Damage scenario.** A contributor clones the repo, follows the README, and loses whatever hand-written global instructions they had — for Claude Code that includes anything written through the `#` remember shortcut. The design accepts this loss for the *maintainer* (`design.md:113-115`), deliberately and with a migration step. It does not consider that the manifest travels with the repository to everyone else. The overwrite is also silent: `ToolsEngine.kt:268` warns only when `replace: true`, and this manifest does not set it, so the instructions file is replaced with an `INFO`-level line.

**Fix direction.** Two independent changes, either of which helps and both of which are cheap: forward `"$@"` from `deploy.sh` into `--args` so the documented escape hatch actually exists; and make a user-scope deploy require an explicit opt-in for the run (a `--user-scope` flag, or having `deploy.sh` name the home it is about to write), so that reaching outside the repository is always something the operator asked for on the command line rather than something a committed manifest decides for them. Log the instructions-file overwrite at `WARN` naming the absolute path, not just the home.

---

### SEC-4 — MAJOR — project-scope `prepare()` still deletes the tool directory wholesale, and the home is now a plausible target for it

**Where:** `ClaudeAdapter.kt:26-30`, `CodexAdapter.kt:26-30`, and the same shape in `WindsurfAdapter.kt:26`, `CursorAdapter.kt:26`, `AntigravityAdapter.kt:26`, `GitHubCopilotAdapter.kt:38-40`. Destination from `ToolsEngine.kt:175-176` (`deploy.directory`, variable-substituted, absolute permitted per `DeclaredPaths.kt:18-21`).

**Defect and how it relates to this branch.** The design is explicit that in user scope *"parent directories are never deleted wholesale"* (`design.md:126`), and the code honours that: I traced every delete call site in the main sources, and `exportUserAdapter` (`ToolsEngine.kt:252-312`) never calls `prepare()`. The user scope keeps its promise. **This finding is that the project scope makes the opposite promise, on the same adapters, into a destination the manifest names freely** — and the feature under review normalises pointing the engine at the home.

`prepare()` deletes `<deploy.directory>/.claude` in full when `replace: true`. `deploy.directory` is a manifest string expanded with the run's variables, and `config.yml:24` already declares `HOME_FOLDER`. A manifest with `directory: '${HOME_FOLDER}'` and `replace: true` — a shape that reads perfectly reasonable next to the new `user.yml`, and which the design doc itself names as the workaround people currently reach for (`design.md:10`) — deletes `~/.claude` entirely: `settings.local.json`, `projects/**/memory/` (Claude Code's auto-memory), every hand-installed skill, agent, and command. `deleteRecursively()` here follows symlinks exactly as in SEC-1, so the blast radius extends through any link inside it.

I checked the manifests actually on this machine: no in-repo or external manifest currently points at a home root (`09_deployments/ai-tools/project.yml:37` uses `directory: "."` and does not set `replace`; the external ones under `../ai-tools-projects/projects` all point at concrete project directories). So this is a loaded gun rather than a fired one — but the safety catch is a naming convention, not code.

**Fix direction.** Refuse a `deploy.directory` that resolves to the user's home, to `/`, or to any tool root the user scope owns, before `prepare()` runs — and refuse it during `resolveDeployDirectories`, which already exists precisely so that no project is touched before every destination is known (`ToolsEngine.kt:157-161`). Longer term, `prepare()` should delete the artifact paths it owns rather than the whole directory, which is the discipline the user scope already adopted.

---

### SEC-5 — MINOR — `--user-home` accepts relative, empty, and non-existent values, resolved against the process CWD

**Where:** `AiToolsCli.kt:33-38`.

**Defect.** The option is declared `.path(canBeFile = false)` with no `mustExist`, unlike `--working-dir` at `:20-26` which requires existence. A non-existent home is therefore created silently, and a relative value resolves against the process's current directory — not against `--working-dir`, which is the base every other declared path in the run uses (`ConfigService.resolveLocations`, `ToolsEngine.kt:176`). That inconsistency is invisible until something is written to the wrong place.

**Verified** (real `kotlin.io.FilesKt.resolve`, process CWD = repository root):

| `--user-home` | resulting instructions file |
| --- | --- |
| `''` | `.claude/CLAUDE.md` relative to the CWD — i.e. it writes into the repository |
| `.` | `./.claude/CLAUDE.md` — same |
| `../..` | `../../.claude/CLAUDE.md` — two levels above the CWD |
| `/` | `/.claude/CLAUDE.md` |

The empty-string case is the sharp one: Kotlin's `File.resolve` on an empty base yields a *relative* path (Java's `File(File(""), child)` would have yielded `/child` instead), so `--user-home ""` quietly redirects the whole user deploy into whatever directory the shell happened to be in — and, with `replace: true`, the deletes go there too.

**Fix direction.** Resolve the option against `--working-dir` for consistency with every other declared path, or require it to be absolute; reject an empty value explicitly; and log the absolute home the run resolved to before the first write, so the destination appears in the transcript rather than being inferred.

---

### SEC-6 — MINOR — `ArtifactPathException` bypasses the CLI's error translation and aborts a partially completed deploy

**Where:** `ArtifactPaths.kt:32-36` (intent), `ToolsEngine.kt:450-467` (`exportOrCollectFailure` catches only `RulesetResolvingException`, `FragmentResolvingException`, `SkillFileResolvingException`), `AiToolsCli.kt:44-60` (the `catch` list).

**Defect.** Aborting the run rather than collecting the failure is a defensible choice and the KDoc argues it well. But `ArtifactPathException` is not in the CLI's `catch` list, so it does not become a `CliktError`. It propagates out of `main`, and the operator gets a raw JVM stack trace instead of the carefully written remediation sentence the guard composes (*"Give the manifest an id that names a single directory"*), which is now buried in the middle of it. That contradicts the documented contract at `AiToolsCli.kt:40-43` — translate the failures a manifest author can actually fix — and this is exactly such a failure.

Second, smaller point: the `@throws` at `ArtifactPaths.kt:16` says the exception leaves *"everything on disk untouched"*. True of that one delete; not true of the run, which by then has exported every project and, in the user scope, at minimum the instructions file (queued first at `ToolsEngine.kt:272-276`) plus any artifact ordered before the offending one. The wording invites a reader to believe the deploy is transactional.

**Fix direction.** Add `ArtifactPathException` to the CLI's `catch` list so it exits through `CliktError` like its peers, and reword the `@throws` to promise only that this delete did not happen.

---

### SEC-7 — MINOR — an omitted filter block selects everything, at the most privileged destination the engine writes to

**Where:** `FilterService.kt:9` (`if (filters.isEmpty()) return manifests.toList()`), `UserDeploymentManifest.kt:28-32` (every block defaults to an empty filter).

**Defect.** The permissive-by-default filter is a deliberate, consistent choice inherited from `ProjectManifest`, and in project scope its cost is a few extra files in a repository. In user scope the same default writes into `~/.claude/CLAUDE.md`, the highest-privilege instruction surface a Claude Code user has: it is prepended to every session in every project on the machine. A `user.yml` that simply omits `rulesets:` flattens **every ruleset in the run** into that file. The repository's own manifest sidesteps this correctly — `09_deployments/globals/user.yml:9-13` filters on the `global` tag and comments the four empty whitelists as deliberate — so this is about the trap left for the next manifest, not about the one that shipped.

The supply-chain edge of the same point: ruleset text is copied verbatim into the global instructions file with no provenance marker beyond the header naming the manifest. A ruleset added from a third-party `locations.rulesets` directory becomes a standing instruction to every agent the user runs. There is no injection *vulnerability* here in the classic sense — the content is meant to be instructions — but the trust boundary between "a YAML file in some directory" and "my global agent instructions" is crossed with no visible step.

**Fix direction.** Require `rulesets` to be declared explicitly in a `user.yml` (make the field non-defaulting for this kind), so selecting everything is something the author typed. Consider having `UserInstructionsPrinter` emit the source manifest id per ruleset block, so an unexpected rule can be traced to the file it came from.

---

### SEC-8 — MINOR — `strictMode = false` makes the `locations.projects` → `locations.deployments` rename silently lossy

**Where:** `ConfigService.kt:24-29`, `ConfigManifest.kt:21-28`.

**Defect.** The config YAML is decoded with `strictMode = false`, so an unknown key is dropped without a word. This branch renames `locations.projects` to `locations.deployments`. A machine-local `config.local.yml` still using the old key — the file is gitignored, so it does not travel with the rename and no migration can reach it — loses its deployment directories silently: `resolveLocations` falls back to `config.yml`'s single `09_deployments` entry, and every machine-private project simply stops being deployed. The run reports success.

The maintainer's own `config.local.yml` is already migrated (it declares `deployments` at line 12), so this does not bite here — but it will bite on any other machine, and the same silence hides ordinary typos in every `locations.*` key.

**Fix direction.** Turn strict decoding on for the config the way manifests already have it (`LoaderService` relies on strict decoding as its validation story per `design.md:138`), or, if unknown keys must stay tolerated, special-case the retired `projects` key with an explicit "renamed to `deployments`" error for one release.

---

### SEC-9 — INFO — TOCTOU between canonicalisation and delete

**Where:** `ArtifactPaths.kt:19-27`.

The guard canonicalises, decides, and then deletes by re-walking the *original* path. A process that swaps a component for a symlink in that window redirects the delete. Exploiting it requires an attacker who can already write inside `~/.claude/skills`, which in this threat model means they have the user's own privileges — so the practical value is near zero and it is recorded only for completeness. Fixing SEC-1 with a `NOFOLLOW_LINKS` walk narrows this too, since the walk then refuses to descend through links regardless of when they appeared.

---

### SEC-10 — INFO — the loader's directory walk follows symlinks and has no cycle detection

**Where:** `LoaderService.kt:173-177` (`directory.walkTopDown()`), also `LoaderService.kt:141` for skills.

`FileTreeWalk` follows directory symlinks and does not track visited inodes, so a symlink cycle under any configured `locations.*` directory makes the loader walk forever. Self-inflicted denial of service only, pre-existing, and not reachable without the user creating the loop themselves. Noted because the same symlink-following primitive is what makes SEC-1 exploitable, and a fix there is a natural moment to look at this one.

---

### SEC-11 — INFO — `HOME_FOLDER: "~"` in `config.yml` is not a home directory

**Where:** `config.yml:24` (pre-existing on `main`, not introduced by this branch; commit `3406289`).

Java's `File` does not expand `~`, so on any machine that does not override `HOME_FOLDER` in `config.local.yml`, a manifest using `${HOME_FOLDER}/x` deploys into a *literal directory named `~`* under the working directory rather than into the home. The local `config.local.yml:30` overrides it with `/home/blaha`, which is why this has not been noticed. It is listed here because it sits directly on the "how does this project name the home" question and because a default that silently means something other than what it looks like is exactly the kind of thing that turns SEC-4 from theoretical into real.

---

## What the implementation gets right

These are load-bearing and were verified against the code, not inferred from the tests:

- **No wholesale parent delete in user scope.** I enumerated every destructive filesystem call in the main sources. The only deletes are the six adapters' `prepare()` (project scope) and the two `deleteArtifactDirectoryWithin` call sites. `exportUserAdapter` never calls `prepare()`, so `~/.claude` and `~/.codex` — and `skills/`, `agents/`, `commands/` inside them — are never removed. The design's central safety requirement holds in the code, not just in the tests.
- **The containment check uses `Path.startsWith`, not a string prefix.** `Path.startsWith` compares whole name elements, so a sibling named `skills-evil` is correctly *not* treated as being inside `skills`. This is the failure mode a hand-rolled `absolutePath.startsWith(…)` would have had.
- **The `artifactPath == ownedPath` equality rejection is not redundant.** It is what stops an empty id or `.` (both of which canonicalise back to the skills directory) from deleting the entire skills directory. Easy to omit; correctly present.
- **Writes are atomic.** `ExportService.export` renders to a temp file in the target directory and `Files.move`s it into place, so a ruleset that fails to resolve halfway through cannot leave a truncated `~/.claude/CLAUDE.md` — which matters much more now that the file is a global instruction surface.
- **The `--user-home` seam genuinely protects the real home during tests.** `UserScopeSupportTest` even asserts that nothing was written into the temp home while discovering that a tool has no user scope.
- **Unimplemented tools fail loudly, not silently.** `ToolAdapter.userScope` has no default implementation; each of the four adapters without a user scope returns `null` explicitly and `ToolsEngine.kt:260-263` warns. A manifest naming a tool is never dropped without a word.
- **The migration of the maintainer's hand-written `~/.claude/CLAUDE.md` is faithful.** I diffed the live file against `01_rulesets/global/global-code-style.yml` and `global-questions.yml`: both rules are carried over verbatim, and those two files are the only rulesets in the repository tagged `global`, so the generated file will be bounded to exactly them. The one destructive step the design signed up for was done carefully.

---

## Files reviewed

Engine (main):
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/io/ArtifactPaths.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/io/DeclaredPaths.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/ToolsEngine.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/LoaderService.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/ExportService.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/ConfigService.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/services/FilterService.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/UserDeploymentManifest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/UserDeployment.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/EngineConfig.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/ConfigManifest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/SkillManifest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/ToolAdapter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/UserScopeExporter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/UserInstructionsPrinter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/claude/ClaudeAdapter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/claude/ClaudeLayout.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/codex/CodexAdapter.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/codex/CodexLayout.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/tools/adapters/{windsurf,cursor,github,antigravity}/*Adapter.kt` (delete sites and `userScope` only)

CLI (main):
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/cli/src/main/kotlin/cz/cleanship/aitools/cli/AiToolsCli.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/cli/src/main/kotlin/cz/cleanship/aitools/cli/ToolsApplicationRunner.kt`

Tests (read to judge coverage of the guard, not reviewed for their own quality):
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/tools/adapters/claude/ClaudeAdapterTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/tools/adapters/codex/CodexAdapterTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/tools/UserScopeSupportTest.kt`
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/engine/src/test/kotlin/cz/cleanship/aitools/engine/ToolsEngineTest.kt` (diff only)
- `/home/blaha/Documents/Projects/ai-tools/ai-tools-engine/cli/src/test/kotlin/cz/cleanship/aitools/cli/AiToolsCliIntegrationTest.kt`

Repository / configuration:
- `/home/blaha/Documents/Projects/ai-tools/config.yml`, `/home/blaha/Documents/Projects/ai-tools/config.local.yml`
- `/home/blaha/Documents/Projects/ai-tools/deploy.sh`, `/home/blaha/Documents/Projects/ai-tools/setup.sh`
- `/home/blaha/Documents/Projects/ai-tools/09_deployments/globals/user.yml`, `/home/blaha/Documents/Projects/ai-tools/09_deployments/ai-tools/project.yml`, `/home/blaha/Documents/Projects/ai-tools/09_deployments/README.md`
- `/home/blaha/Documents/Projects/ai-tools/01_rulesets/global/global-code-style.yml`, `/home/blaha/Documents/Projects/ai-tools/01_rulesets/global/global-questions.yml`
- `/home/blaha/Documents/Projects/ai-tools/.tmp/plans/analyst-user-scope-deployments-design.md`

Test coverage gaps worth noting to the developer: the two containment tests (`ClaudeAdapterTest` "should delete nothing when the id of a skill climbs out of the skills directory", `CodexAdapterTest` likewise) both exercise `replace = true` only, which is precisely the configuration where the guard exists. There is no test for an escaping id with `replace = false`, none for an escaping *agent* or *prompt* id in Claude's user scope, none for a `skillFile.target` that climbs out, and none for a symlink inside an artifact directory. Each of those is a currently-passing suite hiding a currently-broken behaviour.

---

## Recommendations beyond the immediate fixes

1. **Validate manifest ids once, centrally, at load time.** One rule — an id is a single path segment — collapses SEC-2 entirely and turns every per-adapter path guard into a redundant assertion. Doing it in the loader means it also covers the four adapters that have no user scope yet and any adapter added later, which is the difference between a fix and a pattern someone has to remember to repeat.
2. **Give the engine one chokepoint for "write this file" and "delete this tree", both taking an owning root.** Right now `ExportService` writes wherever it is pointed and each adapter decides for itself whether to check. A single `writeArtifactWithin(owned)` / `deleteArtifactDirectoryWithin(owned)` pair that every adapter must route through makes the containment property checkable by reading one file instead of six.
3. **Make destinations visible before they are used.** Log the resolved absolute home and the absolute path of the instructions file at `INFO` before the first write of a user deploy, and at `WARN` when it replaces an existing file the engine did not write in this run. The current logs name the home but never the file, which is the one thing a user would want to see in the transcript afterwards.
4. **Offer a real dry run.** `--dry-run` that prints every path the run would write or delete would make SEC-3's escape hatch honest, would let a user inspect the blast radius of `replace: true` before it happens, and would have made SEC-1 and SEC-2 obvious during development.
5. **Reconsider `replace: true` for user scope specifically.** Deleting a directory the engine did not create in this run, inside a home shared with hand-installed content, buys stale-file cleanup for one artifact. The deployed-files ledger already recorded as future work (`design.md:127-128`) gets the same benefit without the delete ever being unbounded — it is worth asking whether `replace` should ship at all in user scope before the ledger exists.
6. **Write the tests that would have caught these.** Specifically: an escaping id with `replace: false` (asserts the write is contained), a symlink inside an artifact directory pointing at a file outside the home (asserts the delete does not follow it), and a `skillFile.target` containing `../` (asserts the copy is contained). All three are cheap against a `@TempDir` home and all three currently fail.

---

## Verdict

**REQUEST CHANGES.**

Blocking: **SEC-1**, **SEC-2**, **SEC-3**.

SEC-1 and SEC-2 together mean the feature's stated safety property — a deploy touches only the artifact paths it owns — does not hold: SEC-1 lets a delete follow a symlink out of the owned directory, and SEC-2 lets a write leave it outright, in the default `replace: false` configuration where no check runs at all. Both are verified, not theoretical. SEC-3 is blocking for a different reason: the feature reaches outside the repository into a real user's home on the documented first-run path, with the one documented opt-out not actually wired up.

SEC-4 is rated MAJOR but is **not** blocking this branch — it is pre-existing behaviour on the project path that this feature makes more likely to be hit rather than something it introduces. It should be filed and fixed on its own, not folded into this review's remediation.

The rest (SEC-5 through SEC-8) are worth fixing in this branch while the code is warm but do not block. SEC-9 through SEC-11 are recorded for completeness only.

One note on judgement, since the developer asked for it: the guard that was added is well-reasoned, correctly implemented for the case it addresses, and its KDoc is genuinely good writing. The problem is scope, not craft — it protects the operation that only runs under an opt-in flag while leaving the operation that always runs unprotected, and its comments read as though the id problem has been dealt with. Validating ids at load time is a smaller change than the guard already made and subsumes it.

---

# Re-review — fix cycle 1

Scope: commits `01eaa7b`, `beb3a2c`, `a0458c4` (`git diff 730c995..HEAD`), limited to the findings I raised. Waived items (SEC-4, SEC-7, SEC-9, SEC-10, SEC-11, and the opt-in-flag portion of SEC-3) are not re-raised; I read `.tmp/user-scope-deployments/waivers.md` and the rationales are sound, in particular that SEC-4 is pre-existing project-scope behaviour recorded as a follow-up rather than smuggled into this branch.

I re-ran every empirical probe from the first pass against the new code, and added three more. I also ran the build this time: `./gradlew clean build` in `ai-tools-engine` is **BUILD SUCCESSFUL**, 416 tests across all modules with 0 failures, 0 errors, 0 skipped, ktlint and detekt included. `~/.gradle/init.gradle.kts` was moved aside for the run and is restored — verified present at its original path with no leftover backup file.

## Per-finding status

### SEC-1 — CLOSED

`ArtifactPaths.kt:46-63` replaces `deleteRecursively()` with `Files.walkFileTree` and a `SimpleFileVisitor`. The two-argument `walkFileTree` overload passes an empty `FileVisitOption` set, so links are not followed; a symlink is reported to `visitFile` (not `preVisitDirectory`) and `Files.deleteIfExists` unlinks it rather than descending.

Verified by re-running my original probe against a faithful replica of the new function, extended to cover both link shapes:

```
skillDir removed          = true
Documents/ still exists   = true
Documents/thesis survives = true
single.txt survives       = true
```

That is the exact scenario that failed in the first pass, now passing: a directory symlink and a file symlink inside the artifact directory are both unlinked, and both targets survive untouched.

The KDoc at `ArtifactPaths.kt:14-24` now claims exactly what the code does — it separates the containment decision (canonical, catches a link *as* the artifact directory) from the walk (never follows a link *inside* it) instead of conflating them. `ArtifactPathsTest` covers the delete, both link shapes, the not-inside rejection, the owned-directory-itself rejection, and the `skills-evil` sibling case; `ClaudeAdapterTest` adds the end-to-end "corpus linked into a skill bundle survives a replacing deploy". Nothing left open.

### SEC-2 — CLOSED (one unrequested residual recorded as SEC-16)

Both halves are addressed, in the right place:

- **Ids.** `LoaderService.kt:208-219` validates every manifest at load time, and `Yaml.load` is now `private inline fun <reified T : VersionedManifest>` so all eight `loadX` functions route through it — there is no loader that skips the check. Failure is a `ManifestLoadingException` naming the file and the offending id, which the CLI already translates.
- **Skill file targets.** `ExportService.kt:75-84` normalises `targetDir.resolve(target)` and rejects anything that leaves `targetDir`, as a `SkillFileResolvingException` collected per manifest rather than aborting the run — the right severity for an authoring error.

I battery-tested the id rule against 24 ids, including every lookalike I could think of, checking both the bare form (`skills/<id>`) and the composed forms the layouts build (`skill-<id>`, `<id>.md`). Rejected: `""`, `.`, `..`, `../x`, `..\x`, `a/../..`. Accepted and **none escaping**: `....`, `..;`, `.. `, ` ..`, `%2e%2e`, `..%2F..`, fullwidth `．．`, `~`, `-rf`, `skill-..`, plus the real ids in this repository (`xbid.bobcat`, `coding-kotlin`, `a.b`). An id containing a NUL byte is accepted by the rule but throws `IOException` on the first path operation — loud, not silent. The `skill-` prefix case matters and is safe: `skills/skill-..` is a literal directory named `skill-..`, not a traversal, because the prefix makes it a distinct name rather than a `..` component.

The regression test is the one I asked for and asserts the right thing: `ToolsEngineTest.kt:1002-1026` uses `replace = false` (commented as "the write is what has to be contained, not only the delete"), and asserts the run fails at load with nothing written anywhere in the tree and `userHome` never created.

One observation, not a defect: a bad id aborts the run on the first offender rather than collecting every bad id the way the engine's stated collect-all-then-fail policy does for unresolvable references. That is consistent with how malformed YAML already behaves and it fails closed, so I would leave it; it only means an author with three bad ids sees them one run at a time.

### SEC-3 — CLOSED for the two required mitigations; one new defect in the forwarding (SEC-17)

`deploy.sh` now builds a `FORWARDED_ARGS` string from `"$@"` and appends it to `--args`, and `ToolsEngine.instructionsExport` logs `WARN "Replacing the instructions file '<absolute path>'"` when the file already exists (`INFO "Writing…"` when it does not). Both are what I asked for.

I verified the forwarding by executing the script's argument-building logic in isolation. It is correct for every realistic input and contains **no command injection** — `$(touch /tmp/pwned)` and backticks passed as arguments are forwarded as literal text and are never evaluated (confirmed: `/tmp/pwned` was not created). Empty `"$@"` under `set -u` is safe, and a path containing spaces round-trips correctly. The one case that does not work is a path containing a double quote — see SEC-17.

The residual risk the lead is surfacing to the maintainer (a cloner running `./deploy.sh` overwrites their own `~/.claude/CLAUDE.md`) is unchanged and correctly escalated as a product decision rather than silently accepted. With the WARN naming the absolute path, it is at least visible in the transcript when it happens.

### SEC-5 — CLOSED

`AiToolsCli.kt` rejects an empty value with `.check("--user-home must name a directory, not an empty path")`, and `run()` resolves the value through `workingDir.toFile().resolveDeclaredPath(userHome.toString())`, so a relative value now shares the base every other declared path of the run uses. `ToolsEngine.exportUserDeployments` logs `INFO "Deploying the user scope under '<absolutePath>'"` before any target is built, and separately says so when the home does not exist yet. The KDoc explains why a non-existent home is allowed but an empty one is not, which is the right distinction. Tests cover both the empty rejection and the relative resolution.

### SEC-6 — CLOSED

`ArtifactPathException` and the new `RetiredConfigKeyException` are both in the CLI's `catch` list, so they exit as `CliktError` with their own message rather than a stack trace. The `@throws` on `deleteArtifactDirectoryWithin` now reads "Nothing was deleted by this call; whatever the run had already exported before it stays where it is" — accurate, and no longer implies the run is transactional. `ToolsEngine.process` documents the abort and why it differs from a collected authoring error.

### SEC-8 — CLOSED

`LocationsConfig` keeps a `projects` field solely so `ConfigService.rejectRetiredKeys` can fail on it, naming the retired key, its replacement, and which of the two files declared it (local checked first). `warnWhenNothingToDeploy` adds a second net for the case the rejection cannot see — no deployment locations configured, or locations that hold no manifest. Both were what the finding asked for, and the KDoc explains why tolerating unknown keys generally is still right while this one key is not.

## The "guard is now unreachable" claim — verified, with the boundary stated

The developer's claim holds. I checked it four ways rather than taking it on the loader alone:

1. **Every loader validates.** All eight `loadX` functions call the now-private `Yaml.load`, which validates before returning. There is no bypass inside `LoaderService`.
2. **Nothing constructs manifests outside the loader in production code.** Grepping every `*Manifest(` construction site across all module main source sets returns only the `data class` declarations themselves. The only callers that build manifests directly are tests.
3. **The `:server` module is not a second entry point.** It exposes `GET /{id}` and `POST /{id}/fill` for prompts and neither builds a filesystem path from manifest content nor drives `ToolsEngine`.
4. **Ids that pass validation cannot compose badly.** Every layout builds either `<id>`, `<prefix>-<id>` or `<id>.md` inside a constant directory. With separators, `.` and `..` excluded, all three stay a single path segment — confirmed by the battery above, which tested the composed forms too. The surrounding path elements (`.claude`, `.codex`, `skills`, `agents`, `commands`, `CLAUDE.md`, `AGENTS.md`) are compile-time constants, so no tool or layout constant contributes attacker-controlled text.

The honest boundary: the per-adapter guard is defence-in-depth *for anything that went through the loader*, and it remains the only line for a caller that constructs a manifest itself. Today that is only tests — which is exactly why keeping the guard rather than deleting it as redundant was the right call.

The manifest-derived strings that still reach a filesystem path after this cycle are: `skillFile.target` (now contained), `skillFile.source` (still unconstrained — SEC-16), and `deploy.directory` (SEC-4, waived and recorded as a follow-up). That is the complete list.

## New findings

### SEC-16 — MINOR — the skill-file fix contained the write but not the read

**Where:** `ExportService.kt:99-110` (`resolveSource`).

`resolveTarget` now bounds where a companion file lands, but `resolveSource` is unchanged: `if (file.isAbsolute) return file`, otherwise `sourceDir.resolve(source)` with no normalisation and no containment. A skill manifest can therefore name `source: /home/user/.ssh/id_rsa` or `source: ../../../.aws/credentials` and have it copied into the skill directory it legitimately owns — which in user scope is `~/.claude/skills/<id>/`, a directory whose whole purpose is to be read into an agent's context.

This is pre-existing and I did **not** ask for it in SEC-2's fix direction, which named only `target`; I am recording it because the fix closed one half of the same manifest field and the other half is now the more interesting one. Exploitability under the stated threat model is low — it needs a skill manifest the user did not write, from a directory they added to `locations.skills`. Severity is bounded by the fact that it is a copy into a readable location rather than an overwrite of anything.

**Fix direction.** Either require `source` to resolve inside the skill's own directory (symmetrical with `resolveTarget`, and the shape every manifest in this repository already uses), or keep absolute sources but log each one at `WARN` so a file pulled in from outside the manifest's directory is visible in the run.

### SEC-17 — MINOR — `deploy.sh` escapes quotes in a way Gradle's argument splitter does not understand

**Where:** `deploy.sh:18-24`, specifically `FORWARDED_ARGS="$FORWARDED_ARGS \"${arg//\"/\\\"}\""` and the comment above it claiming "any quote inside it escaped".

**Defect.** Gradle's `--args` string is split by `org.gradle.util.internal.ArgumentsSplitter`, which honours double quotes as grouping but has **no escape mechanism** — a backslash is a literal character. I ran the real splitter from the wrapper's own distribution (`gradle-9.0.0/lib/gradle-stdlib-java-extensions-9.0.0.jar`, the version `gradle-wrapper.properties` pins) against the exact strings the script produces:

| forwarded argument | what Gradle hands the CLI |
| --- | --- |
| `/tmp/try` | `/tmp/try` — correct |
| `/tmp/my dir` | `/tmp/my dir` — correct |
| `/tmp/we"ird` | `/tmp/we\ird` — **wrong path, silently** |
| `/tmp/back\` | `/tmp/back\` — correct, by accident |

So the escaping does not do what its comment says: `\"` is not an escape, and an argument containing a quote is corrupted into a different, still-plausible path rather than rejected. The trailing-backslash case works only *because* the escape is not honoured, which means the two behaviours are mutually exclusive and no amount of quoting fixes both.

**Damage scenario.** Narrow but exactly the wrong shape: someone runs `./deploy.sh --user-home '/tmp/my "test" home'` to avoid touching their real configuration, and the deploy silently writes to `/tmp/my \test\ home` instead of the directory they named. A path containing `"` is rare on Linux, which is why this is MINOR rather than more — the common cases (plain paths, paths with spaces) are correct, and there is no command injection.

**Fix direction.** Since Gradle offers no escape, do not pretend to provide one: reject any forwarded argument containing `"` with a clear message naming the limitation, so the run stops instead of writing somewhere the operator did not ask for. Fix the comment either way — it currently tells the next reader that quotes are handled.

### SEC-18 — INFO — `resolveTarget` containment is lexical, so a symlink already inside the skill directory still redirects a copy

**Where:** `ExportService.kt:75-77` (`normalize()` on both sides rather than canonicalisation).

The KDoc gives an honest reason — the target does not exist yet, so there is nothing to canonicalise — and that reasoning is correct for the leaf. It is not correct for the ancestors: if `~/.claude/skills/<id>/corpus` is a symlink the user created, `target: corpus/notes.md` passes the lexical check and `Files.copy` follows the link out of the skill directory. That is the same setup SEC-1's own KDoc describes ("a corpus linked into a skill bundle"), and it is reachable only with `replace: false`, since a replacing deploy unlinks the symlink first.

Impact is one declared file written inside a directory the user deliberately linked in, so this is a note rather than a defect worth a fix on its own. If it is ever tightened, the shape is to canonicalise the deepest ancestor that exists and check the remainder lexically against that.

### SEC-19 — INFO — the id rule is Linux-shaped

**Where:** `LoaderService.kt:212-213`.

The rule rejects `\` as well as `/`, so Windows was partly in view, but `C:`, `NUL`, `COM1` and friends pass: on Windows a drive-relative prefix or a reserved device name is not a normal filename, and an agent id of `NUL` would write to the null device rather than to a file. Purely a portability note — the engine is developed and run on Linux here, and nothing in the repository suggests Windows is a supported target. Worth one line in a follow-up only if Windows support is ever claimed.

## Additional observation — the contended-instructions-file logic

Not something I raised, so not a finding, but it lands in the destructive path and is worth recording as reviewed. `reportContendedInstructionsFiles` detects two user deployments claiming the same `<home>/.claude/CLAUDE.md`, reports both ids, writes neither, and fails each claimant through the normal collection path so the run cannot exit successfully having quietly written nothing. I checked it for the failure modes that would matter: the contention set is computed from every target before any export runs; comparison is on `absoluteFile` consistently on both sides; a single manifest deploying through one adapter is never falsely flagged (`distinctBy { manifest.id }.size > 1`); and duplicate ids are already removed by the loader before this point. The exporters are constructed eagerly to build the target list, which is safe because `userScope` only constructs a layout and writes nothing — `UserScopeSupportTest` asserts exactly that.

This closes a hazard adjacent to SEC-7 that neither review named: two manifests silently fighting over the one file that governs every session on the machine. Fail-closed was the right choice.

## Re-review verdict

**APPROVE.**

All three blocking findings are resolved and independently verified, not merely asserted: SEC-1 by re-running the symlink probe that previously destroyed data outside the owned directory, SEC-2 by a 24-id battery against both the bare and composed path forms plus confirmation that no production code reaches a path without going through the validating loader, and SEC-3 by executing the argument-forwarding logic and confirming both the correct behaviour and the absence of injection. SEC-5, SEC-6 and SEC-8 are closed as specified. The build is green with ktlint and detekt, 416 tests passing, and the tests added are the ones that would actually have caught the original defects rather than restatements of the fixes.

Nothing new blocks. SEC-16 and SEC-17 are MINOR and belong on the follow-up list with the already-waived items; SEC-18 and SEC-19 are informational. If any one of them is picked up, SEC-17 is the one to take first — it is three lines, it is in code written during this cycle, and its comment currently misinforms the next reader about a safety mechanism.

---

## Spot-check — cleanup commit `0220926`

Scope: this commit only, limited to SEC-17 and SEC-16. Build re-run after the commit: `./gradlew clean build` **BUILD SUCCESSFUL**, `:engine:test` and `:cli:test` green, ktlint and detekt included. `~/.gradle/init.gradle.kts` moved aside for the run and restored — verified present, no leftover backup.

### SEC-17 — CLOSED

`deploy.sh:19-33` drops the broken escape and rejects any argument containing a double quote with `exit 2`, a message naming the limitation, and a pointer to running the CLI directly. The comment now states the real contract — Gradle groups on double quotes and has no escape mechanism, so spaces work and quotes cannot be expressed — which is what the splitter actually does.

Re-ran the forwarding probe against a copy of the new script with the Gradle invocation stubbed out:

| input | result |
| --- | --- |
| no arguments | `--working-dir "<repo>"` — correct, safe under `set -u` |
| `--user-home /tmp/try` | forwarded correctly |
| `--user-home "/tmp/my dir"` | forwarded correctly, still one argument |
| `--user-home '/tmp/we"ird'` | **refused, exit 2**, message on stderr, nothing invoked |
| `--user-home '/tmp/back\'` | forwarded correctly — a literal backslash now round-trips, since nothing pretends to escape it |
| `'$(touch /tmp/pwned2)'` | stays literal text; `/tmp/pwned2` not created — no command injection |

The rejection happens in the argument loop, before the subshell that runs Gradle, so a refused argument cannot leave a partial run behind. The corrupt-silently case that made this a finding is gone: the only two outcomes now are the argument the operator typed, or a refusal.

### SEC-16 — STILL OPEN (narrow), non-blocking

`ExportService.warnWhenSourceComesFromOutside` implements the option I suggested — keep absolute and `..`-climbing sources supported, but log each one at `WARN` naming the skill, the declared source and the resolved path. The KDoc explains why the destination makes it worth saying out loud. All six adapters pass the skill id (I initially misread this from a truncated grep; Windsurf, Antigravity, Cursor and GitHub Copilot all pass it explicitly, so the `skillId: String = ""` default is never taken in production).

The gap is the first line: `val owned = sourceDir?.toPath()?.toAbsolutePath()?.normalize() ?: return`. A **standalone** skill — a single `04_skills/<id>.yml` with no directory of its own — has `sourceDir == null`, so the function returns before warning. And a standalone skill can only ever declare an *absolute* source, because `resolveSource` throws `SkillFileResolvingException` for a relative one without a source directory. So the one shape that is guaranteed to pull a file in from outside the manifest's own directory is precisely the shape that produces no warning.

Reachability today is nil — no standalone skill in `04_skills/` declares `files:` at all — and behaviour is unchanged either way, so this does not block. It is a one-line fix when someone is next in the file: when `sourceDir` is null, warn unconditionally rather than returning, since anything that reached that point is absolute and therefore outside by definition.

### Spot-check verdict

**APPROVE.**

SEC-17 is fully closed and verified by re-running the probe that found it, including confirmation that the refusal precedes the Gradle invocation and that no command injection exists. SEC-16 is closed for every skill shape that exists in this repository, with a narrow hole for standalone skills with absolute sources that leaves a supported-but-noteworthy copy unlogged; it is a gap in a visibility measure for a MINOR finding, changes no behaviour, and belongs on the follow-up list rather than in another fix cycle. Nothing in this commit introduces a new gap.
