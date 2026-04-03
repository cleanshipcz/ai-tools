package cz.cleanship.aitools.engine.tools.adapters.windsurf

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.expectedFeature
import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.feature
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.data.textOnlySkill
import cz.cleanship.aitools.engine.models.ProjectDeploy
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import io.mockk.every
import io.mockk.mockk
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

    private lateinit var adapter: WindsurfAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".windsurf").toFile()
        rulesDir = targetDir.resolve("rules")
        workflowsDir = targetDir.resolve("workflows")

        adapter = WindsurfAdapter(printers)
    }

    @Test
    fun `should output a prompt`() {
        // given
        val prompt = PromptContext(prompt, rulesets)

        // when
        adapter.export(tempDir.toFile(), prompt)

        // then
        assertThat(rulesDir.resolve("prompt-${prompt.prompt.id}.md").readText()).isEqualTo(withManualHeader(expectedPrompt))
    }

    @Test
    fun `should output an agent`() {
        // given
        val agent = agent

        // when
        adapter.export(tempDir.toFile(), AgentContext(agent, rulesets))

        // then
        assertThat(rulesDir.resolve("agent-${agent.id}.md").readText()).isEqualTo(withManualHeader(expectedAgent))
    }

    @Test
    fun `should output a feature`() {
        // given
        val featureContext = FeatureContext(feature)

        // when
        adapter.export(tempDir.toFile(), featureContext)

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
            """.trimMargin(),
        )
    }

    @Test
    fun `should output a skill`() {
        // given
        val skillContext = SkillContext(textOnlySkill)

        // when
        adapter.export(tempDir.toFile(), skillContext)

        // then
        val skillFile = rulesDir.resolve("skill-${textOnlySkill.id}.md")
        assertThat(skillFile).exists()
        val content = skillFile.readText()
        assertThat(content).startsWith("---\ntrigger: manual\n---")
        assertThat(content).contains(textOnlySkill.description)
    }

    @Test
    fun `prepare should delete windsurf directory when replace is true`() {
        // given
        val projectDir = tempDir.toFile()
        val windsurfDir = projectDir.resolve(".windsurf")
        windsurfDir.mkdirs()
        File(windsurfDir, "some-file.txt").writeText("content")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns true

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(windsurfDir).doesNotExist()
    }

    @Test
    fun `prepare should keep windsurf directory when replace is false`() {
        // given
        val projectDir = tempDir.toFile()
        val windsurfDir = projectDir.resolve(".windsurf")
        windsurfDir.mkdirs()
        File(windsurfDir, "some-file.txt").writeText("content")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns false

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(windsurfDir).exists()
        assertThat(File(windsurfDir, "some-file.txt")).exists()
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
