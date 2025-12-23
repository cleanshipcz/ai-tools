package cz.cleanship.aitools.engine.tools.adapters.github

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.feature
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
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
    private lateinit var adapter: GitHubCopilotAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".github").toFile()
        adapter = GitHubCopilotAdapter(printers)
    }

    @Test
    fun `should output a prompt`() {
        // given
        val prompt = PromptContext(prompt, rulesets)

        // when
        adapter.export(tempDir.toFile(), prompt)

        // then
        val expectedOutput = expectedPrompt.trim()
        assertThat(targetDir.resolve("prompts/prompt-${prompt.prompt.id}.prompt.md").readText().trim()).isEqualTo(expectedOutput)
    }

    @Test
    fun `should output an agent with applyTo frontmatter`() {
        // given
        val agent = agent

        // when
        adapter.export(tempDir.toFile(), AgentContext(agent, rulesets))

        // then
        val content = targetDir.resolve("instructions/agent-${agent.id}.instructions.md").readText().trim()
        
        assertThat(content).startsWith("---")
        assertThat(content).contains("applyTo: \"**\"")
        assertThat(content).contains(expectedAgent.trim())
    }

    @Test
    fun `should output a feature`() {
        // given
        val featureContext = FeatureContext(feature)

        // when
        adapter.export(tempDir.toFile(), featureContext)

        // then
        // We'll put features in workflows_docs or features folder?
        // Let's say `.github/features/`
        assertThat(targetDir.resolve("features/feature-${feature.id}.md").readText()).contains(feature.description)
    }
}
