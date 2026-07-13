package cz.cleanship.aitools.engine.tools.adapters.github

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.expectedFeature
import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.feature
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.data.textOnlySkill
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class GitHubCopilotAdapterTest {

    @TempDir
    lateinit var tempDir: Path

    private val printers = Printers
    private lateinit var targetDir: File
    private lateinit var promptsDir: File
    private lateinit var instructionsDir: File

    private lateinit var adapter: GitHubCopilotAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".github").toFile()
        promptsDir = targetDir.resolve("prompts")
        instructionsDir = targetDir.resolve("instructions")
        adapter = GitHubCopilotAdapter(printers)
    }

    @Test
    fun `should output a prompt`() {
        // given
        val prompt = PromptContext(prompt, rulesets)

        // when
        adapter.export(tempDir.toFile(), prompt)

        // then
        assertThat(promptsDir.resolve("prompt-${prompt.prompt.id}.prompt.md").readText()).isEqualTo(withApplyToHeader(expectedPrompt))
    }

    @Test
    fun `should output an agent`() {
        // given
        val agent = AgentContext(agent, rulesets)

        // when
        adapter.export(tempDir.toFile(), agent)

        // then
        assertThat(instructionsDir.resolve("agent-${agent.agent.id}.instructions.md").readText()).isEqualTo(withApplyToHeader(expectedAgent))
    }

    @Test
    fun `should output a feature`() {
        // given
        val featureContext = FeatureContext(feature)

        // when
        adapter.export(tempDir.toFile(), featureContext)

        // then
        assertThat(instructionsDir.resolve("feature-${featureContext.feature.id}.instructions.md").readText()).isEqualTo(withApplyToHeader(expectedFeature))
    }

    @Test
    fun `should output a skill`() {
        // given
        val skillContext = SkillContext(textOnlySkill)

        // when
        adapter.export(tempDir.toFile(), skillContext)

        // then
        val skillFile = promptsDir.resolve("skill-${textOnlySkill.id}.prompt.md")
        assertThat(skillFile).exists()
        val content = skillFile.readText()
        assertThat(content).startsWith("---\napplyTo: \"**/*\"\n---")
        assertThat(content).contains(textOnlySkill.description)
    }

    private fun withApplyToHeader(content: String) = """
        |---
        |applyTo: "**/*"
        |---
        |
        |$content
        |
        """.trimMargin()
}
