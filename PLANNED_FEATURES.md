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

- **DONE** MCP server manifests in `07_mcp/`, inline or as a pointer at a `server.json` of the MCP Registry schema `2025-12-11`. A project selects them with `deploy.mcps`, and a deploy merges them entry by entry into `.mcp.json`, `.vscode/mcp.json`, `.github/mcp.json` (since run 4), `.cursor/mcp.json`, and `.codex/config.toml`. Secrets are written as references only. See [07_mcp/README.md](07_mcp/README.md).
- **DONE** Run 2: see [07_mcp/README.md](07_mcp/README.md).
  - **User scope.** A `user.yml` selects MCP servers with a top-level `mcps` block. They land in `~/.claude.json` (`mcpServers`) and `~/.codex/config.toml`, with the same entry-level ownership as the project scope. `~/.claude.json` is edited in place, keeping every byte outside the owned entries, and a write is refused when the file changed since it was read. Files created under the home get the mode `0600`.
  - **Per-agent attachment.** An agent manifest names its servers under `mcps`. Claude Code gets `mcpServers`. GitHub Copilot, Codex, Cursor, Windsurf and Antigravity are reported as skipped; a Copilot agent file never carries `tools`, which would remove its built-in tools. A deployment that deploys an agent without selecting its servers is not exported.
  - **Pinning a pointer server.** `pin: sha256:<hex>` of the `server.json` bytes. A mismatch fails loading; an unpinned pointer warns with the value to add. `07_mcp/github.yml` is pinned.
  - **A ledger of written MCP entries**, version 2, `.ai-tools/mcp-ledger.json` per project and `~/.ai-tools/mcp-ledger.json` for the user scope. Each entry carries a fingerprint of its content. A deploy removes a recorded entry its deployment no longer selects: by name while the deployment selects servers, whatever the entry holds; otherwise, when the deployment selects no server or the entry's manifest no longer exists, only while the entry holds a recorded fingerprint, and a changed entry is kept with a warning. The record is widened before each file is written and narrowed after it. Two deployments of one run covering the same MCP file both fail for it.
  - **Tool allow and deny lists** per server under `mcps.tools`: Codex `enabled_tools` / `disabled_tools`, Claude Code `permissions.deny` only. Deny entries written by hand are never taken over. GitHub Copilot and Cursor are reported as skipped, and so is `allow` for Claude Code.
  - **Every tool directory and its fixed subdirectories are checked** in a dry run as in a deploy: a link leading nowhere or in a loop, something that is not a directory, or in a project a link leading outside it, fails only that deployment and tool. A user-scope write failure fails only that deployment and tool.
  - **Hostile files.** Raw control characters in JSON strings and nesting deeper than 512 levels are refused, and names read from files or manifests are escaped and cut to 160 characters in messages. Control characters, line and paragraph separators, and Unicode format characters (category `Cf`, such as the bidirectional override U+202E) are escaped as `\uXXXX`, and refused in ledger paths and entry names.
- **DONE** Run 3: see [07_mcp/README.md](07_mcp/README.md#secret-and-plain-variables).
  - **The libsecret keyring as the default source of secret values**, with the environment of the tool as the fallback. A secret variable declares `from: manager`, the default, or `from: environment`; `secrets_manager: libsecret | environment` in `config.yml` or `config.local.yml` names the manager of the machine. A stdio server with a keyring secret is started through the launcher `scripts/mcp-launch`, which reads each secret when the server starts and then replaces itself with the server, so no MCP config file or argument list holds the value of a secret, and no message of the launcher holds a value. The launcher starts its helper programs only from absolute directories of the `PATH`, gives `timeout` and `secret-tool` only the bus variables and `HOME`, gives no helper the value of a secret, and gives one lookup at most 6 seconds. The names the launcher refuses are in two classes. A class A name, one that changes how the launcher's shell or its helpers run or that a shell sets or prints itself, fails loading in every role of a server started through the launcher. A class B name fails loading as a keyring secret and as any name of a `server.json`. The engine gives a tool the launcher only while it is a regular, executable file inside the checkout, owned by the user running the engine and writable by no one else, in directories up to the root of the checkout that meet the same two conditions. The Codex entry forwards `DBUS_SESSION_BUS_ADDRESS` and `XDG_RUNTIME_DIR` by name. Every run reports each secret the launcher reads as stored, set only in the environment, found nowhere, or not known with the reason, without receiving a value. An unknown key of `config.yml` or `config.local.yml` is reported as a warning. Remote servers keep reading the environment of the tool.
- **DONE** Run 4: MCP servers for Copilot CLI; see [07_mcp/README.md](07_mcp/README.md#github-copilot-copilot-in-vs-code-and-copilot-cli).
  - **Project scope.** The tool `github_copilot` serves Copilot in VS Code and Copilot CLI. It merges the selected servers into `.vscode/mcp.json`, unchanged, and into `.github/mcp.json`, which Copilot CLI 1.0.61 and later reads. Every entry of `.github/mcp.json` carries `"tools": ["*"]`, `type`, and `args` on a stdio entry, the form `copilot mcp add` and `remove` write, and a secret as `${NAME}` or `${NAME:-}`.
  - **A hidden file is reported.** Copilot CLI reads `.github/mcp.json` only while no `.mcp.json` lies in the same directory. The run warns when a `.mcp.json` exists that the same deployment does not write through `claude`.
  - **User scope.** A `user.yml` with an `mcps` block that names `github_copilot` merges the selected servers into `~/.copilot/mcp-config.json`, recorded in `~/.ai-tools/mcp-ledger.json`. Only MCP servers are deployed for `github_copilot` in the user scope, and the run logs one line saying so. A file the engine creates is readable and writable by its owner only; an existing one keeps its permission bits.

### Open items of run 2

- **Windsurf stays unsupported**, by decision of the project owner in run 2. The installed Windsurf 1.13.9 reads user servers from `~/.codeium/windsurf/mcp_config.json` and has no project MCP file. Windsurf was renamed Devin Desktop on 2026-06-02, and its documentation now names other files: `~/.config/devin/mcp_config.json` for the legacy Cascade agent ([Cascade MCP](https://docs.devin.ai/desktop/cascade/mcp)), `~/.codeium/mcp_config.json` in the FAQ ([Devin Desktop FAQ](https://docs.devin.ai/desktop/devin-desktop-faq)), and `.devin/mcp_config.json` with `~/.config/devin/mcp_config.json` for the Devin Local agent ([Devin CLI MCP configuration](https://docs.devin.ai/cli/extensibility/mcp/configuration)). Revisit once one path is settled for the version in use.
- **Antigravity stays unsupported** while it expands no environment variable in its MCP config file ([google-antigravity/antigravity-cli#233](https://github.com/google-antigravity/antigravity-cli/issues/233), open on 2026-09-25).
- **A `.` in a server id**, such as `xbid.bobcat`, is written unchanged. Whether Claude Code names the tools of such a server `mcp__<id>__<tool>` exactly as written, which `permissions.deny` entries rely on, is not verified.
- **A dry run still misses a write the file system refuses**, such as into a read-only directory or where a directory stands at the path of a file.
- **The user scope of Copilot in VS Code and of Cursor.** VS Code keeps user servers in the `mcp.json` of each profile and remote; on this machine that file lives on the Windows side, out of reach from WSL. Cursor reads `~/.cursor/mcp.json`. The user scope of Copilot CLI is done in run 4.
- **A version 1 ledger is refused** until it is removed by hand. After that, the entries it recorded count as foreign, so a `permissions.allow` entry written by an earlier version of the engine stays in `settings.json` until it is removed by hand. No such ledger exists in this repository.
- **A `mcpServers` key the engine added** stays as `{}` once its last entry is removed; the engine cannot tell that it created the key.

### Open after run 3

- **A ledger nobody else can plant.** A ledger written into a project or home by anyone other than the user running the engine cannot make a deploy remove any entry. Today a fingerprint is not a secret, so such a ledger can claim an entry whose exact content it knows; every removal is logged.
- **A bounded ledger entry.** A ledger entry never records more than the content before and after one edit.
- **Links at a file in the home.** A link at `~/.claude.json`, `~/.claude/settings.json` or `~/.codex/config.toml` cannot make a deploy write a file another user owns.
- **Pinned pointer servers only.** A pointer server without a `pin` fails loading instead of warning, once every manifest carries one.
- **No stale ledger records.** A ledger never keeps, or logs, a record of a file that no tool of its target writes.
- **Hand-made servers kept.** A server added by hand under the id of a manifest, such as one added with `claude mcp add --scope user github`, is reported instead of replaced or removed.
- **Engine structure.** Ownership, ledger and claim rules behave the same for projects and user deployments, every MCP decision is made before anything is written, and every MCP failure is reported under one scheme, naming its deployment.
- **Every class A name refused a second time.** A server that the loader did not build, and that is rendered to start through the launcher, is refused when it has a class A name in any role: a secret read from the environment, a plain variable or an `env` key, as a server loaded from a manifest is.
- **Launcher tests without `/proc`.** The tests of the launcher run, and check the exact environment of each program it starts, on a machine without `/proc`, such as macOS, instead of being skipped.
- **Further secrets managers.** `pass` and 1Password can be named in `secrets_manager`, and a stdio server reads its secrets from them as it does from the keyring, without any change to the manifests.
- **The keyring under Claude Code with a reduced environment.** A stdio server started by Claude Code with `CLAUDE_CODE_MCP_ALLOWLIST_ENV` set reads its secrets from the keyring, as it does in every other case.

### Open after run 4

- **An `allow` list for Copilot CLI.** Copilot CLI reads the tools a server may offer from the entry field `tools`, as raw tool names. The engine writes `"tools": ["*"]` on every entry and warns that `github_copilot` applies no restriction. Copilot CLI has no file setting that denies a single tool.
- **The rest of the user scope for Copilot CLI.** Instructions, agents, prompts, and skills of `github_copilot` in the home. A `user.yml` deploys only MCP servers for it today.
- **The skill folders of Copilot CLI**, `.github/skills` and `.agents/skills`. The tool writes skills as `.github/prompts/skill-<id>.prompt.md` today.
- **The user scope of Copilot in VS Code**; see the open items of run 2.

### Not scheduled

- **A start script for tools, for remote servers.** A tool is started with the secrets of its remote servers read from the keyring, so no value has to be exported; OAuth is used instead where the server supports it.
- **A one-line error for a manifest that fails strict decoding.** An unknown key or an unknown value, such as `from: vault`, ends the run with one line naming the file and the field, not with a stack trace.
- **A launcher that does not depend on one secrets manager.** The launcher reads a secret from whichever secrets manager the machine names, so a new manager needs no launcher of its own.
- **One owner of what the check remembers.** Within one run, every part of the engine gets the same answer about a secret from one place, rather than from memories kept by several parts.
- **No `${` formed by joining text.** A resolved argument, environment value, header or url whose joined text holds `${` fails, naming the server and the place, never the value.
- **A decision on a launcher writable by a private group.** It is settled whether the engine accepts a launcher that its group may write when that group holds only the user running the engine, as after a checkout with the umask `002`.
- **BusyBox as `/bin/sh`.** The launcher starts where `/bin/sh` is BusyBox, as on Alpine Linux, with the same protection against the inherited environment that it has under dash and bash.
- **Tool allow and deny lists for Cursor and Copilot in VS Code.** Cursor has `mcpAllowlist` in `permissions.json`, which controls auto-run rather than access; no file-level mechanism of VS Code is confirmed. For Copilot CLI, see the `allow` list under "Open after run 4".
- **Per-agent MCP servers for Codex**, through a custom agent TOML with `mcp_servers` instead of the skill the engine renders today.
- **A `/manifests-create-mcp` prompt** that scaffolds an MCP server manifest from `McpServerManifest.kt`.

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
- **DONE** deploy local/global mcps - a `user.yml` selects MCP servers with an `mcps` block, for Claude Code, Codex, and, since run 4, Copilot CLI through `github_copilot`. See [MCPs](#mcps).
- custom destinations

### Open items of user-scope deployments

- **User scope for the remaining tools.** v1 implements `claude` and `codex` only. `github_copilot` (VS Code user-profile `prompts/` and user instructions, and the instructions, agents, prompts and skills of Copilot CLI), `windsurf`, `antigravity`, and `cursor` follow the same pattern; a `user.yml` naming one of them is currently logged as skipped for that tool. Since run 4, `github_copilot` gets the MCP servers of a `user.yml` in `~/.copilot/mcp-config.json`, and the run logs that nothing else is deployed for it.
- **A ledger of deployed files.** The MCP ledger records MCP server entries and the `deny` entries of tool restrictions only. For every other artifact, the engine keeps no record of what it wrote, so an artifact dropped from a `user.yml` leaves its previously deployed copy in the home until it is deleted by hand, and `replace: true` cannot reach it. A ledger is what would let a deploy remove its own stale artifacts without touching anything the user installed.
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
