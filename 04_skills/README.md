# Skills

A skill is a set of instructions an AI assistant loads on demand, such as how to run a linter or how to write a Jira ticket.
Each skill is a YAML manifest here, and the engine turns it into each tool's native skill file, for example `.claude/skills/<id>/SKILL.md` for Claude Code.
The engine only writes files: a skill describes what the assistant should do, and nothing in it is executed by the engine.

The Kotlin model `ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/SkillManifest.kt` is the schema.
When this page and the model disagree, the model wins.

## What's Here

| Skill | Form | Content |
| --- | --- | --- |
| `run-detekt` | single file | Run Detekt static analysis for Kotlin |
| `run-gradle-tests` | single file | Run unit tests with Gradle |
| `run-ktlint` | single file | Run ktlint for Kotlin formatting |
| `run-pytest` | single file | Run Python tests with pytest |
| `search-repo` | single file | Search the repository for code patterns or text |
| `docx-export` | directory | Convert a `.docx` document to Markdown with a bundled script |
| `jira-ticket` | pointer | Create and update Jira tickets |
| `confluence-doc` | pointer | Create and update Confluence pages |
| `confluence-search` | pointer | Search and read Confluence |

The three pointer skills point at `${PROJECTS_FOLDER}/jira-confluence-mcp-server/skills/`, a separate repository. Every run, `./deploy.sh --dry-run` included, needs that repository checked out there; see [Pointer skill](#pointer-skill).

## Three Ways to Write a Skill

| Form | Location | Content lives in | Use it when |
| --- | --- | --- | --- |
| Single file | `04_skills/<id>.yml` | the manifest | the skill is text only |
| Directory | `04_skills/<name>/skill.yml` plus files | the manifest and the files beside it | the skill ships scripts, templates, or other files |
| Pointer skill | `04_skills/<id>/skill.yml` with `source` | a plain skill folder outside this repository | the skill must also work for people who do not use ai-tools |

Keep one tool or workflow per skill.
The file name and the directory name do not have to match the `id`; only `id` is ever referenced.

### Single file

```yaml
id: my-skill
description: >-
  Write or edit ai-tools manifests.
  Use when the user asks to create or change a manifest of this repository.
sections:
  - text: |
      Read the Kotlin model of the manifest kind before writing the manifest.
  - ruleset: authoring-manifests
  - fragment: authoring-engine-models
metadata:
  version: 1.0.0
  tags:
    - ai
```

The generated skill file holds a `# <id>` heading, the `description`, and then each entry of `sections` in order.
A `text` entry is copied as written.
A `ruleset` entry becomes the rules of the matching rulesets as a bullet list, and a `fragment` entry becomes the content of the matching fragments. Both match by id or by a regular expression on the id.

### Directory with companion files

```yaml
# 04_skills/docx-export/skill.yml
id: docx-export
description: >-
  Convert a .docx document to Markdown with extracted images using the bundled converter script.
sections:
  - text: |
      Run the converter bundled at scripts/docx_to_markdown.py.
files:
  - path: scripts/docx_to_markdown.py
metadata:
  version: 1.0.0
  tags:
    - docx
```

Each entry in `files` is copied into the directory of the generated skill, for Claude Code next to `SKILL.md`:

- `{ path: <p> }` copies `<p>` to the same relative path.
- `{ source: <s>, target: <t> }` copies `<s>` to `<t>`.

A relative `path` or `source` of a `files` entry resolves against the directory holding `skill.yml`.
These paths are taken as written: a `${NAME}` in them is not substituted, and a leading `~` is not expanded.
A single-file skill has no directory of its own, so it can list only absolute paths.
A file that is not listed is not copied.

### Pointer skill

A *pointer skill* is a skill manifest whose skill-level `source` key (not the `source` of a `files` entry) names a plain Claude Code skill folder: a `SKILL.md` and its companion files.
Use this form when a skill has to stay a plain Claude Code skill because people who do not use ai-tools use it directly, for example the Atlassian skills kept in the `jira-confluence-mcp-server` repository.
The content then has one home, the plain folder, and is edited only there.
The manifest here holds only what ai-tools owns: the `id`, and the `metadata` whose tags the deployments select on.

Put a pointer skill in `04_skills/<id>/skill.yml`, as the three in this repository are.
A single file `04_skills/<id>.yml` loads too, but a relative `source` resolves against the folder holding the manifest, which would then be `04_skills/` itself.

```yaml
# 04_skills/jira-ticket/skill.yml
id: jira-ticket
source: ${PROJECTS_FOLDER}/jira-confluence-mcp-server/skills/jira-ticket
metadata:
  version: 2.0.0
  tags:
    - jira
    - ticket
    - atlassian
```

The folder must hold a `SKILL.md` that starts with a YAML frontmatter declaring `name` and `description`:

```markdown
---
name: jira-ticket
description: Create, draft, update, and link Jira tickets. Use when ...
---

# Jira tickets

Instructions ...
```

How the engine reads the folder:

- **Path**: `source` may reference a variable as `${NAME}`. The variable comes from `env_vars` in `config.yml` or `config.local.yml`, or from the environment, the same way as for `deploy.directory`.
- **Home directory**: after substitution, a `source` that is `~` or starts with `~/` resolves against the home directory of the user running the engine, as `deploy.directory` does. `~user` is not expanded: it is read as a relative path whose first folder is named `~user`. A `~` anywhere else is part of the name.
- **Relative path**: any other relative `source` resolves against the folder holding the manifest, not against the directory `deploy.sh` runs from.
- **No own content**: the manifest must not declare `description`, `sections`, or `files` together with `source`. The run fails if it does.
- **Name**: `name` in the frontmatter must equal the manifest `id`.
- **Description**: the `description` of the frontmatter becomes the description of the deployed skill.
- **Other frontmatter keys**: only `name` and `description` are read. Any other key, such as `allowed-tools`, is not deployed, and the run logs a warning naming it.
- **Body**: the text after the frontmatter, from its first line that is not empty, is deployed unchanged. No `# <id>` heading and no description are added, so the `SKILL.md` carries its own heading.
- **Companion files**: every other file in the folder, including files in subfolders and hidden files, is copied into the directory of the generated skill at the same relative path. There is no list to keep up to date.
- **Links**: a symbolic link inside the folder is followed and deployed as the file it points at. A link that points at nothing fails the run.
- **Byte order mark**: a `SKILL.md` saved with a UTF-8 byte order mark is read like one without it; the mark is not deployed.

What the engine guarantees about the source folder:

> The engine only reads the source folder of a pointer skill. Before a run writes or deletes anything, `--dry-run` included, it compares two kinds of path with the source folder of every pointer skill it loaded, whether a deployment selects that skill or not: every path it would write a skill to, and every directory `replace: true` would delete (in a project, the directories each tool generates: `.claude`, `.codex`, `.cursor`, `.windsurf`, `.agent`, and for GitHub Copilot `.github/prompts`, `.github/instructions`, and `.github/agents`; in the user scope, the directory of each skill, and for Codex also of each prompt and agent, that it rewrites). The run fails if such a path is, lies inside, or contains a source folder once symbolic links are resolved, or if a symbolic link anywhere below such a path leads to a path that does. Each path is compared as it will be when the run writes or deletes it: a replaced directory that is itself a symbolic link is compared by where it sits and by where it leads, not by the links in the folder it leads to, and every path below a replaced directory of the same deployment and tool is compared by where it lies once that directory is deleted. During export, every companion file is checked once more, and one whose target lands, once links are resolved, in the folder it is copied from or in the source folder of any pointer skill the run loaded fails that skill. A replacing deploy never follows a symbolic link and never opens what one leads to: it removes a link below a replaced directory and a replaced directory that is itself a link, except that a user deployment refuses, before anything is written, a replaced directory that is a link leading to an existing folder or file outside the skills folder of the tool, or to that folder itself; a link leading inside that folder, or leading nowhere, it removes. Other writes are not compared with source folders: the files of agents, prompts, commands, and features, and the instructions files (`CLAUDE.md`, `AGENTS.md`), are written without this check, except where they sit in a directory that `replace: true` deletes. Keep every source folder outside every directory a deployment writes to.

The usual way to trip the check is a symbolic link left from installing the plain skill by hand, such as `~/.claude/skills/<id>` pointing at the source folder: remove the link before a deployment selects the skill.

What to keep in mind:

- Every skill manifest is loaded on every run, whether a deployment selects it or not. A pointer skill whose folder is missing or invalid therefore fails the whole run, `./deploy.sh --dry-run` included.
- The folder must exist on every machine that runs a deploy. A machine without a checkout of the source repository cannot deploy anything until the folder exists, `source` points elsewhere, or the manifest is removed. The pointer skills here reach their folder through `${PROJECTS_FOLDER}`. For a checkout elsewhere, link it into `${PROJECTS_FOLDER}`, or set `PROJECTS_FOLDER` under `env_vars` in `config.local.yml` to the folder that holds it; the second also moves every `deploy.directory` that uses `${PROJECTS_FOLDER}`, and a deployment with `replace: true` deletes its tool folders at the new location, so use it only when your projects live in that folder too.
- Bumping `metadata.version` is up to you. The engine does not notice when the content in the source folder changes.

## Fields

| Field | Required | Description |
| --- | --- | --- |
| `id` | Yes | Unique skill id, kebab-case, conventionally `<verb>-<tool>`. Names the generated files. |
| `description` | Yes, unless `source` is set | What the skill does and when to use it. Assistants choose a skill by this text. Not allowed with `source`. |
| `metadata` | Yes | `version` (required), `author`, `created`, `updated`, `tags` |
| `sections` | No | List of `text`, `ruleset`, or `fragment` entries. Not allowed with `source`. |
| `files` | No | Companion files, as `{ path }` or `{ source, target }`. Not allowed with the skill-level `source`. |
| `source` | No | Makes the manifest a pointer skill: the folder holding a plain `SKILL.md` skill whose description, body, and files are deployed |

There is no `command`, `timeout_sec`, `outputs`, or `capabilities` field.
Manifests are decoded strictly, so an unknown key fails the run.

## Deploying a Skill

1. Add or edit the manifest in `04_skills/`.
2. Make sure a deployment selects it. `09_deployments/<name>/project.yml` and `user.yml` select skills with `tags`, `whitelist`, or `blacklist` filters; see [Deployments](../09_deployments/README.md).
3. Validate without writing anything:

   ```bash
   ./deploy.sh --dry-run
   ```

4. Deploy:

   ```bash
   ./deploy.sh
   ```

The generated path for each tool is listed in the [main README](../README.md).

## Troubleshooting

Each failure below stops the run and names the manifest, as `Failed to load <manifest path>: <reason>`.

| Reason in the message | Fix |
| --- | --- |
| `Skill '<id>' has a missing or empty 'description'.` | Add a `description` that is not blank, or add `source` if the content lives in a plain skill folder. |
| `A skill declaring 'source' takes its content from the SKILL.md in that folder, but this one also declares ...` | Remove the named fields from the manifest, or remove `source`. |
| `The 'source' '...' resolves to '...', which does not exist.` or `... which is not a directory.` | Check out the source repository, or fix the path so that it names the folder, not a file. Check that the variable it uses holds a full path or one starting with `~/`. |
| `Unresolved variable '<NAME>' ...` | Declare the variable under `env_vars` in `config.yml` or `config.local.yml`, or export it. |
| `The 'source' folder '...' holds no SKILL.md` | Point `source` at the folder that directly contains `SKILL.md`. |
| `'.../SKILL.md' cannot be read: ...` | Make `SKILL.md` readable by the user running the deploy. |
| `The 'source' folder '...' cannot be read: ...` | Make every subfolder readable by the user running the deploy, and remove any symbolic link that leads back into a folder above it. |
| `The 'source' folder '...' holds the link '...', which points at nothing.` | Restore the target of the link, or remove the link. |
| `... does not start with a YAML frontmatter` | Make the first line of `SKILL.md` exactly `---`, followed by `name`, `description`, and a closing `---` line. |
| `The frontmatter of '...' is not valid YAML: ...` | Fix the YAML between the two `---` lines; quote a `description` that contains `: `. |
| `The frontmatter of '...' is not a YAML mapping of 'name' and 'description'.` | Write the frontmatter as `name: ...` and `description: ...` lines, not as a list or a single value. |
| `... declares no text 'name'` or `... declares no text 'description'` | Add the missing key to the frontmatter. |
| `The frontmatter of '...' names the skill '<a>', but the manifest declares the id '<b>'.` | Make the manifest `id` equal the frontmatter `name`. |

A path that overlaps a source folder is reported differently. The run fails before writing anything, `--dry-run` included, with `Refusing to deploy: N path(s) the run would write or delete overlap the source folder of a pointer skill; nothing was written:`, followed by one line per overlap. Each line names the pointer skill, its manifest, its source folder, the tool, the deployment, what the deployment would do (`write the skill '<id>' ... to '<path>'`, or `delete '<path>' ... to replace it`), and how the path overlaps the folder: directly, through `the link '<link>' in it`, where it lies `once the link '<link>' is removed` when the path is, or lies below, a replaced directory that is a link, or, for a replaced directory, through `the link '<link>' below the directory to be replaced` or, when the replaced directory is itself a link, through `'<path>' is itself a link to '<destination>'`. A replacing deploy never opens what either kind of link leads to, but it still refuses. Remove the link or folder that leads there, deselect the skill, move the source folder out of the replaced directory, move the directory the deployment deploys to out of the source folder, or turn off `replace` for that deployment, as the line advises.

The failures below are reported late, only when a deployment selects the skill: the other artifacts are still written, and the run then ends with a failure.

- `Skill file '...' does not exist.` A companion file listed under `files` is missing, or, for a pointer skill, was removed from its source folder while the run was going on.
- `Skill '<id>' would copy a companion file to '...', which lies in the folder '...' it is copied from once links are resolved.` A companion file would land in the folder it is read from. Remove the link at the target.
- `Skill '<id>' would copy a companion file to '...', which lies in the source folder '...' of a pointer skill once links are resolved.` A chain of links below the generated skill leads the companion file into the source folder of a pointer skill. Remove the link that leads there.
- `Skill file target '...' would be written outside the skill directory '...'.` The `target` of a companion file resolves to the generated skill directory itself or outside it, for example through `..`. Declare a target inside the skill.
- `Cannot resolve relative skill file '...' without a source directory.` A standalone skill file declares a relative `source`, which has no folder to resolve against. Use a directory-based skill or provide an absolute path.

## Related

- [Quick reference](../QUICKREF.md) - manifest syntax for every kind
- [Deployments](../09_deployments/README.md) - how projects and user scopes select skills
- [Fragments](../02_fragments/README.md) - reference material a skill section can include
- [Rulesets](../01_rulesets/README.md) - rules a skill section can include
- [Agents](../05_agents/README.md) - agents that compose rulesets and fragments the same way
