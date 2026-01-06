package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import java.io.File

interface ToolAdapter {

    val toolType: ToolType

    fun prepare(projectDir: File, project: ProjectManifest)

    fun export(projectDir: File, globalContext: GlobalContext)

    fun export(projectDir: File, promptContext: PromptContext)

    fun export(projectDir: File, agentContext: AgentContext)

    fun export(projectDir: File, featureContext: FeatureContext)
}
