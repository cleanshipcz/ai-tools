package cz.cleanship.aitools.engine.tools.adapters.windsurf

import cz.cleanship.aitools.engine.io.OutputStreamOutput
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.RulepackManifest
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.Printer
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.ToolAdapter
import java.io.File
import java.io.FileOutputStream

class WindsurfAdapter(
    private val printers: Printers,
) : ToolAdapter {


    override fun export(projectDir: File, promptManifest: PromptManifest) {
        val targetFile = rulesDir(projectDir).resolve("prompt-${promptManifest.id}.md")
        export(targetFile, printers.promptPrinter, promptManifest)
    }

    override fun export(projectDir: File, agentContext: AgentContext) {
        val targetFile = rulesDir(projectDir).resolve("agent-${agentContext.agent.id}.md")
        export(targetFile, printers.agentPrinter, agentContext)
    }

    override fun export(projectDir: File, rulepackManifest: RulepackManifest) {
        TODO("Not yet implemented")
    }

    private fun <T> export(targetFile: File, printer: Printer<T>, entity: T) {
        targetFile.parentFile.mkdirs()
        OutputStreamOutput(FileOutputStream(targetFile)).use {
            it.appendText(
                """
                ---
                trigger: manual
                ---
            """.trimIndent()
            )
            it.appendLine()
            printer.print(entity, it)
        }
    }

    private fun windsurfDir(projectDir: File) = projectDir.resolve(".windsurf")

    private fun rulesDir(projectDir: File) = windsurfDir(projectDir).resolve("rules")

    private fun instructionsDir(projectDir: File) = windsurfDir(projectDir).resolve("instructions")
}