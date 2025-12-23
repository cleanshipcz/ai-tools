package cz.cleanship.aitools.engine.tools.adapters.claude

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.expectedFeature
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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@Disabled
class ClaudeAdapterTest {

    @TempDir
    lateinit var tempDir: Path

    private val printers = Printers
    private lateinit var targetDir: File
    private lateinit var claudeAdapter: ClaudeAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".claude").toFile()
        claudeAdapter = ClaudeAdapter(printers)
    }

    @Test
    fun `should output a prompt`() {
        // given
        val prompt = PromptContext(prompt, rulesets)

        // when
        claudeAdapter.export(tempDir.toFile(), prompt)

        // then
        assertThat(targetDir.resolve("prompt-${prompt.prompt.id}.md").readText().trim()).isEqualTo(expectedPrompt.trim())
    }

    @Test
    fun `should output an agent`() {
        // given
        val agent = agent

        // when
        claudeAdapter.export(tempDir.toFile(), AgentContext(agent, rulesets))

        // then
        assertThat(targetDir.resolve("agent-${agent.id}.md").readText().trim()).isEqualTo(expectedAgent.trim())
    }

    @Test
    fun `should output a feature`() {
        // given
        val featureContext = FeatureContext(feature)

        // when
        claudeAdapter.export(tempDir.toFile(), featureContext)

        // then
        assertThat(targetDir.resolve("workflows/feature-${feature.id}.md").readText()).contains(feature.description)
    }
}
