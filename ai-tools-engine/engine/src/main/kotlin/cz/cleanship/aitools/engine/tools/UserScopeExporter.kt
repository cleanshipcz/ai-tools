package cz.cleanship.aitools.engine.tools

import java.io.File

/**
 * Writes the artifacts of one [cz.cleanship.aitools.engine.models.UserDeploymentManifest] into the per-user configuration of one tool - `~/.claude`, `~/.codex` - the way [ToolAdapter] writes them into a project directory. It is obtained from [ToolAdapter.userScope], which binds it to both the home base of the run and the manifest being deployed, so neither has to be repeated on every call.
 *
 * An exporter owns the paths of the artifacts it writes and nothing else. The directories holding them are shared with everything the user installed by hand, so they are created when missing and never removed - see [cz.cleanship.aitools.engine.models.UserDeploymentManifest.replace] for how far a replacing deploy reaches.
 */
interface UserScopeExporter {

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
