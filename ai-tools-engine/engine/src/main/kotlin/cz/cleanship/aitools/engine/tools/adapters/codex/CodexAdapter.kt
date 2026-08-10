package cz.cleanship.aitools.engine.tools.adapters.codex

import cz.cleanship.aitools.engine.io.deleteArtifactDirectoryWithin
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.UserDeploymentManifest
import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.UserInstructionsContext
import cz.cleanship.aitools.engine.tools.UserScopeExporter
import java.io.File

class CodexAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override val toolType: ToolType = ToolType.CODEX

    override fun prepare(projectDir: File, project: ProjectManifest) {
        if (project.deploy.replace) {
            CodexLayout.ofProject(projectDir).toolDir.deleteRecursively()
        }
    }

    override fun export(projectDir: File, globalContext: GlobalContext) {
        exportService.export(
            globalContext.project,
            CodexLayout.ofProject(projectDir).instructionsFile,
        ) {
            printers.globalFilePrinter.print(globalContext, it)
        }
    }

    override fun export(projectDir: File, promptContext: PromptContext) =
        exportPrompt(CodexLayout.ofProject(projectDir), promptContext)

    override fun export(projectDir: File, agentContext: AgentContext) =
        exportAgent(CodexLayout.ofProject(projectDir), agentContext)

    override fun export(projectDir: File, featureContext: FeatureContext) = exportService.export(
        featureContext.feature,
        CodexLayout.ofProject(projectDir).featureFile(featureContext.feature.id),
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

    override fun export(projectDir: File, skillContext: SkillContext) =
        exportSkill(CodexLayout.ofProject(projectDir), skillContext)

    override fun userScope(userHome: File, deployment: UserDeploymentManifest): UserScopeExporter =
        CodexUserScopeExporter(CodexLayout.ofUser(userHome), deployment)

    private fun exportPrompt(layout: CodexLayout, promptContext: PromptContext) = exportService.export(
        promptContext.prompt,
        layout.promptDir(promptContext.prompt.id).resolve(SKILL_FILE),
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

    private fun exportAgent(layout: CodexLayout, agentContext: AgentContext) = exportService.export(
        agentContext.agent,
        layout.agentDir(agentContext.agent.id).resolve(SKILL_FILE),
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

    private fun exportSkill(layout: CodexLayout, skillContext: SkillContext) {
        val skillDir = layout.skillDir(skillContext.skill.id)
        exportService.export(
            skillContext.skill,
            skillDir.resolve(SKILL_FILE),
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

    /**
     * Writes [deployment] into `<home>/.codex`, rendering exactly what a project deploy renders - see [CodexLayout].
     *
     * The instructions file is owned by the engine and overwritten on every deploy. Everything else the home holds is
     * left alone: a replacing deploy rewrites the directory of each artifact it deploys, never the `skills` directory
     * around them, so a skill the user wrote by hand survives.
     */
    private inner class CodexUserScopeExporter(
        private val layout: CodexLayout,
        private val deployment: UserDeploymentManifest,
    ) : UserScopeExporter {

        override val instructionsFile: File get() = layout.instructionsFile

        override fun export(instructionsContext: UserInstructionsContext) = exportService.export(
            instructionsContext.deployment,
            layout.instructionsFile,
        ) {
            printers.userInstructionsPrinter.print(instructionsContext, it)
        }

        override fun export(promptContext: PromptContext) {
            replaceIfRequested(layout.promptDir(promptContext.prompt.id), "prompt '${promptContext.prompt.id}'")
            exportPrompt(layout, promptContext)
        }

        override fun export(agentContext: AgentContext) {
            replaceIfRequested(layout.agentDir(agentContext.agent.id), "agent '${agentContext.agent.id}'")
            exportAgent(layout, agentContext)
        }

        override fun export(skillContext: SkillContext) {
            replaceIfRequested(layout.skillDir(skillContext.skill.id), "skill '${skillContext.skill.id}'")
            exportSkill(layout, skillContext)
        }

        private fun replaceIfRequested(artifactDir: File, describedBy: String) {
            if (deployment.replace) {
                artifactDir.deleteArtifactDirectoryWithin(owned = layout.skillsDir, describedBy = describedBy)
            }
        }
    }

    companion object {
        private const val SKILL_FILE = "SKILL.md"
    }
}
