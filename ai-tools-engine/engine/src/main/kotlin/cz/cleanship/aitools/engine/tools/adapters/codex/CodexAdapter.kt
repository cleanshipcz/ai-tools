package cz.cleanship.aitools.engine.tools.adapters.codex

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

class CodexAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override val toolType: ToolType = ToolType.CODEX

    override fun prepare(projectDir: File, project: ProjectManifest) {
        if (project.deploy.replace) {
            codexDir(projectDir).deleteRecursively()
        }
    }

    override fun export(projectDir: File, globalContext: GlobalContext) {
        exportService.export(
            globalContext.project,
            projectDir.resolve("AGENTS.md"),
        ) {
            printers.globalFilePrinter.print(globalContext, it)
        }
    }

    override fun export(projectDir: File, promptContext: PromptContext) = exportService.export(
        promptContext.prompt,
        skillsDir(projectDir).resolve("prompt-${promptContext.prompt.id}").resolve("SKILL.md"),
    ) {
        it.appendText(
            """
            ---
            name: ${promptContext.prompt.id}
            description: ${promptContext.prompt.description.replace("\n", " ")}
            ---
            
            """.trimIndent(),
        )
        printers.promptPrinter.print(promptContext, it)
    }

    override fun export(projectDir: File, agentContext: AgentContext) = exportService.export(
        agentContext.agent,
        skillsDir(projectDir).resolve("agent-${agentContext.agent.id}").resolve("SKILL.md"),
    ) {
        it.appendText(
            """
            ---
            name: ${agentContext.agent.id}
            description: ${agentContext.agent.description.replace("\n", " ")}
            ---
            
            """.trimIndent(),
        )
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
            
            """.trimIndent(),
        )
        it.appendLine()
        printers.featurePrinter.print(featureContext, it)
    }

    override fun export(projectDir: File, skillContext: SkillContext) {
        val skillDir = skillsDir(projectDir).resolve("skill-${skillContext.skill.id}")
        exportService.export(
            skillContext.skill,
            skillDir.resolve("SKILL.md"),
        ) {
            it.appendText(
                """
                ---
                name: ${skillContext.skill.id}
                description: ${skillContext.skill.description.replace("\n", " ")}
                ---

                """.trimIndent(),
            )
            printers.skillPrinter.print(skillContext, it)
        }
        exportService.copySkillFiles(skillContext.skill.files, skillContext.sourceDir, skillDir)
    }

    private fun codexDir(projectDir: File) = projectDir.resolve(".codex")

    private fun skillsDir(projectDir: File) = codexDir(projectDir).resolve("skills")
}
