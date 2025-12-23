package cz.cleanship.aitools.engine.tools.adapters.cursor

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

class CursorAdapterTest {

    @TempDir
    lateinit var tempDir: Path

    private val printers = Printers
    private lateinit var targetDir: File
    private lateinit var adapter: CursorAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".cursor").toFile()
        adapter = CursorAdapter(printers)
    }

    @Test
    fun `should output a prompt`() {
        // given
        val prompt = PromptContext(prompt, rulesets)

        // when
        adapter.export(tempDir.toFile(), prompt)

        // then
        assertThat(targetDir.resolve("prompts/prompt-${prompt.prompt.id}.md").readText().trim()).isEqualTo(expectedPrompt.trim())
    }

    @Test
    fun `should output an agent with mdc frontmatter`() {
        // given
        val agent = agent

        // when
        adapter.export(tempDir.toFile(), AgentContext(agent, rulesets))

        // then
        val content = targetDir.resolve("rules/agent-${agent.id}.mdc").readText().trim()
        
        assertThat(content).startsWith("---")
        assertThat(content).contains("description: ${agent.description.replace("\n", " ")}")
        assertThat(content).contains("globs: \"**/*\"")
        assertThat(content).contains(expectedAgent.trim())
    }

    @Test
    fun `should output a feature`() {
        // given
        val featureContext = FeatureContext(feature)

        // when
        adapter.export(tempDir.toFile(), featureContext)

        // then
        assertThat(targetDir.resolve("features/feature-${feature.id}.md").readText()).contains(feature.description)
    }
}
