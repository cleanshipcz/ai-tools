package cz.cleanship.aitools.engine.tools.adapters.cursor

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.feature
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.data.textOnlySkill
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectContext
import cz.cleanship.aitools.engine.models.ProjectDeploy
import cz.cleanship.aitools.engine.models.ProjectDocumentation
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.GlobalContext
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
    fun `should output project context rule`() {
        // given
        val project = ProjectManifest(
            id = "test-project",
            description = "Test Project",
            metadata = ManifestMetadata(version = Version("1.0.0")),
            context = ProjectContext(
                rules = listOf("Rule one."),
                overview = "Overview",
                documentation = ProjectDocumentation(readme = "README.md"),
            ),
            deploy = ProjectDeploy(directory = tempDir.toString()),
        )

        // when
        adapter.export(tempDir.toFile(), GlobalContext(project))

        // then
        val content = targetDir.resolve("rules/project.mdc").readText().trim()
        assertThat(content).startsWith("---")
        assertThat(content).contains("description: Test Project")
        assertThat(content).contains("globs: \"**/*\"")
        assertThat(content).contains("alwaysApply: true")
        assertThat(content).contains("# test-project")
    }

    @Test
    fun `should output a prompt`() {
        // given
        val prompt = PromptContext(prompt, rulesets)

        // when
        adapter.export(tempDir.toFile(), prompt)

        // then
        assertThat(targetDir.resolve("commands/prompt-${prompt.prompt.id}.md").readText().trim()).isEqualTo(expectedPrompt.trim())
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

    @Test
    fun `should output a skill`() {
        // given
        val skillContext = SkillContext(textOnlySkill)

        // when
        adapter.export(tempDir.toFile(), skillContext)

        // then
        val skillFile = targetDir.resolve("commands/skill-${textOnlySkill.id}.md")
        assertThat(skillFile).exists()
        assertThat(skillFile.readText()).contains(textOnlySkill.description)
    }

    @Test
    fun `prepare should delete cursor directory when replace is true`() {
        // given
        val projectDir = tempDir.toFile()
        val cursorDir = projectDir.resolve(".cursor")
        cursorDir.mkdirs()
        File(cursorDir, "some-file.txt").writeText("content")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns true

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(cursorDir).doesNotExist()
    }

    @Test
    fun `prepare should keep cursor directory when replace is false`() {
        // given
        val projectDir = tempDir.toFile()
        val cursorDir = projectDir.resolve(".cursor")
        cursorDir.mkdirs()
        File(cursorDir, "some-file.txt").writeText("content")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns false

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(cursorDir).exists()
        assertThat(File(cursorDir, "some-file.txt")).exists()
    }
}
