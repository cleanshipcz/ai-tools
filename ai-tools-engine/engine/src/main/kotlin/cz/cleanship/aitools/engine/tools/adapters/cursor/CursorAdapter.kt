package cz.cleanship.aitools.engine.tools.adapters.cursor

import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import cz.cleanship.aitools.engine.tools.ToolAdapter
import java.io.File

class CursorAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override val toolType: ToolType = ToolType.CURSOR

    override fun prepare(projectDir: File, project: ProjectManifest) {
        if (project.deploy.replace) {
            cursorDir(projectDir).deleteRecursively()
        }
    }

    override fun export(projectDir: File, globalContext: GlobalContext) {
        exportService.export(
            globalContext.project,
            cursorDir(projectDir).resolve("rules").resolve("project.mdc"),
        ) {
            it.appendText(
                """
                ---
                description: ${globalContext.project.description.replace("\n", " ")}
                globs: "**/*"
                alwaysApply: true
                ---
                
                """.trimIndent(),
            )
            printers.globalFilePrinter.print(globalContext, it)
        }
    }

    override fun export(projectDir: File, promptContext: PromptContext) = exportService.export(
        promptContext.prompt,
        cursorDir(projectDir).resolve("commands").resolve("prompt-${promptContext.prompt.id}.md"),
    ) {
        printers.promptPrinter.print(promptContext, it)
    }

    override fun export(projectDir: File, agentContext: AgentContext) = exportService.export(
        agentContext.agent,
        cursorDir(projectDir).resolve("rules").resolve("agent-${agentContext.agent.id}.mdc"),
    ) {
        it.appendText(
            """
            ---
            alwaysApply: false
            ---
            
            """.trimIndent(),
        )
        printers.agentPrinter.print(agentContext, it)
    }

    override fun export(projectDir: File, featureContext: FeatureContext) = exportService.export(
        featureContext.feature,
        cursorDir(projectDir).resolve("features").resolve("feature-${featureContext.feature.id}.md"),
    ) {
        it.appendText(
            """
            # ${featureContext.feature.id}
            
            ${featureContext.feature.description}
            
            """.trimIndent(),
        )
        it.appendLine()
        printers.featurePrinter.print(featureContext, it)
    }

    override fun export(projectDir: File, skillContext: SkillContext) = exportService.export(
        skillContext.skill,
        cursorDir(projectDir).resolve("commands").resolve("skill-${skillContext.skill.id}.md"),
    ) {
        printers.skillPrinter.print(skillContext, it)
    }

    private fun cursorDir(projectDir: File) = projectDir.resolve(".cursor")
}
