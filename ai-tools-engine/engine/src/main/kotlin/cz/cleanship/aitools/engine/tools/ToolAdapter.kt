package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.deleteTreeWithoutFollowingLinks
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.UserDeploymentManifest
import cz.cleanship.aitools.engine.tools.mcp.McpConfigExporter
import cz.cleanship.aitools.engine.tools.mcp.McpPermissionsExporter
import java.io.File

// One member per kind of artifact and per decision about it, each answered by every adapter on purpose; splitting the interface would only move that list.
@Suppress("TooManyFunctions")
interface ToolAdapter {

    val toolType: ToolType

    /**
     * Returns every path [prepare] deletes in [projectDir] for [project]: the directories this tool generates when the project sets `deploy.replace`, otherwise an empty list. No path is, or holds, a file of [mcpConfigs], whose entries the engine owns one by one. A path may hold the file of [mcpPermissions], such as `.claude/settings.json`, which is then deleted with it.
     */
    fun replacedPaths(projectDir: File, project: ProjectManifest): List<File>

    fun export(projectDir: File, globalContext: GlobalContext)

    fun export(projectDir: File, promptContext: PromptContext)

    fun export(projectDir: File, agentContext: AgentContext)

    fun export(projectDir: File, featureContext: FeatureContext)

    fun export(projectDir: File, skillContext: SkillContext)

    /**
     * Returns every path the export of the skill [skillId] into [projectDir] writes, replaces, or copies companion files into: files, and directories together with everything under them.
     */
    fun skillPaths(projectDir: File, skillId: String): List<File>

    /**
     * Returns how this tool writes [deployment] into its per-user configuration under [userHome]: a [UserScopeExporter] when it gets every artifact of the deployment, a [McpFilesUserScope] when it gets only the MCP servers, or `null` when the per-user layout of this tool is not implemented yet.
     *
     * Every adapter answers this deliberately rather than inheriting an answer, so that a tool gaining a user scope is a decision someone made about that tool rather than something a default quietly decided. The engine reports a `null` as a manifest skipped for this tool, and a [McpFilesUserScope] as deploying nothing for this tool when the deployment selects no MCP server and the ledger of the home records none it wrote, which is why an unimplemented layout never silently drops a tool a manifest declared - see [cz.cleanship.aitools.engine.ToolsEngine].
     */
    fun userScope(userHome: File, deployment: UserDeploymentManifest): UserScope?

    /**
     * Returns how this tool writes the MCP servers of a project into each of its MCP config files in [projectDir], one exporter per file, or an empty list when this tool does not support MCP servers in this engine.
     *
     * Every adapter answers this deliberately rather than inheriting an answer, like [userScope]. The engine reports an empty list as the MCP servers of the project skipped for this tool.
     */
    fun mcpConfigs(projectDir: File): List<McpConfigExporter>

    /**
     * Returns how this tool writes the MCP tool restrictions of a project into a permissions file in [projectDir], or `null` when this tool reads them from its MCP config file or has no way to read them.
     */
    fun mcpPermissions(projectDir: File): McpPermissionsExporter?

    /**
     * Returns the directories of [projectDir] this tool writes its files into, such as `<project>/.codex` and `<project>/.codex/skills`, each after the directory holding it; a file of the tool at the top of [projectDir], such as `AGENTS.md`, lies in none of them.
     *
     * The engine checks each of them before it writes any file of this tool - see [cz.cleanship.aitools.engine.io.requireToolDirectory] - and writes nothing of this tool for the project when one cannot hold its files.
     */
    fun toolDirectories(projectDir: File): List<File>

    /** What of the MCP support of the engine this tool lacks, each with the reason the engine reports. */
    val mcpLimits: McpLimits
}

/**
 * What of the MCP support of the engine a tool lacks, each part as the reason the engine logs when a deployment asks for it, or `null` when the tool has it.
 *
 * @property agentServers why the tool attaches no MCP server to an agent, or `null` when it lists them in the agent file
 * @property allowedTools why the tool applies no `allow` list of a server, or `null` when it applies it or has no MCP config file at all, which the engine reports on its own
 * @property deniedTools why the tool applies no `deny` list of a server, or `null` when it applies it or has no MCP config file at all
 * @property userScope why the tool gets no MCP servers in the user scope, or `null` when it gets them
 */
data class McpLimits(
    val agentServers: String?,
    val allowedTools: String?,
    val deniedTools: String?,
    val userScope: String?,
)

/**
 * Deletes every path of [ToolAdapter.replacedPaths] for [project] in [projectDir], each together with everything under it.
 *
 * A symbolic link among or below those paths is removed as a link; what it leads to is left untouched.
 *
 * @throws cz.cleanship.aitools.engine.io.ArtifactDeleteException if an entry cannot be deleted
 */
// An extension rather than an interface member, so no adapter can override it: what a replacing deploy deletes is exactly what replacedPaths names to the overlap check that runs before it.
fun ToolAdapter.prepare(projectDir: File, project: ProjectManifest) {
    replacedPaths(projectDir, project).forEach { it.deleteTreeWithoutFollowingLinks() }
}

/**
 * Returns [directories] when this project sets `deploy.replace`, otherwise an empty list - the answer of [ToolAdapter.replacedPaths] for a tool generating [directories].
 */
internal fun ProjectManifest.replacing(vararg directories: File): List<File> =
    if (deploy.replace) directories.toList() else emptyList()

/**
 * Returns these adapters narrowed to the tools of [declaredTools], or all of them when it is `null`.
 */
internal fun List<ToolAdapter>.narrowedTo(declaredTools: Collection<ToolType>?): List<ToolAdapter> =
    if (declaredTools == null) this else filter { it.toolType in declaredTools }
