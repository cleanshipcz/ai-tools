package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.tools.adapters.claude.ClaudeAdapter
import cz.cleanship.aitools.engine.tools.adapters.cursor.CursorAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ToolsEngineTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var workspace: File
    private lateinit var destination: File
    private lateinit var engine: ToolsEngine

    @BeforeEach
    fun setUp() {
        workspace = tempDir.resolve("workspace").toFile()
        destination = tempDir.resolve("destination").toFile()
        engine = ToolsEngine(tools = listOf(ClaudeAdapter()))
        // - a resolvable ruleset and the project that deploys into the destination are always present
        writeRuleset("base")
        writeProject()
    }

    @Nested
    inner class SuccessfulExport {

        @Test
        fun `should export every manifest when all references resolve`() {
            // given
            writeAgent("good-agent", "base")
            writePrompt("good-prompt", "base")

            // when
            engine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(agentFile("good-agent")).exists()
            assertThat(agentFile("good-agent").readText()).contains("A rule from base.")
            assertThat(promptFile("good-prompt")).exists()
            assertThat(promptFile("good-prompt").readText()).contains("A rule from base.")
        }

        @Test
        fun `should export the project memory file when the project has no agents or prompts`() {
            // when
            engine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
        }
    }

    @Nested
    inner class UnresolvableReferences {

        @Test
        fun `should fail with the resolver message when an agent references an unknown ruleset`() {
            // given
            writeAgent("broken-agent", "missing-ruleset")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("No rulesets match pattern 'missing-ruleset'")
                .hasMessageContaining("required by agent 'broken-agent'")
                .hasMessageContaining("All available: [base]")
        }

        @Test
        fun `should not leave a truncated file when an agent references an unknown ruleset`() {
            // given
            // - the ruleset is resolved half-way through printing, after the frontmatter has been written
            writeAgent("broken-agent", "missing-ruleset")

            // when
            runCatching { engine.process(locations()) }

            // then
            assertThat(agentFile("broken-agent")).doesNotExist()
        }

        @Test
        fun `should replace no previously exported file when a manifest starts failing`() {
            // given
            // - a first run exported the agent successfully
            writeAgent("agent", "base")
            engine.process(locations())
            val exportedContent = agentFile("agent").readText()

            // - the agent is then edited to reference a ruleset that does not exist
            writeAgent("agent", "missing-ruleset")

            // when
            runCatching { engine.process(locations()) }

            // then
            assertThat(agentFile("agent").readText()).isEqualTo(exportedContent)
        }

        @Test
        fun `should export the remaining manifests when one manifest fails`() {
            // given
            // - agents are exported before prompts, so a broken agent used to skip every prompt
            writeAgent("broken-agent", "missing-ruleset")
            writePrompt("good-prompt", "base")

            // when
            runCatching { engine.process(locations()) }

            // then
            assertThat(promptFile("good-prompt")).exists()
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(agentFile("broken-agent")).doesNotExist()
        }

        @Test
        fun `should report every broken reference of a single run together`() {
            // given
            writeAgent("broken-agent", "missing-ruleset")
            writePrompt("broken-prompt", "another-missing-ruleset")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(ExportFailedException::class.java)
            assertThat((error as ExportFailedException).failures.map { it.manifest })
                .containsExactlyInAnyOrder("agent 'broken-agent'", "prompt 'broken-prompt'")
            assertThat(error)
                .hasMessageContaining("missing-ruleset")
                .hasMessageContaining("another-missing-ruleset")
        }
    }

    @Nested
    inner class UnusableSkillFiles {

        @Test
        fun `should report the failure when a standalone skill declares a relative file`() {
            // given
            // - a standalone skill YAML has no directory of its own, so 'helper.md' cannot be resolved
            writeStandaloneSkill("standalone-skill", "helper.md")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("Cannot resolve relative skill file 'helper.md'")
            assertThat((error as ExportFailedException).failures.map { it.manifest })
                .containsExactly("skill 'standalone-skill'")
        }

        @Test
        fun `should report the failure when a skill declares a companion file that does not exist`() {
            // given
            // - the skill directory exists but the declared companion file was never written
            writeDirectorySkill("directory-skill", "missing.md", companionFileExists = false)

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("missing.md")
            assertThat((error as ExportFailedException).failures.map { it.manifest })
                .containsExactly("skill 'directory-skill'")
        }

        @Test
        fun `should export the remaining manifests when a skill file cannot be copied`() {
            // given
            // - the first skill fails on resolution, the second one on the copy itself
            writeStandaloneSkill("standalone-skill", "helper.md")
            writeDirectorySkill("directory-skill", "missing.md", companionFileExists = false)
            writeAgent("good-agent", "base")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat((error as ExportFailedException).failures.map { it.manifest })
                .containsExactlyInAnyOrder("skill 'standalone-skill'", "skill 'directory-skill'")
            assertThat(agentFile("good-agent")).exists()
        }

        @Test
        fun `should export the skill when its companion file exists`() {
            // given
            writeDirectorySkill("directory-skill", "helper.md", companionFileExists = true)

            // when
            engine.process(locations())

            // then
            assertThat(destination.resolve(".claude/skills/directory-skill/SKILL.md")).exists()
            assertThat(destination.resolve(".claude/skills/directory-skill/helper.md")).exists()
        }
    }

    @Nested
    inner class FailureReport {

        @Test
        fun `should count a manifest once when it fails for several adapters`() {
            // given
            // - every configured adapter exports the same broken agent, so it fails once per adapter
            val multiAdapterEngine = ToolsEngine(tools = listOf(ClaudeAdapter(), CursorAdapter()))
            writeAgent("broken-agent", "missing-ruleset")

            // when
            val error = runCatching { multiAdapterEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("Export failed for 1 manifest(s):")
            assertThat((error as ExportFailedException).failures).hasSize(2)
            assertThat(error.message)
                .contains("[test-project | CLAUDE | agent 'broken-agent']")
                .contains("[test-project | CURSOR | agent 'broken-agent']")
        }

        @Test
        fun `should count each broken manifest when several manifests fail`() {
            // given
            writeAgent("broken-agent", "missing-ruleset")
            writePrompt("broken-prompt", "another-missing-ruleset")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error).hasMessageContaining("Export failed for 2 manifest(s):")
        }
    }

    private fun locations() = Locations(
        agents = listOf(workspace.resolve("agents")),
        projects = listOf(workspace.resolve("projects")),
        prompts = listOf(workspace.resolve("prompts")),
        rulesets = listOf(workspace.resolve("rulesets")),
        fragments = emptyList(),
        skills = listOf(workspace.resolve("skills")),
    )

    private fun agentFile(id: String) = destination.resolve(".claude/agents/$id.md")

    private fun promptFile(id: String) = destination.resolve(".claude/commands/$id.md")

    private fun writeRuleset(id: String) = writeYaml(
        "rulesets/$id.yml",
        "id: $id\ndescription: A ruleset\nrules:\n  - A rule from $id.\n",
    )

    private fun writeAgent(id: String, ruleset: String) = writeYaml(
        "agents/$id.yml",
        "id: $id\ndescription: An agent\nrulesets:\n  - $ruleset\n" +
            "persona: A persona\nprompt: An agent prompt\n",
    )

    private fun writePrompt(id: String, ruleset: String) = writeYaml(
        "prompts/$id.yml",
        "id: $id\ndescription: A prompt\nrulesets:\n  - $ruleset\ncontent: Some prompt content\n",
    )

    private fun writeStandaloneSkill(id: String, file: String) = writeYaml(
        "skills/$id.yml",
        "id: $id\ndescription: A skill\nsections:\n  - text: Some skill content\n" +
            "files:\n  - path: $file\n",
    )

    private fun writeDirectorySkill(id: String, file: String, companionFileExists: Boolean): File {
        val manifest = writeYaml(
            "skills/$id/skill.yml",
            "id: $id\ndescription: A skill\nsections:\n  - text: Some skill content\n" +
                "files:\n  - path: $file\n",
        )
        if (companionFileExists) {
            manifest.parentFile.resolve(file).writeText("Companion content.\n")
        }
        return manifest
    }

    private fun writeProject() = writeYaml(
        "projects/test-project/project.yml",
        "id: test-project\ndescription: A project\n" +
            "context:\n  documentation:\n    readme: README.md\n" +
            "deploy:\n  directory: \"${destination.absolutePath}\"\n",
    )

    private fun writeYaml(relativePath: String, content: String): File {
        val file = workspace.resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText(content + "metadata:\n  version: 1.0.0\n")
        return file
    }
}
