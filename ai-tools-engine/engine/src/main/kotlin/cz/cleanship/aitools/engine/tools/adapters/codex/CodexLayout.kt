package cz.cleanship.aitools.engine.tools.adapters.codex

import java.io.File

/**
 * Where one deploy of [CodexAdapter] writes each artifact. A project deploy writes the layout Codex reads inside a
 * repository, a user deploy the one it reads from the home directory; what is written to those paths is identical,
 * which is why the adapter renders once and only chooses a layout.
 *
 * Codex has one shape for everything it can be asked to do, so agents and prompts are skills here too, told apart
 * from each other by the prefix of their directory.
 *
 * @param toolDir the `.codex` directory of this scope, which a project deploy may replace as a whole and a user
 * deploy never touches beyond the artifacts it writes inside it
 * @param instructionsFile the instructions file Codex reads for this scope, which sits beside `.codex` in a project
 * and inside it in the user scope
 */
internal class CodexLayout(
    val toolDir: File,
    val instructionsFile: File,
) {
    fun skillDir(skillId: String): File = skillsDir.resolve("skill-$skillId")

    fun agentDir(agentId: String): File = skillsDir.resolve("agent-$agentId")

    fun promptDir(promptId: String): File = skillsDir.resolve("prompt-$promptId")

    fun featureFile(featureId: String): File = toolDir.resolve("features").resolve("feature-$featureId.md")

    /** The directory holding every skill-shaped artifact of this scope, which a deploy writes into but never removes. */
    val skillsDir: File get() = toolDir.resolve("skills")

    companion object {
        fun ofProject(projectDir: File) = CodexLayout(
            toolDir = projectDir.resolve(CODEX_DIR),
            instructionsFile = projectDir.resolve(INSTRUCTIONS_FILE),
        )

        fun ofUser(userHome: File): CodexLayout {
            val toolDir = userHome.resolve(CODEX_DIR)
            return CodexLayout(toolDir, instructionsFile = toolDir.resolve(INSTRUCTIONS_FILE))
        }

        private const val CODEX_DIR = ".codex"
        private const val INSTRUCTIONS_FILE = "AGENTS.md"
    }
}
