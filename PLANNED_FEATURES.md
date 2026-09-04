# Planned features

## CLI execution

- add support for CLI execution -> using gradle wrapper and a simple CLI app

## Deployment ignored

- when deploying to an external project, for each deployed folder check:
  - is the folder present? yes -> throw error
  - is the folder not present or empty? continue:
    - is the folder mentioned in .gitignore or .git/info/exclude? yes -> continue, no -> add it to .git/info/exclude and continue

## Skills

- add support for skills

## Project archetype

- add support for project archetype
  - archetype would be a parent of a project to reuse common configuration
  - archetype would be defined in a similar way as project.yml
  - project.yml in the new project would then just reference the archetype and override what is needed
- issues:
  - would require project manifests to have optional fields because they can be filled by archetypes
  - for proper validation then a custom validation would be required while the fields are still optional -> leads to separating IO model and internal model -> can do in later stage once it settles, for now use the copy prompt

## Prompts

- prompts webpage supported
- should it be somehow interconnected? Maybe CRUD server? Or just a webpage?

## Relative location

- add support for env variables to define absolute base, then projects can reference that variable -> requires only a local .env file

## MCPs

- add support for MCPs

## Fragment filtering per deployment

- deployments filter agents, rulesets, and skills by tag, but every fragment reaches every deployment
- add a `fragments` filter to project and user deployments, same shape as the ruleset filter
- motivation: worked-example fragments per language for the documenter agents (one good KDoc for a function with a nullable return and one for a value class calibrate better than adjectives); without the filter a Kotlin example would land in Python projects

## Project-specific configuration

- add support for project-specific tools (skills, prompts, rulesets, agents)
- based on structure of the project
  - project.yml
  - skills/
  - prompts/
  - rulesets/
  - agents/

## Separate DAO and service layers

- this will allow hierarchical definitions and better validation

## Deployment

- **DONE** deploy local/global skills - shipped as user-scope deployments (`user.yml`), covering skills, agents, prompts, and rulesets. See [09_deployments/README.md](09_deployments/README.md#user-deployments).
- deploy local/global mcps - a `user.yml` carries no `mcps` block. It is the natural home for one, but MCP manifests and MCP config emission are a separate feature that exists in neither scope.
- custom destinations

### Open items of user-scope deployments

- **User scope for the remaining tools.** v1 implements `claude` and `codex` only. `github_copilot` (VS Code user-profile `prompts/` and user instructions), `windsurf`, `antigravity`, and `cursor` follow the same pattern; a `user.yml` naming one of them is currently logged as skipped for that tool.
- **A ledger of deployed files.** The engine keeps no record of what it wrote, so an artifact dropped from a `user.yml` leaves its previously deployed copy in the home until it is deleted by hand, and `replace: true` cannot reach it. A ledger is what would let a deploy remove its own stale artifacts without touching anything the user installed.
- **Guard a project-scope `deploy.directory`** against resolving to the user home, `/`, or a tool root, and make the project-scope `prepare()` delete only the artifact paths it owns rather than the whole tool directory (SEC-4).
- **Warn on out-of-directory skill sources for standalone skills.** `ExportService` warns when a companion file comes from outside the skill's own directory, but only when a `sourceDir` is known; a standalone `<id>.yml` skill has `sourceDir == null`, so its `files` are copied without a word (SEC-16 residual).
- **Emit per-ruleset provenance** in the generated user-scope instructions files, so a rule in `~/.claude/CLAUDE.md` names the ruleset manifest it came from instead of being flattened into an anonymous list (SEC-7 note).
- **Cycle detection in the loader's directory walk.** `LoaderService.findYamlFiles` uses `walkTopDown`, which follows symbolic links and has no loop detection, so a linked cycle under a configured location does not terminate (SEC-10).
- **`HOME_FOLDER: "~"` in `config.yml` is a literal path**, not the home directory - nothing expands a tilde, so a value referencing it resolves to a directory literally named `~` (SEC-11).

## Known issues

Raised by the analysis and code review in `.delivery/project-improvements/`. Not fixed there, with reasons.

### Manifest validation is not enforced anywhere

- The old npm CI ran `npm run validate`. There is no Gradle equivalent, so nothing validates manifests today.
- The JSON schemas in `10_schemas/` cannot serve as-is: three have drifted from the Kotlin models, and
  `skill.schema.json` describes a completely different shape (`command`, `timeout_sec`, `retry`) from
  `SkillManifest` (`sections`, `files`).
- Fix: realign the schemas with the data classes, then wire validation into the Gradle build so it is enforced
  by the same gate as ktlint and detekt and cannot drift again.

### SonarCloud and CodeQL workflows never run

- Both live in `ai-tools-engine/.github/workflows/`, but `ai-tools-engine` is a plain subdirectory rather than a
  submodule, and GitHub Actions only reads workflows from the repository root. They have never executed.
- Moving them as-is is not enough: they need `defaults.run.working-directory: ai-tools-engine`, and Sonar's
  `projectBaseDir` currently points away from `sonar-project.properties`.
- Also requires confirming the `cleanshipcz_ai-tools` project and `SONAR_TOKEN` exist, or relocating them just
  turns a dormant workflow into a failing check on every pull request.
- Alternative: delete them and stop implying the project has Sonar and CodeQL coverage.

### `prepare()` swallows cleanup failures

- All six adapters discard the `Boolean` returned by `deleteRecursively()`, which does not throw on failure.
- With `deploy.replace: true`, a permission error during cleanup is silently ignored: stale files from a previous
  run survive and mix with new output while the run reports success.
- Pre-existing, and the one genuinely swallowed failure left in the export path.

### Smaller items

- Export writes use `Files.move` without `ATOMIC_MOVE`, and without `fsync` before the rename, so a power loss
  (not a process crash) can still leave a truncated file. A JVM kill between temp-file creation and the move
  strands a `*.tmp` in the output directory.
- `copySkillFiles` uses `copyTo`, which is not atomic, so skill companion files can still be left half-copied.
- CI actions are pinned to mutable major tags; Gradle caching is configured twice; unfiltered `push` +
  `pull_request` double-runs pull request branches.
- `./gradlew clean build` pulls in `:server`, whose frontend shells out to `npm ci` with no `setup-node` - an
  undeclared network dependency. `setup.sh` sidesteps it by building `:cli:build` instead.
- `90_docs/` still references the deleted `15_config/`, and `README.md` and `QUICKREF.md` link
  `90_docs/TOOLS.md` as authoritative while it still documents `.output/`, `.backups/` and `deploy.yml`.
- `90_docs/AGENTS.md` is tracked while matching the `AGENTS.md` ignore pattern.
- `07_mcp/github/` is a 4.4 MB vendored copy of `github/github-mcp-server`, 438 of the repository's tracked
  files. Git history shows it was forked deliberately, so it was left alone - but keep, submodule, or drop is
  an open decision.
