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
 * @param mcps which MCP servers this deployment writes into `<home>/.claude.json` (Claude Code), `<home>/.codex/config.toml` (Codex) and `<home>/.copilot/mcp-config.json` (GitHub Copilot, read by Copilot CLI), and which of their tools it restricts, YAML key `mcps` with the shape of [ProjectDeploy.mcps] - see [ProjectMcps] - or `null` for none. The denied tools of Claude Code land in `<home>/.claude/settings.json`, and its allowed tools nowhere. Leaving it out selects no server: the deployment then reads the MCP ledger of the home, `<home>/.ai-tools/mcp-ledger.json`, and changes those files only to remove the entries the ledger records for them, each only while it still holds what the engine wrote - see [cz.cleanship.aitools.engine.tools.mcp.McpLedger]. It leaves alone every such file that the `mcps` block of another deployment of the run covers, even when its ledger records entries there. Cursor, Windsurf and Antigravity have no user-scope MCP config file in this engine, and a deployment that selects servers reports them as skipped. GitHub Copilot gets only its MCP config file in the home, and a deployment that selects no server reports it as not deployed.
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
    val mcps: ProjectMcps? = null,
    override val metadata: ManifestMetadata,
) : VersionedManifest
