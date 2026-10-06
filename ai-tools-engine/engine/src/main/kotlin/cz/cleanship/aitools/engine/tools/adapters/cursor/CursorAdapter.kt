package cz.cleanship.aitools.engine.tools.adapters.cursor

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
import cz.cleanship.aitools.engine.tools.mcp.JsonMcpConfigFormat
import cz.cleanship.aitools.engine.tools.mcp.McpConfigExporter
import cz.cleanship.aitools.engine.tools.mcp.McpConfigFileExporter
import cz.cleanship.aitools.engine.tools.mcp.McpPermissionsExporter
import cz.cleanship.aitools.engine.tools.replacing
import java.io.File

class CursorAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override val toolType: ToolType = ToolType.CURSOR

    // The directories this adapter generates, not `.cursor` as a whole: `.cursor/mcp.json` holds the MCP servers, and the user's own among them, beside them.
    override fun replacedPaths(projectDir: File, project: ProjectManifest): List<File> =
        project.replacing(rulesDir(projectDir), commandsDir(projectDir), featuresDir(projectDir))

    override fun mcpConfigs(projectDir: File): List<McpConfigExporter> =
        listOf(McpConfigFileExporter(cursorDir(projectDir).resolve("mcp.json"), JsonMcpConfigFormat.CURSOR, exportService, projectDir))

    override fun mcpPermissions(projectDir: File): McpPermissionsExporter? = null

    override fun toolDirectories(projectDir: File): List<File> = listOf(cursorDir(projectDir), rulesDir(projectDir), commandsDir(projectDir), featuresDir(projectDir))

    override val mcpLimits = McpLimits(
        agentServers = "Cursor agents are rendered as rules, and Cursor gives every subagent all servers it is configured with",
        allowedTools = NO_TOOL_RESTRICTION,
        deniedTools = NO_TOOL_RESTRICTION,
        userScope = "this engine writes no user-scope files for Cursor, which reads the MCP servers of the user from ~/.cursor/mcp.json",
    )

    /**
     * Returns no exporter: the per-user layout of Cursor is not implemented yet, so the engine reports a user deployment naming this tool as skipped for it instead of writing anything into the home.
     */
    override fun userScope(userHome: File, deployment: UserDeploymentManifest): UserScopeExporter? = null

    override fun export(projectDir: File, globalContext: GlobalContext) {
        exportService.export(
            globalContext.project,
            rulesDir(projectDir).resolve("project.mdc"),
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
        rulesDir(projectDir).resolve("agent-${agentContext.agent.id}.mdc"),
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
        featuresDir(projectDir).resolve("feature-${featureContext.feature.id}.md"),
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
}

private fun cursorDir(projectDir: File) = projectDir.resolve(".cursor")

private fun commandsDir(projectDir: File) = cursorDir(projectDir).resolve("commands")

private fun rulesDir(projectDir: File) = cursorDir(projectDir).resolve("rules")

private fun featuresDir(projectDir: File) = cursorDir(projectDir).resolve("features")

private const val NO_TOOL_RESTRICTION = "the engine knows no setting of .cursor/mcp.json that restricts the tools of a server"
