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

- **DONE** MCP server manifests in `07_mcp/`, inline or as a pointer at a `server.json` of the MCP Registry schema `2025-12-11`, selected by `deploy.mcps` of a project and merged entry by entry into `.mcp.json`, `.vscode/mcp.json`, `.cursor/mcp.json`, and `.codex/config.toml`; secrets are written as references only. See [07_mcp/README.md](07_mcp/README.md).
- **User scope.** `user.yml` gains `mcps.filter`; entries land in `~/.claude.json` (`mcpServers`) and `~/.codex/config.toml` with the same entry-level ownership as the project scope, never rewriting anything else in those files.
- **Per-agent attachment.** `AgentManifest` gains `mcps: [id]`, rendered to the `mcpServers` of a Claude Code subagent, the MCP configuration of a Codex agent, and `tools: [<server>/*]` of a Copilot agent; Cursor and Windsurf, which have no per-agent mechanism, report it as skipped.
- **Windsurf**, project and user scope, once the conflicting official paths (`.devin/mcp_config.json` and `~/.config/devin/...` against `~/.codeium/windsurf/mcp_config.json`) are settled against the installed version. Antigravity stays unsupported while it expands no environment variable in its MCP config file.
- **A secrets manager as the default value source.** A manifest declares how each secret variable is obtained, a secrets manager (candidates: `pass`/gopass, 1Password `op`, Bitwarden `bws`, libsecret) by default and the shell environment as the fallback. A stdio server is launched through the manager so no tool sees the value in a file; for a remote server, the tool itself is launched through the manager, and OAuth is preferred where the server supports it.
- **Tool allow and deny lists** per server: Codex `enabled_tools` / `disabled_tools`, Claude Code `permissions`, Cursor `mcpAllowlist`, and the equivalents of the other tools.
- **A `/manifests-create-mcp` prompt** that scaffolds an MCP server manifest from `McpServerManifest.kt`.
- **Pinning a pointer server** (SEC-2): the manifest records a hash of its `server.json` or of the rendered entry, a mismatch fails the run until the author updates the pin, and the dry run prints the command line it would write.
- **A ledger of written MCP entries** (SEC-6): with opt-in selection, removing a project's `mcps` block leaves the entries an earlier deploy wrote; a ledger would let a deploy remove them and own only what it wrote.
- **Split rendering from merging** (ARCH-4): each format becomes an entry renderer and a file merger, so per-agent attachment can render a server without a config file.
- **MCP export on `UserScopeExporter`** (ARCH-5), built from `ClaudeLayout.mcpConfigFile` and `CodexLayout.mcpConfigFile`, which already carry the user-scope paths.
- **Byte-range merge for `~/.claude.json`** (ARCH-6): replace only the `mcpServers` object, located by a position-reporting parser, and keep every other byte of a file Claude Code rewrites while it runs.
- **Run-level MCP resolution** (ARCH-7): resolve servers once for the project loop, the user loop and agent rendering, and fail an agent that attaches a server its deployment does not select.
- **A value-source seam and a launcher transform** (ARCH-9, ARCH-10): a secrets manager plugs in behind "where does variable X come from", and a launcher wraps a resolved stdio server; how a secret is obtained is a new optional field, not a change of `secret`.

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
- deploy local/global mcps - a `user.yml` carries no `mcps` block yet; MCP servers deploy into the project scope only. See [MCPs](#mcps).
- custom destinations

### Open items of user-scope deployments

- **User scope for the remaining tools.** v1 implements `claude` and `codex` only. `github_copilot` (VS Code user-profile `prompts/` and user instructions), `windsurf`, `antigravity`, and `cursor` follow the same pattern; a `user.yml` naming one of them is currently logged as skipped for that tool.
- **A ledger of deployed files.** The engine keeps no record of what it wrote, so an artifact dropped from a `user.yml` leaves its previously deployed copy in the home until it is deleted by hand, and `replace: true` cannot reach it. A ledger is what would let a deploy remove its own stale artifacts without touching anything the user installed.
- **Guard a project-scope `deploy.directory`** against resolving to the user home, `/`, or a tool root, and make the project-scope `prepare()` delete only the artifact paths it owns rather than the whole tool directory (SEC-4).
- **Warn on out-of-directory skill sources for standalone skills.** `ExportService` warns when a companion file comes from outside the skill's own directory, but only when a `sourceDir` is known; a standalone `<id>.yml` skill has `sourceDir == null`, so its `files` are copied without a word (SEC-16 residual).
- **Emit per-ruleset provenance** in the generated user-scope instructions files, so a rule in `~/.claude/CLAUDE.md` names the ruleset manifest it came from instead of being flattened into an anonymous list (SEC-7 note).
- **Cycle detection in the loader's directory walk.** `LoaderService.findYamlFiles` uses `walkTopDown`, which follows symbolic links and has no loop detection, so a linked cycle under a configured location does not terminate (SEC-10).
- **DONE** a leading `~` or `~/` in every declared path - `locations.*`, `deploy.directory`, the `source` of a pointer skill, and `--user-home` - resolves against the home directory, so `HOME_FOLDER: "~"` in `config.yml` is the home directory (SEC-11). See [README.md](README.md#path-variables).

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

- **DONE** a replacing deploy deletes through `deleteTreeWithoutFollowingLinks`, which stops the run with a `ReplaceFailedException` naming the deployment, the tool, and the entry, for a project and a user deployment alike, instead of ignoring a failed delete. A replaced directory holding a folder the deploy cannot read is refused before anything is written, `--dry-run` included.

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
- **DONE** `07_mcp/github/`, a 4.4 MB vendored copy of `github/github-mcp-server`, was moved out of this repository to `${PROJECTS_FOLDER}/github-mcp-server`; `07_mcp/github.yml` points at its `server.json`.
