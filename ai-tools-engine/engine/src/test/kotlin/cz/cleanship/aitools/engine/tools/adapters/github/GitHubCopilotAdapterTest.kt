package cz.cleanship.aitools.engine.tools.adapters.github

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.expectedFeature
import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.expectedTextOnlySkill
import cz.cleanship.aitools.engine.data.feature
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.data.textOnlySkill
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectContext
import cz.cleanship.aitools.engine.models.ProjectDeploy
import cz.cleanship.aitools.engine.models.ProjectDocumentation
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.PromptVariable
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import cz.cleanship.aitools.engine.tools.adapters.claude.ClaudeAdapter
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

class GitHubCopilotAdapterTest {

    @TempDir
    lateinit var tempDir: Path

    private val printers = Printers
    private val logAppender = ListAppender<ILoggingEvent>()

    private lateinit var targetDir: File
    private lateinit var promptsDir: File
    private lateinit var instructionsDir: File
    private lateinit var agentsDir: File
    private lateinit var adapterLogger: Logger

    private lateinit var adapter: GitHubCopilotAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".github").toFile()
        promptsDir = targetDir.resolve("prompts")
        instructionsDir = targetDir.resolve("instructions")
        agentsDir = targetDir.resolve("agents")
        adapter = GitHubCopilotAdapter(printers)
        adapterLogger = LoggerFactory.getLogger(GitHubCopilotAdapter::class.java) as Logger
        logAppender.start()
        adapterLogger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        adapterLogger.detachAppender(logAppender)
        logAppender.stop()
    }

    @Test
    fun `should output the project context to copilot-instructions`() {
        // given
        val globalContext = GlobalContext(project)

        // when
        adapter.export(tempDir.toFile(), globalContext)

        // then
        assertThat(targetDir.resolve("copilot-instructions.md").readText())
            .isEqualTo("$expectedCopilotInstructions\n")
    }

    @Test
    fun `should output the project context exactly once and without trailing whitespace`() {
        // given
        // - the global file is owned by the global file printer alone, no adapter header is prepended
        val globalContext = GlobalContext(project)

        // when
        adapter.export(tempDir.toFile(), globalContext)

        // then
        val content = targetDir.resolve("copilot-instructions.md").readText()
        assertThat(content.lines().filter { it.startsWith("# ") }).containsExactly("# ${project.id}")
        assertThat(content).containsOnlyOnce(project.description)
        assertThat(content.lines().filter { it != it.trimEnd() }).isEmpty()
    }

    @Test
    fun `should output a prompt as a VS Code prompt file`() {
        // given
        val promptContext = PromptContext(prompt, rulesets)

        // when
        adapter.export(tempDir.toFile(), promptContext)

        // then
        val content = promptsDir.resolve("prompt-${prompt.id}.prompt.md").readText()
        assertThat(content).isEqualTo(
            withFrontmatter(
                """
                |---
                |name: id#1
                |description: Multiline description#1
                |argument-hint: <var1> [var2]
                |---
                """.trimMargin(),
                expectedCopilotPrompt,
            ),
        )
    }

    @Test
    fun `should omit argument-hint when a prompt declares no variables`() {
        // given
        val promptContext = PromptContext(prompt.copy(variables = emptyList()), rulesets)

        // when
        adapter.export(tempDir.toFile(), promptContext)

        // then
        val content = promptsDir.resolve("prompt-${prompt.id}.prompt.md").readText()
        assertThat(content).startsWith(
            """
            |---
            |name: id#1
            |description: Multiline description#1
            |---
            """.trimMargin(),
        )
        assertThat(content).doesNotContain("argument-hint")
    }

    @ParameterizedTest
    @CsvSource(
        "'var1:true', '<var1>'",
        "'var1:false', '[var1]'",
        "'var1:true|var2:false', '<var1> [var2]'",
        "'style:false|target:true', '[style] <target>'",
    )
    fun `should render argument-hint from the declared variables`(specification: String, expectedHint: String) {
        // given
        // - variables declared as `name:required` pairs, in manifest order, separated by a pipe
        val variables = specification.split("|").map { it.split(":") }.map { (name, required) ->
            PromptVariable(name = name, required = required.toBoolean(), description = "Description of $name")
        }
        val promptContext = PromptContext(prompt.copy(variables = variables), rulesets)

        // when
        adapter.export(tempDir.toFile(), promptContext)

        // then
        val content = promptsDir.resolve("prompt-${prompt.id}.prompt.md").readText()
        assertThat(content).contains("\nargument-hint: $expectedHint\n")
    }

    @Test
    fun `should rewrite declared variable placeholders in the prompt content`() {
        // given
        // - content referencing a declared variable and an undeclared placeholder
        val promptContext = PromptContext(
            prompt.copy(content = "Use {{var1}} together with {{unknown}}."),
            rulesets,
        )

        // when
        adapter.export(tempDir.toFile(), promptContext)

        // then
        val content = promptsDir.resolve("prompt-${prompt.id}.prompt.md").readText()
        assertThat(content).contains("Use ${input("var1", "Multiline description#2")} together with {{unknown}}.")
        assertThat(content).doesNotContain("{{var1}}")
    }

    @Test
    fun `should not add applyTo to a prompt`() {
        // given
        val promptContext = PromptContext(prompt, rulesets)

        // when
        adapter.export(tempDir.toFile(), promptContext)

        // then
        assertThat(promptsDir.resolve("prompt-${prompt.id}.prompt.md").readText()).doesNotContain("applyTo")
    }

    @Test
    fun `should output an agent as a custom agent profile`() {
        // given
        val agentContext = AgentContext(agent, rulesets)

        // when
        adapter.export(tempDir.toFile(), agentContext)

        // then
        val content = agentsDir.resolve("${agent.id}.agent.md").readText()
        assertThat(content).isEqualTo(
            withFrontmatter(
                """
                |---
                |name: test-agent
                |description: Multiline description
                |---
                """.trimMargin(),
                expectedAgent,
            ),
        )
    }

    @Test
    fun `should not output an agent as an always-on instruction file`() {
        // given
        val agentContext = AgentContext(agent, rulesets)

        // when
        adapter.export(tempDir.toFile(), agentContext)

        // then
        assertThat(instructionsDir.resolve("agent-${agent.id}.instructions.md")).doesNotExist()
        assertThat(agentsDir.resolve("${agent.id}.agent.md").readText()).doesNotContain("applyTo")
    }

    @Test
    fun `should warn when an agent body exceeds the custom agent character limit`() {
        // given
        // - an agent whose rendered body is past the documented 30 000 character limit
        val oversizedAgent = agent.copy(prompt = "x".repeat(30_000))

        // when
        adapter.export(tempDir.toFile(), AgentContext(oversizedAgent, rulesets))

        // then
        assertThat(warnings()).hasSize(1)
        assertThat(warnings().first()).contains(oversizedAgent.id, "30000")
        assertThat(agentsDir.resolve("${oversizedAgent.id}.agent.md").readText()).contains("x".repeat(30_000))
    }

    @Test
    fun `should not warn when an agent body fits the custom agent character limit`() {
        // given
        val agentContext = AgentContext(agent, rulesets)

        // when
        adapter.export(tempDir.toFile(), agentContext)

        // then
        assertThat(warnings()).isEmpty()
    }

    @Test
    fun `should output a feature as an always-on instruction file`() {
        // given
        val featureContext = FeatureContext(feature)

        // when
        adapter.export(tempDir.toFile(), featureContext)

        // then
        assertThat(instructionsDir.resolve("feature-${featureContext.feature.id}.instructions.md").readText())
            .isEqualTo(withApplyToHeader(expectedFeature))
    }

    @Test
    fun `should output a skill as a VS Code prompt file`() {
        // given
        val skillContext = SkillContext(textOnlySkill)

        // when
        adapter.export(tempDir.toFile(), skillContext)

        // then
        val content = promptsDir.resolve("skill-${textOnlySkill.id}.prompt.md").readText()
        assertThat(content).isEqualTo(
            withFrontmatter(
                """
                |---
                |name: run-pytest
                |description: Run Python tests with pytest
                |---
                """.trimMargin(),
                expectedTextOnlySkill,
            ),
        )
    }

    @Test
    fun `should not add applyTo or argument-hint to a skill`() {
        // given
        val skillContext = SkillContext(textOnlySkill)

        // when
        adapter.export(tempDir.toFile(), skillContext)

        // then
        val content = promptsDir.resolve("skill-${textOnlySkill.id}.prompt.md").readText()
        assertThat(content).doesNotContain("applyTo")
        assertThat(content).doesNotContain("argument-hint")
    }

    @ParameterizedTest
    @CsvSource("true", "false")
    fun `should always delete the generated directories on prepare`(replace: Boolean) {
        // given
        // - leftovers from earlier exports in every directory this adapter owns
        writeFile(promptsDir.resolve("stale.prompt.md"))
        writeFile(instructionsDir.resolve("stale.instructions.md"))
        writeFile(agentsDir.resolve("stale.agent.md"))
        // - files the adapter must never remove
        val copilotInstructions = writeFile(targetDir.resolve("copilot-instructions.md"))
        val workflow = writeFile(targetDir.resolve("workflows/ci.yml"))
        // - a project manifest with the given global replace flag
        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns replace

        // when
        adapter.prepare(tempDir.toFile(), manifest)

        // then
        assertThat(promptsDir).doesNotExist()
        assertThat(instructionsDir).doesNotExist()
        assertThat(agentsDir).doesNotExist()
        assertThat(copilotInstructions).exists()
        assertThat(workflow).exists()
    }

    @Test
    fun `should leave the prompt output of other adapters untouched`() {
        // given
        // - the prompt printer is shared, so the Copilot placeholder rewrite must not leak into it
        val claudeAdapter = ClaudeAdapter(printers)

        // when
        claudeAdapter.export(tempDir.toFile(), PromptContext(prompt, rulesets))

        // then
        val content = tempDir.resolve(".claude/commands/${prompt.id}.md").toFile().readText()
        assertThat(content).isEqualTo("$expectedPrompt\n")
        assertThat(content).contains("`{{var1}}`", "`{{var2}}`")
    }

    private fun warnings() = logAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    private fun writeFile(file: File): File = file.apply {
        parentFile.mkdirs()
        writeText("leftover")
    }

    private fun input(name: String, description: String) = "\${input:$name:$description}"

    private fun withFrontmatter(frontmatter: String, body: String) = "$frontmatter\n\n$body\n"

    private fun withApplyToHeader(content: String) = """
        |---
        |applyTo: "**/*"
        |---
        |
        |$content
        |
        """.trimMargin()

    private val project = ProjectManifest(
        id = "test-project",
        description = "Test Project",
        metadata = ManifestMetadata(version = Version("1.0.0")),
        context = ProjectContext(
            rules = listOf("Rule one."),
            overview = "Overview",
            documentation = ProjectDocumentation(readme = "README.md"),
        ),
        deploy = ProjectDeploy(directory = "target"),
    )

    private val expectedCopilotInstructions =
        """
        # test-project

        Test Project

        ## Overview

        Overview

        ## Rules

        - Rule one.

        ## Documentation files

        - README.md

        """.trimIndent()

    private val expectedCopilotPrompt =
        """
        # id#1

        Multiline
        description#1

        ## Variables

        - `${input("var1", "Multiline description#2")}` (required): Multiline
          description#2
        - `${input("var2", "Multiline description#3")}`: Multiline
          description#3

        ## Rules

        - Rule number one.
        - Rule number two.
        - Rule number three.
        - rule1
        - rule2

        ## Prompt

        Multiline
        content#3

        """.trimIndent()
}
