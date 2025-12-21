package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.RulepackManifest
import java.io.File

interface ToolAdapter {
    fun export(projectDir: File, promptManifest: PromptManifest)
    fun export(projectDir: File, agentContext: AgentContext)
    fun export(projectDir: File, rulepackManifest: RulepackManifest)
}
