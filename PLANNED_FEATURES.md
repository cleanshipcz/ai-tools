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

## MCPs

- add support for MCPs

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

- deploy local/global skills
- deploy local/global mcps
- custom destinations

## Known issues

Raised by the analysis and code review in `.delivery/project-improvements/`. Not fixed there, with reasons.

### `deploy.directory` resolves against the wrong base

- `ToolsEngine.kt:108` does `File(deploy.directory).absoluteFile`, which resolves a relative path against the
  JVM working directory. `ConfigService.resolvePaths` resolves every `locations.*` path against `--working-dir`.
  Two different bases in the same config file.
- Consequence: a relative `deploy.directory` only means what you expect under `./deploy.sh`, which runs
  `:cli:run`, where Gradle sets the working directory to `ai-tools-engine/cli`. Under `installDist` or
  `java -jar` from the repository root the same value points somewhere else entirely.
- This matters because `prepare()` **deletes** directories under whatever it resolves to: with `replace: true`
  every adapter removes its own generated directories, and `GitHubCopilotAdapter` removes three of them
  (`.github/prompts`, `.github/instructions`, `.github/agents`).
- Circumstantial evidence it has already happened: `ai-tools-projects/projects/` exists inside this repository
  and is completely empty, which is what a relative path created at the wrong base looks like.
- Fix: resolve `deploy.directory` against `--working-dir` like every other path, after which `directory: "."`
  is correct under any launcher. Deliberately deferred: it changes a path that deletes directories, so it wants
  its own change and its own review.

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
