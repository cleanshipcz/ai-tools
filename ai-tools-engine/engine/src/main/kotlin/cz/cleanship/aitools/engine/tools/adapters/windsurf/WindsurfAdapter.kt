package cz.cleanship.aitools.engine.tools.adapters.windsurf

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
import cz.cleanship.aitools.engine.tools.UserScopeExporter
import cz.cleanship.aitools.engine.tools.mcp.McpConfigExporter
import cz.cleanship.aitools.engine.tools.mcp.McpPermissionsExporter
import cz.cleanship.aitools.engine.tools.replacing
import java.io.File

class WindsurfAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override val toolType: ToolType = ToolType.WINDSURF

    override fun replacedPaths(projectDir: File, project: ProjectManifest): List<File> = project.replacing(windsurfDir(projectDir))

    /**
     * Returns no exporter: the per-user layout of Windsurf is not implemented yet, so the engine reports a user deployment naming this tool as skipped for it instead of writing anything into the home.
     */
    override fun userScope(userHome: File, deployment: UserDeploymentManifest): UserScopeExporter? = null

    /**
     * Returns no exporter: the official Windsurf and Devin documentation disagree on where the MCP config file lives, so the engine reports the MCP servers of a project as skipped for this tool instead of writing a file the installed version may ignore.
     */
    override fun mcpConfig(projectDir: File): McpConfigExporter? = null

    override fun mcpPermissions(projectDir: File): McpPermissionsExporter? = null

    override fun toolDirectories(projectDir: File): List<File> = listOf(windsurfDir(projectDir), rulesDir(projectDir), workflowsDir(projectDir))

    // The facts behind the user scope: the installed Windsurf (1.13.9, from before the rename) reads ~/.codeium/windsurf/mcp_config.json and has no project MCP file, while the documentation of Devin Desktop names ~/.config/devin/mcp_config.json and ~/.codeium/mcp_config.json for the user and .devin/mcp_config.json for a project.
    override val mcpLimits = McpLimits(
        agentServers = "Windsurf agents are rendered as rules, which name no MCP server, and the engine writes no Windsurf MCP config file",
        allowedTools = null,
        deniedTools = null,
        userScope = "the installed Windsurf reads the MCP servers of the user only from ~/.codeium/windsurf/mcp_config.json, and its documentation, since the product was renamed Devin Desktop, names conflicting paths (~/.config/devin/mcp_config.json, ~/.codeium/mcp_config.json), so the engine writes none of them",
    )

    override fun export(projectDir: File, globalContext: GlobalContext) = exportService.export(
        globalContext.project,
        rulesDir(projectDir).resolve("project.md"),
    ) {
        it.appendText(
            """
            ---
            trigger: always_on
            description: ${Frontmatter.value(globalContext.project.description)}
            ---
            
            """.trimIndent(),
        )
        printers.globalFilePrinter.print(globalContext, it)
    }

    override fun export(projectDir: File, promptContext: PromptContext) = exportService.export(
        promptContext.prompt,
        rulesDir(projectDir).resolve("prompt-${promptContext.prompt.id}.md"),
    ) {
        it.appendText(manualHeader)
        printers.promptPrinter.print(promptContext, it)
    }

    override fun export(projectDir: File, agentContext: AgentContext) = exportService.export(
        agentContext.agent,
        rulesDir(projectDir).resolve("agent-${agentContext.agent.id}.md"),
    ) {
        it.appendText(manualHeader)
        printers.agentPrinter.print(agentContext, it)
    }

    override fun export(projectDir: File, featureContext: FeatureContext) = exportService.export(
        featureContext.feature,
        workflowsDir(projectDir).resolve("feature-${featureContext.feature.id}.md"),
    ) {
        it.appendText(
            """
            ---
            description: ${Frontmatter.value(featureContext.feature.description)}
            auto_execution_mode: 3
            ---
            
            """.trimIndent(),
        )
        printers.featurePrinter.print(featureContext, it)
    }

    override fun export(projectDir: File, skillContext: SkillContext) {
        val skillId = skillContext.skill.id
        exportService.export(
            skillContext.skill,
            skillFile(projectDir, skillId),
        ) {
            it.appendText(manualHeader)
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

    companion object {
        private val manualHeader =
            """
            ---
            trigger: manual
            ---
            
            """.trimIndent()
    }
}

private fun skillFile(projectDir: File, skillId: String) = rulesDir(projectDir).resolve("skill-$skillId.md")

private fun skillFilesDir(projectDir: File, skillId: String) = rulesDir(projectDir).resolve("skill-$skillId")

private fun windsurfDir(projectDir: File) = projectDir.resolve(".windsurf")

private fun rulesDir(projectDir: File) = windsurfDir(projectDir).resolve("rules")

private fun workflowsDir(projectDir: File) = windsurfDir(projectDir).resolve("workflows")
