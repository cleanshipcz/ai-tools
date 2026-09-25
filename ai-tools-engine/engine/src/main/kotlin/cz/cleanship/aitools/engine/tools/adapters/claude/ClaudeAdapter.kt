package cz.cleanship.aitools.engine.tools.adapters.claude

import cz.cleanship.aitools.engine.io.TargetRoot
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.UserDeploymentManifest
import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.Frontmatter
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.McpLimits
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.UserInstructionsContext
import cz.cleanship.aitools.engine.tools.UserScopeExporter
import cz.cleanship.aitools.engine.tools.mcp.ClaudeSettingsPermissionsExporter
import cz.cleanship.aitools.engine.tools.mcp.JsonMcpConfigFormat
import cz.cleanship.aitools.engine.tools.mcp.McpConfigExporter
import cz.cleanship.aitools.engine.tools.mcp.McpConfigFileExporter
import cz.cleanship.aitools.engine.tools.mcp.McpPermissionsExporter
import cz.cleanship.aitools.engine.tools.replacing
import java.io.File

class ClaudeAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override val toolType: ToolType = ToolType.CLAUDE

    override fun replacedPaths(projectDir: File, project: ProjectManifest): List<File> = project.replacing(ClaudeLayout.ofProject(projectDir).toolDir)

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

    override fun skillPaths(projectDir: File, skillId: String): List<File> =
        listOf(ClaudeLayout.ofProject(projectDir).skillDir(skillId))

    override fun userScope(userHome: File, deployment: UserDeploymentManifest): UserScopeExporter =
        ClaudeUserScopeExporter(ClaudeLayout.ofUser(userHome), deployment, userHome)

    // `.mcp.json` sits beside `.claude`, so the replaced `.claude` never holds it.
    override fun mcpConfig(projectDir: File): McpConfigExporter =
        McpConfigFileExporter(ClaudeLayout.ofProject(projectDir).mcpConfigFile, JsonMcpConfigFormat.CLAUDE_CODE, exportService, projectDir)

    // `settings.json` lies in `.claude`, which a replacing deploy deletes as a whole: the file is then written again from the restrictions of the project, and the ledger names the entries the engine owns in it.
    override fun mcpPermissions(projectDir: File): McpPermissionsExporter =
        ClaudeSettingsPermissionsExporter(ClaudeLayout.ofProject(projectDir).settingsFile, exportService, TargetRoot.Project(projectDir))

    override fun toolDirectories(projectDir: File): List<File> =
        ClaudeLayout.ofProject(projectDir).let { listOf(it.toolDir, it.agentsDir, it.commandsDir, it.skillsDir, it.workflowsDir) }

    override val mcpLimits = McpLimits(
        agentServers = null,
        // An allow entry of settings.json approves a call without a prompt, for every server of that name in any project; Claude Code offers no setting that narrows the tools of a server, so allow would grant where it means to restrict.
        allowedTools = "Claude Code has no list of the tools a server may offer; only 'deny' is rendered",
        deniedTools = null,
        userScope = null,
    )

    private fun exportPrompt(layout: ClaudeLayout, promptContext: PromptContext) = exportService.export(
        promptContext.prompt,
        layout.promptFile(promptContext.prompt.id),
    ) {
        printers.promptPrinter.print(promptContext, it)
    }

    // The servers are named, not defined: a name shares the connection the MCP config file of the scope configures, and whether Claude Code expands `${NAME}` in a server defined inside an agent file is not documented.
    private fun exportAgent(layout: ClaudeLayout, agentContext: AgentContext) = exportService.export(
        agentContext.agent,
        layout.agentFile(agentContext.agent.id),
    ) {
        val agent = agentContext.agent
        it.appendText(
            buildString {
                appendLine("---")
                appendLine("name: ${agent.id}")
                appendLine("description: ${Frontmatter.value(agent.description)}")
                if (agent.mcps.isNotEmpty()) appendLine("mcpServers: ${Frontmatter.list(agent.mcps.distinct())}")
                appendLine("---")
            },
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
                description: ${Frontmatter.value(skillContext.skill.description)}
                ---

                """.trimIndent(),
            )
            printers.skillPrinter.print(skillContext, it)
        }
        exportService.copySkillFiles(skillContext.skill.files, skillContext.sourceDir, skillDir, skillContext.skill.id, skillContext.pointerSourceDirs)
    }

    /**
     * Writes [deployment] into `<home>/.claude`, rendering exactly what a project deploy renders - see [ClaudeLayout].
     *
     * The instructions file is owned by the engine and overwritten on every deploy. Everything else the home holds is left alone: a replacing deploy rewrites the directory of each skill it deploys, never the `skills` directory around them, so a skill the user wrote by hand survives.
     */
    private inner class ClaudeUserScopeExporter(
        private val layout: ClaudeLayout,
        private val deployment: UserDeploymentManifest,
        private val userHome: File,
    ) : UserScopeExporter {

        override val instructionsFile: File get() = layout.instructionsFile

        // A user deploy writes no features, so the workflows directory is not one of them.
        override val toolDirectories: List<File> get() = listOf(layout.toolDir, layout.agentsDir, layout.commandsDir, layout.skillsDir)

        // Claude Code keeps its session state in the same file and rewrites it while it runs, so the file is edited in place and refused when it changes during the deploy.
        override fun mcpConfig(): McpConfigExporter =
            McpConfigFileExporter(layout.mcpConfigFile, JsonMcpConfigFormat.CLAUDE_CODE_USER, exportService, TargetRoot.UserHome(userHome))

        override fun mcpPermissions(): McpPermissionsExporter =
            ClaudeSettingsPermissionsExporter(layout.settingsFile, exportService, TargetRoot.UserHome(userHome))

        override fun export(instructionsContext: UserInstructionsContext) = exportService.export(
            instructionsContext.deployment,
            layout.instructionsFile,
        ) {
            printers.userInstructionsPrinter.print(instructionsContext, it)
        }

        override fun export(promptContext: PromptContext) = exportPrompt(layout, promptContext)

        override fun export(agentContext: AgentContext) = exportAgent(layout, agentContext)

        override fun skillPaths(skillId: String): List<File> = listOf(layout.skillDir(skillId))

        override val replacedWithin: File get() = layout.skillsDir

        // Prompts and agents are single files in this layout, which a deploy overwrites rather than deletes.
        override fun replacedPaths(
            promptIds: Collection<String>,
            agentIds: Collection<String>,
            skillIds: Collection<String>,
        ): List<File> =
            if (deployment.replace) skillIds.map { layout.skillDir(it) } else emptyList()

        override fun export(skillContext: SkillContext) {
            if (deployment.replace) {
                exportService.replaceArtifactDirectory(
                    layout.skillDir(skillContext.skill.id),
                    owned = replacedWithin,
                    describedBy = "skill '${skillContext.skill.id}'",
                )
            }
            exportSkill(layout, skillContext)
        }
    }
}
