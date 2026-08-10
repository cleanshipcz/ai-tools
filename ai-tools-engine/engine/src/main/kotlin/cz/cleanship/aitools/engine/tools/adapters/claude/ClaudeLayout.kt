package cz.cleanship.aitools.engine.tools.adapters.claude

import java.io.File

/**
 * Where one deploy of [ClaudeAdapter] writes each artifact. A project deploy writes the layout Claude Code reads
 * inside a repository, a user deploy the one it reads from the home directory; what is written to those paths is
 * identical, which is why the adapter renders once and only chooses a layout.
 *
 * @param toolDir the `.claude` directory of this scope, which a project deploy may replace as a whole and a user
 * deploy never touches beyond the artifacts it writes inside it
 * @param instructionsFile the memory file Claude Code reads for this scope, which sits beside `.claude` in a project
 * and inside it in the user scope
 */
internal class ClaudeLayout(
    val toolDir: File,
    val instructionsFile: File,
) {
    fun skillDir(skillId: String): File = toolDir.resolve("skills").resolve(skillId)

    fun agentFile(agentId: String): File = toolDir.resolve("agents").resolve("$agentId.md")

    fun promptFile(promptId: String): File = toolDir.resolve("commands").resolve("$promptId.md")

    fun featureFile(featureId: String): File = toolDir.resolve("workflows").resolve("feature-$featureId.md")

    companion object {
        fun ofProject(projectDir: File) = ClaudeLayout(
            toolDir = projectDir.resolve(CLAUDE_DIR),
            instructionsFile = projectDir.resolve(INSTRUCTIONS_FILE),
        )

        fun ofUser(userHome: File) = ClaudeLayout(
            toolDir = userHome.resolve(CLAUDE_DIR),
            instructionsFile = userHome.resolve(CLAUDE_DIR).resolve(INSTRUCTIONS_FILE),
        )

        private const val CLAUDE_DIR = ".claude"
        private const val INSTRUCTIONS_FILE = "CLAUDE.md"
    }
}
