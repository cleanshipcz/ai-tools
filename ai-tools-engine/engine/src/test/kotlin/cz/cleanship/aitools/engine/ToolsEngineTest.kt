package cz.cleanship.aitools.engine

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.services.DuplicateManifestIdException
import cz.cleanship.aitools.engine.tools.adapters.claude.ClaudeAdapter
import cz.cleanship.aitools.engine.tools.adapters.cursor.CursorAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

class ToolsEngineTest {

    @TempDir
    lateinit var tempDir: Path

    private val logAppender = ListAppender<ILoggingEvent>()

    private lateinit var workspace: File
    private lateinit var destination: File
    private lateinit var engine: ToolsEngine
    private lateinit var engineLogger: Logger

    @BeforeEach
    fun setUp() {
        workspace = tempDir.resolve("workspace").toFile()
        destination = tempDir.resolve("destination").toFile()
        engine = ToolsEngine(workspace, tools = listOf(ClaudeAdapter()))
        engineLogger = LoggerFactory.getLogger(ToolsEngine::class.java) as Logger
        logAppender.start()
        engineLogger.addAppender(logAppender)
        // - a resolvable ruleset and the project that deploys into the destination are always present
        writeRuleset("base")
        writeProject()
    }

    @AfterEach
    fun tearDown() {
        engineLogger.detachAppender(logAppender)
        logAppender.stop()
        logAppender.list.clear()
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
    inner class DeployDirectoryResolution {

        @Test
        fun `should resolve a relative deploy directory against the working directory`() {
            // given
            // - a project deploying to a path relative to the working directory of the run
            val relativeDestination = "relative-destination"
            writeProject(deployDirectory = relativeDestination)

            // when
            engine.process(locations())

            // then
            assertThat(workspace.resolve(relativeDestination).resolve("CLAUDE.md")).exists()
            // - and nothing landed relative to the JVM working directory, which is a different base entirely
            assertThat(File(relativeDestination)).doesNotExist()
        }

        @Test
        fun `should resolve a relative deploy directory that climbs out of the working directory`() {
            // given
            // - the base of a `..` segment is the working directory, so the project lands next to it
            writeProject(deployDirectory = "../sibling-destination")

            // when
            engine.process(locations())

            // then
            assertThat(tempDir.resolve("sibling-destination").resolve("CLAUDE.md").toFile()).exists()
        }

        @Test
        fun `should use an absolute deploy directory as given`() {
            // given
            val absoluteDestination = tempDir.resolve("absolute-destination").toFile()
            writeProject(deployDirectory = absoluteDestination.absolutePath)

            // when
            engine.process(locations())

            // then
            assertThat(absoluteDestination.resolve("CLAUDE.md")).exists()
            // - an absolute destination is never re-based under the working directory
            assertThat(workspace.walkTopDown().filter { it.name == "CLAUDE.md" }.toList()).isEmpty()
        }
    }

    @Nested
    inner class ToolSelection {

        @Test
        fun `should export a project only through the tools it declares`() {
            // given
            // - the run configures two tools
            val multiAdapterEngine = ToolsEngine(workspace, tools = listOf(ClaudeAdapter(), CursorAdapter()))
            // - the project narrows itself down to one of them
            writeProject(tools = listOf("claude"))
            writeAgent("good-agent", "base")

            // when
            multiAdapterEngine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(agentFile("good-agent")).exists()
            assertThat(cursorProjectFile()).doesNotExist()
            assertThat(cursorAgentFile("good-agent")).doesNotExist()
        }

        @Test
        fun `should export a project through every configured tool when it declares none`() {
            // given
            // - the project omits deploy.tools entirely, leaving the run-wide tool list in charge
            val multiAdapterEngine = ToolsEngine(workspace, tools = listOf(ClaudeAdapter(), CursorAdapter()))
            writeAgent("good-agent", "base")

            // when
            multiAdapterEngine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(agentFile("good-agent")).exists()
            assertThat(cursorProjectFile()).exists()
            assertThat(cursorAgentFile("good-agent")).exists()
        }

        @Test
        fun `should export through the configured tools and warn when a declared tool is not configured`() {
            // given
            // - the run configures Claude alone, so the Cursor the project declares cannot be honoured
            writeProject(tools = listOf("claude", "cursor"))

            // when
            engine.process(locations())

            // then
            // - the run does not fail over a tool it was simply not asked to build
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(cursorProjectFile()).doesNotExist()
            // - the warning quotes the tool back in the spelling the manifest uses, so the author can grep for it
            assertThat(warnings()).anyMatch { it.contains("test-project") && it.contains("cursor") }
            assertThat(warnings()).noneMatch { it.contains("CURSOR") }
        }

        @Test
        fun `should name a repeated unavailable tool only once`() {
            // given
            // - the schema asks for unique items, but a manifest that repeats one should not stutter in the warning
            writeProject(tools = listOf("cursor", "cursor"))

            // when
            engine.process(locations())

            // then
            assertThat(warnings()).anyMatch { it.contains("[cursor]") }
        }

        @Test
        fun `should warn once when the run itself configures no tools`() {
            // given
            // - an engine with no adapters at all, which is what an emptied `tools:` block of the config produces
            val toollessEngine = ToolsEngine(workspace, tools = emptyList())
            // - a second project, so that the warning is proven to name the run once rather than once per project
            writeProject("second-project", "second-project", tempDir.resolve("second-destination").toString())

            // when
            toollessEngine.process(locations())

            // then
            // - the misconfiguration is named instead of every project being skipped in silence, and the run still succeeds
            assertThat(warnings().filter { it.contains("configures no tools") }).hasSize(1)
            assertThat(destination).doesNotExist()
        }

        @Test
        fun `should restrict one project without affecting another project of the same run`() {
            // given
            // - two projects deploy in the same run, only the first one restricts itself
            val multiAdapterEngine = ToolsEngine(workspace, tools = listOf(ClaudeAdapter(), CursorAdapter()))
            val unrestrictedDestination = tempDir.resolve("unrestricted-destination").toFile()
            writeProject(tools = listOf("claude"))
            writeProject("unrestricted-project", "unrestricted-project", unrestrictedDestination.absolutePath)

            // when
            multiAdapterEngine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(cursorProjectFile()).doesNotExist()
            // - the restriction of the first project does not leak onto the second one
            assertThat(unrestrictedDestination.resolve("CLAUDE.md")).exists()
            assertThat(unrestrictedDestination.resolve(".cursor/rules/project.mdc")).exists()
        }

        @Test
        fun `should export a project through no tool when it declares an empty tool list`() {
            // given
            // - an explicit empty list restricts to nothing, unlike an omitted one
            writeProject(tools = emptyList())

            // when
            engine.process(locations())

            // then
            assertThat(destination).doesNotExist()
            assertThat(warnings()).anyMatch { it.contains("test-project") && it.contains("not exported") }
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
    inner class DuplicateIds {

        @Test
        fun `should export the unaffected projects when two projects share an id`() {
            // given
            // - two project directories declare the same id, next to the healthy project written in setUp
            val firstDestination = tempDir.resolve("first-destination").toFile()
            val secondDestination = tempDir.resolve("second-destination").toFile()
            val firstFile = writeProject("duplicated-a", "duplicated-project", firstDestination.absolutePath)
            val secondFile = writeProject("duplicated-b", "duplicated-project", secondDestination.absolutePath)

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            // - the project that shares nothing with the collision deployed as usual
            assertThat(destination.resolve("CLAUDE.md")).exists()
            // - neither colliding project was exported, because neither may be picked as the winner
            assertThat(firstDestination).doesNotExist()
            assertThat(secondDestination).doesNotExist()
            // - and the run still fails, naming the id and both files
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("duplicated-project")
                .hasMessageContaining(firstFile.absolutePath)
                .hasMessageContaining(secondFile.absolutePath)
        }

        @Test
        fun `should export the unaffected projects when two features of one project share an id`() {
            // given
            val featureDestination = tempDir.resolve("feature-destination").toFile()
            writeProject("feature-project", "feature-project", featureDestination.absolutePath)
            val firstFile = writeFeature("feature-project", "first.yml", "duplicated-feature")
            val secondFile = writeFeature("feature-project", "second.yml", "duplicated-feature")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(featureDestination).doesNotExist()
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("duplicated-feature")
                .hasMessageContaining(firstFile.absolutePath)
                .hasMessageContaining(secondFile.absolutePath)
        }

        @Test
        fun `should report both the duplicate and the broken references of the same run`() {
            // given
            writeAgent("broken-agent", "base")
            writePrompt("broken-prompt", "missing-ruleset")
            writeProject("duplicated-a", "duplicated-project", tempDir.resolve("first-destination").toString())
            writeProject("duplicated-b", "duplicated-project", tempDir.resolve("second-destination").toString())

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("No rulesets match pattern 'missing-ruleset'")
                .hasMessageContaining("duplicated-project")
            assertThat((error as ExportFailedException).duplicates.map { it.id }).containsExactly("duplicated-project")
        }

        @Test
        fun `should export nothing when two rulesets share an id`() {
            // given
            // - rulesets are shared by every project, and a project with no ruleset filter deploys all of them,
            //   so exporting anything would silently ship content that lost one of its two sources
            writeRuleset("base", fileName = "duplicate-of-base.yml")
            writeAgent("good-agent", "base")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(DuplicateManifestIdException::class.java)
                .hasMessageContaining("base")
            assertThat(destination).doesNotExist()
        }

        @Test
        fun `should export nothing when two agents share an id`() {
            // given
            // - a dropped agent would leave every project silently missing an agent it never filtered out
            writeAgent("good-agent", "base")
            writeYaml(
                "agents/copy-of-good-agent.yml",
                "id: good-agent\ndescription: An agent\nrulesets:\n  - base\n" +
                    "persona: A persona\nprompt: An agent prompt\n",
            )

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(DuplicateManifestIdException::class.java)
            assertThat(destination).doesNotExist()
        }
    }

    @Nested
    inner class FailureReport {

        @Test
        fun `should count a manifest once when it fails for several adapters`() {
            // given
            // - every configured adapter exports the same broken agent, so it fails once per adapter
            val multiAdapterEngine = ToolsEngine(workspace, tools = listOf(ClaudeAdapter(), CursorAdapter()))
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

    private fun cursorProjectFile() = destination.resolve(".cursor/rules/project.mdc")

    private fun cursorAgentFile(id: String) = destination.resolve(".cursor/rules/agent-$id.mdc")

    private fun warnings() = logAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    private fun writeRuleset(id: String, fileName: String = "$id.yml") = writeYaml(
        "rulesets/$fileName",
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

    private fun writeProject(
        directoryName: String = "test-project",
        id: String = "test-project",
        deployDirectory: String = destination.absolutePath,
        tools: List<String>? = null,
    ) = writeYaml(
        "projects/$directoryName/project.yml",
        "id: $id\ndescription: A project\n" +
            "context:\n  documentation:\n    readme: README.md\n" +
            "deploy:\n  directory: \"$deployDirectory\"\n" + toolsDeclaration(tools),
    )

    /**
     * Renders the optional `deploy.tools` list: absent for `null`, an explicit empty list for an empty one, since
     * the two mean opposite things to the engine.
     */
    private fun toolsDeclaration(tools: List<String>?) = when {
        tools == null -> ""
        tools.isEmpty() -> "  tools: []\n"
        else -> tools.joinToString(separator = "", prefix = "  tools:\n") { "    - $it\n" }
    }

    private fun writeFeature(projectDirectoryName: String, fileName: String, id: String) = writeYaml(
        "projects/$projectDirectoryName/features/$fileName",
        "id: $id\ndescription: A feature\nprompt: A feature prompt\n",
    )

    private fun writeYaml(relativePath: String, content: String): File {
        val file = workspace.resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText(content + "metadata:\n  version: 1.0.0\n")
        return file
    }
}
