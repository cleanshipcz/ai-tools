package cz.cleanship.aitools.engine.tools.adapters.windsurf

import cz.cleanship.aitools.engine.data.*
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class WindsurfAdapterTest {

    @TempDir
    lateinit var tempDir: Path

    private val printers = Printers
    private lateinit var targetDir: File
    private lateinit var rulesDir: File
    private lateinit var workflowsDir: File
    private lateinit var output: StringOutput

    private lateinit var windsurfAdapter: WindsurfAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".windsurf").toFile()
        rulesDir = targetDir.resolve("rules")
        workflowsDir = targetDir.resolve("workflows")
        output = StringOutput()

        windsurfAdapter = WindsurfAdapter(printers)
    }

    @Test
    fun `should output a prompt`() {
        // given
        val prompt = PromptContext(prompt)

        // when
        windsurfAdapter.export(tempDir.toFile(), prompt)

        // then
        assertThat(rulesDir.resolve("prompt-${prompt.prompt.id}.md").readText()).isEqualTo(withManualHeader(expectedPrompt))
    }

    @Test
    fun `should output an agent`() {
        // given
        val agent = agent

        // when
        windsurfAdapter.export(tempDir.toFile(), AgentContext(agent, rulepacks))

        // then
        assertThat(rulesDir.resolve("agent-${agent.id}.md").readText()).isEqualTo(withManualHeader(expectedAgent))
    }


    @Test
    fun `should output a feature`() {
        // given
        val featureContext = FeatureContext(feature)

        // when
        windsurfAdapter.export(tempDir.toFile(), featureContext)

        // then
        assertThat(workflowsDir.resolve("feature-${feature.id}.md").readText()).isEqualTo(
            """
            |---
            |description: ${feature.description.replace("\n", " ")}
            |auto_execution_mode: 3
            |---
            |
            |$expectedFeature
            |
        """.trimMargin()
        )
    }

    private fun withManualHeader(content: String) = """
        |---
        |trigger: manual
        |---
        |
        |$content
        |
        """.trimMargin()
}
