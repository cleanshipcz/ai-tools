package cz.cleanship.aitools.engine.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectManifest(
    override val id: String,
    override val description: String,
    override val metadata: ManifestMetadata,
    val context: ProjectContext,
    val deploy: ProjectDeploy,
) : VersionedManifest

@Serializable
data class ProjectContext(
    val rules: List<String> = emptyList(),
    val overview: String? = null,
    val documentation: ProjectDocumentation,
)

@Serializable
data class ProjectDocumentation(
    val readme: String? = null,
    @SerialName("per_topic")
    val perTopic: Map<String, Map<String, String>> = emptyMap(),
    val additional: List<ProjectDocumentationItem> = emptyList(),
)

@Serializable
data class ProjectDocumentationItem(
    val path: String,
    val description: String? = null,
)

/**
 * @param directory where the generated artifacts of this project land. Any `${NAME}` reference it carries is expanded
 * first, from the `env_vars` of the config files of the run or from its environment - see
 * [cz.cleanship.aitools.engine.env.VariableResolver] - which is how a base that differs between machines stays out of
 * a manifest that is shared. A relative value resolves against the `--working-dir` of the run - the directory holding
 * `config.yml`, the same base its `locations.*` paths use - so `.` means that directory itself and the value does not
 * shift with the working directory of the JVM process.
 * An absolute value is used exactly as written, which is what a project outside the manifest repository wants.
 * @param replace whether a deploy may delete the directories it generates before writing them again. It never deletes an MCP config file, whose entries the engine owns one by one - see [cz.cleanship.aitools.engine.tools.mcp.McpConfigExporter]. It does delete `.claude/settings.json` together with `.claude`, entries the user wrote in it included.
 * @param mcps which MCP servers the project deploys and which of their tools it restricts, or `null` for none - see [ProjectMcps]. MCP servers are opt-in, unlike every other kind: their entries land in files other repositories commit, and a tool starts the processes they name. A project that declares no block, or a block that selects no server, writes no server entry: it reads the MCP ledger of the project, `.ai-tools/mcp-ledger.json`, and changes an MCP config file or a permissions file only to remove the entries the ledger records for it, each only while it still holds what the engine wrote - see [cz.cleanship.aitools.engine.tools.mcp.McpLedger]. Without a ledger, such a project reads and writes no MCP config file. A project that declares no block leaves alone every MCP config file and permissions file that the `mcps` block of another deployment of the run covers, even when its own ledger records entries there.
 * @param tools which of the tools configured for the run deploy this project. Omitting it - not emptying it - means
 * every configured tool, so a project that says nothing keeps following the tool list of the run, while an empty
 * list deliberately restricts the project to no tool at all. A `tools:` key with no value under it decodes to null
 * and therefore means all of them, like omitting the key. A tool the run does not configure is narrowed away rather
 * than rejected: the same manifest is deployed by runs configuring different tools, so naming one that this run
 * does not build is a difference in scope rather than an authoring error.
 */
@Serializable
data class ProjectDeploy(
    val directory: String,
    val replace: Boolean = false,
    val tools: List<ToolType>? = null,
    val prompts: ProjectPrompts = ProjectPrompts(),
    val agents: ProjectAgents = ProjectAgents(),
    val features: ProjectFeatures = ProjectFeatures(),
    val rulesets: ProjectRulesets = ProjectRulesets(),
    val fragments: ProjectFragments = ProjectFragments(),
    val skills: ProjectSkills = ProjectSkills(),
    val mcps: ProjectMcps? = null,
)

@Serializable
data class ProjectPrompts(
    val filter: List<ProjectFilter> = emptyList(),
)

@Serializable
data class ProjectAgents(
    val filter: List<ProjectFilter> = emptyList(),
)

@Serializable
data class ProjectFeatures(
    val filter: List<ProjectFilter> = emptyList(),
)

@Serializable
data class ProjectRulesets(
    val filter: List<ProjectFilter> = emptyList(),
)

@Serializable
data class ProjectFragments(
    val filter: List<ProjectFilter> = emptyList(),
)

@Serializable
data class ProjectSkills(
    val filter: List<ProjectFilter> = emptyList(),
)

/**
 * Which MCP servers a deployment deploys into the MCP config file of each tool, and which of their tools it allows and denies.
 *
 * Unlike every other block of [ProjectDeploy], leaving it out selects no server: see [ProjectDeploy.mcps] and [UserDeploymentManifest.mcps].
 *
 * @property filter the filters that select the servers, YAML key `filter`, written as for every other kind; optional, and an empty list, which selects every server, by default
 * @property tools the tools of each selected server the deployment allows and denies, YAML key `tools`: a map from server id to a [McpToolRestriction], such as `{github: {allow: [get_me], deny: [delete_repository]}}`; optional, and an empty map, which restricts nothing, by default. Codex receives them as `enabled_tools` and `disabled_tools` of the server, which hide every other tool or the denied ones. Claude Code receives only the denied tools, as `permissions.deny` entries `mcp__<id>__<tool>` of `.claude/settings.json` in a project or `<home>/.claude/settings.json` in the user scope, which block a call without hiding any tool - see [cz.cleanship.aitools.engine.tools.mcp.ClaudeSettingsPermissionsExporter]; its `permissions.allow` is never written, and a deployment that allows tools gets the `allow` list reported as skipped for Claude Code. GitHub Copilot and Cursor are reported as skipped for both lists. A server id the deployment does not select, a server id that holds `__`, or a tool name that holds `__` or is not 1 to 128 of the characters `A-Z a-z 0-9 _ - .`, leaves the deployment unexported, reported with the other failures of the run; `__` separates the server from the tool in `mcp__<id>__<tool>`. An empty map writes nothing into a `settings.json`, and removes from it only the entries the MCP ledger records there; a deny entry the engine did not write is never taken over or removed - see [cz.cleanship.aitools.engine.tools.mcp.McpLedger].
 */
@Serializable
data class ProjectMcps(
    val filter: List<ProjectFilter> = emptyList(),
    val tools: Map<String, McpToolRestriction> = emptyMap(),
)

/**
 * The tools of one MCP server a deployment allows and denies, each named as the server exposes it, such as `get_me`.
 *
 * @property allow the tools that are allowed, YAML key `allow`, a list of tool names; optional, and empty by default. They are the only tools Codex shows; Claude Code receives none of them
 * @property deny the tools that are denied, YAML key `deny`, a list of tool names; optional, and empty by default. Codex hides them, and Claude Code blocks them
 */
@Serializable
data class McpToolRestriction(
    val allow: List<String> = emptyList(),
    val deny: List<String> = emptyList(),
)

@Serializable
sealed class ProjectFilter {
    @Serializable
    @SerialName("tags")
    data class ByTags(val tags: List<String>) : ProjectFilter()

    @Serializable
    @SerialName("whitelist")
    data class ByWhitelistedIds(val ids: List<String>) : ProjectFilter()

    @Serializable
    @SerialName("blacklist")
    data class ByBlacklistedIds(val ids: List<String>) : ProjectFilter()
}
