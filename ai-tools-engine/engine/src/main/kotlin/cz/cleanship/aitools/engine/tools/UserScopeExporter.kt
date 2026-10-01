package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.tools.mcp.McpConfigExporter
import cz.cleanship.aitools.engine.tools.mcp.McpPermissionsExporter
import java.io.File

/**
 * The MCP files one tool reads from the home, obtained from [ToolAdapter.userScope], which binds it to both the home base of the run and the manifest being deployed; it is either a [UserScopeExporter], for a tool that gets every artifact of a deployment in the home, or a [McpFilesUserScope], for a tool that gets only these files.
 *
 * It owns, in its MCP config and permissions files, only the entries [McpConfigExporter] and [McpPermissionsExporter] own. The directories holding them are shared with everything the user installed by hand, so they are created when missing and never removed.
 */
sealed interface UserScope {

    /**
     * The directories of this home the tool writes its files into, each after the directory holding it - `<home>/.claude` and `<home>/.claude/agents`, `<home>/.codex` and `<home>/.codex/skills`, `<home>/.copilot`.
     *
     * The engine checks each of them before it writes any file of this tool, by the rules of [cz.cleanship.aitools.engine.io.TargetRoot.UserHome] - see [cz.cleanship.aitools.engine.io.requireToolDirectory].
     */
    val toolDirectories: List<File>

    /**
     * Returns how this tool writes the MCP servers of the deployment into its MCP config file of this home - `<home>/.claude.json`, `<home>/.codex/config.toml`, `<home>/.copilot/mcp-config.json` - or `null` when it has none.
     */
    fun mcpConfig(): McpConfigExporter?

    /**
     * Returns how this tool writes the MCP tool restrictions of the deployment into a permissions file of this home - `<home>/.claude/settings.json` - or `null` when it reads them from its MCP config file or has no way to read them.
     */
    fun mcpPermissions(): McpPermissionsExporter?
}

/**
 * The [UserScope] of a tool that gets only its MCP files in the home, and none of the other artifacts of a deployment.
 */
interface McpFilesUserScope : UserScope

/**
 * The exporter of every artifact of the deployment when this is a [UserScopeExporter], or `null` when it is a [McpFilesUserScope].
 */
val UserScope.artifactExporter: UserScopeExporter?
    get() = when (this) {
        is UserScopeExporter -> this
        is McpFilesUserScope -> null
    }

/**
 * Writes the artifacts of one [cz.cleanship.aitools.engine.models.UserDeploymentManifest] into the per-user configuration of one tool - `~/.claude`, `~/.claude.json`, `~/.codex` - the way [ToolAdapter] writes them into a project directory, beside the MCP files of its [UserScope].
 *
 * An exporter owns the paths of the artifacts it writes. The directories holding them are shared with everything the user installed by hand, so they are created when missing and never removed - see [cz.cleanship.aitools.engine.models.UserDeploymentManifest.replace] for how far a replacing deploy reaches.
 */
interface UserScopeExporter : UserScope {

    /**
     * The single instructions file of this tool in this home - `<home>/.claude/CLAUDE.md`, `<home>/.codex/AGENTS.md`.
     *
     * The engine reads it before exporting: there is exactly one per tool per home, so two manifests deploying to the same tool would otherwise overwrite each other here without a word - see [cz.cleanship.aitools.engine.ToolsEngine].
     */
    val instructionsFile: File

    fun export(instructionsContext: UserInstructionsContext)

    fun export(promptContext: PromptContext)

    fun export(agentContext: AgentContext)

    fun export(skillContext: SkillContext)

    /**
     * Returns every path the export of the skill [skillId] into this home writes, replaces, or copies companion files into: files, and directories together with everything under them.
     */
    fun skillPaths(skillId: String): List<File>

    /**
     * Returns every directory a deploy of the prompts [promptIds], the agents [agentIds] and the skills [skillIds] into this home deletes, each together with everything under it, before writing it again; an empty list when the deployment does not set `replace`.
     */
    fun replacedPaths(
        promptIds: Collection<String>,
        agentIds: Collection<String>,
        skillIds: Collection<String>,
    ): List<File>

    /**
     * The directory every path of [replacedPaths] has to lie in, judged by where it leads when it is a symbolic link, for a replacing deploy to delete it - `<home>/.claude/skills`, `<home>/.codex/skills`.
     */
    val replacedWithin: File
}
