package cz.cleanship.aitools.engine.tools.adapters.github

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

class GitHubCopilotAdapter(
    private val printers: Printers = Printers,
) : ToolAdapter {

    override fun export(projectDir: File, promptContext: PromptContext) {
        // .github/prompts/prompt-{id}.prompt.md
        val targetFile = githubDir(projectDir).resolve("prompts").resolve("prompt-${promptContext.prompt.id}.prompt.md")
        export(targetFile, printers.promptPrinter, promptContext)
        LOG.info("Exported prompt ${promptContext.prompt.id} to ${targetFile.absolutePath}")
    }

    override fun export(projectDir: File, agentContext: AgentContext) {
        // .github/instructions/agent-{id}.instructions.md
        val targetFile = githubDir(projectDir).resolve("instructions").resolve("agent-${agentContext.agent.id}.instructions.md")
        
        // Add YAML frontmatter for applyTo
        targetFile.parentFile.mkdirs()
        OutputStreamOutput(FileOutputStream(targetFile)).use {
            it.appendText(
                """
                ---
                applyTo: "**"
                ---
                
                """.trimIndent()
            )
            printers.agentPrinter.print(agentContext, it)
        }
        LOG.info("Exported agent ${agentContext.agent.id} to ${targetFile.absolutePath}")
    }

    override fun export(projectDir: File, featureContext: FeatureContext) {
        val feature = featureContext.feature
        val targetFile = githubDir(projectDir).resolve("features").resolve("feature-${feature.id}.md")
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

    private fun githubDir(projectDir: File) = projectDir.resolve(".github")

    companion object {
        private val LOG = LoggerFactory.getLogger(GitHubCopilotAdapter::class.java)
    }
}
