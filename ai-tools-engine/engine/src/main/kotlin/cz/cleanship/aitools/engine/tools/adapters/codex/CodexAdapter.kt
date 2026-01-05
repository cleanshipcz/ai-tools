package cz.cleanship.aitools.engine.tools.adapters.codex

import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.io.OutputStreamOutput
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.Printer
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.ToolAdapter

import java.io.File


class CodexAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    init {
        TODO("Not implemented yet")
    }

    override fun export(projectDir: File, globalContext: GlobalContext) {
        TODO("Not yet implemented")
    }

    override fun export(projectDir: File, promptContext: PromptContext) = exportService.export(
        promptContext.prompt,
        codexDir(projectDir).resolve("prompt-${promptContext.prompt.id}.md"),
    ) {
        printers.promptPrinter.print(promptContext, it)
    }

    override fun export(projectDir: File, agentContext: AgentContext) = exportService.export(
        agentContext.agent,
        codexDir(projectDir).resolve("agent-${agentContext.agent.id}.md"),
    ) {
        printers.agentPrinter.print(agentContext, it)
    }

    override fun export(projectDir: File, featureContext: FeatureContext) = exportService.export(
        featureContext.feature,
        codexDir(projectDir).resolve("features").resolve("feature-${featureContext.feature.id}.md"),
    ) {
        it.appendText(
            """
            # ${featureContext.feature.id}
            
            ${featureContext.feature.description}
            
            """.trimIndent()
        )
        it.appendLine()
        printers.featurePrinter.print(featureContext, it)
    }

    private fun codexDir(projectDir: File) = projectDir.resolve(".codex")

}
