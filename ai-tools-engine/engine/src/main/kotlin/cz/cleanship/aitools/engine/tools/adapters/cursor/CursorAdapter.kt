package cz.cleanship.aitools.engine.tools.adapters.cursor

import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.UserDeploymentManifest
import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.Frontmatter
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.UserScopeExporter
import cz.cleanship.aitools.engine.tools.replacing
import java.io.File

class CursorAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override val toolType: ToolType = ToolType.CURSOR

    override fun replacedPaths(projectDir: File, project: ProjectManifest): List<File> = project.replacing(cursorDir(projectDir))

    /**
     * Returns no exporter: the per-user layout of Cursor is not implemented yet, so the engine reports a user deployment naming this tool as skipped for it instead of writing anything into the home.
     */
    override fun userScope(userHome: File, deployment: UserDeploymentManifest): UserScopeExporter? = null

    override fun export(projectDir: File, globalContext: GlobalContext) {
        exportService.export(
            globalContext.project,
            cursorDir(projectDir).resolve("rules").resolve("project.mdc"),
        ) {
            it.appendText(
                """
                ---
                description: ${Frontmatter.value(globalContext.project.description)}
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
        commandsDir(projectDir).resolve("prompt-${promptContext.prompt.id}.md"),
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

    override fun export(projectDir: File, skillContext: SkillContext) {
        val skillId = skillContext.skill.id
        exportService.export(
            skillContext.skill,
            skillFile(projectDir, skillId),
        ) {
            printers.skillPrinter.print(skillContext, it)
        }
        exportService.copySkillFiles(
            skillContext.skill.files,
            skillContext.sourceDir,
            skillFilesDir(projectDir, skillId),
            skillId,
            skillContext.pointerSourceDirs,
        )
    }

    override fun skillPaths(projectDir: File, skillId: String): List<File> =
        listOf(skillFile(projectDir, skillId), skillFilesDir(projectDir, skillId))

    private fun skillFile(projectDir: File, skillId: String) = commandsDir(projectDir).resolve("skill-$skillId.md")

    private fun skillFilesDir(projectDir: File, skillId: String) = commandsDir(projectDir).resolve("skill-$skillId")

    private fun commandsDir(projectDir: File) = cursorDir(projectDir).resolve("commands")

    private fun cursorDir(projectDir: File) = projectDir.resolve(".cursor")
}
