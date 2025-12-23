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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

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
        // We probably don't have a specific defined format for "workflows" in Claude yet in the original sense,
        // but Antigravity exports features.
        // Assuming we just dump the feature description or content.
        // The implementation plan didn't specify strict Feature exports for Claude, but the interface requires update.
        // I'll stick to a simple markdown export for now.
        // Wait, standard `CLAUDE.md` is mostly context.
        // I will assume for now we write feature to a file in .claude/
        // Actually, the AntigravityAdapter writes "workflows/feature-X.md".
        // I'll put it in .claude/workflows/feature-X.md for consistency if not specified.
        // The plan didn't specify workflows for Claude, but Interface forces implementation.
        
        assertThat(targetDir.resolve("workflows/feature-${feature.id}.md").readText()).contains(feature.description)
    }
}
