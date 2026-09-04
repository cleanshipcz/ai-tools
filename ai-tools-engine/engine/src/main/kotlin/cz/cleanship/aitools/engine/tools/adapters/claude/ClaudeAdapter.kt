package cz.cleanship.aitools.engine.tools.adapters.claude

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

class ClaudeAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override val toolType: ToolType = ToolType.CLAUDE

    override fun prepare(projectDir: File, project: ProjectManifest) {
        if (project.deploy.replace) {
            ClaudeLayout.ofProject(projectDir).toolDir.deleteRecursively()
        }
    }

    override fun export(projectDir: File, globalContext: GlobalContext) {
        exportService.export(
            globalContext.project,
            ClaudeLayout.ofProject(projectDir).instructionsFile,
        ) {
            printers.globalFilePrinter.print(globalContext, it)
        }
    }

    override fun export(projectDir: File, promptContext: PromptContext) =
        exportPrompt(ClaudeLayout.ofProject(projectDir), promptContext)

    override fun export(projectDir: File, agentContext: AgentContext) =
        exportAgent(ClaudeLayout.ofProject(projectDir), agentContext)

    override fun export(projectDir: File, featureContext: FeatureContext) = exportService.export(
        featureContext.feature,
        ClaudeLayout.ofProject(projectDir).featureFile(featureContext.feature.id),
    ) {
        printers.featurePrinter.print(featureContext, it)
    }

    override fun export(projectDir: File, skillContext: SkillContext) =
        exportSkill(ClaudeLayout.ofProject(projectDir), skillContext)

    override fun userScope(userHome: File, deployment: UserDeploymentManifest): UserScopeExporter =
        ClaudeUserScopeExporter(ClaudeLayout.ofUser(userHome), deployment)

    private fun exportPrompt(layout: ClaudeLayout, promptContext: PromptContext) = exportService.export(
        promptContext.prompt,
        layout.promptFile(promptContext.prompt.id),
    ) {
        printers.promptPrinter.print(promptContext, it)
    }

    private fun exportAgent(layout: ClaudeLayout, agentContext: AgentContext) = exportService.export(
        agentContext.agent,
        layout.agentFile(agentContext.agent.id),
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

    private fun exportSkill(layout: ClaudeLayout, skillContext: SkillContext) {
        val skillDir = layout.skillDir(skillContext.skill.id)
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
        exportService.copySkillFiles(skillContext.skill.files, skillContext.sourceDir, skillDir, skillContext.skill.id)
    }

    /**
     * Writes [deployment] into `<home>/.claude`, rendering exactly what a project deploy renders - see [ClaudeLayout].
     *
     * The instructions file is owned by the engine and overwritten on every deploy. Everything else the home holds is
     * left alone: a replacing deploy rewrites the directory of each skill it deploys, never the `skills` directory
     * around them, so a skill the user wrote by hand survives.
     */
    private inner class ClaudeUserScopeExporter(
        private val layout: ClaudeLayout,
        private val deployment: UserDeploymentManifest,
    ) : UserScopeExporter {

        override val instructionsFile: File get() = layout.instructionsFile

        override fun export(instructionsContext: UserInstructionsContext) = exportService.export(
            instructionsContext.deployment,
            layout.instructionsFile,
        ) {
            printers.userInstructionsPrinter.print(instructionsContext, it)
        }

        override fun export(promptContext: PromptContext) = exportPrompt(layout, promptContext)

        override fun export(agentContext: AgentContext) = exportAgent(layout, agentContext)

        override fun export(skillContext: SkillContext) {
            if (deployment.replace) {
                exportService.replaceArtifactDirectory(
                    layout.skillDir(skillContext.skill.id),
                    owned = layout.skillsDir,
                    describedBy = "skill '${skillContext.skill.id}'",
                )
            }
            exportSkill(layout, skillContext)
        }
    }
}
