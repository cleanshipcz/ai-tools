package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.deleteTreeWithoutFollowingLinks
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.UserDeploymentManifest
import cz.cleanship.aitools.engine.tools.mcp.McpConfigExporter
import java.io.File

interface ToolAdapter {

    val toolType: ToolType

    /**
     * Returns every path [prepare] deletes in [projectDir] for [project]: the directories this tool generates when the project sets `deploy.replace`, otherwise an empty list. No path is, or holds, the file of [mcpConfig], whose entries the engine owns one by one.
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
     * Returns how this tool writes [deployment] into its per-user configuration under [userHome], or `null` when the per-user layout of this tool is not implemented yet.
     *
     * Every adapter answers this deliberately rather than inheriting an answer, so that a tool gaining a user scope is a decision someone made about that tool rather than something a default quietly decided. The engine reports a `null` as a manifest skipped for this tool, which is why an unimplemented layout never silently drops a tool a manifest declared - see [cz.cleanship.aitools.engine.ToolsEngine].
     */
    fun userScope(userHome: File, deployment: UserDeploymentManifest): UserScopeExporter?

    /**
     * Returns how this tool writes the MCP servers of a project into its MCP config file in [projectDir], or `null` when this tool does not support MCP servers in this engine.
     *
     * Every adapter answers this deliberately rather than inheriting an answer, like [userScope]. The engine reports a `null` as the MCP servers of the project skipped for this tool.
     */
    fun mcpConfig(projectDir: File): McpConfigExporter?
}

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
