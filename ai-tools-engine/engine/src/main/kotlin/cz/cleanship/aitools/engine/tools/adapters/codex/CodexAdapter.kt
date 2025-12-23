package cz.cleanship.aitools.engine.tools.adapters.codex

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

class CodexAdapter(
    private val printers: Printers = Printers,
) : ToolAdapter {

    override fun export(projectDir: File, promptContext: PromptContext) {
        val targetFile = codexDir(projectDir).resolve("prompt-${promptContext.prompt.id}.md")
        export(targetFile, printers.promptPrinter, promptContext)
        LOG.info("Exported prompt ${promptContext.prompt.id} to ${targetFile.absolutePath}")
    }

    override fun export(projectDir: File, agentContext: AgentContext) {
        // Usually Codex looks for AGENTS.md, but we support multiple agents here, so we name them uniquely.
        val targetFile = codexDir(projectDir).resolve("agent-${agentContext.agent.id}.md")
        export(targetFile, printers.agentPrinter, agentContext)
        LOG.info("Exported agent ${agentContext.agent.id} to ${targetFile.absolutePath}")
    }

    override fun export(projectDir: File, featureContext: FeatureContext) {
        val feature = featureContext.feature
        val targetFile = codexDir(projectDir).resolve("features").resolve("feature-${feature.id}.md")
        targetFile.parentFile.mkdirs()
        OutputStreamOutput(FileOutputStream(targetFile)).use {
            it.appendText(
                """
                # ${feature.id}
                
                ${feature.description}
                
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
             printer.print(entity, it)
        }
    }

    private fun codexDir(projectDir: File) = projectDir.resolve(".codex")

    companion object {
        private val LOG = LoggerFactory.getLogger(CodexAdapter::class.java)
    }
}
