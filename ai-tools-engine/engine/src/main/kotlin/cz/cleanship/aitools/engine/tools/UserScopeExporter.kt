package cz.cleanship.aitools.engine.tools

/**
 * Writes the artifacts of one [cz.cleanship.aitools.engine.models.UserDeploymentManifest] into the per-user
 * configuration of one tool - `~/.claude`, `~/.codex` - the way [ToolAdapter] writes them into a project directory.
 * It is obtained from [ToolAdapter.userScope], which binds it to both the home base of the run and the manifest
 * being deployed, so neither has to be repeated on every call.
 *
 * An exporter owns the paths of the artifacts it writes and nothing else. The directories holding them are shared
 * with everything the user installed by hand, so they are created when missing and never removed - see
 * [cz.cleanship.aitools.engine.models.UserDeploymentManifest.replace] for how far a replacing deploy reaches.
 */
interface UserScopeExporter {

    fun export(instructionsContext: UserInstructionsContext)

    fun export(promptContext: PromptContext)

    fun export(agentContext: AgentContext)

    fun export(skillContext: SkillContext)
}
