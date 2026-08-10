package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

/**
 * A deployment into the user scope of a tool - `~/.claude`, `~/.codex` - rather than into a project directory.
 * It is read from a `user.yml`, the way a [ProjectManifest] is read from a `project.yml`: the filename names the
 * kind and the directory holding it names the instance, so both kinds live side by side under `locations.deployments`.
 *
 * It carries none of the fields a project has and the user scope has no answer for. There is no `context`, because
 * a user scope has no repository, README or per-topic documentation to describe; no `directory`, because the
 * destination is the canonical per-user location of each tool, derived from the home base of the run - see
 * [cz.cleanship.aitools.engine.ToolsEngine]; and no `features`, because a feature belongs to the project whose
 * directory it lives under.
 *
 * @param tools which of the tools configured for the run deploy this manifest, with the same meaning
 * [ProjectDeploy.tools] has: omitting the key means every configured tool, an empty list means none of them.
 * @param replace whether a deploy may delete the artifact directories it generates before writing them again.
 * Only the paths of the artifacts this manifest deploys are ever removed - never the directories of the tool that
 * hold them, which the user shares with everything they installed by hand.
 */
@Serializable
data class UserDeploymentManifest(
    override val id: String,
    override val description: String,
    val tools: List<ToolType>? = null,
    val replace: Boolean = false,
    val prompts: ProjectPrompts = ProjectPrompts(),
    val agents: ProjectAgents = ProjectAgents(),
    val rulesets: ProjectRulesets = ProjectRulesets(),
    val fragments: ProjectFragments = ProjectFragments(),
    val skills: ProjectSkills = ProjectSkills(),
    override val metadata: ManifestMetadata,
) : VersionedManifest
