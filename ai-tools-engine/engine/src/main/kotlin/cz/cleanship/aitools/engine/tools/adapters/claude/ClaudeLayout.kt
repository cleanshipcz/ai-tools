package cz.cleanship.aitools.engine.tools.adapters.claude

import java.io.File

/**
 * Where one deploy of [ClaudeAdapter] writes each artifact. A project deploy writes the layout Claude Code reads
 * inside a repository, a user deploy the one it reads from the home directory; what is written to those paths is
 * identical, which is why the adapter renders once and only chooses a layout.
 *
 * @param toolDir the `.claude` directory of this scope, which a project deploy may replace as a whole and a user
 * deploy never touches beyond the artifacts it writes inside it and the entries it owns in [settingsFile]
 * @param instructionsFile the memory file Claude Code reads for this scope, which sits beside `.claude` in a project
 * and inside it in the user scope
 * @param mcpConfigFile the file Claude Code reads the MCP servers of this scope from: `.mcp.json` beside `.claude` in a
 * project, `~/.claude.json` in the user scope
 */
internal class ClaudeLayout(
    val toolDir: File,
    val instructionsFile: File,
    val mcpConfigFile: File,
) {
    /** The directory holding every skill of this scope, which a deploy writes into but never removes. */
    val skillsDir: File get() = toolDir.resolve("skills")

    fun skillDir(skillId: String): File = skillsDir.resolve(skillId)

    /** The directory holding every agent of this scope. */
    val agentsDir: File get() = toolDir.resolve("agents")

    /** The directory holding every prompt of this scope. */
    val commandsDir: File get() = toolDir.resolve("commands")

    /** The directory holding every feature of this scope; only a project deploy writes features. */
    val workflowsDir: File get() = toolDir.resolve("workflows")

    fun agentFile(agentId: String): File = agentsDir.resolve("$agentId.md")

    fun promptFile(promptId: String): File = commandsDir.resolve("$promptId.md")

    fun featureFile(featureId: String): File = workflowsDir.resolve("feature-$featureId.md")

    /** The settings file of this scope, whose `permissions` allow and deny the tools of MCP servers. */
    val settingsFile: File get() = toolDir.resolve("settings.json")

    companion object {
        fun ofProject(projectDir: File) = ClaudeLayout(
            toolDir = projectDir.resolve(CLAUDE_DIR),
            instructionsFile = projectDir.resolve(INSTRUCTIONS_FILE),
            mcpConfigFile = projectDir.resolve(".mcp.json"),
        )

        fun ofUser(userHome: File): ClaudeLayout {
            val toolDir = userHome.resolve(CLAUDE_DIR)
            return ClaudeLayout(toolDir, instructionsFile = toolDir.resolve(INSTRUCTIONS_FILE), mcpConfigFile = userHome.resolve(".claude.json"))
        }

        private const val CLAUDE_DIR = ".claude"
        private const val INSTRUCTIONS_FILE = "CLAUDE.md"
    }
}
