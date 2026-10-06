package cz.cleanship.aitools.engine.tools.adapters.antigravity

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

class AntigravityAdapter(
    private val printers: Printers = Printers,
    private val exportService: ExportService = ExportService(),
) : ToolAdapter {

    override val toolType: ToolType = ToolType.ANTIGRAVITY

    override fun replacedPaths(projectDir: File, project: ProjectManifest): List<File> = project.replacing(agentDir(projectDir))

    /**
     * Returns no exporter: the per-user layout of Antigravity is not implemented yet, so the engine reports a user deployment naming this tool as skipped for it instead of writing anything into the home.
     */
    override fun userScope(userHome: File, deployment: UserDeploymentManifest): UserScopeExporter? = null

    /**
     * Returns no exporter: Antigravity expands no environment variable in its MCP config file, so a secret could only reach a server by being written into it, and the engine reports the MCP servers of a project as skipped for this tool instead.
     */
    override fun mcpConfigs(projectDir: File): List<McpConfigExporter> = emptyList()

    override fun mcpPermissions(projectDir: File): McpPermissionsExporter? = null

    override fun toolDirectories(projectDir: File): List<File> = listOf(agentDir(projectDir), rulesDir(projectDir), workflowsDir(projectDir))

    override val mcpLimits = McpLimits(
        agentServers = "Antigravity agents are rendered as rules, which name no MCP server, and the engine writes no Antigravity MCP config file",
        allowedTools = null,
        deniedTools = null,
        userScope = "Antigravity expands no environment variable in its MCP config file, so a secret could reach a server only by being written into the file",
    )

    override fun export(projectDir: File, globalContext: GlobalContext) = exportService.export(
        globalContext.project,
        rulesDir(projectDir).resolve("project.md"),
    ) {
        it.appendText(
            """
            ---
            trigger: always_on
            glob:
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
        it.appendText(ruleHeader)
        printers.promptPrinter.print(promptContext, it)
    }

    override fun export(projectDir: File, agentContext: AgentContext) = exportService.export(
        agentContext.agent,
        rulesDir(projectDir).resolve("agent-${agentContext.agent.id}.md"),
    ) {
        it.appendText(ruleHeader)
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
            it.appendText(ruleHeader)
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
        private val ruleHeader =
            """
            ---
            trigger: manual
            ---
            
            """.trimIndent()
    }
}

private fun skillFile(projectDir: File, skillId: String) = rulesDir(projectDir).resolve("skill-$skillId.md")

private fun skillFilesDir(projectDir: File, skillId: String) = rulesDir(projectDir).resolve("skill-$skillId")

private fun agentDir(projectDir: File) = projectDir.resolve(".agent")

private fun rulesDir(projectDir: File) = agentDir(projectDir).resolve("rules")

private fun workflowsDir(projectDir: File) = agentDir(projectDir).resolve("workflows")
