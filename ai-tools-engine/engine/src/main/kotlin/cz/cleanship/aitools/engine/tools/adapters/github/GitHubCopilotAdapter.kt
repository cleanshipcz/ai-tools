package cz.cleanship.aitools.engine.tools.adapters.github

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

class GitHubCopilotAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override val toolType: ToolType = ToolType.GITHUB_COPILOT

    override fun prepare(projectDir: File, project: ProjectManifest) {
        if (project.deploy.replace) {
            promptsDir(projectDir).deleteRecursively()
            instructionsDir(projectDir).deleteRecursively()
        }
    }

    override fun export(projectDir: File, globalContext: GlobalContext) = exportService.export(
        globalContext.project,
        githubDir(projectDir).resolve("copilot-instructions.md"),
    ) {
        it.appendText(
            """
            # Copilot Instructions for this project
            
            ## Description
            
            ${globalContext.project.description}                
            """.trimIndent(),
        )
        printers.globalFilePrinter.print(globalContext, it)
    }

    override fun export(projectDir: File, promptContext: PromptContext) = exportService.export(
        promptContext.prompt,
        promptsDir(projectDir).resolve("prompt-${promptContext.prompt.id}.prompt.md"),
    ) {
        it.appendText(header)
        printers.promptPrinter.print(promptContext, it)
    }

    override fun export(projectDir: File, agentContext: AgentContext) = exportService.export(
        agentContext.agent,
        instructionsDir(projectDir).resolve("agent-${agentContext.agent.id}.instructions.md"),
    ) {
        it.appendText(header)
        printers.agentPrinter.print(agentContext, it)
    }

    override fun export(projectDir: File, featureContext: FeatureContext) = exportService.export(
        featureContext.feature,
        instructionsDir(projectDir).resolve("feature-${featureContext.feature.id}.instructions.md"),
    ) {
        it.appendText(header)
        printers.featurePrinter.print(featureContext, it)
    }

    override fun export(projectDir: File, skillContext: SkillContext) {
        val skillId = skillContext.skill.id
        exportService.export(
            skillContext.skill,
            promptsDir(projectDir).resolve("skill-$skillId.prompt.md"),
        ) {
            it.appendText(header)
            printers.skillPrinter.print(skillContext, it)
        }
        exportService.copySkillFiles(
            skillContext.skill.files,
            skillContext.sourceDir,
            promptsDir(projectDir).resolve("skill-$skillId"),
        )
    }

    private fun githubDir(projectDir: File) = projectDir.resolve(".github")

    private fun promptsDir(projectDir: File) = githubDir(projectDir).resolve("prompts")

    private fun instructionsDir(projectDir: File) = githubDir(projectDir).resolve("instructions")

    companion object {
        private val header =
            """
            ---
            applyTo: "**/*"
            ---
            
            """.trimIndent()
    }
}
