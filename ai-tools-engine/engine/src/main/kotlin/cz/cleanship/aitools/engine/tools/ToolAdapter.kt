package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.UserDeploymentManifest
import java.io.File

interface ToolAdapter {

    val toolType: ToolType

    fun prepare(projectDir: File, project: ProjectManifest)

    fun export(projectDir: File, globalContext: GlobalContext)

    fun export(projectDir: File, promptContext: PromptContext)

    fun export(projectDir: File, agentContext: AgentContext)

    fun export(projectDir: File, featureContext: FeatureContext)

    fun export(projectDir: File, skillContext: SkillContext)

    /**
     * Returns how this tool writes [deployment] into its per-user configuration under [userHome], or `null` when
     * the per-user layout of this tool is not implemented yet.
     *
     * Every adapter answers this deliberately rather than inheriting an answer, so that a tool gaining a user scope
     * is a decision someone made about that tool rather than something a default quietly decided. The engine reports
     * a `null` as a manifest skipped for this tool, which is why an unimplemented layout never silently drops a tool
     * a manifest declared - see [cz.cleanship.aitools.engine.ToolsEngine].
     */
    fun userScope(userHome: File, deployment: UserDeploymentManifest): UserScopeExporter?
}
