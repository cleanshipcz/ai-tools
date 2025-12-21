package cz.cleanship.aitools.engine.tools.adapters.windsurf

import cz.cleanship.aitools.engine.services.LoaderService
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.utils.getExpectedOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class WindsurfAdapterTest {

    @TempDir
    lateinit var tempDir: Path

    private val loaderService = LoaderService()
    private val printers = Printers
    private lateinit var targetDir: File
    private lateinit var rulesDir: File
    private lateinit var instructionsDir: File

    private lateinit var windsurfAdapter: WindsurfAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".windsurf").toFile()
        rulesDir = targetDir.resolve("rules")
        instructionsDir = targetDir.resolve("instructions")

        windsurfAdapter = WindsurfAdapter(printers)
    }

    @Test
    fun `should output a prompt`() {
        // given
        val file = File(javaClass.getResource("/prompts/summarize-pr.yml")!!.toURI())
        val prompt = loaderService.loadPrompt(file)

        // when
        windsurfAdapter.export(tempDir.toFile(), prompt)

        // then
        val expected = getExpectedOutput(printers.promptPrinter, prompt)
        assertThat(rulesDir.resolve("prompt-summarize-pr.md").readText()).contains(expected)
    }

    @Test
    fun `should output an agent`() {
        // given
        val file = File(javaClass.getResource("/agents/code-reviewer.yml")!!.toURI())
        val agent = loaderService.loadAgent(file)

        // when
        windsurfAdapter.export(tempDir.toFile(), agent)

        // then
        val expected = getExpectedOutput(printers.agentPrinter, agent)
        assertThat(rulesDir.resolve("agent-code-reviewer.md").readText()).contains(expected)
    }
}
