# Manifest Creation Prompts

Prompts for creating the YAML manifests of the ai-tools repository: agents, rulesets, prompts, fragments, skills, features, projects, user deployments, and recipes.

## How they work

Each prompt is a clarify-first conversation: it asks questions about intent, proposes an ID and structure, and only then generates the manifest. All of them compose the `authoring-engine-models` fragment, which makes the Kotlin engine data models (`ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/`) the source of truth: the manifest is derived from the model file, never from memorized schemas.

The prompts are deployed as commands into each configured tool by `./deploy.sh` (e.g., `/manifests-create-agent` in Claude Code).

## Available prompts

| Prompt | Creates | Target location |
| --- | --- | --- |
| `manifests-create-agent` | Agent manifest | `05_agents/<id>.yml` |
| `manifests-create-ruleset` | Ruleset manifest | `01_rulesets/<domain>/<id>.yml` |
| `manifests-create-prompt` | Prompt manifest | `03_prompts/<category>/<id>.yml` |
| `manifests-create-fragment` | Fragment manifest | `02_fragments/<id>.yml` |
| `manifests-create-skill` | Skill manifest | `04_skills/<id>.yml` or `04_skills/<id>/skill.yml` |
| `manifests-create-feature` | Feature manifest | `09_deployments/<deployment>/features/<id>.yml` |
| `manifests-create-project` | Project manifest | `09_deployments/<deployment>/project.yml` |
| `manifests-create-user-deployment` | User deployment manifest | `09_deployments/<deployment>/user.yml` |
| `manifests-create-recipe` | Recipe (no engine model) | `08_recipes/<id>.yml` |
| `manifests-duplicate-project` | Copy of an existing project setup | `ai-tools-projects/projects/<name>/` |

Each `create-*` prompt takes a single `user_description` variable — a brief description of what to create; the conversation gathers the rest. `duplicate-project` takes source and destination paths instead.

## Naming conventions

- **IDs:** kebab-case (`^[a-z0-9]+(-[a-z0-9]+)*$`), prefixed by role or domain: `role-specialization` for agents, `domain-topic` for rulesets, `category-action` for prompts, `verb-tool` for skills.
- **Variables:** snake_case.
- **Versions:** semver in `metadata.version`; never top-level.

## Validation

Run `./deploy.sh` from the repository root. The engine parses every manifest and the run fails on any invalid one; a successful run also deploys the generated artifacts. Building the engine with `./gradlew clean build` does not validate manifests.

Recipes are the exception: they have no engine model and no automated validation — `08_recipes/GUIDE.md` and the existing recipes are the reference.

## Related agent

The **manifest-builder** agent (`05_agents/manifest-builder.yml`) provides the same interactive manifest creation for any type, backed by the same engine-models fragment.

## See also

- [Main README](../../README.md) — repository overview
- [Prompts guide](../README.md) — general prompt documentation
- [Rulesets](../../01_rulesets/README.md) and [fragments](../../02_fragments/README.md) the prompts compose
