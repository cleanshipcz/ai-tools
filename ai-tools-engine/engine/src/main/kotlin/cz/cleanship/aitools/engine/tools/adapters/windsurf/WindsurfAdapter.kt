package cz.cleanship.aitools.engine.tools.adapters.windsurf

import cz.cleanship.aitools.engine.io.OutputStreamOutput
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.Printer
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.ToolAdapter
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream

class WindsurfAdapter(
    private val printers: Printers = Printers,
) : ToolAdapter {


    override fun export(projectDir: File, promptContext: PromptContext) {
        val targetFile = rulesDir(projectDir).resolve("prompt-${promptContext.prompt.id}.md")
        export(targetFile, printers.promptPrinter, promptContext)
        LOG.info("Exported prompt ${promptContext.prompt.id} to ${targetFile.absolutePath}")
    }

    override fun export(projectDir: File, agentContext: AgentContext) {
        val targetFile = rulesDir(projectDir).resolve("agent-${agentContext.agent.id}.md")
        export(targetFile, printers.agentPrinter, agentContext)
        LOG.info("Exported agent ${agentContext.agent.id} to ${targetFile.absolutePath}")
    }

    override fun export(projectDir: File, featureContext: FeatureContext) {
        val feature = featureContext.feature
        val targetFile = workflowsDir(projectDir).resolve("feature-${feature.id}.md")
        targetFile.parentFile.mkdirs()
        OutputStreamOutput(FileOutputStream(targetFile)).use {
            it.appendText(
                """
                ---
                description: ${feature.description.replace("\n", " ")}
                auto_execution_mode: 3
                ---
            """.trimIndent()
            )
            it.appendLine()
            printers.featurePrinter.print(featureContext, it)
        }
        LOG.info("Exported feature ${featureContext.feature.id} to ${targetFile.absolutePath}")
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

    private fun workflowsDir(projectDir: File) = windsurfDir(projectDir).resolve("workflows")

    companion object {
        private val LOG = LoggerFactory.getLogger(WindsurfAdapter::class.java)
    }
}
