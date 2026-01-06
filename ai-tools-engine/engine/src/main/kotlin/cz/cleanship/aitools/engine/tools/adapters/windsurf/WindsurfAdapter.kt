package cz.cleanship.aitools.engine.tools.adapters.windsurf

import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.ToolAdapter
import java.io.File

class WindsurfAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override fun export(projectDir: File, globalContext: GlobalContext) = exportService.export(
        globalContext.project,
        rulesDir(projectDir).resolve("project.md"),
    ) {
        it.appendText(
            """
            ---
            trigger: always_on
            description: ${globalContext.project.description.replace("\n", " ")}
            ---
            
            """.trimIndent(),
        )
        printers.globalFilePrinter.print(globalContext, it)
    }

    override fun export(projectDir: File, promptContext: PromptContext) = exportService.export(
        promptContext.prompt,
        rulesDir(projectDir).resolve("prompt-${promptContext.prompt.id}.md"),
    ) {
        it.appendText(manualHeader)
        printers.promptPrinter.print(promptContext, it)
    }

    override fun export(projectDir: File, agentContext: AgentContext) = exportService.export(
        agentContext.agent,
        rulesDir(projectDir).resolve("agent-${agentContext.agent.id}.md"),
    ) {
        it.appendText(manualHeader)
        printers.agentPrinter.print(agentContext, it)
    }

    override fun export(projectDir: File, featureContext: FeatureContext) = exportService.export(
        featureContext.feature,
        workflowsDir(projectDir).resolve("feature-${featureContext.feature.id}.md"),
    ) {
        it.appendText(
            """
            ---
            description: ${featureContext.feature.description.replace("\n", " ")}
            auto_execution_mode: 3
            ---
            
            """.trimIndent(),
        )
        printers.featurePrinter.print(featureContext, it)
    }

    private fun windsurfDir(projectDir: File) = projectDir.resolve(".windsurf")

    private fun rulesDir(projectDir: File) = windsurfDir(projectDir).resolve("rules")

    private fun workflowsDir(projectDir: File) = windsurfDir(projectDir).resolve("workflows")

    companion object {
        private val manualHeader =
            """
            ---
            trigger: manual
            ---
            
            """.trimIndent()
    }
}
