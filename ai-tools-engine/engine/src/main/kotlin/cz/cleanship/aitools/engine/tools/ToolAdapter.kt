package cz.cleanship.aitools.engine.tools

import java.io.File

interface ToolAdapter {
    fun export(projectDir: File, globalContext: GlobalContext)

    fun export(projectDir: File, promptContext: PromptContext)

    fun export(projectDir: File, agentContext: AgentContext)

    fun export(projectDir: File, featureContext: FeatureContext)
}
