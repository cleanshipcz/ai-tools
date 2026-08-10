# Delivery report: user-scope deployments (`UserDeploymentManifest`)

Run slug: `user-scope-deployments`
Branch: `feature/user-scope-deployments` (not merged, not pushed — stops here per policy)
Specification: `.tmp/plans/analyst-user-scope-deployments-design.md` (approved design, 2026-08-10)
Status: **DELIVERED** — all gates satisfied.

## What was delivered

The approved design, implemented in full:

- New manifest kind `UserDeploymentManifest`, loaded from `user.yml` (filename selects the schema, directory names the instance), strict decoding with named origins, no discriminator field.
- `locations.projects` renamed to `locations.deployments`; both kinds flow through it; duplicate ids scoped per kind; a lingering `locations.projects` key now fails the run naming the rename.
- Second export loop in `ToolsEngine` with the same `tools`-selection warnings and collect-all-then-fail isolation as projects; two deployments contending for one instructions file fail the run naming both (fail-closed, consistent with the duplicate-id policy).
- Claude and Codex user-scope layouts via a shared project/user layout abstraction: `~/.claude/CLAUDE.md`, `skills/<id>/`, `agents/<id>.md`, `commands/<id>.md`; `~/.codex/AGENTS.md`, `skills/skill-<id>/`, skills-shaped agents and prompts. The four other adapters log a per-manifest skip.
- Replace semantics owning specific paths only: symlink-safe per-artifact deletes (NOFOLLOW walk + canonical-path containment), parent directories never deleted, hand-made neighbors proven to survive.
- Security hardening beyond the spec, driven by review: load-time single-path-segment validation of every manifest id, containment check on skill `files[].target`, WARN on skill sources resolving outside the skill's directory, `--user-home` rejecting empty and resolving relative values against `--working-dir`, `deploy.sh` forwarding arguments and refusing double quotes (a Gradle `--args` limitation) instead of corrupting them.
- Repository migration: `09_deployments/` flattened (no `global/`/`local/` split), retired TypeScript-CLI registry files removed, `config.yml` and the untracked `config.local.yml` migrated, repo-wide reference sweep (historical docs in `90_docs/` and `.delivery/` deliberately left).
- Your hand-written `~/.claude/CLAUDE.md` rules captured verbatim as `01_rulesets/global/global-code-style.yml` and `global-questions.yml`, tagged `global`, selected by `09_deployments/globals/user.yml` (`tools: [claude, codex]`) — nothing is lost when the engine takes ownership of the file.
- Documentation: README, QUICKREF, a full rewrite of `09_deployments/README.md` (1391 stale lines → 460 accurate ones), `PLANNED_FEATURES.md` done/open updates plus the security follow-ups; every documented claim verified by the documentation reviewer running the actual engine against scratch fixtures.

Tests: 379 → 422 (all new behavior TDD, Red verified), JUnit5 + MockK + AssertJ. No test touches the real home.

## Gate outcomes

| Gate | Outcome | Evidence |
| --- | --- | --- |
| Plan | Complete, committed | `2b4508e`, `.tmp/user-scope-deployments/plan.md` |
| Code review | APPROVE after 1 fix cycle + spot-check | CR-1..CR-16, report in `.tmp/user-scope-deployments/reviewer-code-user-scope-deployments.md` |
| Security review | APPROVE after 1 fix cycle + spot-check | SEC-1..SEC-19, report in `.tmp/user-scope-deployments/reviewer-security-user-scope-deployments.md`; blocking findings verified closed by re-running the destructive probes |
| Documentation review | APPROVE after 1 fix cycle + spot-check | DOC-1..DOC-6 + 3 gaps, report in `.tmp/user-scope-deployments/reviewer-documentation-user-scope-deployments.md` |
| Verification | PASS | `./gradlew clean build` in `ai-tools-engine/` (ktlint + detekt), run independently by team-lead twice, exit 0, 422 tests, 0 failures |

Review loop used all 3 iterations: initial review → fix cycle 1 (11 findings) → re-review (approve + 5 new minors) → fix cycle 2 → spot-checks (approve).

## Staffing decisions taken

- ENGAGED: developer-feature (implementation + both fix cycles), reviewer-code, reviewer-security, documenter-project, reviewer-documentation.
- SKIPPED (justifications in `plan.md`): analyst-codebase, analyst-security, analyst-performance, reviewer-api, reviewer-architecture, documenter-code, documenter-rest.

## Waived findings

Full rationales in `.tmp/user-scope-deployments/waivers.md`. Summary: SEC-4 (project-scope `prepare()` wholesale delete — pre-existing, recorded in `PLANNED_FEATURES.md`), SEC-7 (permissive default filters are spec semantics), SEC-3's opt-in-flag portion (spec deviation), SEC-9/SEC-10/SEC-11 (INFO, pre-existing), SEC-16 residual / CR-16 (standalone-skill source warning gap — zero occurrences today, recorded as follow-up), SEC-18/SEC-19 (INFO).

## Decision points for you (not blockers, but yours to make)

1. **`~/.codex/AGENTS.md` ownership**: `globals/user.yml` includes `codex`, so the next real deploy overwrites that file. It currently holds stale generated output from the retired CLI, so this looked right — drop `codex` from the manifest's `tools` if you prefer otherwise.
2. **Cloner risk**: anyone who clones this repo and runs `./deploy.sh` gets their own `~/.claude/CLAUDE.md` / `~/.codex/AGENTS.md` overwritten with your global rules (documented prominently now, warned at runtime, but not gated). If you want a hard opt-in flag for user scope, that is a small follow-up — it was waived here as a spec deviation.
3. **No real end-to-end deploy was run to verify the final branch**: a genuine `./deploy.sh` rewrites ~28 real project directories on this machine, so I declined it; forwarding and user-scope behavior are proven by the CLI integration tests and the reviewers' probe runs against scratch homes. Recommended first real run: `./deploy.sh --user-home /tmp/try`, inspect, then the real one.
4. **`config.local.yml`** was migrated on disk but is untracked — other machines with a stale copy will now fail loudly with the "renamed to locations.deployments" error (deliberate, per CR-5/SEC-8).

## Process notes

- The developer's first run included a real deploy of all project manifests while verifying the migration (output byte-identical to a prior `./deploy.sh`; no tracked file changed; user scope went to a scratch home). Flagged by the developer itself; no damage found by reviewers.
- Every Gradle run moved `~/.gradle/init.gradle.kts` aside and restored it; verified in place.

## Commits on the branch (main..HEAD)

- `2b4508e` Add delivery plan for user-scope deployments
- `2b684ff` Load user deployment manifests from locations.deployments
- `2525457` Give the Claude and Codex adapters a user scope
- `77c0094` Deploy user deployments after the projects of a run
- `a3ef9b3` Migrate the repository to 09_deployments
- `2da03be` Satisfy detekt and name the user scope in the deploy scripts
- `730c995` Keep a replacing user deploy inside the directory it owns
- `01eaa7b` Keep a deploy inside the directory it owns, on both halves
- `beb3a2c` Report what a user deploy is about to do, and what it cannot do
- `a0458c4` Point the containment KDoc at the validation that backs it
- `0220926` Say only what a deploy actually does
- `1c29a20` Document what a user deploy writes, and what it takes over
- `70f9e8e` Warn where the deploy is actually run, and name the error a bad id gives
- (+ this report and the review artifacts under `.tmp/user-scope-deployments/`)
